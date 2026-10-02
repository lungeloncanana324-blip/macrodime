/*
 * BodyScienceEngine.kt
 * MacroDime
 *
 * Pure and deterministic, so the whole engine is testable with plain
 * assertions. Port of MacroDime/Engine/BodyScienceEngine.swift.
 *
 * Calculation chain:
 *   BMR (Mifflin-St Jeor), TDEE (activity multiplier), target calories (goal
 *   multiplier), protein (g/kg body weight), fat (25% of calories), carbohydrate
 *   (whatever calories remain).
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.AtwaterFactor
import com.lungelo.macrodime.domain.BiologicalSex
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.NutritionFacts
import kotlin.math.max
import kotlin.math.min

object BodyScienceEngine {

    /**
     * Every magic number in the prescription, so the science can be adjusted in
     * one place and the tests assert against the same source.
     */
    object Constants {
        /** Share of *target* calories allocated to dietary fat. */
        const val FAT_CALORIE_SHARE = 0.25

        /**
         * Fat is squeezed no lower than this before protein is clamped: below
         * roughly 20% of calories, hormonal and satiety costs start to outweigh
         * the benefit of freeing up calories.
         */
        const val MINIMUM_FAT_CALORIE_SHARE = 0.20

        /** Absolute floor on dietary fat regardless of body size, in g/kg. */
        const val MINIMUM_FAT_GRAMS_PER_KILOGRAM = 0.5

        /** Plausible input bounds. Values outside these are rejected, not guessed at. */
        val WEIGHT_RANGE_KG = 25.0..350.0
        val HEIGHT_RANGE_CM = 90.0..250.0

        /**
         * Adults only. This app prescribes calorie deficits, and a growing body
         * needs supervised intake rather than an algorithm's.
         */
        val AGE_RANGE = 18..100
    }

    /** Everything the prescription needs, already normalised to metric. */
    data class Input(
        val weightKg: Double,
        val heightCm: Double,
        val age: Int,
        val sex: BiologicalSex,
        val activity: ActivityLevel,
        val goal: FitnessGoal,
    ) {
        /** Null when the input is usable, otherwise the first problem found. */
        val validationError: ValidationError?
            get() = when {
                weightKg !in Constants.WEIGHT_RANGE_KG -> ValidationError.WeightOutOfRange
                heightCm !in Constants.HEIGHT_RANGE_CM -> ValidationError.HeightOutOfRange
                age !in Constants.AGE_RANGE -> ValidationError.AgeOutOfRange
                else -> null
            }

        val isValid: Boolean get() = validationError == null
    }

    enum class ValidationError(val message: String) {
        WeightOutOfRange("Enter a weight between 25 kg and 350 kg."),
        HeightOutOfRange("Enter a height between 90 cm and 250 cm."),
        AgeOutOfRange("MacroDime is for adults. Enter an age between 18 and 100."),
    }

    /** Thrown by [prescribe] for input outside plausible human range. */
    class InvalidInputException(val error: ValidationError) : IllegalArgumentException(error.message)

    /**
     * Notes attached to a prescription when the engine had to override the
     * textbook formula, surfaced in the UI so the numbers are never silently
     * different from what the stated rules would produce.
     */
    enum class Adjustment(val message: String) {
        CalorieFloorApplied("Your deficit was raised to the minimum safe intake for your sex."),
        FatReducedToFitProtein("Fat was trimmed toward 20% of calories to fit your protein target."),
        ProteinClamped("Protein was reduced to fit inside your calorie target."),
    }

    data class Prescription(
        val bmi: Double,
        val bmiCategory: BMICategory,
        val bmr: Double,
        val tdee: Double,
        /** Signed difference from TDEE: negative for a deficit, positive for a surplus. */
        val calorieDelta: Double,
        /** Daily targets. The macro grams always sum, via Atwater, to the calories. */
        val targets: NutritionFacts,
        val adjustments: List<Adjustment>,
    ) {
        val isDeficit: Boolean get() = calorieDelta < 0

        /** Protein per kilogram as actually prescribed, after any clamping. */
        fun proteinGramsPerKilogram(weightKg: Double): Double =
            if (weightKg > 0) targets.protein / weightKg else 0.0
    }

    /**
     * Standard WHO adult BMI bands. Presented with the caveat that BMI ignores
     * body composition, which is why the app tracks waist and photos, and the
     * category is never used to drive the prescription.
     */
    enum class BMICategory(val displayName: String) {
        Underweight("Underweight"),
        Healthy("Healthy range"),
        Overweight("Overweight"),
        Obese("Obese");

        companion object {
            fun categoryFor(bmi: Double): BMICategory = when {
                bmi < 18.5 -> Underweight
                bmi < 25 -> Healthy
                bmi < 30 -> Overweight
                else -> Obese
            }
        }
    }

    // Individual calculations, exposed separately so each formula can be tested
    // in isolation and reused by the UI.

    /** Body Mass Index: `weight_kg / height_m²`. */
    fun bmi(weightKg: Double, heightCm: Double): Double {
        if (heightCm <= 0) return 0.0
        val heightM = heightCm / 100
        return weightKg / (heightM * heightM)
    }

    /** Mifflin-St Jeor: `10 × kg + 6.25 × cm - 5 × age + constant` (+5 male, -161 female). */
    fun basalMetabolicRate(weightKg: Double, heightCm: Double, age: Int, sex: BiologicalSex): Double =
        (10 * weightKg) + (6.25 * heightCm) - (5.0 * age) + sex.mifflinConstant

    /** Total Daily Energy Expenditure: `BMR × activity multiplier`. */
    fun totalDailyEnergyExpenditure(bmr: Double, activity: ActivityLevel): Double = bmr * activity.multiplier

    data class TargetCalories(val calories: Double, val flooredForSafety: Boolean)

    /** Goal-adjusted intake, floored at the minimum safe intake for the sex. */
    fun targetCalories(tdee: Double, goal: FitnessGoal, sex: BiologicalSex): TargetCalories {
        val raw = tdee * goal.calorieMultiplier
        val floor = sex.minimumSafeCalories
        return if (raw < floor) TargetCalories(floor, true) else TargetCalories(raw, false)
    }

    data class MacroSplit(val macros: NutritionFacts, val adjustments: List<Adjustment>)

    /**
     * Splits a calorie target into grams of protein, fat and carbohydrate, in
     * the order a coach would: protein first from body weight, fat at 25%,
     * carbohydrate takes the remainder.
     *
     * Protein and fat can collide: a heavy person in a steep deficit can need
     * more protein plus fat calories than the target allows. Rather than emit a
     * negative carbohydrate figure, fat is squeezed toward its 20% floor, then
     * protein is clamped, and each override is reported.
     */
    fun macroSplit(targetCalories: Double, weightKg: Double, goal: FitnessGoal): MacroSplit {
        val adjustments = mutableListOf<Adjustment>()

        var proteinGrams = weightKg * goal.proteinGramsPerKilogram
        var fatGrams = (targetCalories * Constants.FAT_CALORIE_SHARE) / AtwaterFactor.FAT

        var proteinCalories = proteinGrams * AtwaterFactor.PROTEIN
        var fatCalories = fatGrams * AtwaterFactor.FAT

        // Step 1: trim fat toward its floor. The floor is the stricter of "20%
        // of calories" and "0.5 g/kg", then capped at the original allocation:
        // without that cap a heavy person on a small target, whose 0.5 g/kg
        // floor sits above their 25% share, would see fat silently *raised*.
        if (proteinCalories + fatCalories > targetCalories) {
            val fatFloorCalories = min(
                max(
                    targetCalories * Constants.MINIMUM_FAT_CALORIE_SHARE,
                    weightKg * Constants.MINIMUM_FAT_GRAMS_PER_KILOGRAM * AtwaterFactor.FAT,
                ),
                fatCalories,
            )
            val roomForFat = max(targetCalories - proteinCalories, 0.0)
            val newFatCalories = max(min(fatCalories, roomForFat), fatFloorCalories)

            if (newFatCalories < fatCalories) {
                fatCalories = newFatCalories
                fatGrams = fatCalories / AtwaterFactor.FAT
                adjustments += Adjustment.FatReducedToFitProtein
            }
        }

        // Step 2: still over budget? Protein yields the remainder.
        if (proteinCalories + fatCalories > targetCalories) {
            proteinCalories = max(targetCalories - fatCalories, 0.0)
            proteinGrams = proteinCalories / AtwaterFactor.PROTEIN
            adjustments += Adjustment.ProteinClamped
        }

        // Step 3: carbohydrate absorbs what is left. The clamps above already
        // guarantee a non-negative remainder; max() keeps that invariant local.
        val carbCalories = max(targetCalories - proteinCalories - fatCalories, 0.0)
        val carbGrams = carbCalories / AtwaterFactor.CARBOHYDRATE

        return MacroSplit(
            NutritionFacts(calories = targetCalories, protein = proteinGrams, carbs = carbGrams, fat = fatGrams),
            adjustments,
        )
    }

    /** Runs the whole chain. Throws rather than guessing when the input is implausible. */
    fun prescribe(input: Input): Prescription {
        input.validationError?.let { throw InvalidInputException(it) }
        return prescribeUnchecked(input)
    }

    /**
     * For live preview, where the user is mid-typing and transient nonsense is
     * expected. Callers gate on [Input.isValid] before showing the result as a
     * real prescription.
     */
    fun prescribeUnchecked(input: Input): Prescription {
        val bmiValue = bmi(input.weightKg, input.heightCm)
        val bmrValue = basalMetabolicRate(input.weightKg, input.heightCm, input.age, input.sex)
        val tdeeValue = totalDailyEnergyExpenditure(bmrValue, input.activity)
        val target = targetCalories(tdeeValue, input.goal, input.sex)
        val split = macroSplit(target.calories, input.weightKg, input.goal)

        val adjustments = split.adjustments.toMutableList()
        if (target.flooredForSafety) adjustments.add(0, Adjustment.CalorieFloorApplied)

        return Prescription(
            bmi = bmiValue,
            bmiCategory = BMICategory.categoryFor(bmiValue),
            bmr = bmrValue,
            tdee = tdeeValue,
            calorieDelta = target.calories - tdeeValue,
            targets = split.macros,
            adjustments = adjustments,
        )
    }
}
