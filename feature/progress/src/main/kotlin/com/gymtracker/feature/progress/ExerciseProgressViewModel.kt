package com.gymtracker.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.progress.ExerciseLogEntry
import com.gymtracker.core.domain.progress.ExerciseLogOf
import com.gymtracker.core.domain.progress.ExerciseTrend
import com.gymtracker.core.domain.progress.ExerciseTrendOf
import com.gymtracker.core.domain.progress.ExerciseTrendPoint
import com.gymtracker.core.domain.progress.PersonalRecord
import com.gymtracker.core.domain.progress.PersonalRecordsOf
import com.gymtracker.core.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

/**
 * Which number the chart is plotting (US-16: "top-set weight and total volume as switchable
 * series").
 *
 * [ESTIMATED_ONE_REP_MAX] is the default because it answers "am I getting stronger", where
 * volume answers "did I do more today". It is also the only one of the three that is not a
 * measurement, which is why its label says so wherever it appears.
 */
enum class TrendSeries(
    val label: String,
    /** True for the series that is computed rather than lifted (US-16, constitution §2.4). */
    val isEstimate: Boolean = false,
) {
    ESTIMATED_ONE_REP_MAX("Est. 1RM", isEstimate = true),
    TOP_SET("Top set"),
    VOLUME("Volume"),
    ;

    /**
     * [label] as it reads mid-sentence, for "not enough _top set_ data yet".
     *
     * Lowercased with [Locale.ROOT], deliberately, and **not** with the member's locale. These
     * are fixed English strings the app ships, not localized content, so folding their case by
     * the device's locale is the classic Turkish-i bug waiting to happen. It is also what
     * Android Lint's `NonObservableLocale` was flagging when it read `Locale.getDefault()` here:
     * a composable that reads the default locale does not observe it, so it would not recompose
     * if the member changed language.
     */
    val inSentence: String get() = label.lowercase(Locale.ROOT)

    /** The value this series reads off a point, or null when the day carried no load. */
    fun valueOf(point: ExerciseTrendPoint): Double? =
        when (this) {
            ESTIMATED_ONE_REP_MAX -> point.estimatedOneRepMaxKg
            TOP_SET -> point.topSetKg
            VOLUME -> point.volumeKg
        }
}

/** Everything the per-exercise progress screen renders. */
data class ExerciseProgressUiState(
    val isLoading: Boolean = true,
    val exerciseName: String = "",
    val trend: ExerciseTrend = ExerciseTrend.NoData,
    val series: TrendSeries = TrendSeries.ESTIMATED_ONE_REP_MAX,
    val unit: WeightUnit = WeightUnit.LB,
    /** US-34: what was actually done, newest first. Empty when [trend] is [ExerciseTrend.NoData]. */
    val log: List<ExerciseLogEntry> = emptyList(),
    /** US-18: the standing PR list, one per rep count ever reached, ascending. */
    val records: List<PersonalRecord> = emptyList(),
)

/**
 * One exercise's progress over time (US-16, US-19).
 *
 * The arithmetic belongs to [ExerciseTrendOf] in `:core:domain`; this only chooses which
 * exercise and hands the result over. Note what it does **not** do: it never converts
 * [ExerciseTrend.NoData] or [ExerciseTrend.SinglePoint] into an empty list of points. US-19's
 * "not enough data yet" survives all the way to the screen because the type it travels in has
 * no list to flatten.
 */
@HiltViewModel
class ExerciseProgressViewModel
    @Inject
    constructor(
        private val exerciseTrendOf: ExerciseTrendOf,
        private val exerciseLogOf: ExerciseLogOf,
        private val personalRecordsOf: PersonalRecordsOf,
        private val catalog: ExerciseCatalog,
        private val currentMember: CurrentMember,
        unitPreference: UnitPreference,
    ) : ViewModel() {
        private val charting = MutableStateFlow<ExerciseId?>(null)
        private val chosenSeries = MutableStateFlow(TrendSeries.ESTIMATED_ONE_REP_MAX)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val loaded: Flow<Loaded> =
            charting.filterNotNull().flatMapLatest { exerciseId ->
                flow {
                    val member = currentMember.id()
                    val name =
                        catalog
                            .observeRanked(member)
                            .first()
                            .firstOrNull { it.id == exerciseId }
                            ?.name
                    emit(
                        Loaded(
                            name = name ?: exerciseId.value,
                            trend = exerciseTrendOf(exerciseId, member),
                            log = exerciseLogOf(exerciseId, member),
                            records = personalRecordsOf(exerciseId, member),
                        ),
                    )
                }
            }

        val uiState: StateFlow<ExerciseProgressUiState> =
            combine(loaded, chosenSeries, unitPreference.observe()) { loaded, series, unit ->
                ExerciseProgressUiState(
                    isLoading = false,
                    exerciseName = loaded.name,
                    trend = loaded.trend,
                    series = series,
                    unit = unit,
                    log = loaded.log,
                    records = loaded.records,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExerciseProgressUiState())

        /** Points the screen at an exercise. Idempotent, so recomposition does not re-read. */
        fun open(exerciseId: ExerciseId) {
            if (charting.value != exerciseId) charting.value = exerciseId
        }

        /**
         * Switches the plotted series.
         *
         * The trend itself is not re-read: all three numbers are already on every point, so
         * changing series is a redraw rather than a query.
         */
        fun onSeriesChanged(series: TrendSeries) {
            chosenSeries.value = series
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** What one exercise-id load fetches together, before [TrendSeries] or unit are folded in. */
private data class Loaded(
    val name: String,
    val trend: ExerciseTrend,
    val log: List<ExerciseLogEntry>,
    val records: List<PersonalRecord>,
)
