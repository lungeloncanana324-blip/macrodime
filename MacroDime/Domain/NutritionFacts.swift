//
//  NutritionFacts.swift
//  MacroDime
//
//  A macro tuple with arithmetic. Kept as a value type so the engine can add,
//  subtract and scale meals without touching persistence or the UI.
//

import Foundation

/// Atwater factors — kilocalories per gram of each macronutrient.
enum AtwaterFactor {
    static let protein: Double = 4
    static let carbohydrate: Double = 4
    static let fat: Double = 9
}

/// Calories plus the three tracked macronutrients, in grams.
struct NutritionFacts: Hashable, Codable, Sendable {
    var calories: Double
    var protein: Double
    var carbs: Double
    var fat: Double

    init(calories: Double = 0, protein: Double = 0, carbs: Double = 0, fat: Double = 0) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fat = fat
    }

    static let zero = NutritionFacts()

    /// Calories implied by the macro grams alone.
    ///
    /// Real label data rarely matches this exactly (fibre, sugar alcohols and
    /// rounding all leak), so it is used for sanity checks and never as a
    /// substitute for the measured `calories` value.
    var caloriesFromMacros: Double {
        protein * AtwaterFactor.protein
            + carbs * AtwaterFactor.carbohydrate
            + fat * AtwaterFactor.fat
    }

    /// Share of total calories contributed by each macro. Returns `zero` for an
    /// empty meal rather than dividing by zero.
    var macroShares: (protein: Double, carbs: Double, fat: Double) {
        let total = caloriesFromMacros
        guard total > 0 else { return (0, 0, 0) }
        return (
            protein * AtwaterFactor.protein / total,
            carbs * AtwaterFactor.carbohydrate / total,
            fat * AtwaterFactor.fat / total
        )
    }

    /// Multiplies every field. Used to turn "per serving" into "per portion".
    func scaled(by factor: Double) -> NutritionFacts {
        NutritionFacts(
            calories: calories * factor,
            protein: protein * factor,
            carbs: carbs * factor,
            fat: fat * factor
        )
    }

    /// Rounds every field to whole numbers for display. Never use the result
    /// for further arithmetic — rounding error compounds across a week of meals.
    var rounded: NutritionFacts {
        NutritionFacts(
            calories: calories.rounded(),
            protein: protein.rounded(),
            carbs: carbs.rounded(),
            fat: fat.rounded()
        )
    }

    static func + (lhs: NutritionFacts, rhs: NutritionFacts) -> NutritionFacts {
        NutritionFacts(
            calories: lhs.calories + rhs.calories,
            protein: lhs.protein + rhs.protein,
            carbs: lhs.carbs + rhs.carbs,
            fat: lhs.fat + rhs.fat
        )
    }

    static func - (lhs: NutritionFacts, rhs: NutritionFacts) -> NutritionFacts {
        NutritionFacts(
            calories: lhs.calories - rhs.calories,
            protein: lhs.protein - rhs.protein,
            carbs: lhs.carbs - rhs.carbs,
            fat: lhs.fat - rhs.fat
        )
    }

    static func += (lhs: inout NutritionFacts, rhs: NutritionFacts) {
        lhs = lhs + rhs
    }
}

extension Sequence where Element == NutritionFacts {
    /// Sums a sequence of macro tuples.
    var total: NutritionFacts { reduce(.zero, +) }
}

// MARK: - Macro Drift

/// How far a substituted meal has moved from the meal it replaced, expressed as
/// a *relative* error per field. This is the quantity the 10% swap tolerance is
/// measured against.
struct MacroDrift: Hashable, Sendable {
    let calories: Double
    let protein: Double
    let carbs: Double
    let fat: Double

    /// Below these thresholds a macro is treated as trace, and drift is judged
    /// on an absolute gram/kcal difference instead of a percentage. Without
    /// this, a meal with 1 g of fat fails any percentage test the moment the
    /// replacement carries 2 g.
    private enum TraceFloor {
        static let calories: Double = 60
        static let grams: Double = 8
    }

    /// Relative error, falling back to an absolute-difference test scaled
    /// against the trace floor when the baseline value is too small for a
    /// percentage to be meaningful.
    private static func drift(from baseline: Double, to candidate: Double, floor: Double) -> Double {
        let delta = abs(candidate - baseline)
        if baseline < floor {
            // Treat the floor itself as the denominator: a 2 g move on a 1 g
            // baseline scores 0.25, not 200%.
            return delta / floor
        }
        return delta / baseline
    }

    init(baseline: NutritionFacts, candidate: NutritionFacts) {
        calories = Self.drift(from: baseline.calories, to: candidate.calories, floor: TraceFloor.calories)
        protein = Self.drift(from: baseline.protein, to: candidate.protein, floor: TraceFloor.grams)
        carbs = Self.drift(from: baseline.carbs, to: candidate.carbs, floor: TraceFloor.grams)
        fat = Self.drift(from: baseline.fat, to: candidate.fat, floor: TraceFloor.grams)
    }

    /// The single worst field. A swap is accepted only if this is within tolerance.
    var worst: Double { max(calories, max(protein, max(carbs, fat))) }

    /// Average drift across the four fields, used to rank otherwise-acceptable
    /// candidates against each other.
    var mean: Double { (calories + protein + carbs + fat) / 4 }

    func isWithin(_ tolerance: Double) -> Bool { worst <= tolerance }
}
