package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.session.StartSession
import com.gymtracker.core.domain.session.StartSessionResult
import com.gymtracker.core.domain.sessionexercise.AddExerciseToSession
import com.gymtracker.core.domain.sessionexercise.FakeSessionExerciseRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-29: starting Tuesday's routine puts Tuesday's movements on the screen.
 *
 * The load-bearing assertion is the last one. ADR-0020 buys the routine concept by promising
 * that a session started from one is an *ordinary* session — nothing points back at the
 * routine, so nothing can later render "planned versus actual" and reintroduce the
 * prescription §2.4 forbids.
 */
class StartSessionFromRoutineTest {
    private val now: Instant = Instant.parse("2026-08-08T18:00:00Z")
    private val alice = UserId("alice")
    private val bench = ExerciseId("bench")
    private val squat = ExerciseId("squat")
    private val row = ExerciseId("row")

    private val items = FakeRoutineItemRepository()
    private val routines = FakeRoutineRepository(cascade = { items.cascadeDelete(it) })
    private val sessions = FakeSessionRepository()
    private val sessionExercises = FakeSessionExerciseRepository()
    private var nextRoutine = 1
    private var nextItem = 1
    private var nextAppearance = 1

    private val createRoutine = CreateRoutine(routines) { RoutineId("r-${nextRoutine++}") }
    private val addToRoutine = AddExerciseToRoutine(items) { RoutineItemId("i-${nextItem++}") }

    private val startFromRoutine =
        StartSessionFromRoutine(
            routines = routines,
            items = items,
            startSession =
                StartSession(sessions, Clock.fixed(now, ZoneOffset.UTC)) { SessionId("s-1") },
            addExerciseToSession =
                AddExerciseToSession(sessionExercises) { SessionExerciseId("se-${nextAppearance++}") },
        )

    private suspend fun upperA(): RoutineId {
        val routine = createRoutine(alice, "Upper A")
        addToRoutine(routine.id, bench)
        addToRoutine(routine.id, squat)
        addToRoutine(routine.id, row)
        return routine.id
    }

    @Test
    fun `starting a routine starts a session`() =
        runTest {
            val result = startFromRoutine(upperA(), alice)

            assertTrue(result is StartSessionResult.Started)
            assertEquals(1, sessions.sessions.size)
        }

    @Test
    fun `the movements are copied in the routine's order`() =
        runTest {
            val started = startFromRoutine(upperA(), alice) as StartSessionResult.Started

            val copied = sessionExercises.forSession(started.session.id)
            assertEquals(listOf(bench, squat, row), copied.map { it.exerciseId })
            assertEquals(listOf(1, 2, 3), copied.map { it.position })
        }

    @Test
    fun `a routine with the same movement twice copies it twice`() =
        runTest {
            val routine = createRoutine(alice, "Bench day").id
            addToRoutine(routine, bench)
            addToRoutine(routine, bench)

            val started = startFromRoutine(routine, alice) as StartSessionResult.Started

            val copied = sessionExercises.forSession(started.session.id)
            assertEquals(listOf(bench, bench), copied.map { it.exerciseId })
            assertEquals(2, copied.map { it.id }.toSet().size, "two appearances, so their sets stay apart")
        }

    @Test
    fun `an empty routine starts an empty session rather than failing`() =
        runTest {
            val empty = createRoutine(alice, "New routine").id

            val started = startFromRoutine(empty, alice) as StartSessionResult.Started

            assertTrue(sessionExercises.forSession(started.session.id).isEmpty())
        }

    @Test
    fun `a routine that does not exist starts nothing at all`() =
        runTest {
            val result = startFromRoutine(RoutineId("gone"), alice)

            assertNull(result)
            assertTrue(sessions.sessions.isEmpty(), "no empty session left behind")
        }

    @Test
    fun `a workout already running is resumed, and the routine is not poured into it`() =
        runTest {
            // US-01 allows one active session. Copying six movements into a workout already in
            // progress would be a surprising, unasked-for edit of what is on the screen, so the
            // running session is handed back untouched and the caller decides what to say.
            sessions.startSession(
                WorkoutSession(SessionId("running"), alice, null, now.minusSeconds(600), null, null),
            )

            val result = startFromRoutine(upperA(), alice)

            assertTrue(result is StartSessionResult.Resumed)
            assertTrue(
                sessionExercises.forSession(SessionId("running")).isEmpty(),
                "the running session gained nothing",
            )
        }

    @Test
    fun `editing the session afterwards does not edit the routine`() =
        runTest {
            val routine = upperA()
            val started = startFromRoutine(routine, alice) as StartSessionResult.Started

            val copied = sessionExercises.forSession(started.session.id)
            sessionExercises.remove(copied.first().id)

            assertEquals(
                listOf(bench, squat, row),
                items.itemsOf(routine).map { it.exerciseId },
                "Tuesday is unchanged by what happened on Tuesday",
            )
        }

    @Test
    fun `nothing in the session points back at the routine`() =
        runTest {
            // The promise that buys the routine concept (ADR-0020). SessionExercise has no
            // routine field, so "planned versus actual" cannot be rendered — there is no
            // authored number anywhere near a logged one, which is what §2.4 asks for.
            val started = startFromRoutine(upperA(), alice) as StartSessionResult.Started

            val session = started.session
            val appearance = sessionExercises.forSession(session.id).first()
            val appearanceFields = appearance.javaClass.declaredFields.map { it.name }
            val sessionFields = session.javaClass.declaredFields.map { it.name }

            assertTrue(
                appearanceFields.none { it.contains("routine", ignoreCase = true) },
                "SessionExercise gained a routine field: $appearanceFields",
            )
            assertTrue(
                sessionFields.none { it.contains("routine", ignoreCase = true) },
                "WorkoutSession gained a routine field: $sessionFields",
            )
        }
}
