package com.gymtracker.feature.settings

import com.gymtracker.core.domain.backup.AppVersion
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.BackupEncoder
import com.gymtracker.core.domain.backup.BackupFileWriter
import com.gymtracker.core.domain.backup.ExportBackup
import com.gymtracker.core.domain.backup.ExportBackupToFile
import com.gymtracker.core.domain.backup.FakeBackupStore
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * US-40: exporting from Settings, and what the screen shows while it happens and after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val member = UserId("member-1")
    private val now: Instant = Instant.parse("2026-08-15T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val destination = "content://fake/backup.json"

    private val emptyContents =
        BackupContents(
            memberId = member,
            unit = WeightUnit.LB,
            restDefault = Duration.ofSeconds(60),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            sets = emptyList(),
            routines = emptyList(),
            routineItems = emptyList(),
        )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        writer: BackupFileWriter = FakeFileWriter(),
        currentMember: CurrentMember = FakeCurrentMember(member),
    ) = SettingsViewModel(
        currentMember = currentMember,
        export =
            ExportBackupToFile(
                exportBackup = ExportBackup(FakeBackupStore(seed = mapOf(member to emptyContents))),
                encoder = FakeEncoder(),
                fileWriter = writer,
                appVersion = AppVersion { "1.0-test" },
                clock = clock,
            ),
    )

    @Test
    fun `starts idle, and returns to idle once the export completes`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(false, viewModel.uiState.value.isExporting)

            viewModel.onExport(destination)

            assertEquals(false, viewModel.uiState.value.isExporting)
            assertNull(viewModel.uiState.value.exportError)
        }

    @Test
    fun `exporting writes to the destination the member chose`() =
        runTest {
            val writer = FakeFileWriter()
            val viewModel = viewModel(writer = writer)

            viewModel.onExport(destination)

            assertEquals(destination, writer.lastDestination)
        }

    @Test
    fun `a write failure is reported, not thrown`() =
        runTest {
            val viewModel = viewModel(writer = FakeFileWriter(failWith = IllegalStateException("no space")))

            viewModel.onExport(destination)

            assertEquals("no space", viewModel.uiState.value.exportError)
            assertEquals(false, viewModel.uiState.value.isExporting)
        }

    @Test
    fun `dismissing the error clears it without touching anything else`() =
        runTest {
            val viewModel = viewModel(writer = FakeFileWriter(failWith = IllegalStateException("no space")))
            viewModel.onExport(destination)

            viewModel.onExportErrorDismissed()

            assertNull(viewModel.uiState.value.exportError)
        }

    private class FakeEncoder : BackupEncoder {
        override fun encode(
            contents: BackupContents,
            exportedAt: Instant,
            appVersion: String,
        ): String = "encoded:${contents.memberId.value}"
    }

    private class FakeFileWriter(
        private val failWith: Throwable? = null,
    ) : BackupFileWriter {
        var lastDestination: String? = null
            private set

        override suspend fun write(
            destination: String,
            content: String,
        ) {
            failWith?.let { throw it }
            lastDestination = destination
        }
    }

    private class FakeCurrentMember(
        private val id: UserId,
    ) : CurrentMember {
        override suspend fun id(): UserId = id
    }
}
