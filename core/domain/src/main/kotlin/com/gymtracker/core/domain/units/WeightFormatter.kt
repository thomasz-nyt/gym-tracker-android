package com.gymtracker.core.domain.units

/**
 * A weight as it appears on screen: the member's unit first, the other alongside.
 *
 * @property secondary null when there is nothing to convert — a bodyweight movement.
 */
data class WeightDisplay(
    val primary: String,
    val secondary: String?,
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
        if (kilograms == null) return WeightDisplay(BODYWEIGHT, secondary = null)

        val other = if (primary == WeightUnit.LB) WeightUnit.KG else WeightUnit.LB
        return WeightDisplay(
            primary = render(kilograms, primary),
            // Converted from the stored kilograms, not from the primary string: deriving one
            // from the other would round twice and drift.
            secondary = render(kilograms, other),
        )
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
