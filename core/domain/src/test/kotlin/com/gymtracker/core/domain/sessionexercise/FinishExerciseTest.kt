package com.gymtracker.core.domain.sessionexercise

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.set.FakeSetRepository
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-02d: a finished exercise is the member's explicit declaration, recorded as a timestamp
 * and erased by any set logged after it (ADR-0019).
 *
 * The clearing is tested against [LogSet] because that is the one write path every way of
 * logging a set — manual, several-at-once, guided — funnels through. If it holds there, the
 * invariant "a displayed done is never older than the newest set" holds by construction.
 */
class FinishExerciseTest {
    private val now: Instant = Instant.parse("2026-08-04T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val session = SessionId("s1")

    private val sets = FakeSetRepository()
    private val sessionExercises = FakeSessionExerciseRepository(cascade = sets::cascadeDeleteExercise)

    private val finish = FinishExercise(sessionExercises, clock)
    private val logSet = LogSet(sets, sessionExercises, clock) { "set-${nextSet++}" }
    private var nextSet = 1

    private val bench = SessionExercise(SessionExerciseId("se-1"), session, ExerciseId("bench"), 1)

    @Test
    fun `marking done records when, not just that`() =
        runTest {
            sessionExercises.add(bench)

            finish.mark(bench.id)

            assertEquals(now, sessionExercises.find(bench.id)?.finishedAt)
        }

    @Test
    fun `the mark toggles back off`() =
        runTest {
            sessionExercises.add(bench)
            finish.mark(bench.id)

            finish.clear(bench.id)

            assertNull(sessionExercises.find(bench.id)?.finishedAt)
        }

    @Test
    fun `logging a set clears the mark`() =
        runTest {
            // The machine freed up and a drop set happened: "done" must not be displayed
            // about an exercise whose newest set came after it (constitution §2.4).
            sessionExercises.add(bench)
            sets.belongsTo(bench)
            finish.mark(bench.id)

            logSet(bench.id, 60.0, WeightUnit.KG, 10, null)

            assertNull(sessionExercises.find(bench.id)?.finishedAt)
        }

    @Test
    fun `an exercise starts in progress and a set alone does not finish it`() =
        runTest {
            sessionExercises.add(bench)
            sets.belongsTo(bench)

            logSet(bench.id, 60.0, WeightUnit.KG, 10, null)

            assertNull(sessionExercises.find(bench.id)?.finishedAt, "finishing is never inferred")
        }

    @Test
    fun `undoing a removal restores the mark unchanged`() =
        runTest {
            // US-02c promises the exercise back "with its sets unchanged — same ids, same
            // values, same position"; the mark rides the same row.
            sessionExercises.add(bench)
            sets.belongsTo(bench)
            finish.mark(bench.id)

            val removed = checkNotNull(RemoveExerciseFromSession(sessionExercises, sets)(bench.id))
            RestoreExerciseToSession(sessionExercises, sets)(removed)

            assertEquals(now, sessionExercises.find(bench.id)?.finishedAt)
        }
}
