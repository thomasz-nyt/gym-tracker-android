package com.gymtracker.core.data.exercise

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** [ExerciseCatalog] over Room. */
class RoomExerciseCatalog
    @Inject
    constructor(
        private val dao: ExerciseDao,
        private val json: Json,
    ) : ExerciseCatalog {
        override fun observeRanked(forMember: UserId): Flow<List<Exercise>> =
            dao.observeRanked(forMember.value).map { rows ->
                rows.map { row -> row.toDomain(::decode) }
            }

        override suspend fun knownExerciseIds(): Set<ExerciseId> = dao.allIds().map { ExerciseId(it) }.toSet()

        private fun decode(raw: String): List<String> = json.decodeFromString(raw)
    }
