package com.gymtracker.feature.settings

import com.gymtracker.core.domain.TestData
import com.gymtracker.core.domain.backup.AppVersion
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.BackupDecoder
import com.gymtracker.core.domain.backup.BackupEncoder
import com.gymtracker.core.domain.backup.BackupFileReader
import com.gymtracker.core.domain.backup.BackupFileWriter
import com.gymtracker.core.domain.backup.ExportBackup
import com.gymtracker.core.domain.backup.ExportBackupToFile
import com.gymtracker.core.domain.backup.FakeBackupStore
import com.gymtracker.core.domain.backup.ImportBackup
import com.gymtracker.core.domain.backup.PreviewBackupImport
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.FakeSessionRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-40 (export) and US-41 (import) from Settings, and what the screen shows at each step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val member = UserId("member-1")
    private val now: Instant = Instant.parse("2026-08-15T18:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val destination = "content://fake/backup.json"
    private val known = setOf(TestData.BENCH, TestData.SQUAT)

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

    private val incomingFixture = TestData.memberWithARoutineAndASession(member)
    private val incomingContents =
        BackupContents(
            memberId = member,
            unit = WeightUnit.KG,
            restDefault = Duration.ofSeconds(90),
            sessions = incomingFixture.sessions,
            sessionExercises = incomingFixture.sessionExercises,
            sets = incomingFixture.sets,
            routines = incomingFixture.routines,
            routineItems = incomingFixture.routineItems,
        )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        writer: BackupFileWriter = FakeFileWriter(),
        currentMember: CurrentMember = FakeCurrentMember(member),
        sessions: FakeSessionRepository = FakeSessionRepository(),
        catalog: ExerciseCatalog = FakeCatalog(known),
        store: FakeBackupStore = FakeBackupStore(seed = mapOf(member to emptyContents)),
        decoder: BackupDecoder = BackupDecoder { incomingContents },
        fileReader: BackupFileReader = BackupFileReader { "raw" },
    ) = SettingsViewModel(
        currentMember = currentMember,
        export =
            ExportBackupToFile(
                exportBackup = ExportBackup(store),
                encoder = FakeEncoder(),
                fileWriter = writer,
                appVersion = AppVersion { "1.0-test" },
                clock = clock,
            ),
        previewImport = PreviewBackupImport(fileReader, decoder, catalog, sessions, store),
        importBackup = ImportBackup(sessions, catalog, store),
        sessions = sessions,
    )

    // --- Export (US-40) ---

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
    fun `dismissing the export error clears it without touching anything else`() =
        runTest {
            val viewModel = viewModel(writer = FakeFileWriter(failWith = IllegalStateException("no space")))
            viewModel.onExport(destination)

            viewModel.onExportErrorDismissed()

            assertNull(viewModel.uiState.value.exportError)
        }

    // --- Import (US-41) ---

    @Test
    fun `selecting a valid file shows a preview with real counts, and writes nothing yet`() =
        runTest {
            val store =
                FakeBackupStore(
                    seed =
                        mapOf(
                            member to
                                emptyContents.copy(
                                    sessions =
                                        listOf(
                                            WorkoutSession(SessionId("s1"), member, null, now, now, null),
                                        ),
                                ),
                        ),
                )
            val viewModel = viewModel(store = store)

            viewModel.onImportFileSelected(destination)

            val preview = assertNotNull(viewModel.uiState.value.importPreview)
            assertEquals(1, preview.currentSessionCount)
            assertEquals(1, preview.incomingSessionCount)
            assertEquals(1, preview.incomingRoutineCount)
            assertEquals(1, store.read(member).sessions.size, "nothing is written before confirming")
            assertNull(viewModel.uiState.value.importError)
        }

    @Test
    fun `confirming replaces everything and clears the preview`() =
        runTest {
            val store = FakeBackupStore(seed = mapOf(member to emptyContents))
            val viewModel = viewModel(store = store)
            viewModel.onImportFileSelected(destination)

            viewModel.onImportConfirmed()

            assertEquals(incomingContents, store.lastReplaced)
            assertNull(viewModel.uiState.value.importPreview)
        }

    @Test
    fun `cancelling writes nothing and clears the preview`() =
        runTest {
            val store = FakeBackupStore(seed = mapOf(member to emptyContents))
            val viewModel = viewModel(store = store)
            viewModel.onImportFileSelected(destination)

            viewModel.onImportCancelled()

            assertNull(store.lastReplaced)
            assertNull(viewModel.uiState.value.importPreview)
        }

    @Test
    fun `a file this build cannot read is reported, not thrown, and shows no preview`() =
        runTest {
            val failingReader = BackupFileReader { throw java.io.IOException("boom") }
            val viewModel = viewModel(fileReader = failingReader)

            viewModel.onImportFileSelected(destination)

            assertNotNull(viewModel.uiState.value.importError)
            assertNull(viewModel.uiState.value.importPreview)
        }

    @Test
    fun `a file referencing an unknown exercise is refused, naming it`() =
        runTest {
            val viewModel = viewModel(catalog = FakeCatalog(setOf(TestData.SQUAT))) // BENCH missing

            viewModel.onImportFileSelected(destination)

            val error = assertNotNull(viewModel.uiState.value.importError)
            assertTrue(error.contains(TestData.BENCH.value), "names the missing exercise")
        }

    @Test
    fun `import is unavailable while a workout is running`() =
        runTest {
            val sessions = FakeSessionRepository()
            sessions.startSession(WorkoutSession(SessionId("active"), member, null, now, null, null))
            val viewModel = viewModel(sessions = sessions)

            assertTrue(viewModel.uiState.value.hasActiveSession)

            viewModel.onImportFileSelected(destination)

            assertNotNull(viewModel.uiState.value.importError)
            assertTrue(viewModel.uiState.value.hasActiveSession, "still true after the refused attempt")
        }

    @Test
    fun `dismissing the import error clears it without touching anything else`() =
        runTest {
            val viewModel = viewModel(fileReader = BackupFileReader { throw java.io.IOException("boom") })
            viewModel.onImportFileSelected(destination)

            viewModel.onImportErrorDismissed()

            assertNull(viewModel.uiState.value.importError)
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
        private var id: UserId,
    ) : CurrentMember {
        override suspend fun id(): UserId = id

        override suspend fun restore(id: UserId) {
            this.id = id
        }
    }

    private class FakeCatalog(
        private val ids: Set<ExerciseId>,
    ) : ExerciseCatalog {
        override fun observeRanked(forMember: UserId) = error("not needed for this test")

        override suspend fun knownExerciseIds(): Set<ExerciseId> = ids
    }
}
