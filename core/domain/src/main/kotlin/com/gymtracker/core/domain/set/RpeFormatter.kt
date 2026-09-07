package com.gymtracker.core.domain.set

/**
 * How an RPE reads back (US-60): `@8`, `@8.5` — lifting's own notation (`135 × 8 @8`), and one
 * spelling for every surface that shows a set, so the session row, the rest panel's comparison,
 * the exercise log and the workout detail can never disagree about it.
 *
 * A whole number drops its `.0`: RPE moves in half steps ([SetValidation.requireValidRpe]), so
 * "8.0" is not more precise than "8", only longer. Nothing here rounds — a value that is not a
 * half step is a bug upstream, and this renders it as it is rather than hiding it.
 */
object RpeFormatter {
    /**
     * Every value an RPE may take, in order: 5.0, 5.5, … 10.0 — the eleven half steps
     * [SetValidation] accepts, spelled out once so a screen offering them as choices (US-60)
     * cannot drift from what the write will accept. `RpeFormatterTest` pins the two together.
     */
    val scale: List<Double> = generateSequence(SCALE_MIN) { it + SCALE_STEP }.takeWhile { it <= SCALE_MAX }.toList()

    /** The bare number: `"8"`, `"8.5"`, `"10"`. */
    fun number(rpe: Double): String = if (rpe % 1.0 == 0.0) rpe.toLong().toString() else rpe.toString()

    /** With the notation's own prefix: `"@8"`, `"@8.5"`. */
    fun at(rpe: Double): String = "@" + number(rpe)

    private const val SCALE_MIN = 5.0
    private const val SCALE_MAX = 10.0
    private const val SCALE_STEP = 0.5
}
