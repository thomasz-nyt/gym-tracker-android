package com.gymtracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Settings (US-40, US-41, US-42) — a drill-down reached from Train's header, the same shape
 * ADR-0030 gave Routines. Export and import are here; the unit/rest preference controls (US-42)
 * arrive in their own PR, per `roadmap.md`'s M3c.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { viewModel.onExport(it.toString()) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onImportFileSelected(it.toString()) }
        }

    SettingsScreen(
        state = state,
        onExportClick = { exportLauncher.launch(suggestedBackupFileName()) },
        onExportErrorDismissed = viewModel::onExportErrorDismissed,
        onImportClick = { importLauncher.launch(arrayOf("application/json")) },
        onImportConfirmed = viewModel::onImportConfirmed,
        onImportCancelled = viewModel::onImportCancelled,
        onImportErrorDismissed = viewModel::onImportErrorDismissed,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onExportClick: () -> Unit = {},
    onExportErrorDismissed: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onImportConfirmed: () -> Unit = {},
    onImportCancelled: () -> Unit = {},
    onImportErrorDismissed: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DrillDownTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(GymDimens.ScreenPadding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            Text(
                text =
                    "Export everything you've logged to a file you choose where to keep — " +
                        "including a cloud-synced folder, if you want it off this device too.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.exportError != null) {
                ErrorBanner(message = "Export failed: ${state.exportError}", onDismiss = onExportErrorDismissed)
            }

            PrimaryActionButton(
                text = if (state.isExporting) "Exporting…" else "Export data",
                onClick = onExportClick,
                enabled = !state.isExporting,
            )

            Text(
                text = "Import replaces everything on this device with what the file holds.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.importError != null) {
                ErrorBanner(message = state.importError, onDismiss = onImportErrorDismissed)
            }

            PrimaryActionButton(
                text = if (state.isPreviewingImport) "Reading file…" else "Import data",
                onClick = onImportClick,
                enabled = !state.hasActiveSession && !state.isPreviewingImport,
            )

            if (state.hasActiveSession) {
                Text(
                    text = "Finish or discard your current workout to import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (state.importPreview != null) {
        ImportConfirmDialog(
            preview = state.importPreview,
            onConfirm = onImportConfirmed,
            onDismiss = onImportCancelled,
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Dismiss")
            }
        }
    }
}

/**
 * US-41: real numbers on both sides, named before anything is written — "Replace 12 workouts
 * and 3 routines on this device with 9 and 2 from this file?"
 */
@Composable
private fun ImportConfirmDialog(
    preview: ImportPreviewUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace your data?") },
        text = {
            Text(
                "Replace ${preview.currentSessionCount} workouts and ${preview.currentRoutineCount} routines " +
                    "on this device with ${preview.incomingSessionCount} and ${preview.incomingRoutineCount} " +
                    "from this file?",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Replace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Cancel")
            }
        },
    )
}

/** "gym-tracker-2026-08-15.json" — a default name, not a constraint; the picker lets it be edited. */
private fun suggestedBackupFileName(clock: Clock = Clock.systemDefaultZone()): String {
    val date = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE)
    return "gym-tracker-$date.json"
}
