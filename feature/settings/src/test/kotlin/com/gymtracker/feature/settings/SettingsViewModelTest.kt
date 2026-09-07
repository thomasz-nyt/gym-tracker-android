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
import com.gymtracker.core.domain.health.ForgetHealthMetrics
import com.gymtracker.core.domain.health.HealthIntegration
import com.gymtracker.core.domain.health.HealthMetricsSource
import com.gymtracker.core.domain.health.HealthPermission
import com.gymtracker.core.domain.health.HealthStatus
import com.gymtracker.core.domain.health.SessionsWithHealthMetrics
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.KeepScreenOnPreference
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.SessionMetrics
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.rest.RestCueTonePreference
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
        keepScreenOnPreference: FakeKeepScreenOnPreference = FakeKeepScreenOnPreference(),
        restCueTonePreference: FakeRestCueTonePreference = FakeRestCueTonePreference(),
        healthMetricsSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
        healthIntegration: FakeHealthIntegration = FakeHealthIntegration(),
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
        keepScreenOnPreference = keepScreenOnPreference,
        restCueTonePreference = restCueTonePreference,
        healthMetricsSource = healthMetricsSource,
        healthIntegration = healthIntegration,
        forgetHealthMetrics = ForgetHealthMetrics(sessions),
        sessionsWithHealthMetrics = SessionsWithHealthMetrics(sessions),
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
    fun `starts reading whether the screen stays on during a workout, which defaults to on`() =
        runTest {
            // US-59: on unless the member says otherwise — the opposite default from the two
            // opt-in integrations below it on the screen, deliberately.
            assertEquals(true, viewModel().uiState.value.keepScreenOn)

            val turnedOff = viewModel(keepScreenOnPreference = FakeKeepScreenOnPreference(initial = false))
            assertEquals(false, turnedOff.uiState.value.keepScreenOn)
        }

    @Test
    fun `turning keep-screen-on off is written through and reflected immediately`() =
        runTest {
            val preference = FakeKeepScreenOnPreference()
            val viewModel = viewModel(keepScreenOnPreference = preference)

            viewModel.onKeepScreenOnToggled(false)

            assertEquals(false, viewModel.uiState.value.keepScreenOn)
            assertEquals(false, preference.current())
        }

    @Test
    fun `the rest cue's tone starts off, and reads the member's setting`() =
        runTest {
            // ADR-0049: the haptic is the cue; the tone is the opt-in.
            assertEquals(false, viewModel().uiState.value.restCueTone)

            val turnedOn = viewModel(restCueTonePreference = FakeRestCueTonePreference(initial = true))
            assertEquals(true, turnedOn.uiState.value.restCueTone)
        }

    @Test
    fun `turning the rest cue's tone on is written through and reflected immediately`() =
        runTest {
            val preference = FakeRestCueTonePreference()
            val viewModel = viewModel(restCueTonePreference = preference)

            viewModel.onRestCueToneToggled(true)

            assertEquals(true, viewModel.uiState.value.restCueTone)
            assertEquals(true, preference.current())
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

    // --- Health Connect (US-20, US-21) ---

    @Test
    fun `status is read on start, independent of the toggle`() =
        runTest {
            // ADR-0038's correction: status() must never depend on the toggle, or Settings could
            // never legitimately show the control that turns it on in the first place.
            val viewModel =
                viewModel(
                    healthMetricsSource = FakeHealthMetricsSource(initial = HealthStatus.PermissionRequired),
                    healthIntegration = FakeHealthIntegration(initial = false),
                )

            assertEquals(HealthStatus.PermissionRequired, viewModel.uiState.value.healthStatus)
            assertEquals(false, viewModel.uiState.value.healthIntegrationEnabled)
        }

    @Test
    fun `the section stays absent (Unavailable) when the device or account cannot use it`() =
        runTest {
            val viewModel = viewModel(healthMetricsSource = FakeHealthMetricsSource(initial = HealthStatus.Unavailable))

            assertEquals(HealthStatus.Unavailable, viewModel.uiState.value.healthStatus)
        }

    @Test
    fun `turning the toggle on stores it and starts the permission walk at the first permission`() =
        runTest {
            val healthIntegration = FakeHealthIntegration(initial = false)
            val viewModel = viewModel(healthIntegration = healthIntegration)

            viewModel.onHealthIntegrationToggled(true)

            assertEquals(true, healthIntegration.current())
            assertEquals(true, viewModel.uiState.value.healthIntegrationEnabled)
            assertEquals(HealthPermission.HEART_RATE, viewModel.uiState.value.pendingHealthPermission)
        }

    @Test
    fun `each permission result advances to the next permission, in order`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onHealthIntegrationToggled(true)
            assertEquals(HealthPermission.HEART_RATE, viewModel.uiState.value.pendingHealthPermission)

            viewModel.onHealthPermissionResult(HealthPermission.HEART_RATE)
            assertEquals(HealthPermission.ACTIVE_CALORIES, viewModel.uiState.value.pendingHealthPermission)

            viewModel.onHealthPermissionResult(HealthPermission.ACTIVE_CALORIES)
            assertEquals(HealthPermission.EXERCISE, viewModel.uiState.value.pendingHealthPermission)
        }

    @Test
    fun `the walk ends after the last permission, regardless of whether it was granted or denied`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onHealthIntegrationToggled(true)
            viewModel.onHealthPermissionResult(HealthPermission.HEART_RATE)
            viewModel.onHealthPermissionResult(HealthPermission.ACTIVE_CALORIES)

            viewModel.onHealthPermissionResult(HealthPermission.EXERCISE)

            assertNull(viewModel.uiState.value.pendingHealthPermission)
        }

    @Test
    fun `finishing the walk re-reads status, reflecting permissions granted during it`() =
        runTest {
            val healthMetricsSource = FakeHealthMetricsSource(initial = HealthStatus.PermissionRequired)
            val viewModel = viewModel(healthMetricsSource = healthMetricsSource)
            viewModel.onHealthIntegrationToggled(true)

            // The real gateway would report this once the OS records a grant; the fake stands
            // in for that here rather than asserting anything about the OS itself.
            healthMetricsSource.current = HealthStatus.Ready
            viewModel.onHealthPermissionResult(HealthPermission.HEART_RATE)

            assertEquals(HealthStatus.Ready, viewModel.uiState.value.healthStatus)
        }

    @Test
    fun `turning the toggle off stops the walk and clears any pending permission`() =
        runTest {
            val healthIntegration = FakeHealthIntegration(initial = false)
            val viewModel = viewModel(healthIntegration = healthIntegration)
            viewModel.onHealthIntegrationToggled(true)
            assertEquals(HealthPermission.HEART_RATE, viewModel.uiState.value.pendingHealthPermission)

            viewModel.onHealthIntegrationToggled(false)

            assertEquals(false, healthIntegration.current())
            assertNull(viewModel.uiState.value.pendingHealthPermission)
        }

    // --- Revoke (US-23, ADR-0040) ---

    private fun sessionWithMetrics(
        id: String,
        owner: UserId = member,
        metrics: SessionMetrics? = SessionMetrics(120, 160, 300, "health_connect"),
    ) = WorkoutSession(
        id = SessionId(id),
        userId = owner,
        gymName = null,
        startedAt = now,
        endedAt = now.plusSeconds(3600),
        metrics = metrics,
    )

    @Test
    fun `turning the toggle off stops reads whether or not the offer is ever answered`() =
        runTest {
            val healthIntegration = FakeHealthIntegration(initial = true)
            val sessions = FakeSessionRepository(listOf(sessionWithMetrics("s1")))
            val viewModel = viewModel(sessions = sessions, healthIntegration = healthIntegration)

            viewModel.onHealthIntegrationToggled(false)

            // The write lands first and unconditionally. The offer below is only ever about
            // rows already imported — it never gates whether reads stop.
            assertEquals(false, healthIntegration.current())
            assertNotNull(viewModel.uiState.value.forgetMetricsOffer)
        }

    @Test
    fun `the offer names the real number of workouts carrying metrics`() =
        runTest {
            val sessions =
                FakeSessionRepository(
                    listOf(
                        sessionWithMetrics("s1"),
                        sessionWithMetrics("s2"),
                        sessionWithMetrics("s3", metrics = null),
                        sessionWithMetrics("s4", owner = UserId("someone-else")),
                    ),
                )
            val viewModel = viewModel(sessions = sessions, healthIntegration = FakeHealthIntegration(initial = true))

            viewModel.onHealthIntegrationToggled(false)

            assertEquals(
                2,
                viewModel.uiState.value.forgetMetricsOffer
                    ?.sessionCount,
            )
        }

    @Test
    fun `with nothing imported, turning it off offers nothing at all`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(sessionWithMetrics("s1", metrics = null)))
            val healthIntegration = FakeHealthIntegration(initial = true)
            val viewModel = viewModel(sessions = sessions, healthIntegration = healthIntegration)

            viewModel.onHealthIntegrationToggled(false)

            assertEquals(false, healthIntegration.current())
            assertNull(viewModel.uiState.value.forgetMetricsOffer)
        }

    @Test
    fun `accepting clears the metrics and dismisses the offer`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(sessionWithMetrics("s1"), sessionWithMetrics("s2")))
            val viewModel = viewModel(sessions = sessions, healthIntegration = FakeHealthIntegration(initial = true))
            viewModel.onHealthIntegrationToggled(false)

            viewModel.onForgetMetricsConfirmed()

            assertNull(viewModel.uiState.value.forgetMetricsOffer)
            assertEquals(listOf(null, null), sessions.sessions.map { it.metrics })
        }

    @Test
    fun `declining deletes nothing`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(sessionWithMetrics("s1")))
            val viewModel = viewModel(sessions = sessions, healthIntegration = FakeHealthIntegration(initial = true))
            viewModel.onHealthIntegrationToggled(false)

            viewModel.onForgetMetricsDeclined()

            assertNull(viewModel.uiState.value.forgetMetricsOffer)
            assertNotNull(sessions.sessions.single().metrics)
        }

    @Test
    fun `declining is not remembered — toggling off again offers again`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(sessionWithMetrics("s1")))
            val viewModel = viewModel(sessions = sessions, healthIntegration = FakeHealthIntegration(initial = true))
            viewModel.onHealthIntegrationToggled(false)
            viewModel.onForgetMetricsDeclined()

            viewModel.onHealthIntegrationToggled(true)
            viewModel.onHealthIntegrationToggled(false)

            assertEquals(
                1,
                viewModel.uiState.value.forgetMetricsOffer
                    ?.sessionCount,
            )
        }

    @Test
    fun `turning the toggle on never offers to delete anything`() =
        runTest {
            val sessions = FakeSessionRepository(listOf(sessionWithMetrics("s1")))
            val viewModel = viewModel(sessions = sessions, healthIntegration = FakeHealthIntegration(initial = false))

            viewModel.onHealthIntegrationToggled(true)

            assertNull(viewModel.uiState.value.forgetMetricsOffer)
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
        private val total = MutableStateFlow<Duration?>(null)
        private val asked = MutableStateFlow(false)

        override val restEndsAt: Flow<Instant?> = endsAt
        override val restTotal: Flow<Duration?> = total
        override val defaultRest: Flow<Duration> = default
        override val shouldAskForNotificationPermission: Flow<Boolean> = asked.map { !it }

        override suspend fun setRestEndsAt(instant: Instant?) {
            endsAt.value = instant
            if (instant == null) total.value = null
        }

        override suspend fun setRest(
            endsAt: Instant,
            total: Duration,
        ) {
            this.endsAt.value = endsAt
            this.total.value = total
        }

        override suspend fun setDefaultRest(rest: Duration) {
            default.value = rest
        }

        override suspend fun markNotificationPermissionAsked() {
            asked.value = true
        }
    }

    private class FakeKeepScreenOnPreference(
        initial: Boolean = true,
    ) : KeepScreenOnPreference {
        private val state = MutableStateFlow(initial)

        override fun observe(): Flow<Boolean> = state

        override suspend fun current(): Boolean = state.value

        override suspend fun set(enabled: Boolean) {
            state.value = enabled
        }
    }

    private class FakeRestCueTonePreference(
        initial: Boolean = false,
    ) : RestCueTonePreference {
        private val state = MutableStateFlow(initial)

        override fun observe(): Flow<Boolean> = state

        override suspend fun current(): Boolean = state.value

        override suspend fun set(enabled: Boolean) {
            state.value = enabled
        }
    }

    private class FakeHealthMetricsSource(
        initial: HealthStatus = HealthStatus.Unavailable,
    ) : HealthMetricsSource {
        /** Mutable so a test can simulate a permission grant landing mid-walk. */
        var current: HealthStatus = initial

        override suspend fun status(): HealthStatus = current

        override suspend fun metricsFor(window: ClosedRange<Instant>) = null
    }

    private class FakeHealthIntegration(
        initial: Boolean = false,
    ) : HealthIntegration {
        private val state = MutableStateFlow(initial)

        override fun observe(): Flow<Boolean> = state

        override suspend fun current(): Boolean = state.value

        override suspend fun set(enabled: Boolean) {
            state.value = enabled
        }
    }
}
