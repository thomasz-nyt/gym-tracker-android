package com.gymtracker.core.domain.units

import java.util.Locale
import kotlin.math.roundToLong

/**
 * A weight as it appears on screen: the member's unit first, the other alongside.
 *
 * @property secondary null when there is nothing to convert — a bodyweight movement.
 * @property number the bare number behind [primary] — e.g. `"135"` where [primary] is
 *   `"135 lb"` — for a call site that draws digits and unit separately (ADR-0011's Turn 4
 *   amendment: a load line as a baseline `Row` of separate `Text`s, a numeral role for the
 *   number and a word role for the unit, rather than one formatted string measured as a whole).
 *   Null exactly when [isBodyweight] is true — there is no number to split out of the word
 *   "Bodyweight". Always the *primary* unit's number, matching [primary] itself, never the
 *   secondary conversion.
 * @property unit the unit suffix behind [primary] — e.g. `"lb"` — for the same split. Null
 *   exactly when [number] is null.
 * @property isBodyweight true when there is no weight recorded (constitution §2: absent is a
 *   first-class state, not a zero). A call site drawing the split baseline row uses this to
 *   choose the word role for [primary] itself (`"Bodyweight"`) instead of the numeral role
 *   [number]/[unit] would otherwise take.
 */
data class WeightDisplay(
    val primary: String,
    val secondary: String?,
    val number: String?,
    val unit: String?,
    val isBodyweight: Boolean,
)

/**
 * Renders stored kilograms for reading (ADR-0008).
 *
 * The household is in the US but reads kilograms comfortably: plates are labelled in
 * pounds while the app and most training material are in kilograms. Showing both means
 * nobody converts in their head between sets.
 */
object WeightFormatter {
    private const val BODYWEIGHT = "Bodyweight"

    /**
     * @param kilograms the stored canonical weight, or null for a bodyweight movement.
     * @param primary the unit the member thinks in.
     */
    fun format(
        kilograms: Double?,
        primary: WeightUnit,
    ): WeightDisplay {
        if (kilograms == null) {
            return WeightDisplay(BODYWEIGHT, secondary = null, number = null, unit = null, isBodyweight = true)
        }

        val other = if (primary == WeightUnit.LB) WeightUnit.KG else WeightUnit.LB
        return WeightDisplay(
            primary = render(kilograms, primary),
            // Converted from the stored kilograms, not from the primary string: deriving one
            // from the other would round twice and drift.
            secondary = render(kilograms, other),
            number = number(UnitConverter.fromKilograms(kilograms, primary)),
            unit = primary.label,
            isBodyweight = false,
        )
    }

    /**
     * A whole session's volume, for a history row (US-06).
     *
     * Rounded to whole units and grouped: across a workout nobody cares about a tenth of a
     * pound, and "9,083 lb" is readable at a glance where "9083.0 lb" is not. Only one unit,
     * unlike [format] — a history row is a scan, not a number to act on.
     *
     * @param kilograms the summed volume, or null when nothing in the session had a weight
     *   recorded. Null in, null out: there is no volume to claim, and 0 would be a lie.
     */
    fun formatVolume(
        kilograms: Double?,
        unit: WeightUnit,
    ): String? {
        if (kilograms == null) return null

        val whole = UnitConverter.fromKilograms(kilograms, unit).roundToLong()
        return "${"%,d".format(Locale.US, whole)} ${unit.label}"
    }

    /** The bare number for the entry field — no unit suffix for the member to delete. */
    fun forEntry(
        kilograms: Double?,
        unit: WeightUnit,
    ): String = kilograms?.let { number(UnitConverter.fromKilograms(it, unit)) }.orEmpty()

    private fun render(
        kilograms: Double,
        unit: WeightUnit,
    ): String = "${number(UnitConverter.fromKilograms(kilograms, unit))} ${unit.label}"

    /** Drops a trailing `.0`, so it reads "135 lb" the way a person would say it. */
    private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private val WeightUnit.label: String
        get() =
            when (this) {
                WeightUnit.KG -> "kg"
                WeightUnit.LB -> "lb"
            }
}
