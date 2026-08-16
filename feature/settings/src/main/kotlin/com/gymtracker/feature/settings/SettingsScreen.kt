package com.gymtracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
 * ADR-0030 gave Routines. Export is the only action here yet; Import (US-41) and the unit/rest
 * preference controls (US-42) arrive in their own PRs, per `roadmap.md`'s M3c.
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

    SettingsScreen(
        state = state,
        onExportClick = { exportLauncher.launch(suggestedBackupFileName()) },
        onErrorDismissed = viewModel::onExportErrorDismissed,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onExportClick: () -> Unit = {},
    onErrorDismissed: () -> Unit = {},
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
                ExportErrorBanner(message = state.exportError, onDismiss = onErrorDismissed)
            }

            PrimaryActionButton(
                text = if (state.isExporting) "Exporting…" else "Export data",
                onClick = onExportClick,
                enabled = !state.isExporting,
            )
        }
    }
}

@Composable
private fun ExportErrorBanner(
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
            Text("Export failed: $message", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Dismiss")
            }
        }
    }
}

/** "gym-tracker-2026-08-15.json" — a default name, not a constraint; the picker lets it be edited. */
private fun suggestedBackupFileName(clock: Clock = Clock.systemDefaultZone()): String {
    val date = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE)
    return "gym-tracker-$date.json"
}
