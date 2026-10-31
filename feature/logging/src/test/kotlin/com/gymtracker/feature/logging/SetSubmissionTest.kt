package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.model.SessionExercise
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.set.LogSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SetSubmissionTest {
    @Test
    fun `repeated confirmation during a delayed save writes one actual set`() =
        runTest {
            val stored = FakeSets(sessionOf = { SessionId("session") })
            val delayed =
                object : SetRepository by stored {
                    override suspend fun append(set: ExerciseSet): ExerciseSet {
                        delay(100)
                        return stored.append(set)
                    }
                }
            var sequence = 0
            val controller =
                SetEntryController(
                    LogSets(LogSet(delayed, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)) { "set-${++sequence}" }),
                    { _, _ -> },
                    delayed,
                    FakeUnitPreference(),
                    FakeCurrentMember(UserId("alice")),
                    this,
                )
            val row =
                SessionExerciseRow(
                    SessionExercise(
                        SessionExerciseId("movement"),
                        SessionId("session"),
                        ExerciseId("bench"),
                        1,
                        MovementTarget(3, 12, 50.0),
                    ),
                    null,
                )
            controller.open(row)
            runCurrent()
            assertNotNull(controller.entry.value)

            controller.confirm()
            controller.confirm()
            advanceUntilIdle()

            assertEquals(1, stored.all.size)
            assertNull(controller.entry.value)
        }
}
