package com.gymtracker.feature.logging

import com.gymtracker.core.domain.model.SessionExerciseId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which exercise the member explicitly chose to have open, if any (US-45, ADR-0037).
 *
 * Its own state holder, following [RestController], [SetEntryController] and
 * [ExerciseRemovalController]. [ActiveSessionViewModel] combines [current] into its derivation
 * of the session's open row, where it wins over the "highest-position exercise with a logged
 * set" default whenever it still names a row in the session — a selection pointing at a
 * since-removed exercise (US-02c) is left for that combine's own nullable-fallback shape to
 * catch, not cleaned up here.
 *
 * Sticky by construction: nothing but [select] ever changes [current], so logging a set on a
 * *different* exercise never silently moves the open row out from under a member deliberately
 * working on this one — the exact bug this story fixes (the machine was taken, a set landed on
 * a later exercise first, and the earlier one had no way back).
 */
class ExerciseSelectionController {
    private val selected = MutableStateFlow<SessionExerciseId?>(null)

    val current: StateFlow<SessionExerciseId?> = selected.asStateFlow()

    /** Opens [id] as the current exercise, in place of whatever the derived default is. */
    fun select(id: SessionExerciseId) {
        selected.value = id
    }
}
