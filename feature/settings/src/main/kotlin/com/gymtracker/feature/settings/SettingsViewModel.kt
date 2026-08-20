package com.gymtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.ExportBackupToFile
import com.gymtracker.core.domain.backup.ImportBackup
import com.gymtracker.core.domain.backup.ImportBackupResult
import com.gymtracker.core.domain.backup.ImportPreviewResult
import com.gymtracker.core.domain.backup.ImportRefusalReason
import com.gymtracker.core.domain.backup.PreviewBackupImport
import com.gymtracker.core.domain.health.ForgetHealthMetrics
import com.gymtracker.core.domain.health.HealthIntegration
import com.gymtracker.core.domain.health.HealthMetricsSource
import com.gymtracker.core.domain.health.HealthPermission
import com.gymtracker.core.domain.health.HealthStatus
import com.gymtracker.core.domain.health.SessionsWithHealthMetrics
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.rest.RestTimerStore
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

/** What the confirm dialog shows before an import runs (US-41) — real counts, not a bare warning. */
data class ImportPreviewUi(
    val currentSessionCount: Int,
    val currentRoutineCount: Int,
    val incomingSessionCount: Int,
    val incomingRoutineCount: Int,
)

/**
 * What just landed, once an import actually ran. Carries the same counts [ImportPreviewUi]
 * already validated — reused, not recomputed, since [ImportBackupResult.Imported] is a bare
 * `data object` and the numbers were already known to be correct by the time the member
 * confirmed.
 */
data class ImportSuccessUi(
    val sessionCount: Int,
    val routineCount: Int,
)

/** Everything the Settings screen renders (US-40, US-41, US-42). */
data class SettingsUiState(
    val isExporting: Boolean = false,
    val exportError: String? = null,
    /** True once an export has actually landed on disk. The member replaces nothing here, but a
     * write that silently succeeds is indistinguishable on screen from one that silently did
     * nothing — this is the difference. */
    val exportSucceeded: Boolean = false,
    val hasActiveSession: Boolean = false,
    val isPreviewingImport: Boolean = false,
    val importPreview: ImportPreviewUi? = null,
    val importError: String? = null,
    /** Non-null immediately after a confirmed import replaces the member's data. The whole
     * database just changed under them; a dialog closing is not enough to say so. */
    val importSucceeded: ImportSuccessUi? = null,
    val unit: WeightUnit = WeightUnit.LB,
    val restDefaultSeconds: Long = DEFAULT_REST_SECONDS,
    /**
     * US-20: the device/account gate, independent of [healthIntegrationEnabled] (ADR-0038).
     * `SettingsScreen` renders no health UI at all while this is [HealthStatus.Unavailable] —
     * the same absence pattern US-13 established.
     */
    val healthStatus: HealthStatus = HealthStatus.Unavailable,
    /** The member's own opt-in (US-21), independent of [healthStatus]. Defaults off. */
    val healthIntegrationEnabled: Boolean = false,
    /**
     * The permission currently awaiting its on-screen reason and a system request, or `null`
     * when no walk is in progress. Non-null only between [SettingsViewModel.onHealthIntegrationToggled]
     * turning the toggle on and the last permission's result landing.
     */
    val pendingHealthPermission: HealthPermission? = null,
    /**
     * US-23's offer, non-null only between turning the toggle off with metrics already
     * imported and answering the dialog either way. Never gates whether reads stop — that
     * has already happened by the time this appears (ADR-0040).
     */
    val forgetMetricsOffer: ForgetMetricsOfferUi? = null,
)

/** The revoke offer's one piece of data: how many workouts would lose their metrics (US-23). */
data class ForgetMetricsOfferUi(
    val sessionCount: Int,
)

