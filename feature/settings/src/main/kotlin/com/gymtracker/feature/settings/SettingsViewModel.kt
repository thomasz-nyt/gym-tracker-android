package com.gymtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.backup.ExportBackupToFile
import com.gymtracker.core.domain.member.CurrentMember
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Settings screen renders (US-40, US-41, US-42). */
data class SettingsUiState(
    val isExporting: Boolean = false,
    val exportError: String? = null,
)

/**
 * US-40: export. `destination` throughout is a platform file identifier in its string form —
 * a content URI's `toString()` on Android — the same shape [ExportBackupToFile] and
 * [com.gymtracker.core.domain.backup.BackupFileWriter] take it in, so this class never needs
 * `android.net.Uri` in its own signature. `SettingsRoute` converts the `Uri` the system file
 * picker hands back before calling in, which is what keeps this class (and its test) free of
 * any Android framework type.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val currentMember: CurrentMember,
        private val export: ExportBackupToFile,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
    }
