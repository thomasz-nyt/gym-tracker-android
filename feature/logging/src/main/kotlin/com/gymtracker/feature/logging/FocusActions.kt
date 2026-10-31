package com.gymtracker.feature.logging

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.MovementOutcome
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.rest.UpNextSet

/** The same workout records serve Focus, the plan, and correction; none owns a second plan. */
data class FocusActions(
    val onLogSet: (UpNextSet) -> Unit = {},
    val onAdjust: (SessionExerciseRow) -> Unit = {},
    val onSelect: (SessionExerciseId) -> Unit = {},
    val onEdit: (SessionExerciseRow, ExerciseSet) -> Unit = { _, _ -> },
    val onOutcome: (SessionExerciseRow, MovementOutcome?) -> Unit = { _, _ -> },
    val onRemove: (SessionExerciseId) -> Unit = {},
    val onMove: (SessionExerciseId, Int) -> Unit = { _, _ -> },
    val onAddExercise: () -> Unit = {},
    val onSkipRest: () -> Unit = {},
    val onExtendRest: () -> Unit = {},
    val onUndoLog: () -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onUndoRemoval: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)
