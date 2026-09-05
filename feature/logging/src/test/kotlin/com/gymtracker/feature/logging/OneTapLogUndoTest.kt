package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.rest.LogUpNextSet
import com.gymtracker.core.domain.rest.RestTimer
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.set.DeleteSet
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.SetPrefill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-35's undo: a one-tap log is also a one-tap misfire, and the row it writes — and the rest it
 * starts — can be taken back within ADR-0012's five seconds.
 *
 * Tests the controller directly, with the same fakes `ActiveSessionViewModelTest` builds the
 * whole ViewModel from, because the window's rules (what undo deletes, what it ends, how long it
 * lasts, which log it applies to) are the controller's own and need none of the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OneTapLogUndoTest {
    private val now: Instant = Instant.parse("2026-09-05T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sets = FakeSets(sessionOf = { SessionId("s1") })
    private val restStore = FakeRestTimerStore()
    private val restTimer = RestTimer(restStore, clock)
    private var nextSet = 1

    private val next =
        UpNextSet(
            sessionExerciseId = SessionExerciseId("se-1"),
            exerciseId = ExerciseId("bench"),
            setNumber = 1,
            // 135 lb, the fake member's unit — converted by LogUpNextSet exactly once (ADR-0006).
            prefill = SetPrefill(weight = 135.0, reps = 8),
            comparison = null,
        )

    private fun controller(
        scope: CoroutineScope,
        onUndone: () -> Unit = {},
    ) = OneTapLogController(
        logUpNextSet =
            LogUpNextSet(
                logSets = LogSets(LogSet(sets, clock) { "set-${nextSet++}" }),
                restTimer = restTimer,
                unitPreference = FakeUnitPreference(),
            ),
        deleteSet = DeleteSet(sets),
        restTimer = restTimer,
        scope = scope,
        onUndone = onUndone,
    )

    @Test
    fun `undo within the window deletes the set and ends the rest it started`() =
        runTest {
            val controller = controller(backgroundScope)

            val logged = controller.log(next)
            assertEquals(listOf(logged), sets.all, "the one-tap log wrote exactly its row")
            assertNotNull(restStore.restEndsAt.first(), "and started a rest, as US-05 requires")
            assertTrue(controller.canUndo.first())

            controller.undo()
            runCurrent()

            assertTrue(sets.all.isEmpty(), "the row that tap wrote is gone")
            assertNull(restStore.restEndsAt.first(), "a rest earned by a set that no longer exists is ended too")
            assertFalse(controller.canUndo.first())
        }

    @Test
    fun `the window closes after five seconds, and undo then does nothing`() =
        runTest {
            val controller = controller(backgroundScope)
            controller.log(next)

            advanceTimeBy(WINDOW_MILLIS + 1)
            runCurrent()

            assertFalse(controller.canUndo.first(), "ADR-0012's window has closed")
            controller.undo()
            runCurrent()
            assertEquals(1, sets.all.size, "a closed window takes nothing back")
            assertNotNull(restStore.restEndsAt.first(), "and leaves the rest running")
        }

    @Test
    fun `only the most recent one-tap log can be taken back`() =
        runTest {
            val controller = controller(backgroundScope)
            val first = controller.log(next)
            controller.log(next.copy(setNumber = 2))

            controller.undo()
            runCurrent()

            assertEquals(listOf(first), sets.all, "the second log replaced the first as the undoable row")
        }

    @Test
    fun `undo reports back once the row is gone, and never for nothing`() =
        runTest {
            var reported = 0
            val controller = controller(backgroundScope, onUndone = { reported++ })
            controller.log(next)

            controller.undo()
            runCurrent()
            assertEquals(1, reported, "the screen hears about it once the set and its rest are gone")

            controller.undo()
            runCurrent()
            assertEquals(1, reported, "nothing left to undo, nothing to report")
        }

    private companion object {
        const val WINDOW_MILLIS = 5_000L
    }
}
