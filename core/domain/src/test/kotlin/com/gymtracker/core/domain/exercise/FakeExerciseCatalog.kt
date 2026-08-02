package com.gymtracker.core.domain.exercise

import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fake, per `specs/testing-strategy.md`.
 *
 * Matching is a name substring, as `RoomExerciseCatalog`'s `LIKE` is. Ranking by recent use is
 * the DAO's job and deliberately not reproduced here — a test that cares about ordering wants
 * the real query, not this.
 */
class FakeExerciseCatalog(
    initial: List<Exercise> = emptyList(),
) : ExerciseCatalog {
    private val state = MutableStateFlow(initial)

    override fun search(
        query: String,
        forMember: UserId,
    ): Flow<List<Exercise>> =
        state.map { all ->
            if (query.isEmpty()) all else all.filter { it.name.contains(query, ignoreCase = true) }
        }
}
