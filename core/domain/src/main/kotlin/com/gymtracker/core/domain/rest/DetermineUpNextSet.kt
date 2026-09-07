package com.gymtracker.core.domain.rest

import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.model.SessionExerciseId
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import com.gymtracker.core.domain.sessionexercise.SessionExerciseRepository
import com.gymtracker.core.domain.set.PrefillFromLastSet
import com.gymtracker.core.domain.set.SetPrefill
import com.gymtracker.core.domain.set.SetRepository
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * What the rest panel says is coming (ADR-0023).
 *
 * @property setNumber the next set's number, which is a **count and not a target**. There is
 *   deliberately no "out of N" field here: the app does not know how many sets you intend, and
 *   ADR-0009 and ADR-0017 both refused the prescription entity that would tell it. A screen
 *   cannot render "set 3 of 3" from this type because the 3 does not exist to render.
 * @property comparison the member's most recent set of this movement from an *earlier* session,
 *   or null when there is none. Null means the panel shows nothing in its place, rather than
 *   comparing a set against itself or inventing a baseline (constitution §2.4).
 * @property rest the rest this movement's target names for after each set (ADR-0050), or null
 *   when it names none and the member's default applies. Carried here so the one-tap paths —
 *   the screen's button and the notification's `LOG SET` — start the same rest the sheet would,
 *   without a second lookup of their own.
 */
data class UpNextSet(
    val sessionExerciseId: SessionExerciseId,
    val exerciseId: ExerciseId,
    val setNumber: Int,
    val prefill: SetPrefill,
    val comparison: ExerciseSet?,
    val rest: Duration? = null,
)

/**
 * Works out what follows the rest currently running, deriving all of it from the database
 * rather than remembering anything.
 *
 * That is the same choice ADR-0010 made for the timer itself and ADR-0017 made for the guided
 * queue: nothing here survives a process kill because nothing here needs to. Reopening the app
 * mid-rest recomputes the same answer from the same rows.
 *
 * "Up next" is the exercise of the **most recently logged set in this session** — not the first
 * in `position` order, which is the order exercises were added rather than a plan to perform
 * them (ADR-0023).
 */
class DetermineUpNextSet(
    private val sessionExercises: SessionExerciseRepository,
    private val sets: SetRepository,
    private val prefillFromLastSet: PrefillFromLastSet,
) {
    /** @return null when nothing has been logged in this session yet, so there is nothing to show. */
    suspend operator fun invoke(
        sessionId: SessionId,
        member: UserId,
        unit: WeightUnit,
    ): UpNextSet? {
        val appearances = sessionExercises.observeForSession(sessionId).first()
        val inSession = sets.observeForSessions(listOf(sessionId)).first()
        val mostRecent = inSession.maxByOrNull { it.performedAt }
        val appearance = appearances.firstOrNull { it.id == mostRecent?.sessionExerciseId }

        // Nothing logged in this session yet, so there is no "next" to speak of.
        if (mostRecent == null || appearance == null) return null

        val alreadyLogged = inSession.count { it.sessionExerciseId == appearance.id }

        return UpNextSet(
            sessionExerciseId = appearance.id,
            exerciseId = appearance.exerciseId,
            setNumber = alreadyLogged + 1,
            // The same rule US-03 uses, so the panel and the entry sheet can never disagree
            // about what the next set starts at. The fallback is the set that just happened,
            // which is what the prefill would have found anyway.
            prefill = prefillFromLastSet(appearance.exerciseId, member, unit) ?: mostRecent.asPrefill(unit),
            comparison = sets.lastSetOfBefore(appearance.exerciseId, member, sessionId),
            rest = appearance.target?.rest,
        )
    }

    private fun ExerciseSet.asPrefill(unit: WeightUnit) =
        SetPrefill(weight = weightKg?.let { UnitConverter.fromKilograms(it, unit) }, reps = reps)
}
