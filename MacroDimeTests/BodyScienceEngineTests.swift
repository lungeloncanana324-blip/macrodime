//
//  BodyScienceEngineTests.swift
//  MacroDimeTests
//
//  The engine is pure, so every case here runs without a ModelContainer.
//  Expected values are worked by hand from the stated formulae, not copied from
//  a previous run of the code — a test that only asserts "same as last time"
//  cannot catch a wrong formula.
//

import XCTest
@testable import MacroDime

final class BodyScienceEngineTests: XCTestCase {

    private let accuracy = 0.001

    // MARK: BMI

    func testBMIUsesMetresSquared() {
        // 80 kg / (1.80 m)² = 80 / 3.24 = 24.6914
        XCTAssertEqual(
            BodyScienceEngine.bmi(weightKg: 80, heightCm: 180),
            24.6914,
            accuracy: 0.0001
        )
    }

    func testBMIHandlesZeroHeightWithoutCrashing() {
        XCTAssertEqual(BodyScienceEngine.bmi(weightKg: 80, heightCm: 0), 0)
    }

    func testBMICategoryBoundaries() {
        XCTAssertEqual(BodyScienceEngine.BMICategory.category(for: 18.49), .underweight)
        XCTAssertEqual(BodyScienceEngine.BMICategory.category(for: 18.5), .healthy)
        XCTAssertEqual(BodyScienceEngine.BMICategory.category(for: 24.99), .healthy)
        XCTAssertEqual(BodyScienceEngine.BMICategory.category(for: 25), .overweight)
        XCTAssertEqual(BodyScienceEngine.BMICategory.category(for: 30), .obese)
    }

    // MARK: BMR — Mifflin-St Jeor

    func testMaleBMR() {
        // 10(80) + 6.25(180) − 5(30) + 5 = 800 + 1125 − 150 + 5 = 1780
        XCTAssertEqual(
            BodyScienceEngine.basalMetabolicRate(weightKg: 80, heightCm: 180, age: 30, sex: .male),
            1780,
            accuracy: accuracy
        )
    }

    func testFemaleBMR() {
        // 10(65) + 6.25(165) − 5(30) − 161 = 650 + 1031.25 − 150 − 161 = 1370.25
        XCTAssertEqual(
            BodyScienceEngine.basalMetabolicRate(weightKg: 65, heightCm: 165, age: 30, sex: .female),
            1370.25,
            accuracy: accuracy
        )
    }

    func testSexConstantIsTheOnlyDifference() {
        let male = BodyScienceEngine.basalMetabolicRate(weightKg: 70, heightCm: 170, age: 40, sex: .male)
        let female = BodyScienceEngine.basalMetabolicRate(weightKg: 70, heightCm: 170, age: 40, sex: .female)
        XCTAssertEqual(male - female, 166, accuracy: accuracy)  // +5 − (−161)
    }

    // MARK: TDEE

    func testActivityMultipliers() {
        XCTAssertEqual(ActivityLevel.sedentary.multiplier, 1.200, accuracy: accuracy)
        XCTAssertEqual(ActivityLevel.lightlyActive.multiplier, 1.375, accuracy: accuracy)
        XCTAssertEqual(ActivityLevel.moderatelyActive.multiplier, 1.550, accuracy: accuracy)
        XCTAssertEqual(ActivityLevel.veryActive.multiplier, 1.725, accuracy: accuracy)
    }

    func testTDEE() {
        // 1780 × 1.55 = 2759
        XCTAssertEqual(
            BodyScienceEngine.totalDailyEnergyExpenditure(bmr: 1780, activity: .moderatelyActive),
            2759,
            accuracy: accuracy
        )
    }

    // MARK: Full prescription

    func testFatLossPrescription() throws {
        let input = BodyScienceEngine.Input(
            weightKg: 80, heightCm: 180, age: 30,
            sex: .male, activity: .moderatelyActive, goal: .fatLoss
        )
        let result = try BodyScienceEngine.prescribe(for: input)

        XCTAssertEqual(result.bmr, 1780, accuracy: accuracy)
        XCTAssertEqual(result.tdee, 2759, accuracy: accuracy)
        // 20% deficit: 2759 × 0.80 = 2207.2
        XCTAssertEqual(result.targets.calories, 2207.2, accuracy: accuracy)
        XCTAssertEqual(result.calorieDelta, -551.8, accuracy: accuracy)
        XCTAssertTrue(result.isDeficit)

        // Protein at 2.0 g/kg = 160 g
        XCTAssertEqual(result.targets.protein, 160, accuracy: accuracy)
        // Fat at 25% of 2207.2 kcal ÷ 9 = 61.3111 g
        XCTAssertEqual(result.targets.fat, 61.3111, accuracy: 0.001)
        // Carbs take the remainder: (2207.2 − 640 − 551.8) ÷ 4 = 253.85 g
        XCTAssertEqual(result.targets.carbs, 253.85, accuracy: accuracy)

        XCTAssertTrue(result.adjustments.isEmpty, "A textbook case should need no overrides")
    }

