package com.gymtracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.component.StepperField
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.health.DiscoveredHeartRateBand
import com.gymtracker.core.domain.health.HealthPermission
import com.gymtracker.core.domain.health.HealthStatus
import com.gymtracker.core.domain.health.HeartRateBandAvailability
import com.gymtracker.core.domain.health.HeartRateBandPermission
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
    heartRateBandViewModel: HeartRateBandViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val heartRateBandState by heartRateBandViewModel.uiState.collectAsStateWithLifecycle()

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { viewModel.onExport(it.toString()) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onImportFileSelected(it.toString()) }
        }

    // Which permission this launch was for — read back when the result lands, rather than
    // read from `state.pendingHealthPermission` at that point, so this never depends on the
    // callback closing over a value that might have moved on by the time it fires.
    var requestedHealthPermission by remember { mutableStateOf<HealthPermission?>(null) }
    val healthPermissionLauncher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            requestedHealthPermission?.let(viewModel::onHealthPermissionResult)
            requestedHealthPermission = null
        }

    // Same "read back what this launch was for" shape as requestedHealthPermission above, and
    // the same reason: a plain ActivityResultContracts.RequestPermission() call, since
    // BLUETOOTH_SCAN/BLUETOOTH_CONNECT are ordinary runtime permissions with no SDK-specific
    // contract the way Health Connect's are.
    var requestedHeartRateBandPermission by remember { mutableStateOf<HeartRateBandPermission?>(null) }
    val heartRateBandPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            requestedHeartRateBandPermission?.let(heartRateBandViewModel::onPermissionResult)
            requestedHeartRateBandPermission = null
        }

    SettingsScreen(
        state = state,
        heartRateBandState = heartRateBandState,
        onHeartRateBandToggled = heartRateBandViewModel::onToggled,
        onHeartRateBandPermissionRationaleContinue = { permission ->
            requestedHeartRateBandPermission = permission
            heartRateBandPermissionLauncher.launch(permission.id)
        },
        onHeartRateBandDeviceChosen = heartRateBandViewModel::onDeviceChosen,
        onExportClick = { exportLauncher.launch(suggestedBackupFileName()) },
        onExportErrorDismissed = viewModel::onExportErrorDismissed,
        onExportSuccessDismissed = viewModel::onExportSuccessDismissed,
        onImportClick = { importLauncher.launch(arrayOf("application/json")) },
        onImportConfirmed = viewModel::onImportConfirmed,
        onImportCancelled = viewModel::onImportCancelled,
        onImportErrorDismissed = viewModel::onImportErrorDismissed,
        onImportSuccessDismissed = viewModel::onImportSuccessDismissed,
        onUnitChanged = viewModel::onUnitChanged,
        onRestDefaultStepped = viewModel::onRestDefaultStepped,
        onKeepScreenOnToggled = viewModel::onKeepScreenOnToggled,
        onHealthIntegrationToggled = viewModel::onHealthIntegrationToggled,
        onForgetMetricsConfirmed = viewModel::onForgetMetricsConfirmed,
        onForgetMetricsDeclined = viewModel::onForgetMetricsDeclined,
        onHealthPermissionRationaleContinue = { permission ->
            requestedHealthPermission = permission
            healthPermissionLauncher.launch(setOf(permission.id))
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    heartRateBandState: HeartRateBandUiState = HeartRateBandUiState(),
    onExportClick: () -> Unit = {},
    onExportErrorDismissed: () -> Unit = {},
    onExportSuccessDismissed: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onImportConfirmed: () -> Unit = {},
    onImportCancelled: () -> Unit = {},
    onImportErrorDismissed: () -> Unit = {},
    onImportSuccessDismissed: () -> Unit = {},
    onUnitChanged: (WeightUnit) -> Unit = {},
    onRestDefaultStepped: (Int) -> Unit = {},
    onKeepScreenOnToggled: (Boolean) -> Unit = {},
    onHealthIntegrationToggled: (Boolean) -> Unit = {},
    onHealthPermissionRationaleContinue: (HealthPermission) -> Unit = {},
    onForgetMetricsConfirmed: () -> Unit = {},
    onForgetMetricsDeclined: () -> Unit = {},
    onHeartRateBandToggled: (Boolean) -> Unit = {},
    onHeartRateBandPermissionRationaleContinue: (HeartRateBandPermission) -> Unit = {},
    onHeartRateBandDeviceChosen: (String) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DrillDownTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(GymDimens.ScreenPadding)
                    .fillMaxSize()
                    // The only screen in the app whose content could otherwise outgrow its
                    // viewport with no way to reach the rest — every other long screen either
                    // weights a LazyColumn or scrolls. At 200% font scale, or on a short
                    // emulator profile, "Import data" sat below the fold with nothing to pull
                    // it into view. testing-strategy.md's own trap #1 (a node in the tree is
                    // not a node on screen) applies here exactly as it did to the session
                    // screen's LazyColumn.
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            UnitToggle(unit = state.unit, onUnitChanged = onUnitChanged)

            RestDefaultField(seconds = state.restDefaultSeconds, onStepped = onRestDefaultStepped)

            KeepScreenOnToggle(enabled = state.keepScreenOn, onToggled = onKeepScreenOnToggled)

            ExportSection(
                isExporting = state.isExporting,
                exportError = state.exportError,
                exportSucceeded = state.exportSucceeded,
                onExportClick = onExportClick,
                onExportErrorDismissed = onExportErrorDismissed,
                onExportSuccessDismissed = onExportSuccessDismissed,
            )

            ImportSection(
                state = state,
                onImportClick = onImportClick,
                onImportErrorDismissed = onImportErrorDismissed,
                onImportSuccessDismissed = onImportSuccessDismissed,
            )

            HealthSection(
                status = state.healthStatus,
                enabled = state.healthIntegrationEnabled,
                pendingPermission = state.pendingHealthPermission,
                onToggled = onHealthIntegrationToggled,
                onPermissionRationaleContinue = onHealthPermissionRationaleContinue,
            )

            HeartRateBandSection(
                state = heartRateBandState,
                onToggled = onHeartRateBandToggled,
                onPermissionRationaleContinue = onHeartRateBandPermissionRationaleContinue,
                onDeviceChosen = onHeartRateBandDeviceChosen,
            )
        }
    }

    SettingsDialogs(
        state = state,
        onImportConfirmed = onImportConfirmed,
        onImportCancelled = onImportCancelled,
        onForgetMetricsConfirmed = onForgetMetricsConfirmed,
        onForgetMetricsDeclined = onForgetMetricsDeclined,
    )
}

