package com.gymtracker.feature.logging

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.progress.EightWeekChange
import com.gymtracker.core.domain.progress.ExerciseTrend
import com.gymtracker.core.domain.progress.ExerciseTrendOf
import com.gymtracker.core.domain.progress.MostRecentlyTrainedExercise
import com.gymtracker.core.domain.progress.eightWeekChangeInEstimate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Loads US-33's top section: one lift, chosen without asking — see [MostRecentlyTrainedExercise].
 *
 * Its own class, not three separate constructor parameters on [HistoryController], so that
 * controller's parameter list stays about history and deleting, not about the top section too.
 */
class TopLiftLoader(
    private val mostRecentlyTrainedExercise: MostRecentlyTrainedExercise,
    private val exerciseTrendOf: ExerciseTrendOf,
    private val catalog: ExerciseCatalog,
) {
    /** Refreshes when the underlying actual records change, even if the selected lift does not. */
    fun observe(member: UserId): Flow<TopLift> = mostRecentlyTrainedExercise.observe(member).map { invoke(member) }

    suspend operator fun invoke(member: UserId): TopLift {
        val exerciseId = mostRecentlyTrainedExercise(member)
        val change = exerciseId?.let { changeInEstimate(it, member) }
        if (exerciseId == null || change == null) return TopLift.None

        val name =
            catalog
                .observeRanked(member)
                .first()
                .firstOrNull { it.id == exerciseId }
                ?.name ?: exerciseId.value
        return TopLift.Lift(exerciseId, name, change.currentKg, change.deltaKg)
    }

    private suspend fun changeInEstimate(
        exerciseId: ExerciseId,
        member: UserId,
    ): EightWeekChange? =
        when (val trend = exerciseTrendOf(exerciseId, member)) {
            is ExerciseTrend.Series -> trend.eightWeekChangeInEstimate()
            is ExerciseTrend.SinglePoint ->
                trend.point.estimatedOneRepMaxKg?.let { EightWeekChange(currentKg = it, deltaKg = null) }
            ExerciseTrend.NoData -> null
        }
}
