package com.gymtracker.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.progress.VolumeWeek
import com.gymtracker.core.domain.progress.WeeklyVolumeByBodyPart
import com.gymtracker.core.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** How many weeks the screen shows. See [WeeklyVolumeViewModel] for why this is fixed for now. */
internal const val WEEKS_SHOWN = 8

/**
 * Everything the weekly-volume screen renders (US-17, US-19).
 *
 * [weeks] carries the domain's own [VolumeWeek] rather than a flattened list of bars, for the
 * reason `ExerciseProgressUiState` keeps `ExerciseTrend`: a week the member did not train is
 * present with an empty list, and flattening here would turn "you trained nothing that week"
 * into a missing row.
 */
data class WeeklyVolumeUiState(
    val isLoading: Boolean = true,
    val weeks: List<VolumeWeek> = emptyList(),
    val unit: WeightUnit = WeightUnit.LB,
) {
    /**
     * The busiest single muscle-week in the window, which every bar is drawn against.
     *
     * One scale for the whole range, not one per week: scaling per week would draw each week's
     * top muscle full width and so claim a light week and a heavy one were equal.
     */
    val peakVolumeKg: Double = weeks.flatMap { it.byBodyPart }.maxOfOrNull { it.volumeKg } ?: 0.0

    /** False when nothing in the window carried load — US-19's state, not a floor of zeroes. */
    val hasVolume: Boolean get() = peakVolumeKg > 0.0
}

/**
 * Weekly training volume grouped by muscle (US-17), and the honest absence of it (US-19).
 *
 * The arithmetic belongs to [WeeklyVolumeByBodyPart] in `:core:domain`; this chooses the window
 * and the member, and reverses the result so the most recent week is first — the order the
 * history list already reads in, and the week you opened the screen to see.
 *
 * **The window is a fixed eight weeks.** US-17 says "for a chosen range" and the choosing is the
 * roadmap's own separate M4 line, "time range selector"; that selector belongs to both charts,
 * so it is not built here. Eight weeks is long enough to show a block of training and short
 * enough to stay readable on a phone.
 */
@HiltViewModel
class WeeklyVolumeViewModel
    @Inject
    constructor(
        private val weeklyVolume: WeeklyVolumeByBodyPart,
        private val currentMember: CurrentMember,
        unitPreference: UnitPreference,
        private val clock: Clock,
        private val zone: ZoneId,
    ) : ViewModel() {
        private val opened = MutableStateFlow(false)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val loaded: Flow<List<VolumeWeek>> =
            opened.filter { it }.flatMapLatest {
                flow {
                    // Today in the member's zone, not UTC: which week a late Sunday session
                    // falls in is the same question `WeeklyVolumeByBodyPart` documents.
                    val today = clock.instant().atZone(zone).toLocalDate()
                    emit(volumeSince(today))
                }
            }

        val uiState: StateFlow<WeeklyVolumeUiState> =
            combine(loaded, unitPreference.observe()) { weeks, unit ->
                WeeklyVolumeUiState(isLoading = false, weeks = weeks, unit = unit)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WeeklyVolumeUiState())

        /** Starts reading. Idempotent, so recomposition does not re-query. */
        fun open() {
            opened.value = true
        }

        /** Most recent week first — [WeeklyVolumeByBodyPart] returns them oldest first. */
        private suspend fun volumeSince(today: LocalDate): List<VolumeWeek> =
            weeklyVolume(
                member = currentMember.id(),
                from = today.minusWeeks(WEEKS_SHOWN - 1L),
                to = today,
            ).reversed()

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
