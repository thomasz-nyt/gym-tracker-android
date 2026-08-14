package com.gymtracker.core.domain.routine

import com.gymtracker.core.domain.model.Routine
import com.gymtracker.core.domain.model.RoutineId
import com.gymtracker.core.domain.model.RoutineOrigin
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-36: which routine Train home leads with when no workout is running.
 *
 * [WorkoutSession.routine]'s `id` is a plain `String` (ADR-0028), written once at session start
 * and — until now — never read back. This is the first reader, and it is exactly the use
 * ADR-0028's own doc anticipated: matching by identity to answer "when did I last do this
 * routine", never rendering the id itself.
 */
class NextRoutineToTrainTest {
    private val member = UserId("member")

    @Test
    fun `with no routines at all, there is nothing to train`() =
        runTest {
            val nextRoutineToTrain = NextRoutineToTrain(FakeRoutineRepository(), FakeSessionRepository())

            assertNull(nextRoutineToTrain(member))
        }

    @Test
    fun `a routine never performed comes before one performed recently`() =
        runTest {
            val neverDone = Routine(RoutineId("never"), member, "Push Pull Legs", position = 1)
            val doneRecently = Routine(RoutineId("recent"), member, "Upper A", position = 2)
            val routines =
                FakeRoutineRepository().apply {
                    add(neverDone)
                    add(doneRecently)
                }
            val sessions =
                FakeSessionRepository(
                    initial = listOf(finishedSession(routine = doneRecently, startedAt = "2026-08-01T00:00:00Z")),
                )
            val nextRoutineToTrain = NextRoutineToTrain(routines, sessions)

            assertEquals(neverDone, nextRoutineToTrain(member))
        }

    @Test
    fun `between two performed routines, the one done longer ago comes first`() =
        runTest {
            val doneAWhileAgo = Routine(RoutineId("stale"), member, "Lower A", position = 1)
            val doneRecently = Routine(RoutineId("fresh"), member, "Upper A", position = 2)
            val routines =
                FakeRoutineRepository().apply {
                    add(doneAWhileAgo)
                    add(doneRecently)
                }
            val sessions =
                FakeSessionRepository(
                    initial =
                        listOf(
                            finishedSession(routine = doneAWhileAgo, startedAt = "2026-07-01T00:00:00Z"),
                            finishedSession(routine = doneRecently, startedAt = "2026-08-01T00:00:00Z"),
                        ),
                )
            val nextRoutineToTrain = NextRoutineToTrain(routines, sessions)

            assertEquals(doneAWhileAgo, nextRoutineToTrain(member))
        }

    @Test
    fun `only the most recent session of a routine counts, not the first`() =
        runTest {
            // Done once a long time ago, then again just now — the routine should read as
            // freshly done, not stale, or a routine trained once early and never since would
            // never surface again despite being the one actually overdue.
            val routine = Routine(RoutineId("r"), member, "Upper A", position = 1)
            val otherRoutine = Routine(RoutineId("other"), member, "Lower A", position = 2)
            val routines =
                FakeRoutineRepository().apply {
                    add(routine)
                    add(otherRoutine)
                }
            val sessions =
                FakeSessionRepository(
                    initial =
                        listOf(
                            finishedSession(routine = routine, startedAt = "2026-01-01T00:00:00Z"),
                            finishedSession(routine = routine, startedAt = "2026-08-10T00:00:00Z"),
                            finishedSession(routine = otherRoutine, startedAt = "2026-08-05T00:00:00Z"),
                        ),
                )
            val nextRoutineToTrain = NextRoutineToTrain(routines, sessions)

            // otherRoutine (last done 2026-08-05) is more overdue than routine (last done
            // 2026-08-10), so it comes first — the stale 2026-01-01 session must not win.
            assertEquals(otherRoutine, nextRoutineToTrain(member))
        }

    @Test
    fun `ties among never-performed routines break by list position`() =
        runTest {
            val first = Routine(RoutineId("a"), member, "A", position = 1)
            val second = Routine(RoutineId("b"), member, "B", position = 2)
            // Added out of position order, so a correct implementation has to sort by position
            // rather than by insertion or id.
            val routines =
                FakeRoutineRepository().apply {
                    add(second)
                    add(first)
                }
            val nextRoutineToTrain = NextRoutineToTrain(routines, FakeSessionRepository())

            assertEquals(first, nextRoutineToTrain(member))
        }

    private fun finishedSession(
        routine: Routine,
        startedAt: String,
    ): WorkoutSession {
        val started = Instant.parse(startedAt)
        return WorkoutSession(
            id = SessionId("session-${routine.id.value}-$startedAt"),
            userId = member,
            gymName = null,
            startedAt = started,
            endedAt = started.plusSeconds(SESSION_LENGTH_SECONDS),
            metrics = null,
            routine = RoutineOrigin(id = routine.id.value, name = routine.name),
        )
    }

    private companion object {
        const val SESSION_LENGTH_SECONDS = 3_000L
    }
}
