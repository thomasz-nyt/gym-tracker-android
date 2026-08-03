package com.gymtracker.core.domain.units

import java.math.BigDecimal
import java.math.RoundingMode

/** The unit a member reads and types weights in. Storage is always kilograms. */
enum class WeightUnit {
    KG,
    LB,
}

/**
 * How much one press of a weight stepper moves, in this unit (ADR-0016).
 *
 * The smallest change most gyms can actually load: a 1.25 kg pair on the bar, or a 2.5 lb
 * pair. Expressed in the member's own unit rather than converted from a single canonical
 * step, because a stepper that moved by 2.27 kg — 5 lb rounded — would be arithmetically
 * tidy and useless at a rack.
 */
fun WeightUnit.weightIncrement(): Double =
    when (this) {
        WeightUnit.KG -> KILOGRAM_STEP
        WeightUnit.LB -> POUND_STEP
    }

/** A 1.25 kg pair on the bar. */
private const val KILOGRAM_STEP = 2.5

/** A 2.5 lb pair on the bar. */
private const val POUND_STEP = 5.0

/**
 * The single place a weight changes units (`data-model.md` § Units, ADR-0006).
 *
 * Members type in their own unit to one decimal place; kilograms are stored to two, which
 * is exactly what `weight_kg numeric(6,2)` holds. Anything else in the app doing arithmetic
 * on a display weight is a bug — that is how a lifting app starts showing people numbers
 * they never typed.
 */
object UnitConverter {
    /** Pounds in one kilogram, to the precision the international definition gives. */
    const val POUNDS_PER_KILOGRAM = 2.20462262185

    private const val STORAGE_DECIMALS = 2
    private const val DISPLAY_DECIMALS = 1

    /**
     * Converts a weight the member entered into the canonical kilograms to store.
     *
     * @param entered the weight as typed, in [unit].
     * @throws IllegalArgumentException if [entered] is negative. Postgres enforces
     *   `weight_kg >= 0`; the domain should not wait for a round trip to find out.
     */
    fun toKilograms(
        entered: Double,
        unit: WeightUnit,
    ): Double {
        require(entered >= 0) { "Weight cannot be negative, but was $entered" }

        val kilograms =
            when (unit) {
                WeightUnit.KG -> entered
                WeightUnit.LB -> entered / POUNDS_PER_KILOGRAM
            }
        return kilograms.roundTo(STORAGE_DECIMALS)
    }

    /**
     * Converts stored kilograms into the number to show the member, to one decimal place.
     *
     * Every 2.5 lb increment up to 1000 lb and every 0.1 kg increment up to 300 kg survives
     * [toKilograms] followed by this unchanged; both are asserted in the test table.
     */
    fun fromKilograms(
        kilograms: Double,
        unit: WeightUnit,
    ): Double {
        val converted =
            when (unit) {
                WeightUnit.KG -> kilograms
                WeightUnit.LB -> kilograms * POUNDS_PER_KILOGRAM
            }
        return converted.roundTo(DISPLAY_DECIMALS)
    }

    /**
     * Half-up on the decimal the member sees, not on the binary double underneath.
     *
     * `BigDecimal(60.555)` is 60.55499999999999971578…, so it rounds *down* to 60.55 — correct
     * for the bits, wrong for someone who typed "60.555". `BigDecimal.valueOf` goes through
     * `Double.toString`, giving the shortest decimal that reads back as this double, which is
     * the number the member actually meant.
     */
    private fun Double.roundTo(decimals: Int): Double =
        BigDecimal.valueOf(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()
}