/**
 * The screen's confirm dialogs, extracted so [SettingsScreen] itself stays under detekt's
 * length threshold as US-23 adds a second one. Both are modal decisions about data the member
 * already has — replacing it (US-41) or deleting part of it (US-23) — so they share a home.
 */
@Composable
private fun SettingsDialogs(
    state: SettingsUiState,
    onImportConfirmed: () -> Unit,
    onImportCancelled: () -> Unit,
    onForgetMetricsConfirmed: () -> Unit,
    onForgetMetricsDeclined: () -> Unit,
) {
    if (state.importPreview != null) {
        ImportConfirmDialog(
            preview = state.importPreview,
            onConfirm = onImportConfirmed,
            onDismiss = onImportCancelled,
        )
    }

    if (state.forgetMetricsOffer != null) {
        ForgetMetricsDialog(
            offer = state.forgetMetricsOffer,
            onConfirm = onForgetMetricsConfirmed,
            onDismiss = onForgetMetricsDeclined,
        )
    }
}

/**
 * Split out of [SettingsScreen] to keep that function under detekt's length ceiling, the same
 * reason [SettingsDialogs] already is.
 *
 * Step-only: a rest default is small and bounded, so +/- covers the whole range US-05 needs.
 * `readOnly = true` is what keeps that a promise rather than an accident — see
 * [StepperField]'s own `readOnly` doc.
 */
@Composable
private fun RestDefaultField(
    seconds: Long,
    onStepped: (Int) -> Unit,
) {
    StepperField(
        label = "Default rest (seconds)",
        value = seconds.toString(),
        onValueChange = {},
        onStep = onStepped,
        readOnly = true,
    )
}

/**
 * US-59: the screen stays on while a workout runs, unless the member turns this off. One node,
 * operable by its label — the same `toggleable` row `HealthSection` uses, for the same reason.
 */
