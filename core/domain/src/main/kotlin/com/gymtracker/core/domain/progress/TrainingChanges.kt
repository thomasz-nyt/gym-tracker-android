package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/** Invalidates derived reads on the underlying records, even when an aggregate stays equal. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun trainingChanges(
    member: UserId,
    sessions: SessionRepository,
    movements: SessionExerciseRepository,
    sets: SetRepository,
): Flow<Unit> =
    sessions.observeFinishedSessions(member).flatMapLatest { finished ->
        val ids = finished.map { it.id }
        combine(movements.observeForSessions(ids), sets.observeForSessions(ids)) { _, _ -> Unit }
    }
