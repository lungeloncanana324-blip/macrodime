//
//  BodyScienceEngine.swift
//  MacroDime
//
//  Pure Swift. No SwiftData, no SwiftUI, no Foundation beyond `Foundation`
//  itself. Every function is static and deterministic, so the whole engine is
//  testable with plain XCTest assertions and no container setup.
//
//  Calculation chain:
//    BMR (Mifflin-St Jeor) → TDEE (activity multiplier) → target calories
//    (goal multiplier) → protein (g/kg body weight) → fat (25% of calories)
//    → carbohydrate (whatever calories remain).
//

import Foundation

// MARK: - Engine

struct BodyScienceEngine {

    // MARK: Tunable constants

    /// Every magic number in the prescription lives here, so the science can be
    /// adjusted in one place and the tests can assert against the same source.
    enum Constants {
        /// Share of *target* calories allocated to dietary fat.
        static let fatCalorieShare: Double = 0.25
        /// Fat is squeezed no lower than this before protein is clamped —
        /// below roughly 20% of calories, hormonal and satiety costs start to
        /// outweigh the benefit of freeing up calories.
        static let minimumFatCalorieShare: Double = 0.20
        /// Absolute floor on dietary fat regardless of body size, in g/kg.
        static let minimumFatGramsPerKilogram: Double = 0.5
        /// Plausible input bounds. Values outside these are rejected rather
        /// than silently producing a nonsense prescription.
        static let weightRangeKg: ClosedRange<Double> = 25...350
        static let heightRangeCm: ClosedRange<Double> = 90...250
        static let ageRange: ClosedRange<Int> = 13...100
    }

    // MARK: Input

    /// Everything the prescription needs, already normalised to metric.
    /// Conversion from lb/ft-in happens at the UI boundary in `UnitConversion`.
    struct Input: Hashable, Sendable {
        var weightKg: Double
        var heightCm: Double
        var age: Int
        var sex: BiologicalSex
        var activity: ActivityLevel
        var goal: FitnessGoal

        init(
            weightKg: Double,
            heightCm: Double,
            age: Int,
            sex: BiologicalSex,
            activity: ActivityLevel,
            goal: FitnessGoal
        ) {
            self.weightKg = weightKg
            self.heightCm = heightCm
            self.age = age
            self.sex = sex
            self.activity = activity
            self.goal = goal
        }

        /// `nil` when the input is usable, otherwise the first problem found.
        var validationError: ValidationError? {
            if !Constants.weightRangeKg.contains(weightKg) { return .weightOutOfRange }
            if !Constants.heightRangeCm.contains(heightCm) { return .heightOutOfRange }
            if !Constants.ageRange.contains(age) { return .ageOutOfRange }
            return nil
        }

        var isValid: Bool { validationError == nil }
    }

    enum ValidationError: String, Error, Sendable {
        case weightOutOfRange
        case heightOutOfRange
        case ageOutOfRange

        var message: String {
            switch self {
            case .weightOutOfRange: "Enter a weight between 25 kg and 350 kg."
            case .heightOutOfRange: "Enter a height between 90 cm and 250 cm."
            case .ageOutOfRange: "This app is designed for ages 13 to 100."
            }
        }
    }

    // MARK: Output

    /// Notes attached to a prescription when the engine had to override the
    /// textbook formula. Surfaced in the UI so the numbers are never silently
    /// different from what the stated rules would produce.
    enum Adjustment: String, Hashable, Sendable, Identifiable {
        case calorieFloorApplied
        case fatReducedToFitProtein
        case proteinClamped

        var id: String { rawValue }

        var message: String {
            switch self {
            case .calorieFloorApplied:
                "Your deficit was raised to the minimum safe intake for your sex."
            case .fatReducedToFitProtein:
                "Fat was trimmed toward 20% of calories to fit your protein target."
            case .proteinClamped:
                "Protein was reduced to fit inside your calorie target."
            }
        }
    }

