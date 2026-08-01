package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-06's history row: "date, duration, exercise count, and total volume", plus the rule
 * added with it — bodyweight sets are counted, never valued at zero.
 *
 * Table-driven against hand-computed figures, per `specs/testing-strategy.md`.
 */
class SessionSummaryTest {
    private val startedAt: Instant = Instant.parse("2026-08-01T17:00:00Z")
    private val member = UserId("alice")
    private val session = SessionId("s1")

    private fun session(endedAt: Instant? = startedAt.plus(Duration.ofMinutes(72))) =
        WorkoutSession(
            id = session,
            userId = member,
            gymName = null,
            startedAt = startedAt,
            endedAt = endedAt,
            metrics = null,
        )

    private fun appearance(
        id: String,
        position: Int,
    ) = SessionExercise(SessionExerciseId(id), session, ExerciseId("bench"), position)

    private fun set(
        appearance: String,
        index: Int,
        weightKg: Double?,
        reps: Int,
    ) = ExerciseSet(
        id = "$appearance-$index",
        sessionExerciseId = SessionExerciseId(appearance),
        setIndex = index,
        weightKg = weightKg,
        reps = reps,
        rpe = null,
        performedAt = startedAt,
    )

    @Test
    fun `duration is the span between the two recorded timestamps`() {
        val summary = SessionSummary.of(session(), exercises = emptyList(), sets = emptyList())

        assertEquals(Duration.ofMinutes(72), summary.duration)
    }

    @Test
    fun `a session still running has no duration rather than a duration of zero`() {
        // Constitution §2: if a metric is unavailable, show it as unavailable. History only
        // lists finished sessions, but the type allows an open one and must not invent an end.
        val summary = SessionSummary.of(session(endedAt = null), exercises = emptyList(), sets = emptyList())

        assertNull(summary.duration)
    }

    @Test
    fun `counts follow the rows, so one exercise done twice counts twice`() {
        // ADR-0004: the same exercise may appear twice in a session, and each appearance is
        // its own row. The history row counts appearances, which is what the member did.
        val exercises = listOf(appearance("se-1", 1), appearance("se-2", 2))
        val sets =
            listOf(
                set("se-1", 1, 60.0, 10),
                set("se-1", 2, 60.0, 10),
                set("se-2", 1, 40.0, 12),
            )

        val summary = SessionSummary.of(session(), exercises, sets)

        assertEquals(2, summary.exerciseCount)
        assertEquals(3, summary.setCount)
    }

    @Test
    fun `volume is weight times reps, summed`() {
        val exercises = listOf(appearance("se-1", 1))
        val sets =
            listOf(
                set("se-1", 1, 60.0, 10), // 600
                set("se-1", 2, 62.5, 8), // 500
                set("se-1", 3, 100.0, 5), // 500
            )

        val summary = SessionSummary.of(session(), exercises, sets)

        assertEquals(1600.0, summary.volumeKg)
    }

    @Test
    fun `a bodyweight set is counted separately and never valued at zero`() {
        // Constitution §2: never fabricate a logged value. A set with no weight recorded is a
        // set whose load is unknown, not a set that moved nothing — so it cannot be summed in
        // as 0 and it cannot be dropped silently either.
        val exercises = listOf(appearance("se-1", 1))
        val sets =
            listOf(
                set("se-1", 1, 60.0, 10),
                set("se-1", 2, null, 12),
                set("se-1", 3, null, 10),
            )

        val summary = SessionSummary.of(session(), exercises, sets)

        assertEquals(600.0, summary.volumeKg)
        assertEquals(2, summary.bodyweightSetCount)
        assertEquals(3, summary.setCount, "all three are sets the member performed")
    }

    @Test
    fun `a session of nothing but bodyweight sets reports no volume, not zero volume`() {
        val exercises = listOf(appearance("se-1", 1))
        val sets = listOf(set("se-1", 1, null, 20), set("se-1", 2, null, 18))

        val summary = SessionSummary.of(session(), exercises, sets)

        assertNull(summary.volumeKg, "no weight was recorded, so there is no volume to claim")
        assertEquals(2, summary.bodyweightSetCount)
    }

    @Test
    fun `an exercise added but never performed still counts as an exercise`() {
        // US-02 lets an exercise exist in a session before its first set. Someone who racked
        // up, decided against it and moved on did add it, and the history row should say so.
        val summary = SessionSummary.of(session(), listOf(appearance("se-1", 1)), sets = emptyList())

        assertEquals(1, summary.exerciseCount)
        assertEquals(0, summary.setCount)
        assertNull(summary.volumeKg)
    }

    @Test
    fun `sets belonging to another session are not counted`() {
        // The history query fetches sets for several sessions at once, so the summary is
        // responsible for keeping them apart rather than trusting its input to be filtered.
        val exercises = listOf(appearance("se-1", 1))
        val sets = listOf(set("se-1", 1, 60.0, 10), set("se-elsewhere", 1, 999.0, 10))

        val summary = SessionSummary.of(session(), exercises, sets)

        assertEquals(1, summary.setCount)
        assertEquals(600.0, summary.volumeKg)
    }
}
