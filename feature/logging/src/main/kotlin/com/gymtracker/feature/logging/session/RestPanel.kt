package com.gymtracker.feature.logging.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gymtracker.core.designsystem.component.PrimaryActionButton
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.rest.UpNextSet
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.gymtracker.feature.logging.WarmUp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The warm-up: a stopwatch, and nothing else (US-28, ADR-0021).
 *
 * Idle, it is one quiet text button — the warm-up is the least of what this screen does and it
 * does not get to look like the most. Running, it counts up at the size the rest countdown uses,
 * because it is read from the same distance.
 *
 * What is deliberately absent: a weight field, a rep field, an exercise name, and any "save".
 * There is nothing to save. Stopping it discards it, which is why the control says "Done"
 * rather than anything that sounds like it writes a row.
 */
@Composable
internal fun WarmUpPanel(warmUp: WarmUp) {
    val elapsed = warmUp.elapsed

    if (elapsed == null) {
        TextButton(
            onClick = warmUp.onStart,
            modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
        ) {
            Text("Start warm-up")
        }
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Warm-up", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = elapsed.asCountdown(),
                    style = MaterialTheme.typography.displayMedium,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Warm-up ${elapsed.asCountdown()} elapsed, not recorded"
                        },
                )
            }
            TextButton(
                onClick = warmUp.onStop,
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            ) {
                Text("Done")
            }
        }
    }
}

/**
 * The rest countdown, at the size you can read from where you are actually standing (ADR-0016).
 *
 * It was an assist chip, which made the most-glanced thing on the screen the smallest. Skip is
 * beside it because it is the only decision the timer offers.
 */
@Composable
internal fun RestBanner(
    remaining: Duration,
    upNext: UpNextSet?,
    exerciseName: String?,
    unit: WeightUnit,
    onSkipRest: () -> Unit,
    onLogNext: () -> Unit,
    onAdjust: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GymDimens.Gap),
            verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Rest", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = remaining.asCountdown(),
                        style = MaterialTheme.typography.displayMedium,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Rest ${remaining.asCountdown()} remaining"
                            },
                    )
                }
                TextButton(
                    onClick = onSkipRest,
                    modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                ) {
                    Text("Skip")
                }
            }

            // ADR-0023: the ninety seconds says what is coming, and lets it be logged from here.
            // Absent before the first set of the session — there is nothing to be next yet.
            if (upNext != null) {
                UpNext(upNext = upNext, exerciseName = exerciseName, unit = unit)
                Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
                    PrimaryActionButton(
                        text = "Log set ${upNext.setNumber}",
                        onClick = onLogNext,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onAdjust,
                        modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
                    ) {
                        Text("Adjust")
                    }
                }
            }
        }
    }
}

/**
 * What the next set will be, and what the same movement was last time (ADR-0023).
 *
 * Note what is **not** here: no "of N", because nothing in the app knows how many sets you
 * intend — [UpNextSet] has no field it could be rendered from. And no comparison at all when
 * the movement has no earlier session, rather than a zero or a dash pretending to be one
 * (constitution §2.4).
 */
@Composable
private fun UpNext(
    upNext: UpNextSet,
    exerciseName: String?,
    unit: WeightUnit,
) {
    val next = WeightFormatter.format(upNext.prefill.weight?.let { UnitConverter.toKilograms(it, unit) }, unit)
    Column {
        Text("Up next", style = MaterialTheme.typography.titleSmall)
        Text(
            text = exerciseName ?: upNext.exerciseId.value,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
                buildString {
                    append("Set ${upNext.setNumber}")
                    append("   ${upNext.prefill.reps} reps")
                    append("   ${next.primary}")
                    next.secondary?.let { append("  ·  $it") }
                },
            style = MaterialTheme.typography.titleMedium,
        )
        upNext.comparison?.let { last ->
            val previous = WeightFormatter.format(last.weightKg, unit)
            Text(
                text = "Last ${last.performedAt.asDay()}  ·  ${last.reps} reps   ${previous.primary}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The day a set happened, for the rest panel's comparison line. */
private fun Instant.asDay(): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(this)

/**
 * mm:ss, so 90 seconds reads "1:30" rather than "PT1M30S".
 *
 * Arithmetic on [Duration.getSeconds] rather than `toMinutesPart`/`toSecondsPart`, which are
 * API 31 and would crash on the API 26 devices `tech-stack.md` supports.
 */
private fun Duration.asCountdown(): String =
    "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60
