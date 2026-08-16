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

/** Everything the Settings screen renders (US-40, US-41, US-42). */
data class SettingsUiState(
    val isExporting: Boolean = false,
    val exportError: String? = null,
    val hasActiveSession: Boolean = false,
    val isPreviewingImport: Boolean = false,
    val importPreview: ImportPreviewUi? = null,
    val importError: String? = null,
    val unit: WeightUnit = WeightUnit.LB,
    val restDefaultSeconds: Long = DEFAULT_REST_SECONDS,
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
 */
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
        }

        fun onExport(destination: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isExporting = true, exportError = null) }
                runCatching { export(currentMember.id(), destination) }
                    .onSuccess { _uiState.update { it.copy(isExporting = false) } }
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

        fun onImportFileSelected(source: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isPreviewingImport = true, importError = null, importPreview = null) }
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
            viewModelScope.launch {
                when (val result = importBackup(currentMember.id(), contents)) {
                    is ImportBackupResult.Imported -> {
                        pendingImport = null
                        _uiState.update { it.copy(importPreview = null) }
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

        private fun ImportRefusalReason.message(): String =
            when (this) {
                is ImportRefusalReason.SessionActive ->
                    "A workout is running. Finish or discard it before importing."
                is ImportRefusalReason.UnknownExercises ->
                    "This file references exercises not in this catalog: " +
                        missingExerciseIds.joinToString { it.value }
            }

        private companion object {
            const val REST_STEP_SECONDS = 5L
            const val MIN_REST_SECONDS = 10L
        }
    }

/** ADR-0008 default while the real preference is still loading. */
private const val DEFAULT_REST_SECONDS = 60L
