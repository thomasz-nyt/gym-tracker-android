package com.gymtracker.core.domain.rest

import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.session.SessionRepository
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.first

/**
 * Everything the rest notification needs in order to say something (US-54, ADR-0046).
 *
 * @property upNext the set the notification is about, carried whole rather than flattened: the
 *   `LOG SET` action needs exactly this to perform the write, and copying its fields out would
 *   mean two shapes to keep in step.
 * @property exerciseName null when the catalog has no row for [UpNextSet.exerciseId] — render
 *   nothing in its place, never the raw id and never a placeholder (US-13's absence rule).
 * @property unit the member's own unit, travelling with the numbers it describes so a caller
 *   cannot format a weight against the wrong one.
 *
 * There is deliberately no end time here. Whether a rest is running, and when it ends, is
 * already known to everything that would ask — the notifier is handed the end time, and the
 * alarm firing *is* the answer that it has passed. Carrying it would be a second copy of the
 * one value ADR-0010 keeps in exactly one place.
 */
data class RestNotice(
    val upNext: UpNextSet,
    val exerciseName: String?,
    val unit: WeightUnit,
) {
    val setNumber: Int get() = upNext.setNumber

    /** In [unit], as [UpNextSet.prefill] carries it. Null for a bodyweight movement. */
    val weight: Double? get() = upNext.prefill.weight

    val reps: Int get() = upNext.prefill.reps

    val sessionExerciseId: SessionExerciseId get() = upNext.sessionExerciseId

    val exerciseId: ExerciseId get() = upNext.exerciseId
}

/**
 * Works out what the rest notification should say, deriving all of it from the database.
 *
 * The same choice [DetermineUpNextSet] made, for the same reason, and here it buys something
 * extra: a notification action can be tapped long after the process that posted it has died, so
 * a receiver that remembered anything would be remembering it wrong. Asking again is always
 * cheaper than being stale.
 *
 * This is also where ADR-0010's "the receiver and the notification are untestable glue" is kept
 * true. Every decision worth asserting is here; the glue only formats.
 */
class DescribeRestNotification(
    private val sessions: SessionRepository,
    private val currentMember: CurrentMember,
    private val unitPreference: UnitPreference,
    private val determineUpNextSet: DetermineUpNextSet,
    private val catalog: ExerciseCatalog,
) {
    /**
     * @return null when there is no active session, or nothing has been logged in it yet. There
     *   is no set to name in either case, and naming one anyway is what constitution §2.4
     *   forbids — the caller falls back to its static text instead.
     */
    suspend operator fun invoke(): RestNotice? {
        val member = currentMember.id()
        val unit = unitPreference.current()
        val upNext =
            sessions
                .findActiveSession(member)
                ?.let { determineUpNextSet(it.id, member, unit) }
                ?: return null

        return RestNotice(
            upNext = upNext,
            // Matched out of the ranked catalog rather than fetched by id, which is the shape
            // this interface offers (see `WeeklyVolumeByBodyPart` and `SessionDetail` for the
            // same read). Missing is a real answer here, not an error.
            exerciseName =
                catalog
                    .observeRanked(member)
                    .first()
                    .firstOrNull { it.id == upNext.exerciseId }
                    ?.name,
            unit = unit,
        )
    }
}