    func testMuscleGainPrescription() throws {
        let input = BodyScienceEngine.Input(
            weightKg: 80, heightCm: 180, age: 30,
            sex: .male, activity: .lightlyActive, goal: .muscleGain
        )
        let result = try BodyScienceEngine.prescribe(for: input)

        // 1780 × 1.375 = 2447.5, then ×1.08 = 2643.3
        XCTAssertEqual(result.tdee, 2447.5, accuracy: accuracy)
        XCTAssertEqual(result.targets.calories, 2643.3, accuracy: accuracy)
        XCTAssertFalse(result.isDeficit)
        // Protein at 1.8 g/kg = 144 g
        XCTAssertEqual(result.targets.protein, 144, accuracy: accuracy)
        XCTAssertEqual(result.targets.fat, 73.425, accuracy: 0.001)
        XCTAssertEqual(result.targets.carbs, 351.61875, accuracy: 0.001)
    }

    /// The macro grams must always reconstruct the calorie target via Atwater.
    /// If this drifts, the rings on the dashboard stop agreeing with each other.
    func testMacrosReconstructCalorieTarget() throws {
        for weight in stride(from: 45.0, through: 160.0, by: 5) {
            for goal in FitnessGoal.allCases {
                for activity in ActivityLevel.allCases {
                    let input = BodyScienceEngine.Input(
                        weightKg: weight, heightCm: 172, age: 35,
                        sex: .female, activity: activity, goal: goal
                    )
                    let result = BodyScienceEngine.prescribeUnchecked(for: input)
                    XCTAssertEqual(
                        result.targets.caloriesFromMacros,
                        result.targets.calories,
                        accuracy: 0.01,
                        "Macros must sum to the calorie target (weight \(weight), \(goal), \(activity))"
                    )
                }
            }
        }
    }

    /// No prescription may ever contain a negative macro, at any body size.
    func testNoNegativeMacrosAcrossTheInputRange() {
        for weight in stride(from: 25.0, through: 350.0, by: 25) {
            for height in stride(from: 140.0, through: 210.0, by: 10) {
                for goal in FitnessGoal.allCases {
                    let input = BodyScienceEngine.Input(
                        weightKg: weight, heightCm: height, age: 45,
                        sex: .male, activity: .sedentary, goal: goal
                    )
                    let targets = BodyScienceEngine.prescribeUnchecked(for: input).targets
                    XCTAssertGreaterThanOrEqual(targets.protein, 0)
                    XCTAssertGreaterThanOrEqual(targets.carbs, 0)
                    XCTAssertGreaterThanOrEqual(targets.fat, 0)
                }
            }
        }
    }

    // MARK: Safety floor

    func testCalorieFloorAppliedForVerySmallTargets() {
        // BMR 826.5 → TDEE 991.8 → 20% deficit would be 793.4, below the
        // 1,200 kcal female floor.
        let input = BodyScienceEngine.Input(
            weightKg: 40, heightCm: 150, age: 70,
            sex: .female, activity: .sedentary, goal: .fatLoss
        )
        let result = BodyScienceEngine.prescribeUnchecked(for: input)

        XCTAssertEqual(result.bmr, 826.5, accuracy: accuracy)
        XCTAssertEqual(result.targets.calories, 1_200, accuracy: accuracy)
        XCTAssertTrue(result.adjustments.contains(.calorieFloorApplied))
        // The floor raises intake above maintenance-minus-20%, so the deficit
        // is smaller than requested — that is the point of the floor.
        XCTAssertGreaterThan(result.targets.calories, result.tdee * 0.80)
    }

    func testFloorIsSexSpecific() {
        XCTAssertEqual(BiologicalSex.female.minimumSafeCalories, 1_200)
        XCTAssertEqual(BiologicalSex.male.minimumSafeCalories, 1_500)
    }

