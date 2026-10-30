package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.set.DeleteSet
import com.gymtracker.core.domain.set.RestoreSet
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.set.UpdateSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class SetEditRecoveryTest {
    @Test
    fun `failed correction keeps the edited draft and original record for retry`() =
        runTest {
            val stored = FakeSets(sessionOf = { SessionId("session") })
            val original = ExerciseSet("actual", SessionExerciseId("movement"), 1, 50.0, 10, null, Instant.EPOCH)
            stored.add(original)
            val failing =
                object : SetRepository by stored {
                    override suspend fun update(set: ExerciseSet): Unit = throw IOException("disk full")
                }
            val edit =
                SetEditController(
                    UpdateSet(failing),
                    DeleteSet(failing),
                    RestoreSet(failing),
                    FakeUnitPreference(),
                    this,
                )
            edit.open(original, "Bench")
            runCurrent()
            edit.change(reps = "9")
            edit.save()
            runCurrent()
            assertEquals("9", edit.edit.value?.reps)
            assertNotNull(edit.edit.value?.error)
            assertEquals(original, stored.all.single())
        }
}
