/*
 * NutritionFacts.kt
 * MacroDime
 *
 * A macro tuple with arithmetic, and the drift measure the swap tolerance is
 * judged by. Port of MacroDime/Domain/NutritionFacts.swift.
 */
package com.lungelo.macrodime.domain

import kotlin.math.abs
import kotlin.math.max

/** Atwater factors, kilocalories per gram of each macronutrient. */
object AtwaterFactor {
    const val PROTEIN = 4.0
    const val CARBOHYDRATE = 4.0
    const val FAT = 9.0
}

/** Calories plus the three tracked macronutrients, in grams. */
data class NutritionFacts(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    /**
     * Calories implied by the macro grams alone. Real label data rarely matches
     * this exactly (fibre, sugar alcohols and rounding all leak), so it is used
     * for sanity checks and never in place of the measured [calories].
     */
    val caloriesFromMacros: Double
        get() = protein * AtwaterFactor.PROTEIN +
            carbs * AtwaterFactor.CARBOHYDRATE +
            fat * AtwaterFactor.FAT

    data class MacroShares(val protein: Double, val carbs: Double, val fat: Double)

    /** Share of total calories from each macro. All zero for an empty meal. */
    val macroShares: MacroShares
        get() {
            val total = caloriesFromMacros
            if (total <= 0) return MacroShares(0.0, 0.0, 0.0)
            return MacroShares(
                protein * AtwaterFactor.PROTEIN / total,
                carbs * AtwaterFactor.CARBOHYDRATE / total,
                fat * AtwaterFactor.FAT / total,
            )
        }

    /** Multiplies every field. Used to turn "per serving" into "per portion". */
    fun scaled(factor: Double) = NutritionFacts(
        calories * factor,
        protein * factor,
        carbs * factor,
        fat * factor,
    )

    /**
     * Every field rounded to a whole number, for display. Never use the result
     * for further arithmetic: rounding error compounds across a week of meals.
     */
    val rounded: NutritionFacts
        get() = NutritionFacts(
            calories.roundedHalfAway(),
            protein.roundedHalfAway(),
            carbs.roundedHalfAway(),
            fat.roundedHalfAway(),
        )

    operator fun plus(other: NutritionFacts) = NutritionFacts(
        calories + other.calories,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
    )

    operator fun minus(other: NutritionFacts) = NutritionFacts(
        calories - other.calories,
        protein - other.protein,
        carbs - other.carbs,
        fat - other.fat,
    )

    companion object {
        val ZERO = NutritionFacts()
    }
}

/** Sums a sequence of macro tuples. */
fun Iterable<NutritionFacts>.total(): NutritionFacts = fold(NutritionFacts.ZERO) { sum, next -> sum + next }

// MARK: - Macro Drift

/**
 * How far a substituted meal has moved from the meal it replaced, as a relative
 * error per field. This is the quantity the 10% swap tolerance is measured
 * against.
 */
data class MacroDrift(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
) {
    constructor(baseline: NutritionFacts, candidate: NutritionFacts) : this(
        drift(baseline.calories, candidate.calories, TRACE_FLOOR_CALORIES),
        drift(baseline.protein, candidate.protein, TRACE_FLOOR_GRAMS),
        drift(baseline.carbs, candidate.carbs, TRACE_FLOOR_GRAMS),
        drift(baseline.fat, candidate.fat, TRACE_FLOOR_GRAMS),
    )

    /** The single worst field. A swap is accepted only if this is within tolerance. */
    val worst: Double get() = max(calories, max(protein, max(carbs, fat)))

    /** Average drift across the four fields, used to rank acceptable candidates. */
    val mean: Double get() = (calories + protein + carbs + fat) / 4

    fun isWithin(tolerance: Double): Boolean = worst <= tolerance

    companion object {
        /**
         * Below these a macro is treated as trace, and drift is judged on an
         * absolute difference instead of a percentage. Without this, a meal with
         * 1 g of fat fails any percentage test the moment the replacement
         * carries 2 g.
         */
        private const val TRACE_FLOOR_CALORIES = 60.0
        private const val TRACE_FLOOR_GRAMS = 8.0

        /**
         * Relative error, falling back to the floor as the denominator when the
         * baseline is too small for a percentage to mean anything: a 2 g move on
         * a 1 g baseline scores 0.25, not 200%.
         */
        private fun drift(baseline: Double, candidate: Double, floor: Double): Double {
            val delta = abs(candidate - baseline)
            return if (baseline < floor) delta / floor else delta / baseline
        }
    }
}
