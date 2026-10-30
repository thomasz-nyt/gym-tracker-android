package com.gymtracker.core.data.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.backup.AppVersion
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class FileBackupSafetyStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val member = UserId("alice")
    private val fixture = TestData.memberWithARoutineAndASession(member)
    private val contents =
        BackupContents(
            member,
            WeightUnit.KG,
            Duration.ofSeconds(90),
            fixture.sessions,
            fixture.sessionExercises,
            fixture.sets,
            fixture.routines,
            fixture.routineItems,
        )
    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

    @Test
    fun `complete safety copy survives a new adapter and refused write retains it`() =
        runTest {
            val privateFiles = folder.newFolder("private")
            val context =
                object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                    override fun getFilesDir() = privateFiles
                }

            fun store() = FileBackupSafetyStore(context, BackupCodec(Json), clock, AppVersion { "test" })
            assertNull(store().read())
            store().write(contents)
            assertEquals(contents, store().read())
            val invalid = contents.copy(sets = contents.sets.map { it.copy(weightKg = Double.NaN) })
            assertFails { store().write(invalid) }
            assertEquals(contents, store().read(), "a failed replacement keeps the last verified complete copy")
        }
}
