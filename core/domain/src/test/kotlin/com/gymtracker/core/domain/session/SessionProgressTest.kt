package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Groundwork for a future rest-panel revisit (`adr/0023-the-rest-period-earns-its-space.md`),
 * not yet wired into any screen. A pure function of one session's movements: how many are
 * done, which one is current, which are still to come — table-driven against hand-picked
 * fixtures, per `specs/testing-strategy.md`.
 */
class SessionProgressTest {
    private val startedAt: Instant = Instant.parse("2026-08-01T17:00:00Z")
    private val member = UserId("alice")
    private val sessionId = SessionId("s1")

    private fun session(routine: RoutineOrigin? = null) =
        WorkoutSession(
            id = sessionId,
            userId = member,
            gymName = null,
            startedAt = startedAt,
            endedAt = null,
            metrics = null,
            routine = routine,
        )

    private fun appearance(
        id: String,
        position: Int,
        exercise: ExerciseId = ExerciseId("bench"),
    ) = SessionExercise(SessionExerciseId(id), sessionId, exercise, position)

    private fun set(
        appearance: String,
        index: Int = 1,
    ) = ExerciseSet(
        id = "$appearance-$index",
        sessionExerciseId = SessionExerciseId(appearance),
        setIndex = index,
        weightKg = 60.0,
        reps = 10,
        rpe = null,
        performedAt = startedAt,
    )

    @Test
    fun `an empty session has nothing done, nothing current, nothing to come`() {
        val progress = SessionProgress.of(session(), exercises = emptyList(), sets = emptyList())

        assertEquals(0, progress.movementsTotal)
        assertEquals(0, progress.movementsDone)
        assertNull(progress.current)
        assertEquals(emptyList(), progress.stillToCome)
    }

    @Test
    fun `nothing performed yet, so the first movement is current and the rest are still to come`() {
        val exercises = listOf(appearance("se-1", 1), appearance("se-2", 2), appearance("se-3", 3))

        val progress = SessionProgress.of(session(), exercises, sets = emptyList())

        assertEquals(3, progress.movementsTotal)
        assertEquals(0, progress.movementsDone)
        assertEquals("se-1", progress.current?.id?.value)
        assertEquals(listOf("se-2", "se-3"), progress.stillToCome.map { it.id.value })
    }

    @Test
    fun `a movement with at least one set logged counts as done`() {
        val exercises = listOf(appearance("se-1", 1), appearance("se-2", 2))
        val sets = listOf(set("se-1"))

        val progress = SessionProgress.of(session(), exercises, sets)

        assertEquals(1, progress.movementsDone)
        assertEquals("se-2", progress.current?.id?.value, "the untouched one is next")
        assertEquals(emptyList(), progress.stillToCome, "nothing after the current one")
    }

    @Test
    fun `every movement done leaves nothing current and nothing still to come`() {
        val exercises = listOf(appearance("se-1", 1), appearance("se-2", 2))
        val sets = listOf(set("se-1"), set("se-2"))

        val progress = SessionProgress.of(session(), exercises, sets)

        assertEquals(2, progress.movementsDone)
        assertNull(progress.current)
        assertEquals(emptyList(), progress.stillToCome)
    }

    @Test
    fun `two appearances of the same exercise are two movements`() {
        // ADR-0004: the same exercise may appear twice in a session, and each appearance is
        // its own row in the plan, done or not independently of the other.
        val exercises = listOf(appearance("se-1", 1), appearance("se-2", 2))
        val sets = listOf(set("se-1"))

        val progress = SessionProgress.of(session(), exercises, sets)

        assertEquals(2, progress.movementsTotal)
        assertEquals(1, progress.movementsDone)
    }

    @Test
    fun `skipping ahead leaves the untouched earlier movement current, not the one just logged`() {
        // A member can log out of order — position 2 first, then position 1. "Current" reads
        // the plan's order, not the order sets happened to land in, so it still names position
        // 1 as what is outstanding rather than jumping past it.
        val exercises = listOf(appearance("se-1", 1), appearance("se-2", 2), appearance("se-3", 3))
        val sets = listOf(set("se-2"))

        val progress = SessionProgress.of(session(), exercises, sets)

        assertEquals(1, progress.movementsDone)
        assertEquals("se-1", progress.current?.id?.value)
        assertEquals(listOf("se-3"), progress.stillToCome.map { it.id.value }, "se-2 is done, not still to come")
    }

    @Test
    fun `a routine-started session has an order that is a plan`() {
        // US-32: a session copied from a routine carries the routine's order deliberately.
        val progress =
            SessionProgress.of(session(RoutineOrigin("r1", "Upper A")), exercises = emptyList(), sets = emptyList())

        assertTrue(progress.orderIsAPlan)
    }

    @Test
    fun `a freestyle session has no plan behind its order`() {
        // ADR-0023: session position records the order exercises were added, not a plan to
        // perform them that way, for a session with no routine behind it.
        val noExercises = emptyList<SessionExercise>()
        val progress = SessionProgress.of(session(routine = null), noExercises, emptyList())

        assertFalse(progress.orderIsAPlan)
    }

    @Test
    fun `sets belonging to another session are not counted`() {
        val exercises = listOf(appearance("se-1", 1))
        val sets = listOf(set("se-elsewhere"))

        val progress = SessionProgress.of(session(), exercises, sets)

        assertEquals(0, progress.movementsDone)
        assertEquals("se-1", progress.current?.id?.value)
    }

    @Test
    fun `exercises belonging to another session are not counted`() {
        // The read fetches exercises for several sessions at once, per `SessionSummary`'s own
        // convention — the type is responsible for keeping them apart.
        val exercises =
            listOf(
                appearance("se-1", 1),
                SessionExercise(SessionExerciseId("se-elsewhere"), SessionId("other"), ExerciseId("bench"), 1),
            )

        val progress = SessionProgress.of(session(), exercises, sets = emptyList())

        assertEquals(1, progress.movementsTotal)
    }
}
