/*
 * BodyScienceEngineTest.kt
 *
 * Port of MacroDimeTests/BodyScienceEngineTests.swift. Expected values are
 * worked by hand from the stated formulae, not copied from a previous run: a
 * test that only asserts "same as last time" cannot catch a wrong formula.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.AtwaterFactor
import com.lungelo.macrodime.domain.BiologicalSex
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.UnitConversion
import com.lungelo.macrodime.engine.BodyScienceEngine.Adjustment
import com.lungelo.macrodime.engine.BodyScienceEngine.BMICategory
import com.lungelo.macrodime.engine.BodyScienceEngine.Input
import com.lungelo.macrodime.engine.BodyScienceEngine.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BodyScienceEngineTest {

    private val accuracy = 0.001

    // BMI

    @Test
    fun bmiUsesMetresSquared() {
        // 80 kg / (1.80 m)² = 80 / 3.24 = 24.6914
        assertEquals(24.6914, BodyScienceEngine.bmi(80.0, 180.0), 0.0001)
    }

    @Test
    fun bmiHandlesZeroHeightWithoutCrashing() {
        assertEquals(0.0, BodyScienceEngine.bmi(80.0, 0.0))
    }

    @Test
    fun bmiCategoryBoundaries() {
        assertEquals(BMICategory.Underweight, BMICategory.categoryFor(18.49))
        assertEquals(BMICategory.Healthy, BMICategory.categoryFor(18.5))
        assertEquals(BMICategory.Healthy, BMICategory.categoryFor(24.99))
        assertEquals(BMICategory.Overweight, BMICategory.categoryFor(25.0))
        assertEquals(BMICategory.Obese, BMICategory.categoryFor(30.0))
    }

    // BMR (Mifflin-St Jeor)

    @Test
    fun maleBMR() {
        // 10(80) + 6.25(180) - 5(30) + 5 = 800 + 1125 - 150 + 5 = 1780
        assertEquals(1780.0, BodyScienceEngine.basalMetabolicRate(80.0, 180.0, 30, BiologicalSex.Male), accuracy)
    }

    @Test
    fun femaleBMR() {
        // 10(65) + 6.25(165) - 5(30) - 161 = 650 + 1031.25 - 150 - 161 = 1370.25
        assertEquals(1370.25, BodyScienceEngine.basalMetabolicRate(65.0, 165.0, 30, BiologicalSex.Female), accuracy)
    }

    @Test
    fun sexConstantIsTheOnlyDifference() {
        val male = BodyScienceEngine.basalMetabolicRate(70.0, 170.0, 40, BiologicalSex.Male)
        val female = BodyScienceEngine.basalMetabolicRate(70.0, 170.0, 40, BiologicalSex.Female)
        assertEquals(166.0, male - female, accuracy) // +5 - (-161)
    }

    // TDEE

    @Test
    fun activityMultipliers() {
        assertEquals(1.200, ActivityLevel.Sedentary.multiplier, accuracy)
        assertEquals(1.375, ActivityLevel.LightlyActive.multiplier, accuracy)
        assertEquals(1.550, ActivityLevel.ModeratelyActive.multiplier, accuracy)
        assertEquals(1.725, ActivityLevel.VeryActive.multiplier, accuracy)
    }

    @Test
    fun tdee() {
        // 1780 × 1.55 = 2759
        assertEquals(2759.0, BodyScienceEngine.totalDailyEnergyExpenditure(1780.0, ActivityLevel.ModeratelyActive), accuracy)
    }

    // Full prescription

    @Test
    fun fatLossPrescription() {
        val result = BodyScienceEngine.prescribe(
            Input(80.0, 180.0, 30, BiologicalSex.Male, ActivityLevel.ModeratelyActive, FitnessGoal.FatLoss),
        )

        assertEquals(1780.0, result.bmr, accuracy)
        assertEquals(2759.0, result.tdee, accuracy)
        // 20% deficit: 2759 × 0.80 = 2207.2
        assertEquals(2207.2, result.targets.calories, accuracy)
        assertEquals(-551.8, result.calorieDelta, accuracy)
        assertTrue(result.isDeficit)

        // Protein at 2.0 g/kg = 160 g
        assertEquals(160.0, result.targets.protein, accuracy)
        // Fat at 25% of 2207.2 kcal ÷ 9 = 61.3111 g
        assertEquals(61.3111, result.targets.fat, 0.001)
        // Carbs take the remainder: (2207.2 - 640 - 551.8) ÷ 4 = 253.85 g
        assertEquals(253.85, result.targets.carbs, accuracy)

        assertTrue(result.adjustments.isEmpty(), "A textbook case should need no overrides")
    }

    @Test
    fun muscleGainPrescription() {
        val result = BodyScienceEngine.prescribe(
            Input(80.0, 180.0, 30, BiologicalSex.Male, ActivityLevel.LightlyActive, FitnessGoal.MuscleGain),
        )

        // 1780 × 1.375 = 2447.5, then ×1.08 = 2643.3
        assertEquals(2447.5, result.tdee, accuracy)
        assertEquals(2643.3, result.targets.calories, accuracy)
        assertTrue(!result.isDeficit)
        // Protein at 1.8 g/kg = 144 g
        assertEquals(144.0, result.targets.protein, accuracy)
        assertEquals(73.425, result.targets.fat, 0.001)
        assertEquals(351.61875, result.targets.carbs, 0.001)
    }

    /** The macro grams must always reconstruct the calorie target, or the rings disagree. */
    @Test
    fun macrosReconstructCalorieTarget() {
        var weight = 45.0
        while (weight <= 160.0) {
            for (goal in FitnessGoal.entries) {
                for (activity in ActivityLevel.entries) {
                    val result = BodyScienceEngine.prescribeUnchecked(
                        Input(weight, 172.0, 35, BiologicalSex.Female, activity, goal),
                    )
                    assertEquals(
                        result.targets.calories,
                        result.targets.caloriesFromMacros,
                        0.01,
                        "Macros must sum to the calorie target (weight $weight, $goal, $activity)",
                    )
                }
            }
            weight += 5
        }
    }

    /** No prescription may ever contain a negative macro, at any body size. */
    @Test
    fun noNegativeMacrosAcrossTheInputRange() {
        var weight = 25.0
        while (weight <= 350.0) {
            var height = 140.0
            while (height <= 210.0) {
                for (goal in FitnessGoal.entries) {
                    val targets = BodyScienceEngine.prescribeUnchecked(
                        Input(weight, height, 45, BiologicalSex.Male, ActivityLevel.Sedentary, goal),
                    ).targets
                    assertTrue(targets.protein >= 0)
                    assertTrue(targets.carbs >= 0)
                    assertTrue(targets.fat >= 0)
                }
                height += 10
            }
            weight += 25
        }
    }

    // Safety floor

    @Test
    fun calorieFloorAppliedForVerySmallTargets() {
        // BMR 826.5, TDEE 991.8; a 20% deficit would be 793.4, below the 1,200
        // kcal female floor.
        val result = BodyScienceEngine.prescribeUnchecked(
            Input(40.0, 150.0, 70, BiologicalSex.Female, ActivityLevel.Sedentary, FitnessGoal.FatLoss),
        )

        assertEquals(826.5, result.bmr, accuracy)
        assertEquals(1_200.0, result.targets.calories, accuracy)
        assertTrue(Adjustment.CalorieFloorApplied in result.adjustments)
        // The floor raises intake above maintenance-minus-20%: that is its point.
        assertTrue(result.targets.calories > result.tdee * 0.80)
    }

    @Test
    fun floorIsSexSpecific() {
        assertEquals(1_200.0, BiologicalSex.Female.minimumSafeCalories)
        assertEquals(1_500.0, BiologicalSex.Male.minimumSafeCalories)
    }

    // Clamping

    /**
     * A heavy person on a small target cannot have both 2 g/kg protein and 25%
     * fat. Protein yields, carbs floor at zero, and the override is reported.
     */
    @Test
    fun proteinIsClampedRatherThanProducingNegativeCarbs() {
        val split = BodyScienceEngine.macroSplit(1_200.0, 130.0, FitnessGoal.FatLoss)

        // Requested 260 g protein (1040 kcal) + 300 kcal fat = 1340 > 1200.
        assertEquals(225.0, split.macros.protein, accuracy)
        assertEquals(33.3333, split.macros.fat, 0.001)
        assertEquals(0.0, split.macros.carbs, accuracy)
        assertTrue(Adjustment.ProteinClamped in split.adjustments)
        assertEquals(1_200.0, split.macros.caloriesFromMacros, 0.01)
    }

    /** Regression guard: the fat floor must never *raise* fat above its 25% allocation. */
    @Test
    fun fatFloorNeverIncreasesFatAllocation() {
        var weight = 60.0
        while (weight <= 200.0) {
            var target = 1_200.0
            while (target <= 3_000.0) {
                val split = BodyScienceEngine.macroSplit(target, weight, FitnessGoal.FatLoss)
                val unclampedFatGrams = target * 0.25 / AtwaterFactor.FAT
                assertTrue(
                    split.macros.fat <= unclampedFatGrams + 0.001,
                    "Fat rose above its 25% share (weight $weight, target $target)",
                )
                target += 200
            }
            weight += 10
        }
    }

    // Validation

    @Test
    fun validationRejectsImplausibleInput() {
        val tooLight = Input(10.0, 170.0, 30, BiologicalSex.Male, ActivityLevel.Sedentary, FitnessGoal.FatLoss)
        assertEquals(ValidationError.WeightOutOfRange, tooLight.validationError)
        assertFailsWith<BodyScienceEngine.InvalidInputException> { BodyScienceEngine.prescribe(tooLight) }

        val tooShort = Input(70.0, 40.0, 30, BiologicalSex.Male, ActivityLevel.Sedentary, FitnessGoal.FatLoss)
        assertEquals(ValidationError.HeightOutOfRange, tooShort.validationError)

        val tooYoung = Input(70.0, 170.0, 9, BiologicalSex.Male, ActivityLevel.Sedentary, FitnessGoal.FatLoss)
        assertEquals(ValidationError.AgeOutOfRange, tooYoung.validationError)
    }

    @Test
    fun validInputPasses() {
        val input = Input(70.0, 170.0, 30, BiologicalSex.Female, ActivityLevel.LightlyActive, FitnessGoal.FatLoss)
        assertTrue(input.isValid)
        BodyScienceEngine.prescribe(input)
    }

    /** Not in the iOS suite: a NaN weight from a cleared text field must be refused, not prescribed. */
    @Test
    fun notANumberIsInvalid() {
        val input = Input(Double.NaN, 170.0, 30, BiologicalSex.Female, ActivityLevel.LightlyActive, FitnessGoal.FatLoss)
        assertEquals(ValidationError.WeightOutOfRange, input.validationError)
    }

    // Unit conversion

    @Test
    fun weightRoundTrip() {
        val kg = 82.5
        assertEquals(kg, UnitConversion.kilogramsFromPounds(UnitConversion.poundsFromKilograms(kg)), 0.0001)
    }

    @Test
    fun feetAndInchesCarryRatherThanShowingTwelveInches() {
        // 182.88 cm is exactly 6'0", so rounding must not yield 5' 12".
        val parts = UnitConversion.feetAndInches(182.88)
        assertEquals(6, parts.feet)
        assertEquals(0, parts.inches)

        val nearly = UnitConversion.feetAndInches(182.7)
        assertTrue(nearly.inches < 12)
    }

    @Test
    fun heightRoundTrip() {
        val cm = UnitConversion.centimetres(5, 11)
        val parts = UnitConversion.feetAndInches(cm)
        assertEquals(5, parts.feet)
        assertEquals(11, parts.inches)
    }
}
