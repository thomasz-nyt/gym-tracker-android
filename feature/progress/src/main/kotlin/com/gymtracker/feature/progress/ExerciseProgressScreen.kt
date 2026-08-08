package com.gymtracker.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.core.designsystem.component.DrillDownTopBar
import com.gymtracker.core.designsystem.theme.GymDimens
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.progress.ExerciseTrend
import com.gymtracker.core.domain.progress.ExerciseTrendPoint
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightFormatter
import com.gymtracker.core.domain.units.WeightUnit
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One exercise's progress over time (US-16), and the honest absence when there is not enough
 * of it (US-19).
 *
 * A destination of its own rather than a panel on the exercise detail screen. That keeps
 * `:feature:catalog` from depending on `:feature:progress` — detail emits a callback and the
 * nav graph decides — which is the module boundary `tech-stack.md` draws, where
 * `:feature:progress` owns "charts and PRs".
 */
@Composable
fun ExerciseProgressRoute(
    exerciseId: ExerciseId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseProgressViewModel = hiltViewModel(),
) {
    LaunchedEffect(exerciseId) { viewModel.open(exerciseId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseProgressScreen(
        state = state,
        onBack = onBack,
        onSeriesChanged = viewModel::onSeriesChanged,
        modifier = modifier,
    )
}

@Composable
internal fun ExerciseProgressScreen(
    state: ExerciseProgressUiState,
    onBack: () -> Unit = {},
    onSeriesChanged: (TrendSeries) -> Unit = {},
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
            Text(state.exerciseName, style = MaterialTheme.typography.titleLarge)

            SeriesChips(selected = state.series, onSeriesChanged = onSeriesChanged)

            when (val trend = state.trend) {
                ExerciseTrend.NoData -> NotEnoughYet("No sets logged for this movement yet.")
                is ExerciseTrend.SinglePoint ->
                    // US-19: one point is not a trend, and this screen does not draw one. It
                    // reports the single session instead, which is all that is known.
                    NotEnoughYet(
                        "Done once, on ${trend.point.performedOn.readable()}. " +
                            "One session is not a trend — log it again and this becomes a chart.",
                        detail = trend.point.summary(state.series, state.unit),
                    )
                is ExerciseTrend.Series -> TrendChart(trend, state.series, state.unit)
            }
        }
    }
}

/** US-16: "top-set weight and total volume as switchable series". */
@Composable
private fun SeriesChips(
    selected: TrendSeries,
    onSeriesChanged: (TrendSeries) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(GymDimens.TightGap)) {
        TrendSeries.entries.forEach { series ->
            FilterChip(
                selected = series == selected,
                onClick = { onSeriesChanged(series) },
                label = { Text(series.label) },
                modifier = Modifier.sizeIn(minHeight = GymDimens.MinTouchTarget),
            )
        }
    }
}

/**
 * The chart itself.
 *
 * Days the series has no value for — a bodyweight session has no top set and no volume — are
 * **left out** rather than plotted as zero, which would draw a dip to the floor on a day that
 * was trained (constitution §2.4).
 *
 * There is no bottom axis. The x positions are session indices, and labelling them with raw
 * numbers would be worse than not labelling them; the range is given underneath in dates
 * instead. Date labels on the axis are a follow-up, not a silent omission.
 */
@Composable
private fun TrendChart(
    trend: ExerciseTrend.Series,
    series: TrendSeries,
    unit: WeightUnit,
) {
    val plotted = trend.points.mapNotNull { point -> series.valueOf(point)?.let { point to it } }

    if (plotted.size < 2) {
        NotEnoughYet("Not enough ${series.label.lowercase(Locale.getDefault())} data to draw a trend yet.")
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(plotted, series, unit) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = plotted.indices.toList(),
                    y = plotted.map { (_, value) -> series.inMemberUnit(value, unit) },
                )
            }
        }
    }

    // ADR-0019: one accent on an achromatic ground. Vico's default line is blue, which is a
    // second hue this app does not have — the chart takes the same red every other emphasis
    // does, read from the theme rather than hard-coded.
    val accent = MaterialTheme.colorScheme.primary

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider =
                        LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(Fill(accent)),
                            ),
                        ),
                ),
                startAxis = VerticalAxis.rememberStart(),
            ),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
    )

    Text(
        text = "${plotted.first().first.performedOn.readable()}  →  ${plotted.last().first.performedOn.readable()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (series.isEstimate) {
        // US-16: "estimated 1RM uses Epley and is labelled as an estimate." The one number on
        // this screen nobody lifted says so, every time it is shown.
        Text(
            text = "Estimated from your heaviest set each session (Epley). Not a lift you performed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** US-19's "not enough data yet", said plainly rather than drawn as an empty grid. */
@Composable
private fun NotEnoughYet(
    message: String,
    detail: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.CenterVertically),
        verticalArrangement = Arrangement.spacedBy(GymDimens.TightGap),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Volume is already a total; the weight series convert, so the axis reads in the member's unit. */
private fun TrendSeries.inMemberUnit(
    valueKg: Double,
    unit: WeightUnit,
): Double = UnitConverter.fromKilograms(valueKg, unit)

/** What the one recorded session was, for the single-point state. */
private fun ExerciseTrendPoint.summary(
    series: TrendSeries,
    unit: WeightUnit,
): String =
    series.valueOf(this)?.let { WeightFormatter.format(it, unit).primary }
        ?: "$sets ${if (sets == 1) "set" else "sets"}, no load recorded"

private fun LocalDate.readable(): String = DAY_FORMAT.format(this)

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private val CHART_HEIGHT = 240.dp
