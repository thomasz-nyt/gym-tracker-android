package com.gymtracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.gymtracker.core.designsystem.component.StepperField
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.units.WeightUnit
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Settings (US-40, US-41, US-42) — a drill-down reached from Train's header, the same shape
 * ADR-0030 gave Routines.
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
        onUnitChanged = viewModel::onUnitChanged,
        onRestDefaultStepped = viewModel::onRestDefaultStepped,
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
    onUnitChanged: (WeightUnit) -> Unit = {},
    onRestDefaultStepped: (Int) -> Unit = {},
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

            UnitToggle(unit = state.unit, onUnitChanged = onUnitChanged)

            StepperField(
                label = "Default rest (seconds)",
                value = state.restDefaultSeconds.toString(),
                // Step-only: a rest default is a small, bounded number, so +/- covers the whole
                // range US-05 needs. Typing is not wired to anything, the same as a read-only
                // field would be, rather than adding a parse-and-validate path for a two-digit
                // number nobody needs to type.
                onValueChange = {},
                onStep = onRestDefaultStepped,
            )

            ExportSection(
                isExporting = state.isExporting,
                exportError = state.exportError,
                onExportClick = onExportClick,
                onExportErrorDismissed = onExportErrorDismissed,
            )

            ImportSection(
                state = state,
                onImportClick = onImportClick,
                onImportErrorDismissed = onImportErrorDismissed,
            )
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
private fun ExportSection(
    isExporting: Boolean,
    exportError: String?,
    onExportClick: () -> Unit,
    onExportErrorDismissed: () -> Unit,
) {
    Text(
        text =
            "Export everything you've logged to a file you choose where to keep — " +
                "including a cloud-synced folder, if you want it off this device too.",
        style = MaterialTheme.typography.bodyMedium,
    )

    if (exportError != null) {
        ErrorBanner(message = "Export failed: $exportError", onDismiss = onExportErrorDismissed)
    }

    PrimaryActionButton(
        text = if (isExporting) "Exporting…" else "Export data",
        onClick = onExportClick,
        enabled = !isExporting,
    )
}

@Composable
private fun ImportSection(
    state: SettingsUiState,
    onImportClick: () -> Unit,
    onImportErrorDismissed: () -> Unit,
) {
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

/** ADR-0008: kg/lb, applied at the presentation edge only — nothing stored ever changes. */
@Composable
private fun UnitToggle(
    unit: WeightUnit,
    onUnitChanged: (WeightUnit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        Text("Weight unit", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
            WeightUnit.entries.forEach { candidate ->
                FilterChip(
                    selected = candidate == unit,
                    onClick = { onUnitChanged(candidate) },
                    label = { Text(candidate.name.lowercase()) },
                    // ADR-0019: FilterChip reads CornerFull unless told otherwise (Shape.kt's
                    // documented trap) — the same override Browse's own chips already need.
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                )
            }
        }
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
