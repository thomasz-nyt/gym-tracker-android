package com.gymtracker.feature.logging.session

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.core.domain.session.StaleSessionPolicy
import com.gymtracker.core.domain.session.StaleSessionPrompt
import com.gymtracker.feature.logging.GuidedActions
import com.gymtracker.feature.logging.GuidedSetupDialog
import com.gymtracker.feature.logging.SessionUiState
import com.gymtracker.feature.logging.SetEditCallbacks
import com.gymtracker.feature.logging.SetEditSheet
import com.gymtracker.feature.logging.SetEntryCallbacks
import com.gymtracker.feature.logging.SetEntrySheet

/** The things that can sit over the session screen: the stale prompt, guided setup, and set entry. */
@Composable
internal fun SessionDialogs(
    state: SessionUiState,
    onResolveStale: (StaleSessionPrompt) -> Unit,
    guided: GuidedActions,
    setEntry: SetEntryCallbacks,
    setEdit: SetEditCallbacks,
) {
    state.stalePrompt?.let { prompt ->
        AbandonedSessionDialog(prompt = prompt, onResolve = onResolveStale)
    }

    state.guided.setup?.let { setup ->
        GuidedSetupDialog(
            setup = setup,
            unit = state.unit,
            onWeightChanged = guided.onWeightChanged,
            onWeightStepped = guided.onSetupWeightStepped,
            onRepsChanged = guided.onSetupRepsChanged,
            onRepsStepped = guided.onSetupRepsStepped,
            onSetsChanged = guided.onSetsChanged,
            onSetsStepped = guided.onSetupSetsStepped,
            onBegin = guided.onBegin,
            onDismiss = guided.onDismissSetup,
        )
    }

    state.setEntry?.let { entry ->
        SetEntrySheet(entry = entry, unit = state.unit, callbacks = setEntry)
    }

    state.setEdit?.let { edit ->
        SetEditSheet(edit = edit, unit = state.unit, callbacks = setEdit)
    }
}

/**
 * The guard on the one tap in the app that cannot be taken back (ADR-0016).
 *
 * Finishing is once per workout, so the extra tap costs nothing measurable; ending a workout
 * you were halfway through costs the rest of it.
 *
 * **Partially retuned by ADR-0011's Turn 4 amendment (frame `4e`).** The title reads through
 * `title.lg` now. The frame's own mockup also shows session progress ("24 min · 5 of 5"), an
 * up-next preview and a stats `FlowRow` inside this dialog, and its two buttons stacking full
 * width below 340dp — none of that is done here. The first three would be new content this
 * dialog has no parameters to carry (`onConfirm`/`onDismiss` alone), which is new behaviour, out
 * of scope for a type-and-layout pass. The stacking would need replacing `AlertDialog` itself —
 * Material3's version places `confirmButton`/`dismissButton` in its own internal row with no
 * exposed way to make that row become a column below a width, so doing it faithfully means
 * building a custom `Dialog`, a bigger structural change than this pass's scope. Flagged rather
 * than half-built.
 */
@Composable
internal fun FinishWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { GymText(text = "Finish this workout?", role = GymTextRoles.TitleLg) },
        text = { Text("It moves to your past workouts. Sets you have already logged are kept.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Finish workout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget)) {
                Text("Keep going")
            }
        },
    )
}

@Composable
private fun AbandonedSessionDialog(
    prompt: StaleSessionPrompt,
    onResolve: (StaleSessionPrompt) -> Unit,
) {
    // Not dismissible: US-01 offers finish or discard, and nothing else. Leaving the session
    // open is not one of the choices, so there is no way to tap it away.
    AlertDialog(
        onDismissRequest = {},
        title = { Text("You left a workout running") },
        text = { Text(prompt.explanation()) },
        confirmButton = {
            TextButton(onClick = { onResolve(prompt) }) {
                Text(if (prompt is StaleSessionPrompt.Finish) "Finish it" else "Discard it")
            }
        },
    )
}

private fun StaleSessionPrompt.explanation(): String =
    when (this) {
        is StaleSessionPrompt.Finish ->
            "It has been idle for more than ${StaleSessionPromptCopy.THRESHOLD_HOURS} hours. " +
                "We will end it at your last set, ${endedAt.asLocalTime()} — never at a time you were not lifting."

        is StaleSessionPrompt.Discard ->
            "It has been idle for more than ${StaleSessionPromptCopy.THRESHOLD_HOURS} hours and has no sets, " +
                "so there is nothing to keep and no honest end time to record."
    }

private object StaleSessionPromptCopy {
    /** Derived from the policy, so the copy can never drift from the rule it describes. */
    val THRESHOLD_HOURS: Long = StaleSessionPolicy.STALE_AFTER.toHours()
}
