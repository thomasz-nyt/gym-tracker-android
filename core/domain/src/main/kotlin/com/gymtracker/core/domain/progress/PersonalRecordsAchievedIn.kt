package com.gymtracker.core.domain.progress

import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.session.SessionDetail

/**
 * Which personal records a session actually set (US-31), from what it already logged.
 *
 * [DetectPersonalRecord] is designed for the save path — one candidate set, judged against
 * everything but itself. Looped over every set a *finished* session logged, it is also the
 * correct way to ask, after the fact, "what did this workout set a record at" — a set already
 * committed is still excluded from its own comparison by [DetectPersonalRecord]'s own
 * `excludingSetId`, so nothing here is a new detection rule.
 *
 * **What is new is the dedupe.** Two sets at the same (exercise, reps) in one session can both
 * look like records — the second beats the first, which is now part of the history it is judged
 * against. A summary listing both would be redundant: the member left with one number, not two.
 * Only the heaviest survives per (exercise, reps).
 */
class PersonalRecordsAchievedIn(
    private val detect: DetectPersonalRecord,
) {
    suspend operator fun invoke(
        detail: SessionDetail,
        member: UserId,
    ): List<PersonalRecord> =
        detail.exercises
            .flatMap { performed ->
                performed.sets.mapNotNull { set -> detect(set, performed.sessionExercise.exerciseId, member) }
            }.groupBy { it.exerciseId to it.reps }
            .map { (_, records) -> records.maxBy { it.weightKg } }
}
