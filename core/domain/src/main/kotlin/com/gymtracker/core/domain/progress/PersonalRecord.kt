package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.SetRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * The heaviest load ever lifted for one exercise at one rep count (US-18, ADR-0025).
 *
 * Every record is a set that actually happened. There is no estimate here and nothing to label
 * as one — which is the whole reason ADR-0025 chose this rule over an Epley-based record.
 *
 * @property reps the rep count this record belongs to. Bench at 5 and bench at 8 are different
 *   records, and neither has to beat the other.
 * @property achievedOn the day it was lifted, in the member's zone.
 */
data class PersonalRecord(
    val exerciseId: ExerciseId,
    val reps: Int,
    val weightKg: Double,
    val achievedOn: LocalDate,
)

/**
 * One exercise's records, one per rep count (US-18, ADR-0025).
 *
 * **Reads every session, not just finished ones** — unlike [ExerciseTrendOf], which is
 * deliberately restricted to sessions that are over. A chart point is a day that has finished;
 * a record is a lift, and it is set the moment it is performed. Working up to a heavy single
 * mid-workout is a record before you have racked the bar.
 *
 * Bodyweight sets are skipped throughout. They carry no load to compare, and reading the
 * missing weight as zero would tie every one of them for last place forever — the same rule
 * `WeeklyVolumeByBodyPart` and [ExerciseTrendOf] already apply (constitution §2.4).
 *
 * @param zone the member's zone, for the day they would say a record was set on.
 */
class PersonalRecordsOf(
    private val sessions: SessionRepository,
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
    private val zone: ZoneId,
) {
    /**
     * @param excludingSetId a set to leave out of the history, so [DetectPersonalRecord] can ask
     *   what the records were *before* the set it is judging. Detection runs on the save path
     *   and may well see its own set already committed.
     * @return one record per rep count, ordered by rep count ascending. Empty when the member
     *   has never loaded this movement.
     */
    suspend operator fun invoke(
        exerciseId: ExerciseId,
        member: UserId,
        excludingSetId: String? = null,
    ): List<PersonalRecord> =
        loadedSets(exerciseId, member, excludingSetId)
            .groupBy { it.reps }
            .map { (reps, atThisRepCount) -> recordAt(exerciseId, reps, atThisRepCount) }
            .sortedBy { it.reps }

    /** The heaviest set at one rep count, and the day it happened. */
    private fun recordAt(
        exerciseId: ExerciseId,
        reps: Int,
        atThisRepCount: List<ExerciseSet>,
    ): PersonalRecord {
        // maxBy, not maxOf: the date has to come from the same set as the load, and the
        // *earliest* such set — a record is set the first time you reach it, not the last.
        val best = atThisRepCount.sortedBy { it.performedAt }.maxByOrNull { it.weightKg!! }!!

        return PersonalRecord(
            exerciseId = exerciseId,
            reps = reps,
            weightKg = best.weightKg!!,
            achievedOn = best.performedAt.atZone(zone).toLocalDate(),
        )
    }

    /**
     * Every loaded set of this exercise the member has ever performed.
     *
     * Three reads whatever the length of the history — the sessions, their appearances of this
     * exercise, and their sets — then filtered in memory, the same shape [ExerciseTrendOf] uses
     * for the reason its own comment gives: not a query per session.
     */
    private suspend fun loadedSets(
        exerciseId: ExerciseId,
        member: UserId,
        excludingSetId: String?,
    ): List<ExerciseSet> {
        val sessionIds = everySessionOf(member)
        if (sessionIds.isEmpty()) return emptyList()

        val appearances =
            sessionExercises
                .observeForSessions(sessionIds)
                .first()
                .filter { it.exerciseId == exerciseId }
                .map { it.id }
                .toSet()

        return sets
            .observeForSessions(sessionIds)
            .first()
            .filter { it.sessionExerciseId in appearances }
            .filter { it.id != excludingSetId }
            .filter { it.weightKg != null }
    }

    /** Finished sessions and the one in progress. See the note on this class about why both. */
    private suspend fun everySessionOf(member: UserId): List<SessionId> {
        val finished = sessions.observeFinishedSessions(member).first().map { it.id }
        val active = sessions.findActiveSession(member)?.id

        return if (active == null) finished else finished + active
    }
}

/**
 * Whether a set just logged beats what came before it (US-18, ADR-0025).
 *
 * Two rules do most of the work here, and both exist to stop the banner crying wolf:
 *
 * - **The first time at a rep count is not a record.** There has to be a previous load at the
 *   same (exercise, reps) to beat. Otherwise every first set of every new movement fires a
 *   celebration, which is noise on day one and teaches the household to ignore it.
 * - **Equalling is not beating.** Strictly greater, so repeating the same working weight every
 *   week does not fire a banner every week.
 *
 * **This sits on the save path, which constitution §2.1 makes sacred.** Nothing here may be
 * allowed to delay the set being committed or the entry sheet closing; how that is wired is the
 * caller's problem, and deliberately not decided in the domain.
 */
class DetectPersonalRecord(
    private val recordsOf: PersonalRecordsOf,
    private val zone: ZoneId,
) {
    /**
     * @param candidate the set just logged, or about to be. Its own id is excluded from the
     *   history it is judged against, so a set already committed does not beat itself.
     * @return the record it set, or null — which covers all four ways it is not one: it carried
     *   no load, the rep count has no history, it tied, or it fell short.
     */
    suspend operator fun invoke(
        candidate: ExerciseSet,
        exerciseId: ExerciseId,
        member: UserId,
    ): PersonalRecord? {
        val lifted = candidate.weightKg ?: return null

        val previous =
            recordsOf(exerciseId, member, excludingSetId = candidate.id)
                .firstOrNull { it.reps == candidate.reps }

        return previous
            ?.takeIf { lifted > it.weightKg }
            ?.let {
                PersonalRecord(
                    exerciseId = exerciseId,
                    reps = candidate.reps,
                    weightKg = lifted,
                    achievedOn = candidate.performedAt.atZone(zone).toLocalDate(),
                )
            }
    }
}