@Composable
private fun KeepScreenOnToggle(
    enabled: Boolean,
    onToggled: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .toggleable(value = enabled, onValueChange = onToggled, role = Role.Switch),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Keep the screen on during a workout", style = MaterialTheme.typography.titleSmall)
            Switch(checked = enabled, onCheckedChange = null)
        }
        Text(
            text =
                "While a workout is running the screen does not dim or lock, so the rest countdown " +
                    "stays readable from the bench. Off, the phone's own timeout applies.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ExportSection(
    isExporting: Boolean,
    exportError: String?,
    exportSucceeded: Boolean,
    onExportClick: () -> Unit,
    onExportErrorDismissed: () -> Unit,
    onExportSuccessDismissed: () -> Unit,
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

    // A write that silently succeeds looks, on screen, identical to one that silently did
    // nothing — this is the only signal the member gets that their file actually landed.
    if (exportSucceeded) {
        SuccessBanner(
            message = "Export complete. The file is where you chose to save it.",
            onDismiss = onExportSuccessDismissed,
        )
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
    onImportSuccessDismissed: () -> Unit,
) {
    Text(
        text = "Import replaces everything on this device with what the file holds.",
        style = MaterialTheme.typography.bodyMedium,
    )

    if (state.importError != null) {
        ErrorBanner(message = state.importError, onDismiss = onImportErrorDismissed)
    }

    // The member's entire database was just replaced — a dialog closing is not enough to say
    // so. Reports the same counts the confirm dialog already showed them.
    state.importSucceeded?.let { success ->
        SuccessBanner(
            message =
                "Imported ${success.sessionCount} ${"workout".orPlural(success.sessionCount)} and " +
                    "${success.routineCount} ${"routine".orPlural(success.routineCount)}.",
            onDismiss = onImportSuccessDismissed,
        )
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

/**
 * US-20/US-21. Renders nothing at all — no title, no row, no explanation — while [status] is
 * [HealthStatus.Unavailable]: the device or account cannot use Health Connect, or the SDK
 * needs an update, and `health-connect.md` is explicit that this must be silent, "no settings
 * row that leads nowhere." Once it renders, the toggle itself is a second, independent choice
 * (ADR-0038) — off by default, and showing regardless of whether it is currently on.
 */
@Composable
private fun HealthSection(
    status: HealthStatus,
    enabled: Boolean,
    pendingPermission: HealthPermission?,
    onToggled: (Boolean) -> Unit,
    onPermissionRationaleContinue: (HealthPermission) -> Unit,
) {
    if (status == HealthStatus.Unavailable) return

    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        // `toggleable` on the row, with the Switch itself passing `onCheckedChange = null`:
        // the label and the control become one node, so the row's accessible name is
        // "Health Connect" rather than an unnamed "off, switch", and tapping the label works.
        // Found by US-23's own instrumented test failing on device — the dialog this story adds
        // is reachable only through this control, and the control could not be operated by
        // anything but a precise tap on the switch. `HeartRateBandSection`'s row below had the
        // identical defect, left for M7's accessibility sweep at the time and closed by the
        // 2026-09-04 review instead: a 48dp label that does nothing when tapped is not worth
        // carrying for a milestone that has not started.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .toggleable(value = enabled, onValueChange = onToggled, role = Role.Switch),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Health Connect", style = MaterialTheme.typography.titleSmall)
            Switch(checked = enabled, onCheckedChange = null)
        }
        Text(
            text =
                "Reads heart rate and active calories for a workout from Health Connect, " +
                    "aggregated on this device. Off by default.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (enabled && status == HealthStatus.PermissionRequired && pendingPermission == null) {
            // The toggle is on, but nothing was ever granted — the member turned it off and
            // back on, or every permission in the walk was denied. There is no retry control
            // in this PR; re-opening the walk needs its own affordance, left for when it is
            // actually requested.
            Text(
                text = "No permissions were granted, so nothing is read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingPermission?.let { permission ->
        PermissionRationaleCard(reason = permission.reason, onContinue = { onPermissionRationaleContinue(permission) })
    }
}

/**
 * `toggleable` on the row with the Switch passing `onCheckedChange = null`, exactly as
 * [HealthSection]'s row: one node named "Live heart rate", operable by its label (US-46, closed
 * by the 2026-09-04 review — see the comment on [HealthSection]'s row for the history).
 */
@Composable
private fun LiveHeartRateToggleRow(
    enabled: Boolean,
    onToggled: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = GymDimens.MinTouchTarget)
                .toggleable(value = enabled, onValueChange = onToggled, role = Role.Switch),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Live heart rate", style = MaterialTheme.typography.titleSmall)
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/**
 * US-46, ADR-0039. Renders nothing while [availability] is [HeartRateBandAvailability.Unavailable]
 * — the same absence rule [HealthSection] follows, for the same reason: below API 31 or with no
 * Bluetooth adapter, there is nothing this section could offer.
 */
@Composable
private fun HeartRateBandSection(
    state: HeartRateBandUiState,
    onToggled: (Boolean) -> Unit,
    onPermissionRationaleContinue: (HeartRateBandPermission) -> Unit,
    onDeviceChosen: (String) -> Unit,
) {
    if (state.availability == HeartRateBandAvailability.Unavailable) return

    Column(verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        LiveHeartRateToggleRow(enabled = state.enabled, onToggled = onToggled)
        Text(
            text =
                "Reads live heart rate directly from a paired band during a workout — " +
                    "not Health Connect, and nothing here is ever saved. Off by default.",
            style = MaterialTheme.typography.bodyMedium,
        )

        state.pairedDeviceAddress?.let { address ->
            Text(
                text = "Paired: $address",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.enabled &&
            state.availability == HeartRateBandAvailability.PermissionRequired &&
            state.pendingPermission == null
        ) {
            // Same shape as HealthSection's own message: the toggle is on, but nothing was
            // ever granted. No retry affordance in this PR, for the same reason.
            Text(
                text = "No permissions were granted, so nothing is read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.scanFailed) {
            // Never silently identical to "searching and finding nothing" — that ambiguity is
            // what made a throttled scan undiagnosable on a real phone.
            Text(
                text =
                    "Couldn't start the Bluetooth scan. Turn this off and on again in a " +
                        "moment — Android limits how often an app may scan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isScanning) {
            Text(
                text =
                    "Looking for nearby devices… Your band only appears while it is " +
                        "broadcasting heart rate — start that on the band itself first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.discovered.forEach { device ->
                DiscoveredDeviceRow(device = device, onClick = { onDeviceChosen(device.address) })
            }
        }
    }

    state.pendingPermission?.let { permission ->
        PermissionRationaleCard(reason = permission.reason, onContinue = { onPermissionRationaleContinue(permission) })
    }
}

@Composable
private fun DiscoveredDeviceRow(
    device: DiscoveredHeartRateBand,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = GymDimens.MinTouchTarget)
                    .padding(GymDimens.Gap)
                    .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(device.name ?: "Unnamed device", style = MaterialTheme.typography.bodyMedium)
            Text(device.address, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * One permission's plain-language reason, shown before the system request for it
 * (`health-connect.md` §Permissions: "each with a plain-language reason on screen first") —
 * shared by [HealthSection]'s Health Connect walk and [HeartRateBandSection]'s Bluetooth walk.
 */
@Composable
private fun PermissionRationaleCard(
    reason: String,
    onContinue: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Text(reason, style = MaterialTheme.typography.bodyMedium)
            PrimaryActionButton(text = "Continue", onClick = onContinue)
        }
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
    DismissibleBanner(
        message = message,
        onDismiss = onDismiss,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

/**
 * ADR-0019 reserves red for the accent and for errors, so a success banner does not reach for
 * green — there is none in this palette. `surfaceVariant` matches the informational-banner
 * convention every other screen already uses (`RoutinesScreen`'s already-running notice,
 * `RestPanel`'s warm-up strip, `HistoryScreen`'s and `SessionUndoBars`' undo bars) rather than
 * inventing a fourth treatment for the same kind of message.
 */
@Composable
private fun SuccessBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    DismissibleBanner(
        message = message,
        onDismiss = onDismiss,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DismissibleBanner(
    message: String,
    onDismiss: () -> Unit,
    color: Color,
    contentColor: Color,
) {
    Surface(
        color = color,
        contentColor = contentColor,
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
            // Found on device while verifying US-42's own defects: "Replace 1 workouts" —
            // predates this change (US-41), but it sits in the exact same file and the exact
            // same "count a noun" shape as the two banners above, so it's fixed alongside them
            // rather than left as a known bug in code already being touched.
            Text(
                "Replace ${preview.currentSessionCount} ${"workout".orPlural(preview.currentSessionCount)} " +
                    "and ${preview.currentRoutineCount} ${"routine".orPlural(preview.currentRoutineCount)} " +
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

/**
 * US-23: offered when the member turns Health Connect off with metrics already imported, and
 * never when there are none — an offer to delete nothing is the nag `health-connect.md` forbids
 * (ADR-0040). Reads has already stopped by the time this appears; answering it either way does
 * not change that.
 */
@Composable
private fun ForgetMetricsDialog(
    offer: ForgetMetricsOfferUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete imported health data?") },
        text = {
            Text(
                "Health Connect is off, so nothing new will be read. Delete the heart rate and " +
                    "calories already imported into ${offer.sessionCount} " +
                    "${"workout".orPlural(offer.sessionCount)}? Your workouts, sets and routines " +
                    "are not touched.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Keep")
            }
        },
    )
}

/** "gym-tracker-2026-08-15.json" — a default name, not a constraint; the picker lets it be edited. */
private fun suggestedBackupFileName(clock: Clock = Clock.systemDefaultZone()): String {
    val date = LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE)
    return "gym-tracker-$date.json"
}

// Same shape as GuidedExerciseScreen's own file-private orPlural — not shared, since Kotlin
// gives no visibility narrower than internal for a top-level function across modules, and this
// one exists to fix "Imported 1 workouts", not to open a cross-module pluralization utility.
private fun String.orPlural(count: Int): String = if (count == 1) this else "${this}s"