    /// The complete prescription.
    struct Prescription: Hashable, Sendable {
        let bmi: Double
        let bmiCategory: BMICategory
        let bmr: Double
        let tdee: Double
        /// Signed difference from TDEE: negative for a deficit, positive for a surplus.
        let calorieDelta: Double
        /// Daily targets. `calories` is the prescribed intake; the macro grams
        /// always sum (via Atwater) to within a rounding error of it.
        let targets: NutritionFacts
        let adjustments: [Adjustment]

        var isDeficit: Bool { calorieDelta < 0 }

        /// Protein as a share of body weight, recomputed after any clamping so
        /// the UI reports what was actually prescribed, not what was requested.
        func proteinGramsPerKilogram(weightKg: Double) -> Double {
            guard weightKg > 0 else { return 0 }
            return targets.protein / weightKg
        }
    }

    /// Standard WHO adult BMI bands. Presented with the caveat that BMI ignores
    /// body composition — this app tracks waist and photos precisely because of
    /// that, so the category is never used to drive the prescription.
    enum BMICategory: String, Hashable, Sendable, CaseIterable {
        case underweight
        case healthy
        case overweight
        case obese

        var displayName: String {
            switch self {
            case .underweight: "Underweight"
            case .healthy: "Healthy range"
            case .overweight: "Overweight"
            case .obese: "Obese"
            }
        }

        static func category(for bmi: Double) -> BMICategory {
            switch bmi {
            case ..<18.5: .underweight
            case 18.5..<25: .healthy
            case 25..<30: .overweight
            default: .obese
            }
        }
    }

    // MARK: - Individual calculations
    //
    // Exposed separately from `prescribe(for:)` so each formula can be unit
    // tested in isolation and reused piecemeal by the UI.

    /// Body Mass Index: `weight_kg / height_m²`.
    static func bmi(weightKg: Double, heightCm: Double) -> Double {
        guard heightCm > 0 else { return 0 }
        let heightM = heightCm / 100
        return weightKg / (heightM * heightM)
    }

    /// Basal Metabolic Rate, Mifflin-St Jeor:
    /// `10 × kg + 6.25 × cm − 5 × age + constant` (+5 male, −161 female).
    static func basalMetabolicRate(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        sex: BiologicalSex
    ) -> Double {
        (10 * weightKg) + (6.25 * heightCm) - (5 * Double(age)) + sex.mifflinConstant
    }

    /// Total Daily Energy Expenditure: `BMR × activity multiplier`.
    static func totalDailyEnergyExpenditure(bmr: Double, activity: ActivityLevel) -> Double {
        bmr * activity.multiplier
    }

    /// Goal-adjusted intake, floored at the minimum safe intake for the sex.
    /// Returns the target and whether the floor had to be applied.
    static func targetCalories(
        tdee: Double,
        goal: FitnessGoal,
        sex: BiologicalSex
    ) -> (calories: Double, flooredForSafety: Bool) {
        let raw = tdee * goal.calorieMultiplier
        let floor = sex.minimumSafeCalories
        return raw < floor ? (floor, true) : (raw, false)
    }

    // MARK: - Macro split

