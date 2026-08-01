package com.gymtracker.core.domain.session

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import com.gymtracker.core.domain.set.FakeSetRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US-06a and ADR-0012: the delete is real immediately, and undo puts back exactly what went. */
class DeleteSessionTest {
    private val now: Instant = Instant.parse("2026-08-01T18:00:00Z")
    private val member = UserId("alice")
    private val target = SessionId("s1")
    private val other = SessionId("s2")

    private val sessionExercises = FakeSessionExerciseRepository()
    private val sets = FakeSetRepository()

    // The schema deletes the children with the session (ADR-0012), so the fake has to as well.
    private val sessions =
        FakeSessionRepository(
            initial = listOf(finished(target), finished(other)),
            cascade = { id ->
                sets.cascadeDelete(id)
                sessionExercises.cascadeDelete(id)
            },
        )

    private val deleteSession = DeleteSession(sessions, sessionExercises, sets)
    private val restoreSession = RestoreSession(sessions, sessionExercises, sets)

    private fun finished(id: SessionId) =
        WorkoutSession(
            id = id,
            userId = member,
            gymName = null,
            startedAt = now.minus(Duration.ofHours(2)),
            endedAt = now.minus(Duration.ofHours(1)),
            metrics = null,
        )

    /** A session with two appearances of an exercise and three sets between them. */
    private suspend fun seedWorkout(session: SessionId) {
        val first = SessionExercise(SessionExerciseId("${session.value}-se-1"), session, ExerciseId("bench"), 1)
        val second = SessionExercise(SessionExerciseId("${session.value}-se-2"), session, ExerciseId("squat"), 2)
        listOf(first, second).forEach {
            sessionExercises.add(it)
            sets.belongsTo(it)
        }
        sets.add(ExerciseSet("${session.value}-a", first.id, 1, 60.0, 10, 8.0, now))
        sets.add(ExerciseSet("${session.value}-b", first.id, 2, 60.0, 8, null, now))
        sets.add(ExerciseSet("${session.value}-c", second.id, 1, null, 12, null, now))
    }

    @Test
    fun `deleting takes the session and everything hanging off it`() =
        runTest {
            seedWorkout(target)

            deleteSession(target)

            assertNull(sessions.findSession(target))
            assertEquals(emptyList(), sessionExercises.forSession(target))
            assertEquals(emptyList(), sets.forSession(target))
        }

    @Test
    fun `deleting one workout leaves the others alone`() =
        runTest {
            seedWorkout(target)
            seedWorkout(other)

            deleteSession(target)

            assertEquals(other, sessions.sessions.single().id)
            assertEquals(2, sessionExercises.forSession(other).size)
            assertEquals(3, sets.forSession(other).size)
        }

    @Test
    fun `undo restores the workout exactly as it was`() =
        runTest {
            seedWorkout(target)
            val before = sessions.findSession(target)
            val exercisesBefore = sessionExercises.forSession(target)
            val setsBefore = sets.forSession(target)

            val deleted = deleteSession(target)
            restoreSession(checkNotNull(deleted))

            assertEquals(before, sessions.findSession(target), "same session, not a copy of it")
            assertEquals(exercisesBefore, sessionExercises.forSession(target))
            assertEquals(setsBefore, sets.forSession(target))
        }

    @Test
    fun `a restored set keeps its own id, so nothing is duplicated`() =
        runTest {
            // Ids are preserved on purpose (ADR-0012). A restore that minted new ids would
            // leave M2's sync engine looking at rows it has never seen and cannot reconcile.
            seedWorkout(target)

            val deleted = checkNotNull(deleteSession(target))
            restoreSession(deleted)

            assertEquals(
                listOf("s1-a", "s1-b", "s1-c"),
                sets.forSession(target).map { it.id }.sorted(),
            )
        }

    @Test
    fun `the snapshot carries the whole workout, so undo needs nothing from the database`() =
        runTest {
            seedWorkout(target)

            val deleted = checkNotNull(deleteSession(target))

            assertEquals(target, deleted.session.id)
            assertEquals(2, deleted.exercises.size)
            assertEquals(3, deleted.sets.size)
        }

    @Test
    fun `deleting a workout that is already gone reports nothing to undo`() =
        runTest {
            assertNull(deleteSession(SessionId("never-existed")))
        }

    @Test
    fun `an empty workout deletes and restores without special handling`() =
        runTest {
            val deleted = checkNotNull(deleteSession(target))
            assertNull(sessions.findSession(target))

            restoreSession(deleted)

            assertEquals(finished(target), sessions.findSession(target))
        }
}
