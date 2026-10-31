package com.gymtracker.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.GymText
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.designsystem.theme.GymTextRoles
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.routine.RoutineReuseChoice

/** An optional template decision appears only after the actual workout has been saved. */
@Composable
internal fun RoutineReviewRoute(
    id: SessionId,
    viewModel: RoutineReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.open(id) }
    if (state.review != null) {
        RoutineReviewDialog(state, viewModel)
    } else if (state.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUnavailable,
            title = { ReviewText("Workout saved") },
            text = { ReviewText(checkNotNull(state.error)) },
            confirmButton = { ReviewButton("Retry", onClick = viewModel::retry) },
            dismissButton = { ReviewButton("Keep original", onClick = viewModel::dismissUnavailable) },
        )
    }
}

@Composable
private fun RoutineReviewDialog(
    state: RoutineReviewUiState,
    actions: RoutineReviewViewModel,
) {
    val review = checkNotNull(state.review)
    AlertDialog(
        modifier = Modifier.systemBarsPadding().imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        onDismissRequest = {
            if (!state.isSaving) {
                if (state.naming) actions.cancelNaming() else actions.choose(RoutineReuseChoice.TODAY_ONLY)
            }
        },
        title = { GymText("Reuse workout changes?", role = GymTextRoles.TitleMd) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GymDimens.Gap),
            ) {
                ReviewText("Your workout is saved. Choose whether to reuse its exercise list and order.")
                if (state.naming) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = actions::changeName,
                        label = { ReviewText("New routine name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!review.canUpdateOriginal) {
                    ReviewText(
                        "The original routine changed or was deleted. " +
                            "You can save a new routine or keep these changes for today.",
                    )
                }
                GymText("Before · ${review.session.routine?.name.orEmpty()}", role = GymTextRoles.TitleMd)
                review.session.workflow.originalRoutineItems.sortedBy { it.position }.forEachIndexed { index, item ->
                    ReviewText("${index + 1}. ${state.name(item.exerciseId)}")
                }
                GymText("After", role = GymTextRoles.TitleMd)
                review.movements.forEachIndexed {
                    index,
                    item,
                    ->
                    ReviewText("${index + 1}. ${state.name(item.exerciseId)}")
                }
                ReviewText(
                    "Kept exercises retain their authored targets. " +
                        "Today's load, reps, skips and early finishes stay in this workout.",
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 20) }
            }
        },
        confirmButton = { RoutineReviewChoices(state, actions) },
    )
}

@Composable
private fun RoutineReviewChoices(
    state: RoutineReviewUiState,
    actions: RoutineReviewViewModel,
) {
    Column(Modifier.fillMaxWidth()) {
        if (state.naming) {
            ReviewButton(
                if (state.isSaving) "Saving…" else "Save new routine",
                !state.isSaving && state.newName.isNotBlank(),
            ) {
                actions.choose(RoutineReuseChoice.SAVE_AS_NEW)
            }
            ReviewButton("Back to choices", !state.isSaving, actions::cancelNaming)
        } else {
            ReviewButton("Update original", !state.isSaving && state.review?.canUpdateOriginal == true) {
                actions.choose(RoutineReuseChoice.UPDATE_ORIGINAL)
            }
            ReviewButton("Save as new", !state.isSaving, actions::beginNew)
            ReviewButton("Today only", !state.isSaving) { actions.choose(RoutineReuseChoice.TODAY_ONLY) }
        }
    }
}

@Composable
private fun ReviewButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = GymDimens.MinTouchTarget),
    ) {
        ReviewText(text)
    }
}

@Composable
private fun ReviewText(text: String) {
    Text(text, maxLines = 20)
}

private fun RoutineReviewUiState.name(id: ExerciseId) = names[id] ?: id.value
