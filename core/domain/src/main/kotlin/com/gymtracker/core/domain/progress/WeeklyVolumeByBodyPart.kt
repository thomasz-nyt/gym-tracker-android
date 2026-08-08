package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.model.WorkoutSession
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** How much load one muscle took in a week, in kilograms. */
data class BodyPartVolume(
    val bodyPart: BodyPart,
    val volumeKg: Double,
)

/**
 * One week's training, split by muscle (US-17).
 *
 * [byBodyPart] is heaviest first, and **contains only muscles that were actually loaded**. A
 * week in the range with nothing in it is still a week — the list is empty rather than the
 * week being missing, because "you trained nothing that week" is a fact and closing the gap
 * would imply you trained every week.
 */
data class VolumeWeek(
    /** The Monday the week starts on. */
    val weekStarting: LocalDate,
    val byBodyPart: List<BodyPartVolume>,
)

/**
 * Weekly volume grouped by primary muscle, over a chosen range (US-17).
 *
 * Volume is `weight × reps`, summed. Two things are deliberately left out of it:
 *
 * - **Bodyweight sets.** There is no kilogram figure to add, and adding zero would say the
 *   muscle was trained weightlessly (constitution §2.4).
 * - **Exercises the catalog has no primary muscle for.** There is no honest bucket, and the
 *   app does not invent an "other" — the same rule ADR-0015 applied to `Equipment.UNSPECIFIED`.
 *
 * **On exercises with more than one primary muscle:** the volume counts in full toward each.
 * The number answers "how much load did this muscle take", not "what share of my training was
 * this", so the parts deliberately do not sum to the session's total. Splitting evenly instead
 * would invent a ratio nobody measured. Every one of the 873 bundled exercises records exactly
 * one primary muscle, so today the two readings coincide; this is here for the household
 * exercises M2 allows.
 *
 * @param zone the member's zone, for deciding which day — and so which week — a session was.
 */
class WeeklyVolumeByBodyPart(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
    private val catalog: ExerciseCatalog,
    private val zone: ZoneId,
) {
    /**
     * @param from the first day of interest; the week it falls in is the first week returned.
     * @param to the last day of interest, inclusive.
     * @return one entry per week in the range, oldest first, including weeks with no training.
     */
    suspend operator fun invoke(
        member: UserId,
        from: LocalDate,
        to: LocalDate,
    ): List<VolumeWeek> {
        val volumes = volumesByWeek(member, from, to)

        return generateSequence(from.startOfWeek()) { it.plusWeeks(1) }
            .takeWhile { it <= to.startOfWeek() }
            .map { week -> VolumeWeek(week, volumes[week].orEmpty().sortedByDescending { it.volumeKg }) }
            .toList()
    }

    private suspend fun volumesByWeek(
        member: UserId,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, List<BodyPartVolume>> {
        val inRange =
            sessions
                .observeFinishedSessions(member)
                .first()
                .filter { it.performedOn() in from..to }
        if (inRange.isEmpty()) return emptyMap()

        val sessionIds = inRange.map { it.id }
        val weekOfSession = inRange.associate { it.id to it.weekStarting() }
        val musclesOf = catalog.observeRanked(member).first().associate { it.id to it.primaryMuscles }
        val appearances = sessionExercises.observeForSessions(sessionIds).first().associateBy { it.id }
        val loaded = sets.observeForSessions(sessionIds).first().filter { it.weightKg != null }

        return loaded
            .flatMap { set ->
                val appearance = appearances[set.sessionExerciseId]
                val week = appearance?.let { weekOfSession[it.sessionId] }
                val muscles = appearance?.let { musclesOf[it.exerciseId] }.orEmpty()
                // One row per muscle the exercise trains; see the note on multi-primary above.
                if (week == null) emptyList() else muscles.map { Triple(week, it, set.weightKg!! * set.reps) }
            }.groupBy({ it.first }) { it.second to it.third }
            .mapValues { (_, pairs) ->
                pairs
                    .groupBy({ it.first }) { it.second }
                    .map { (bodyPart, volumes) -> BodyPartVolume(bodyPart, volumes.sum()) }
            }
    }

    /** The day the member would say a session happened on. */
    private fun WorkoutSession.performedOn(): LocalDate = startedAt.atZone(zone).toLocalDate()

    /** The Monday of the week a session falls in. */
    private fun WorkoutSession.weekStarting(): LocalDate = performedOn().startOfWeek()

    /** Weeks start Monday, per ISO — so "this week" means the same thing to everyone. */
    private fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
