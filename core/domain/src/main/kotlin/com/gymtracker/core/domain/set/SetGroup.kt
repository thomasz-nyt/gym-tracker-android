package com.gymtracker.core.domain.set

import com.gymtracker.core.domain.model.ExerciseSet

/**
 * Consecutive identical sets, collapsed for reading (ADR-0009).
 *
 * Display only. The rows underneath stay separate, so US-04 can still edit or delete any
 * single set and every M4 chart still reads individual sets.
 *
 * @property count how many identical sets in a row.
 * @property firstSetIndex the `set_index` of the first set in the group, for numbering.
 */
data class SetGroup(
    val count: Int,
    val firstSetIndex: Int,
    val weightKg: Double?,
    val reps: Int,
) {
    companion object {
        /** @param sets in `set_index` order. */
        fun of(sets: List<ExerciseSet>): List<SetGroup> =
            sets.fold(mutableListOf()) { groups, set ->
                val last = groups.lastOrNull()
                if (last != null && last.weightKg == set.weightKg && last.reps == set.reps) {
                    groups[groups.lastIndex] = last.copy(count = last.count + 1)
                } else {
                    groups += SetGroup(1, set.setIndex, set.weightKg, set.reps)
                }
                groups
            }
    }
}
