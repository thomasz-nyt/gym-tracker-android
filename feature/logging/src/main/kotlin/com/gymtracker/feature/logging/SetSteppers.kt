package com.gymtracker.feature.logging

import kotlin.math.ceil
import kotlin.math.floor

// The arithmetic behind the +/− steppers (ADR-0016), shared by set entry (US-03) and set
// correction (US-04).
//
// Deliberately plain functions rather than methods: none of it touches controller state, and
// it is easier to check when it reads as arithmetic. It was private to `SetEntryController`
// until US-04 needed the same floors — a corrected set and a freshly logged one must not be
// allowed to disagree about what "one rep down" means.

/** US-03 for reps, ADR-0009 for sets: neither is meaningful below one. */
private const val WHOLE_NUMBER_FLOOR = 1

internal fun String.stepWholeNumber(direction: Int): String {
    val from = trim().toIntOrNull() ?: 0
    return (from + direction).coerceAtLeast(WHOLE_NUMBER_FLOOR).toString()
}

/**
 * The next multiple of [increment] in [direction], starting from [from].
 *
 * Rounding towards the direction of travel is what makes an off-grid value tidy itself up on
 * the first press instead of carrying its remainder forever.
 */
internal fun snap(
    from: Double,
    increment: Double,
    direction: Int,
): Double {
    val steps = from / increment
    val next = if (direction >= 0) floor(steps) + 1 else ceil(steps) - 1
    return next * increment
}

internal fun trimNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