/**
 * US-40 (export), US-41 (import) and US-42 (the unit and rest-default preferences). `destination`
 * throughout is a platform file identifier in its string form — a content URI's `toString()` on
 * Android — the same shape every backup port takes it, so this class never needs
 * `android.net.Uri` in its own signature. `SettingsRoute` converts the `Uri` the system file
 * picker hands back before calling in, which is what keeps this class (and its test) free of any
 * Android framework type.
 *
 * Import is two steps, never one: [onImportFileSelected] reads, decodes and validates a chosen
 * file and reports real counts without writing anything; only [onImportConfirmed] calls
 * [importBackup], which re-checks everything itself rather than trusting the preview's word for
 * it — the two are separate reads of live state, and either can have changed in between.
 *
 * The unit and rest-default controls wire a UI to setters nothing has ever called —
 * [UnitPreference.set] and [RestTimerStore.setDefaultRest] both already existed and were already
 * bound; ADR-0008 and US-05 promised both and neither had a home until this screen.
 *
 * US-20/US-21 add a fourth concern, health, at 12 member functions against detekt's threshold
 * of 11 — one past where `ImportRefusalReason.message()` was pulled out to a top-level function
 * to buy the same headroom previously. That trick is spent: [onHealthIntegrationToggled] and
 * [onHealthPermissionResult] both need `_uiState` and can't move out the same way, and folding
 * either into an existing dismiss-style method would blur two genuinely different UI events for
 * a lint count. Suppressed rather than forced smaller by a change this PR's scope doesn't call
 * for; the honest fix is splitting this class by concern (export/import, preferences, health),
 * which is a bigger change than a health opt-in warrants.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val currentMember: CurrentMember,
        private val export: ExportBackupToFile,
        private val previewImport: PreviewBackupImport,
        private val importBackup: ImportBackup,
        sessions: SessionRepository,
        private val unitPreference: UnitPreference,
        private val restTimerStore: RestTimerStore,
        private val healthMetricsSource: HealthMetricsSource,
        private val healthIntegration: HealthIntegration,
        private val forgetHealthMetrics: ForgetHealthMetrics,
        private val sessionsWithHealthMetrics: SessionsWithHealthMetrics,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        /** What [onImportFileSelected] validated, held until [onImportConfirmed] or [onImportCancelled]. */
        private var pendingImport: BackupContents? = null

        init {
            // Launched eagerly rather than exposed as their own lazily-shared StateFlows: this
            // screen's controls must read live the moment it opens, not only once something
            // starts collecting them.
            viewModelScope.launch {
                flow { emit(currentMember.id()) }
                    .flatMapLatest { sessions.observeActiveSession(it) }
                    .map { it != null }
                    .collect { active -> _uiState.update { it.copy(hasActiveSession = active) } }
            }
            viewModelScope.launch {
                unitPreference.observe().collect { unit -> _uiState.update { it.copy(unit = unit) } }
            }
            viewModelScope.launch {
                restTimerStore.defaultRest.collect { rest ->
                    _uiState.update { it.copy(restDefaultSeconds = rest.seconds) }
                }
            }
            viewModelScope.launch {
                // status() is independent of the toggle (ADR-0038) — re-read here so the section
                // can decide whether to render at all, and again whenever the toggle itself
                // changes, since a member flipping it is the other moment the picture can change.
                healthIntegration.observe().collect { enabled ->
                    _uiState.update {
                        it.copy(healthIntegrationEnabled = enabled, healthStatus = healthMetricsSource.status())
                    }
                }
            }
        }

        fun onExport(destination: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isExporting = true, exportError = null, exportSucceeded = false) }
                runCatching { export(currentMember.id(), destination) }
                    .onSuccess { _uiState.update { it.copy(isExporting = false, exportSucceeded = true) } }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(isExporting = false, exportError = error.message ?: "Export failed")
                        }
                    }
            }
        }

        fun onExportErrorDismissed() {
            _uiState.update { it.copy(exportError = null) }
        }

        fun onExportSuccessDismissed() {
            _uiState.update { it.copy(exportSucceeded = false) }
        }

        fun onImportFileSelected(source: String) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isPreviewingImport = true,
                        importError = null,
                        importPreview = null,
                        importSucceeded = null,
                    )
                }
                when (val result = previewImport(currentMember.id(), source)) {
                    is ImportPreviewResult.Ready -> {
                        pendingImport = result.incoming
                        _uiState.update {
                            it.copy(
                                isPreviewingImport = false,
                                importPreview =
                                    ImportPreviewUi(
                                        currentSessionCount = result.currentSessionCount,
                                        currentRoutineCount = result.currentRoutineCount,
                                        incomingSessionCount = result.incomingSessionCount,
                                        incomingRoutineCount = result.incomingRoutineCount,
                                    ),
                            )
                        }
                    }
                    is ImportPreviewResult.Refused ->
                        _uiState.update { it.copy(isPreviewingImport = false, importError = result.reason.message()) }
                    is ImportPreviewResult.Unreadable -> {
                        val message = "Could not read this file: ${result.message}"
                        _uiState.update { it.copy(isPreviewingImport = false, importError = message) }
                    }
                }
            }
        }

        fun onImportConfirmed() {
            val contents = pendingImport ?: return
            // Read before the import runs: onImportFileSelected is the one place that clears
            // importPreview, and this call is about to trigger that same update — capturing the
            // preview now is what lets the success banner report the same counts the member
            // already confirmed, rather than re-deriving them from contents after the fact.
            val preview = _uiState.value.importPreview
            viewModelScope.launch {
                when (val result = importBackup(currentMember.id(), contents)) {
                    is ImportBackupResult.Imported -> {
                        pendingImport = null
                        _uiState.update {
                            it.copy(
                                importPreview = null,
                                importSucceeded =
                                    preview?.let { p ->
                                        ImportSuccessUi(
                                            sessionCount = p.incomingSessionCount,
                                            routineCount = p.incomingRoutineCount,
                                        )
                                    },
                            )
                        }
                    }
                    is ImportBackupResult.Refused -> {
                        pendingImport = null
                        _uiState.update { it.copy(importPreview = null, importError = result.reason.message()) }
                    }
                }
            }
        }

        fun onImportCancelled() {
            pendingImport = null
            _uiState.update { it.copy(importPreview = null) }
        }

        fun onImportErrorDismissed() {
            _uiState.update { it.copy(importError = null) }
        }

        fun onImportSuccessDismissed() {
            _uiState.update { it.copy(importSucceeded = null) }
        }

        fun onUnitChanged(unit: WeightUnit) {
            viewModelScope.launch { unitPreference.set(unit) }
        }

        /**
         * [delta] is +1 or -1 steps of [REST_STEP_SECONDS]. Floored at [MIN_REST_SECONDS] —
         * zero or negative would leave "Add set" with no rest to start at all.
         */
        fun onRestDefaultStepped(delta: Int) {
            val current = _uiState.value.restDefaultSeconds
            val next = (current + delta * REST_STEP_SECONDS).coerceAtLeast(MIN_REST_SECONDS)
            viewModelScope.launch { restTimerStore.setDefaultRest(Duration.ofSeconds(next)) }
        }

        /**
         * US-21. Stores the choice, then either starts the permission walk at the first
         * permission (turning on) or clears any walk in progress (turning off) — the section
         * itself decides what to show from [SettingsUiState.pendingHealthPermission] and
         * [SettingsUiState.healthIntegrationEnabled].
         */
        fun onHealthIntegrationToggled(enabled: Boolean) {
            viewModelScope.launch {
                // Unconditional, and first: US-23's "reads stop immediately" is true the moment
                // this lands, and never waits on the offer below being answered (ADR-0040).
                healthIntegration.set(enabled)
                _uiState.update {
                    it.copy(
                        healthIntegrationEnabled = enabled,
                        pendingHealthPermission = if (enabled) HealthPermission.entries.first() else null,
                    )
                }
                if (!enabled) offerToForgetMetrics()
            }
        }

        /**
         * US-23: offer to delete what was imported — but only when there is something to
         * delete. Counting first is what keeps a member who never imported anything from
         * seeing a dialog about nothing, which is the nag `health-connect.md` forbids.
         */
        private suspend fun offerToForgetMetrics() {
            val count = sessionsWithHealthMetrics(currentMember.id())
            if (count > 0) {
                _uiState.update { it.copy(forgetMetricsOffer = ForgetMetricsOfferUi(sessionCount = count)) }
            }
        }

        /** US-23: the member accepted the offer — clear every imported metric they hold. */
        fun onForgetMetricsConfirmed() {
            viewModelScope.launch {
                forgetHealthMetrics(currentMember.id())
                _uiState.update { it.copy(forgetMetricsOffer = null) }
            }
        }

        /**
         * US-23: the member declined. Nothing is deleted, and nothing is remembered — toggling
         * off again later, with metrics still there, offers again (ADR-0040's rejected
         * DataStore-flag alternative).
         */
        fun onForgetMetricsDeclined() {
            _uiState.update { it.copy(forgetMetricsOffer = null) }
        }

        /**
         * Called once the system permission request for [permission] has returned, granted or
         * denied — the walk advances either way (US-21's "any denial is final for that run" is
         * enforced by never asking for the same permission twice, not by retrying it here).
         * [HealthMetricsSource.status] is re-read after every step, not only the last, since a
         * grant can change it and status is never assumed stable (ADR-0038).
         */
        fun onHealthPermissionResult(permission: HealthPermission) {
            val next = HealthPermission.entries.getOrNull(permission.ordinal + 1)
            viewModelScope.launch {
                val status = healthMetricsSource.status()
                _uiState.update { it.copy(pendingHealthPermission = next, healthStatus = status) }
            }
        }

        private companion object {
            const val REST_STEP_SECONDS = 5L
            const val MIN_REST_SECONDS = 10L
        }
    }

/**
 * Top-level rather than a class member: a pure function of [ImportRefusalReason] with no
 * dependency on [SettingsViewModel]'s own state, and keeping it there was the difference between
 * 11 member functions (detekt's `TooManyFunctions` threshold) and 10 once the two new success-
 * dismissal callbacks landed alongside it.
 */
private fun ImportRefusalReason.message(): String =
    when (this) {
        is ImportRefusalReason.SessionActive ->
            "A workout is running. Finish or discard it before importing."
        is ImportRefusalReason.UnknownExercises ->
            "This file references exercises not in this catalog: " +
                missingExerciseIds.joinToString { it.value }
    }

/** ADR-0008 default while the real preference is still loading. */
private const val DEFAULT_REST_SECONDS = 60L