    /// Splits a calorie target into grams of protein, fat and carbohydrate.
    ///
    /// Order of operations matters, and follows the priority a coach would use:
    ///
    /// 1. **Protein is prescribed first** from body weight (2.0 g/kg cutting,
    ///    1.8 g/kg gaining) because it is the macro with a hard physiological
    ///    requirement.
    /// 2. **Fat takes 25%** of the calorie target.
    /// 3. **Carbohydrate absorbs the remainder.**
    ///
    /// Steps 1 and 2 can collide: a heavy person in a steep deficit can need
    /// more protein + fat calories than the target allows, which would leave
    /// carbohydrate negative. Rather than emit a nonsense plan, the engine
    /// squeezes fat toward its 20% floor, and only if that is still not enough
    /// clamps protein — reporting each override in `adjustments`.
    static func macroSplit(
        targetCalories: Double,
        weightKg: Double,
        goal: FitnessGoal
    ) -> (macros: NutritionFacts, adjustments: [Adjustment]) {
        var adjustments: [Adjustment] = []

        var proteinGrams = weightKg * goal.proteinGramsPerKilogram
        var fatGrams = (targetCalories * Constants.fatCalorieShare) / AtwaterFactor.fat

        var proteinCalories = proteinGrams * AtwaterFactor.protein
        var fatCalories = fatGrams * AtwaterFactor.fat

        // Step 1: if protein + fat overruns the target, trim fat toward its floor.
        //
        // The floor is the stricter of "20% of calories" and "0.5 g/kg", then
        // capped at the original allocation. Without that cap, a heavy person
        // on a small target — whose 0.5 g/kg floor is *above* their 25% share —
        // would see this branch silently raise their fat instead of lowering
        // it. When the floor binds that hard there is nothing to squeeze, and
        // the protein clamp below is the correct lever.
        if proteinCalories + fatCalories > targetCalories {
            let fatFloorCalories = min(
                max(
                    targetCalories * Constants.minimumFatCalorieShare,
                    weightKg * Constants.minimumFatGramsPerKilogram * AtwaterFactor.fat
                ),
                fatCalories
            )
            let roomForFat = max(targetCalories - proteinCalories, 0)
            let newFatCalories = max(min(fatCalories, roomForFat), fatFloorCalories)

            if newFatCalories < fatCalories {
                fatCalories = newFatCalories
                fatGrams = fatCalories / AtwaterFactor.fat
                adjustments.append(.fatReducedToFitProtein)
            }
        }

        // Step 2: still over budget? Protein yields the remainder.
        if proteinCalories + fatCalories > targetCalories {
            proteinCalories = max(targetCalories - fatCalories, 0)
            proteinGrams = proteinCalories / AtwaterFactor.protein
            adjustments.append(.proteinClamped)
        }

        // Step 3: carbohydrate absorbs what is left. `max(0,)` is belt and
        // braces — the two clamps above already guarantee a non-negative
        // remainder — but it keeps the invariant local and obvious.
        let carbCalories = max(targetCalories - proteinCalories - fatCalories, 0)
        let carbGrams = carbCalories / AtwaterFactor.carbohydrate

        let macros = NutritionFacts(
            calories: targetCalories,
            protein: proteinGrams,
            carbs: carbGrams,
            fat: fatGrams
        )
        return (macros, adjustments)
    }

    // MARK: - Full prescription

    /// Runs the whole chain. Throws rather than guessing when the input is
    /// outside plausible human range.
    static func prescribe(for input: Input) throws -> Prescription {
        if let error = input.validationError { throw error }
        return prescribeUnchecked(for: input)
    }

    /// Non-throwing variant for live preview, where the user is mid-typing and
    /// transient nonsense is expected. Callers must gate on `input.isValid`
    /// before showing the result as a real prescription.
    static func prescribeUnchecked(for input: Input) -> Prescription {
        let bmiValue = bmi(weightKg: input.weightKg, heightCm: input.heightCm)

        let bmrValue = basalMetabolicRate(
            weightKg: input.weightKg,
            heightCm: input.heightCm,
            age: input.age,
            sex: input.sex
        )

        let tdeeValue = totalDailyEnergyExpenditure(bmr: bmrValue, activity: input.activity)

        let (calories, floored) = targetCalories(
            tdee: tdeeValue,
            goal: input.goal,
            sex: input.sex
        )

        let split = macroSplit(
            targetCalories: calories,
            weightKg: input.weightKg,
            goal: input.goal
        )

        var adjustments = split.adjustments
        if floored { adjustments.insert(.calorieFloorApplied, at: 0) }

        return Prescription(
            bmi: bmiValue,
            bmiCategory: BMICategory.category(for: bmiValue),
            bmr: bmrValue,
            tdee: tdeeValue,
            calorieDelta: calories - tdeeValue,
            targets: split.macros,
            adjustments: adjustments
        )
    }
}
