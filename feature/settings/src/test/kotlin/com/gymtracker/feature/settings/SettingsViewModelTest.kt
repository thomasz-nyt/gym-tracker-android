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
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.FakeSessionRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
 * US-40 (export), US-41 (import) and US-42 (unit and rest-default preferences) from Settings.
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
        unitPreference: FakeUnitPreference = FakeUnitPreference(),
        restTimerStore: FakeRestTimerStore = FakeRestTimerStore(),
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
        unitPreference = unitPreference,
        restTimerStore = restTimerStore,
    )

    // --- Export (US-40) ---

    // Replaces the old `starts idle, and returns to idle once the export completes`, which
    // pinned an actual defect: a successful export left no trace in the UI beyond the button
    // relabelling itself for a moment mid-write. The member replaces nothing here, but they
    // still deserve to know the file landed — reusing the `ErrorBanner` treatment, recoloured,
    // rather than inventing a second confirmation pattern.
    @Test
    fun `starts idle, and reports success once the export completes`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(false, viewModel.uiState.value.isExporting)
            assertEquals(false, viewModel.uiState.value.exportSucceeded)

            viewModel.onExport(destination)

            assertEquals(false, viewModel.uiState.value.isExporting)
            assertEquals(true, viewModel.uiState.value.exportSucceeded)
            assertNull(viewModel.uiState.value.exportError)
        }

    @Test
    fun `a second export clears the previous success flag while it runs`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExport(destination)
            assertEquals(true, viewModel.uiState.value.exportSucceeded)

            viewModel.onExport(destination)

            assertEquals(true, viewModel.uiState.value.exportSucceeded, "the second export also succeeded")
        }

    @Test
    fun `dismissing the export success clears it without touching anything else`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExport(destination)

            viewModel.onExportSuccessDismissed()

            assertEquals(false, viewModel.uiState.value.exportSucceeded)
        }

    @Test
    fun `a failed export never reports success`() =
        runTest {
            val viewModel = viewModel(writer = FakeFileWriter(failWith = IllegalStateException("no space")))

            viewModel.onExport(destination)

            assertEquals(false, viewModel.uiState.value.exportSucceeded)
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

    // The counterpart defect to the export one above: a successful import replaces the
    // member's entire database and the only feedback was the confirm dialog closing. Reuses the
    // counts the preview already validated — `ImportBackupResult.Imported` is a bare data
    // object, so no domain change was needed to surface real numbers here.
    @Test
    fun `confirming reports what was imported, in real counts`() =
        runTest {
            val store = FakeBackupStore(seed = mapOf(member to emptyContents))
            val viewModel = viewModel(store = store)
            viewModel.onImportFileSelected(destination)

            viewModel.onImportConfirmed()

            val success = assertNotNull(viewModel.uiState.value.importSucceeded)
            assertEquals(1, success.sessionCount)
            assertEquals(1, success.routineCount)
        }

    @Test
    fun `dismissing the import success clears it without touching anything else`() =
        runTest {
            val store = FakeBackupStore(seed = mapOf(member to emptyContents))
            val viewModel = viewModel(store = store)
            viewModel.onImportFileSelected(destination)
            viewModel.onImportConfirmed()

            viewModel.onImportSuccessDismissed()

            assertNull(viewModel.uiState.value.importSucceeded)
        }

    @Test
    fun `selecting a new file clears a previous import's success banner`() =
        runTest {
            val store = FakeBackupStore(seed = mapOf(member to emptyContents))
            val viewModel = viewModel(store = store)
            viewModel.onImportFileSelected(destination)
            viewModel.onImportConfirmed()
            assertNotNull(viewModel.uiState.value.importSucceeded)

            viewModel.onImportFileSelected(destination)

            assertNull(viewModel.uiState.value.importSucceeded)
        }

    @Test
    fun `a refused import never reports success`() =
        runTest {
            val viewModel = viewModel(catalog = FakeCatalog(setOf(TestData.SQUAT))) // BENCH missing

            viewModel.onImportFileSelected(destination)

            assertNull(viewModel.uiState.value.importSucceeded)
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

    // --- Preferences (US-42) ---

    @Test
    fun `starts reading the member's current unit and rest default`() =
        runTest {
            val viewModel =
                viewModel(
                    unitPreference = FakeUnitPreference(initial = WeightUnit.KG),
                    restTimerStore = FakeRestTimerStore(initial = Duration.ofSeconds(90)),
                )

            assertEquals(WeightUnit.KG, viewModel.uiState.value.unit)
            assertEquals(90L, viewModel.uiState.value.restDefaultSeconds)
        }

    @Test
    fun `changing the unit is reflected immediately`() =
        runTest {
            val unitPreference = FakeUnitPreference(initial = WeightUnit.LB)
            val viewModel = viewModel(unitPreference = unitPreference)

            viewModel.onUnitChanged(WeightUnit.KG)

            assertEquals(WeightUnit.KG, viewModel.uiState.value.unit)
            assertEquals(WeightUnit.KG, unitPreference.current())
        }

    @Test
    fun `stepping the rest default moves by 5 seconds and never goes below 10`() =
        runTest {
            val restTimerStore = FakeRestTimerStore(initial = Duration.ofSeconds(60))
            val viewModel = viewModel(restTimerStore = restTimerStore)

            viewModel.onRestDefaultStepped(1)
            assertEquals(65L, viewModel.uiState.value.restDefaultSeconds)

            viewModel.onRestDefaultStepped(-1)
            viewModel.onRestDefaultStepped(-1)
            assertEquals(55L, viewModel.uiState.value.restDefaultSeconds)
        }

    @Test
    fun `the rest default floor is 10 seconds, not zero or negative`() =
        runTest {
            val viewModel = viewModel(restTimerStore = FakeRestTimerStore(initial = Duration.ofSeconds(10)))

            viewModel.onRestDefaultStepped(-1)

            assertEquals(10L, viewModel.uiState.value.restDefaultSeconds)
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

    private class FakeUnitPreference(
        initial: WeightUnit = WeightUnit.LB,
    ) : UnitPreference {
        private val state = MutableStateFlow(initial)

        override fun observe(): Flow<WeightUnit> = state

        override suspend fun current(): WeightUnit = state.value

        override suspend fun set(unit: WeightUnit) {
            state.value = unit
        }
    }

    private class FakeRestTimerStore(
        initial: Duration = Duration.ofSeconds(60),
    ) : RestTimerStore {
        private val default = MutableStateFlow(initial)
        private val endsAt = MutableStateFlow<Instant?>(null)
        private val asked = MutableStateFlow(false)

        override val restEndsAt: Flow<Instant?> = endsAt
        override val defaultRest: Flow<Duration> = default
        override val shouldAskForNotificationPermission: Flow<Boolean> = asked.map { !it }

        override suspend fun setRestEndsAt(instant: Instant?) {
            endsAt.value = instant
        }

        override suspend fun setDefaultRest(rest: Duration) {
            default.value = rest
        }

        override suspend fun markNotificationPermissionAsked() {
            asked.value = true
        }
    }
}
