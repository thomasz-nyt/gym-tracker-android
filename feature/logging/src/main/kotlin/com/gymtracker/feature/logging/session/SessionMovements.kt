package com.gymtracker.feature.logging.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.SessionExerciseRow

/**
 * The exercises in the session, each with its sets and its way to add another (US-03).
 *
 * One card per exercise, each ending in a full-width "Add set" (ADR-0016). It used to be a
 * small text button on the row's right edge — the most-tapped control in the app rendered as
 * the smallest thing on screen. "Start exercise" (US-05a) and "Remove" (US-02c) sit above it as
 * a lighter-weight row: "Add set" stays the one filled action per card.
 */
@Composable
internal fun SessionExercises(
    exercises: List<SessionExerciseRow>,
    unit: WeightUnit,
    onAddSet: (SessionExerciseRow) -> Unit,
    onRemoveExercise: (SessionExerciseId) -> Unit,
    onStartExercise: (SessionExerciseRow) -> Unit,
    onEditSet: (SessionExerciseRow, ExerciseSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GymDimens.Gap)) {
        itemsIndexed(exercises, key = { _, row -> row.sessionExercise.id.value }) { index, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(GymDimens.Gap),
                    verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // The catalog entry is only absent if the row outlived its exercise,
                        // which the schema forbids; show the id rather than a blank line.
                        Text(
                            text = row.exercise?.name ?: row.sessionExercise.exerciseId.value,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // The place added, not the place shown (US-02b): the list itself is
                        // newest-first, but this number counts up in the order you added them,
                        // so removing one leaves a gap on purpose rather than renumbering.
                        Text(
                            text = "${exercises.size - index}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LoggedSets(row.sets, unit) { set -> onEditSet(row, set) }
                    Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
                        TextButton(
                            onClick = { onStartExercise(row) },
                            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                        ) {
                            Text("Start exercise")
                        }
                        // ADR-0019 replaced ADR-0016's "red means destructive" with a structural
                        // rule, because red is the accent now: a destructive control never
                        // shares a surface with a save, and is outlined rather than filled. This
                        // button predates that rule and still sits beside "Add set" on the same
                        // card — a known exception ADR-0019 flags to revisit, not a pattern to
                        // copy (US-02c).
                        TextButton(
                            onClick = { onRemoveExercise(row.sessionExercise.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                        ) {
                            Text("Remove")
                        }
                    }
                    PrimaryActionButton(text = "Add set", onClick = { onAddSet(row) })
                }
            }
        }
    }
}

/**
 * The sets already logged against one exercise, each in both units (ADR-0008), and each its own
 * tap target (ADR-0022).
 *
 * These used to be collapsed — three identical sets read as "3 × 12" on one line (ADR-0009).
 * That was fine to read and impossible to correct: one line, three rows, three ids, and no way
 * for a tap to say which. US-04 needs every set reachable, so the grouping went and the set
 * index stays as the label, naming the row it edits.
 */
@Composable
private fun LoggedSets(
    sets: List<ExerciseSet>,
    unit: WeightUnit,
    onEditSet: (ExerciseSet) -> Unit,
) {
    if (sets.isEmpty()) {
        Text("No sets yet", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column {
        sets.forEach { set ->
            val weight = WeightFormatter.format(set.weightKg, unit)
            Text(
                text =
                    buildString {
                        append("${set.setIndex}.  ${set.reps} reps")
                        append("   ${weight.primary}")
                        weight.secondary?.let { append("  ·  $it") }
                        set.rpe?.let { append("   RPE $it") }
                    },
                // The line you came back to the phone to read, so it takes the role that says
                // so rather than the smallest one there is (ADR-0011).
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = GymDimens.MinTouchTarget)
                        .clickable { onEditSet(set) }
                        // Named rather than left to the row's text, so the target says what it
                        // does — for TalkBack (M7) as much as for the tests.
                        .semantics { contentDescription = "Edit set ${set.setIndex}" }
                        .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}
