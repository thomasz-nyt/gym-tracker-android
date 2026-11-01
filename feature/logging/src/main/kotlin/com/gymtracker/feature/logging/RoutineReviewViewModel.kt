package com.gymtracker.feature.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.routine.RoutineChangeReview
import com.gymtracker.core.domain.routine.RoutineReuse
import com.gymtracker.core.domain.routine.RoutineReuseChoice
import com.gymtracker.core.domain.routine.RoutineReuseResult
import com.gymtracker.core.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The optional decision shown after a structurally changed workout has been saved. */
data class RoutineReviewUiState(
    val review: RoutineChangeReview? = null,
    val names: Map<ExerciseId, String> = emptyMap(),
    val naming: Boolean = false,
    val newName: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val unit: WeightUnit = WeightUnit.LB,
)

/** Keeps template reuse and its failures separate from the already saved workout. */
@HiltViewModel
class RoutineReviewViewModel
    @Inject
    constructor(
        private val reuse: RoutineReuse,
        private val member: CurrentMember,
        private val catalog: ExerciseCatalog,
        private val units: UnitPreference,
    ) : ViewModel() {
        private val state = MutableStateFlow(RoutineReviewUiState())
        val uiState: StateFlow<RoutineReviewUiState> = state.asStateFlow()
        private var opened: SessionId? = null

        fun open(id: SessionId) {
            if (opened == id) return
            opened = id
            refresh(id)
        }

        // A read failure here must leave the saved workout visible and offer a retry.
        @Suppress("TooGenericExceptionCaught")
        private fun refresh(id: SessionId) {
            viewModelScope.launch {
                try {
                    val owner = member.id()
                    val review = reuse.preview(id, owner)
                    val names = catalog.observeRanked(owner).first().associate { it.id to it.name }
                    state.value =
                        RoutineReviewUiState(
                            review,
                            names,
                            newName = "${review?.session?.routine?.name.orEmpty()} copy",
                            unit = units.current(),
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    state.value =
                        RoutineReviewUiState(
                            error = "Could not load routine changes. Your workout is saved. Try again.",
                        )
                }
            }
        }

        fun retry() {
            opened?.let(::refresh)
        }

        fun dismissUnavailable() {
            state.value = RoutineReviewUiState()
        }

        fun beginNew() {
            state.value = state.value.copy(naming = true, error = null)
        }

        fun cancelNaming() {
            state.value = state.value.copy(naming = false, error = null)
        }

        fun changeName(value: String) {
            state.value = state.value.copy(newName = value, error = null)
        }

        fun choose(choice: RoutineReuseChoice) {
            val current = state.value
            val review = current.review ?: return
            if (current.isSaving) return
            state.value = current.copy(isSaving = true, error = null)
            viewModelScope.launch {
                if (member.id() != review.session.userId) {
                    state.value = RoutineReviewUiState()
                } else {
                    when (val result = reuse.apply(review, choice, current.newName)) {
                        is RoutineReuseResult.Saved, RoutineReuseResult.TodayOnly ->
                            state.value =
                                RoutineReviewUiState()
                        RoutineReuseResult.Conflict -> {
                            val latest = reuse.preview(review.session.id, review.session.userId)
                            state.value =
                                current.copy(
                                    review = latest,
                                    isSaving = false,
                                    error =
                                        "The original or workout changed. " +
                                            "Review again, save as new, or keep today only.",
                                )
                        }
                        is RoutineReuseResult.Failed ->
                            state.value =
                                current.copy(isSaving = false, error = result.message)
                    }
                }
            }
        }
    }
