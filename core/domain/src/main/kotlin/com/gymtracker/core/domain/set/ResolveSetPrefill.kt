package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.MovementTarget
import com.gymtracker.core.domain.units.UnitConverter
import com.gymtracker.core.domain.units.WeightUnit

/**
 * What the set-entry sheet opens showing, and whether that came from something the member
 * actually did (US-37).
 *
 * @property reps never null — floors at 12 when neither [SetPrefill] nor [MovementTarget] has
 *   an opinion.
 * @property sets one actual set on ordinary entry, independent of the planned count (US-62).
 * @property fromHistory true when [weight] and [reps] came from a real past set, so the sheet
 *   can say "Prefilled from last Tuesday — 100 lb × 8" rather than leave the number unexplained.
 */
data class ResolvedPrefill(
    val weight: Double?,
    val reps: Int,
    val sets: Int,
    val fromHistory: Boolean,
)

/**
 * US-37 (ADR-0031, superseding ADR-0027's "a target prefills set entry"): the last set actually
 * performed on this exact movement wins over the routine's target, which wins over a floor of
 * 12 reps. Weight never floors; an existing bodyweight record keeps its absent load. Ordinary entry
 * always records one actual set. Bulk entry is an explicit UI choice (US-62, ADR-0052).
 *
 * The one place this rule lives, called from both `SetEntryController.open` and
 * [com.gymtracker.core.domain.set]'s consumers in `ActiveSessionViewModel` — previously each
 * inlined its own `target ?: history` merge, in the opposite order, which is how the two could
 * (and did) drift out of agreement with each other and with the design.
 *
 * The labelling rule ADR-0027 already established — a target renders as a target, never
 * substituted for a performed number — is unchanged by this: this function decides only what
 * *prefills* the fields, not what a screen displays as a target.
 */
object ResolveSetPrefill {
    private const val DEFAULT_REPS = 12
    private const val NO_TARGET_SETS = 1

    operator fun invoke(
        history: SetPrefill?,
        target: MovementTarget?,
        unit: WeightUnit,
    ): ResolvedPrefill {
        val targetWeight = target?.weightKg?.let { UnitConverter.fromKilograms(it, unit) }
        return ResolvedPrefill(
            weight = if (history != null) history.weight else targetWeight,
            reps = history?.reps ?: target?.reps ?: DEFAULT_REPS,
            sets = NO_TARGET_SETS,
            fromHistory = history != null,
        )
    }
}
