package com.gymtracker.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.core.domain.exercise.CatalogFilter
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Everything the browse screen renders (US-12). */
data class CatalogUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: CatalogFilter = CatalogFilter(),
    val results: List<Exercise> = emptyList(),
) {
    /** Whether anything is narrowing the catalog, so the screen can offer to clear it. */
    val isNarrowed: Boolean get() = query.isNotBlank() || !filter.isEmpty
}

/**
 * Browsing and filtering the catalog (US-12).
 *
 * Its own ViewModel rather than more surface on the logging one: browsing is reachable
 * without a workout in progress, and nothing here needs a session.
 */
@HiltViewModel
class CatalogViewModel
    @Inject
    constructor(
        private val catalog: ExerciseCatalog,
        currentMember: CurrentMember,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val filter = MutableStateFlow(CatalogFilter())

        private val member = flow { emit(currentMember.id()) }

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        private val results: Flow<List<Exercise>> =
            combine(
                // No delay on an empty query, so opening browse shows the catalog at once.
                // The debounce only stops re-filtering on every keystroke.
                query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS },
                filter,
                member,
            ) { text, chips, memberId -> Triple(text, chips, memberId) }
                .flatMapLatest { (text, chips, memberId) -> catalog.browse(text, chips, memberId) }

        val uiState: StateFlow<CatalogUiState> =
            combine(query, filter, results) { text, chips, found ->
                CatalogUiState(isLoading = false, query = text, filter = chips, results = found)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CatalogUiState())

        /** One exercise, for the detail screen (US-13). Null while the catalog is loading. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun exercise(id: ExerciseId): Flow<Exercise?> =
            member
                .flatMapLatest(catalog::observeRanked)
                .map { all -> all.firstOrNull { it.id == id } }

        fun onQueryChanged(text: String) {
            query.value = text
        }

        /** Chips toggle: tapping a lit one turns it off, which is how a member clears one. */
        fun onBodyPartToggled(bodyPart: BodyPart) {
            filter.update { it.copy(bodyParts = it.bodyParts.toggle(bodyPart)) }
        }

        fun onEquipmentToggled(equipment: Equipment) {
            filter.update { it.copy(equipment = it.equipment.toggle(equipment)) }
        }

        /** Back to the whole catalog — US-12's "clearing them returns the full catalog". */
        fun onCleared() {
            query.value = ""
            filter.value = CatalogFilter()
        }

        private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

        private fun MutableStateFlow<CatalogFilter>.update(block: (CatalogFilter) -> CatalogFilter) {
            value = block(value)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** Matches the in-session search: long enough to stop thrashing, short enough to feel instant. */
            const val SEARCH_DEBOUNCE_MILLIS = 150L
        }
    }
