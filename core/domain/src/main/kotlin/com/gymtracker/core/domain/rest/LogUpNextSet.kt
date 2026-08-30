package com.gymtracker.core.domain.rest

import com.gymtracker.core.domain.member.UnitPreference
import com.gymtracker.core.domain.model.ExerciseSet
import com.gymtracker.core.domain.set.LogSets
import com.gymtracker.core.domain.set.SetInput

/**
 * Logs the set that is up next and starts the rest that follows (US-35, US-54).
 *
 * One place, two callers: the session screen's one-tap button and the notification's `LOG SET`
 * action. That is deliberate. [DetermineUpNextSet] already refuses to let two screens disagree
 * about *what* the next set is; this refuses to let two call sites disagree about what logging
 * it does — in particular that the weight is converted out of the member's unit exactly once
 * (ADR-0006), which a second hand-rolled call site is precisely the place to get wrong.
 *
 * The rest is started here rather than by the caller because it is not optional: US-05 has a
 * rest follow every set, and a member logging from the shade cannot start one by hand.
 */
class LogUpNextSet(
    private val logSets: LogSets,
    private val restTimer: RestTimer,
    private val unitPreference: UnitPreference,
) {
    /**
     * @param next what [DetermineUpNextSet] said was coming. Its prefill is written as-is —
     *   this path never edits it, which is what makes it one tap (ADR-0029).
     * @return the set that was written, for a caller that has something to say about it.
     */
    suspend operator fun invoke(next: UpNextSet): ExerciseSet {
        val logged =
            logSets(
                sessionExerciseId = next.sessionExerciseId,
                input =
                    SetInput(
                        weight = next.prefill.weight,
                        unit = unitPreference.current(),
                        reps = next.prefill.reps,
                        // Never carried forward: RPE records how hard *that* set felt, and
                        // repeating it would invent a measurement nobody took (US-03).
                        rpe = null,
                    ),
                sets = 1,
            )

        restTimer.start()
        return logged.first()
    }
}
