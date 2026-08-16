package com.gymtracker.core.domain.exercise

import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written fake, per `specs/testing-strategy.md`.
 *
 * [observeRanked] and [knownExerciseIds] are the only abstract members — `browse` and `search`
 * are defaults over [observeRanked], so a fake that overrode them would be testing itself
 * rather than the narrowing in [CatalogQuery].
 *
 * The order is whatever the test supplied. Real ranking joins against the member's sessions
 * and belongs to the DAO; a test that cares about it wants the Room test, not this.
 */
class FakeExerciseCatalog(
    initial: List<Exercise> = emptyList(),
) : ExerciseCatalog {
    private val state = MutableStateFlow(initial)

    override fun observeRanked(forMember: UserId): Flow<List<Exercise>> = state

    override suspend fun knownExerciseIds(): Set<ExerciseId> = state.value.map { it.id }.toSet()
}