    // MARK: Clamping

    /// A heavy person on a small calorie target cannot have both 2 g/kg protein
    /// and 25% fat. Protein yields, carbs floor at zero, and the override is
    /// reported rather than applied silently.
    func testProteinIsClampedRatherThanProducingNegativeCarbs() {
        let split = BodyScienceEngine.macroSplit(
            targetCalories: 1_200,
            weightKg: 130,
            goal: .fatLoss
        )

        // Requested 260 g protein (1040 kcal) + 300 kcal fat = 1340 > 1200.
        XCTAssertEqual(split.macros.protein, 225, accuracy: accuracy)
        XCTAssertEqual(split.macros.fat, 33.3333, accuracy: 0.001)
        XCTAssertEqual(split.macros.carbs, 0, accuracy: accuracy)
        XCTAssertTrue(split.adjustments.contains(.proteinClamped))
        XCTAssertEqual(split.macros.caloriesFromMacros, 1_200, accuracy: 0.01)
    }

    /// Regression guard. The fat floor is the stricter of "20% of calories" and
    /// "0.5 g/kg", but it must never *raise* fat above its 25% allocation —
    /// which is exactly what an uncapped `max()` of the two floors did.
    func testFatFloorNeverIncreasesFatAllocation() {
        for weight in stride(from: 60.0, through: 200.0, by: 10) {
            for target in stride(from: 1_200.0, through: 3_000.0, by: 200) {
                let split = BodyScienceEngine.macroSplit(
                    targetCalories: target,
                    weightKg: weight,
                    goal: .fatLoss
                )
                let unclampedFatGrams = target * 0.25 / AtwaterFactor.fat
                XCTAssertLessThanOrEqual(
                    split.macros.fat,
                    unclampedFatGrams + 0.001,
                    "Fat rose above its 25% share (weight \(weight), target \(target))"
                )
            }
        }
    }

    // MARK: Validation

    func testValidationRejectsImplausibleInput() {
        let tooLight = BodyScienceEngine.Input(
            weightKg: 10, heightCm: 170, age: 30,
            sex: .male, activity: .sedentary, goal: .fatLoss
        )
        XCTAssertEqual(tooLight.validationError, .weightOutOfRange)
        XCTAssertThrowsError(try BodyScienceEngine.prescribe(for: tooLight))

        let tooShort = BodyScienceEngine.Input(
            weightKg: 70, heightCm: 40, age: 30,
            sex: .male, activity: .sedentary, goal: .fatLoss
        )
        XCTAssertEqual(tooShort.validationError, .heightOutOfRange)

        let tooYoung = BodyScienceEngine.Input(
            weightKg: 70, heightCm: 170, age: 9,
            sex: .male, activity: .sedentary, goal: .fatLoss
        )
        XCTAssertEqual(tooYoung.validationError, .ageOutOfRange)
    }

    func testValidInputPasses() {
        let input = BodyScienceEngine.Input(
            weightKg: 70, heightCm: 170, age: 30,
            sex: .female, activity: .lightlyActive, goal: .fatLoss
        )
        XCTAssertTrue(input.isValid)
        XCTAssertNoThrow(try BodyScienceEngine.prescribe(for: input))
    }

    // MARK: Unit conversion

    func testWeightRoundTrip() {
        let kg = 82.5
        XCTAssertEqual(
            UnitConversion.kilograms(fromPounds: UnitConversion.pounds(fromKilograms: kg)),
            kg,
            accuracy: 0.0001
        )
    }

    func testFeetAndInchesCarryRatherThanShowingTwelveInches() {
        // 182.88 cm is exactly 6'0" — rounding must not yield 5' 12".
        let parts = UnitConversion.feetAndInches(fromCentimetres: 182.88)
        XCTAssertEqual(parts.feet, 6)
        XCTAssertEqual(parts.inches, 0)

        // A value that rounds up to the next foot.
        let nearly = UnitConversion.feetAndInches(fromCentimetres: 182.7)
        XCTAssertLessThan(nearly.inches, 12)
    }

    func testHeightRoundTrip() {
        let cm = UnitConversion.centimetres(fromFeet: 5, inches: 11)
        let parts = UnitConversion.feetAndInches(fromCentimetres: cm)
        XCTAssertEqual(parts.feet, 5)
        XCTAssertEqual(parts.inches, 11)
    }
}
