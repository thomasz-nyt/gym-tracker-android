package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineItemId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.session.SessionRepository
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
 * ADR-0020 buys the routine concept by promising that a session started from one is an
 * *ordinary* session — `SessionExercise` never points back at the routine, so nothing can
 * render "planned versus actual" and reintroduce the prescription §2.4 forbids. ADR-0028
 * (US-32) adds a narrower exception on `WorkoutSession` itself: dead provenance, written once
 * and never read back through a repository. The tests at the bottom of this file are what
 * replaced this class's original single tripwire — see ADR-0028 for why a blanket "no field
 * containing 'routine'" assertion on `WorkoutSession` stopped being the right rule.
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
    fun `a movement's target is copied into the session alongside it`() =
        runTest {
            val routine = createRoutine(alice, "Upper A").id
            val benchItem = addToRoutine(routine, bench)
            val target = MovementTarget(sets = 3, reps = 8, weightKg = 47.6)
            items.updateItem(benchItem.copy(target = target))
            addToRoutine(routine, squat)

            val started = startFromRoutine(routine, alice) as StartSessionResult.Started

            val copied = sessionExercises.forSession(started.session.id)
            assertEquals(target, copied.first { it.exerciseId == bench }.target)
            assertNull(copied.first { it.exerciseId == squat }.target, "squat had no target to copy")
        }

    @Test
    fun `editing the routine's target afterwards does not edit the copy in the session`() =
        runTest {
            val routine = createRoutine(alice, "Upper A").id
            val benchItem = addToRoutine(routine, bench)
            items.updateItem(benchItem.copy(target = MovementTarget(3, 8, 47.6)))

            val started = startFromRoutine(routine, alice) as StartSessionResult.Started
            items.updateItem(items.itemsOf(routine).single().copy(target = MovementTarget(4, 6, 60.0)))

            val copied = sessionExercises.forSession(started.session.id).single()
            assertEquals(MovementTarget(3, 8, 47.6), copied.target, "the session keeps its own snapshot")
        }

    @Test
    fun `SessionExercise still has no field pointing back at a routine`() =
        runTest {
            // The half of ADR-0020's original promise that ADR-0028 leaves untouched: a
            // movement's appearance in a session never names the routine it came from, so
            // "planned versus actual" still cannot be rendered from this type.
            val started = startFromRoutine(upperA(), alice) as StartSessionResult.Started

            val appearance = sessionExercises.forSession(started.session.id).first()
            val appearanceFields = appearance.javaClass.declaredFields.map { it.name }

            assertTrue(
                appearanceFields.none { it.contains("routine", ignoreCase = true) },
                "SessionExercise gained a routine field: $appearanceFields",
            )
        }

    @Test
    fun `a session started from a routine carries the routine's name`() =
        runTest {
            val started = startFromRoutine(upperA(), alice) as StartSessionResult.Started

            assertEquals("Upper A", started.session.routine?.name)
        }

    @Test
    fun `a session started without a routine carries no origin at all`() =
        runTest {
            val result = StartSession(sessions, Clock.fixed(now, ZoneOffset.UTC)) { SessionId("freestyle") }
            val started = result(alice) as StartSessionResult.Started

            assertNull(started.session.routine, "US-01's ordinary start path is untouched by ADR-0028")
        }

    @Test
    fun `renaming the routine afterwards does not change a session already started`() =
        runTest {
            val routine = upperA()
            val started = startFromRoutine(routine, alice) as StartSessionResult.Started

            routines.rename(routine, "Push A")

            assertEquals(
                "Upper A",
                sessions.sessions
                    .single { it.id == started.session.id }
                    .routine
                    ?.name,
                "the session's name is a copy, not a live read of the routine",
            )
        }

    @Test
    fun `deleting the routine afterwards does not change a session already started`() =
        runTest {
            val routine = upperA()
            val started = startFromRoutine(routine, alice) as StartSessionResult.Started

            routines.delete(routine)

            assertEquals(
                "Upper A",
                sessions.sessions
                    .single { it.id == started.session.id }
                    .routine
                    ?.name,
            )
        }

    @Test
    fun `RoutineOrigin's id is a plain String, not a RoutineId`() {
        // ADR-0028's enforcement mechanism: resolving this back to a routine —
        // routines.find(RoutineId(origin.id)) — takes a deliberate, greppable wrap that
        // isn't there today. A change to RoutineId here would be exactly that wrap arriving
        // by accident, which is what this test exists to catch.
        val idField = RoutineOrigin::class.java.getDeclaredField("id")
        assertEquals(String::class.java, idField.type)
    }

    @Test
    fun `StartSession takes no RoutineRepository, so it cannot read through at start time`() {
        // ADR-0028 quotes ADR-0027's rejection of a routine_id "read through at set entry" —
        // a live pointer. StartSession is where that read would have to happen for a session
        // not started from StartSessionFromRoutine, so this asserts the constructor it was
        // given has no way to perform one.
        val constructorParamTypes =
            StartSession::class.java.declaredConstructors
                .single()
                .parameterTypes
                .toList()

        assertTrue(
            constructorParamTypes.none { RoutineRepository::class.java.isAssignableFrom(it) },
            "StartSession gained a RoutineRepository: $constructorParamTypes",
        )
        assertTrue(
            constructorParamTypes.any { SessionRepository::class.java.isAssignableFrom(it) },
            "sanity check that reflection found the real constructor",
        )
    }
}
