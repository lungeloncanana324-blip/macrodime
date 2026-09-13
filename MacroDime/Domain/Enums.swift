//
//  Enums.swift
//  MacroDime
//
//  Domain vocabulary shared by the science engine, the food engine and the UI.
//  Every case is `String`-backed so it can be persisted in SwiftData as a raw
//  value. Raw values survive schema migration far better than synthesized
//  `Codable` enum blobs, and unlike blobs they can be used inside `#Predicate`.
//

import Foundation

// MARK: - Biological Sex

/// Biological sex is captured for one reason only: it is a required term of the
/// Mifflin-St Jeor equation. It is not used anywhere else in the app.
enum BiologicalSex: String, Codable, CaseIterable, Identifiable, Sendable {
    case male
    case female

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .male: "Male"
        case .female: "Female"
        }
    }

    /// The trailing constant of Mifflin-St Jeor: `+5` for males, `-161` for females.
    var mifflinConstant: Double {
        switch self {
        case .male: 5
        case .female: -161
        }
    }

    /// Conservative lower bound for sustained daily intake without clinical
    /// supervision. The engine refuses to prescribe a target below this.
    var minimumSafeCalories: Double {
        switch self {
        case .male: 1_500
        case .female: 1_200
        }
    }
}

// MARK: - Goal

/// The user's primary body-composition goal. Drives both the calorie adjustment
/// applied to TDEE and the protein prescription.
enum FitnessGoal: String, Codable, CaseIterable, Identifiable, Sendable {
    case fatLoss
    case muscleGain

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .fatLoss: "Fat Loss"
        case .muscleGain: "Muscle Gain"
        }
    }

    var subtitle: String {
        switch self {
        case .fatLoss: "20% deficit, protein held high to protect lean mass"
        case .muscleGain: "8% surplus, lean gaining to limit fat accrual"
        }
    }

    var systemImage: String {
        switch self {
        case .fatLoss: "arrow.down.right.circle.fill"
        case .muscleGain: "arrow.up.right.circle.fill"
        }
    }

    /// Multiplier applied to TDEE to reach the daily calorie target.
    var calorieMultiplier: Double {
        switch self {
        case .fatLoss: 0.80
        case .muscleGain: 1.08
        }
    }

    /// Protein prescription in grams per kilogram of current body weight.
    var proteinGramsPerKilogram: Double {
        switch self {
        case .fatLoss: 2.0
        case .muscleGain: 1.8
        }
    }
}

// MARK: - Activity Level

/// Standard activity multipliers applied to BMR to reach TDEE.
enum ActivityLevel: String, Codable, CaseIterable, Identifiable, Sendable {
    case sedentary
    case lightlyActive
    case moderatelyActive
    case veryActive

    var id: String { rawValue }

    var multiplier: Double {
        switch self {
        case .sedentary: 1.200
        case .lightlyActive: 1.375
        case .moderatelyActive: 1.550
        case .veryActive: 1.725
        }
    }

    var displayName: String {
        switch self {
        case .sedentary: "Sedentary"
        case .lightlyActive: "Lightly Active"
        case .moderatelyActive: "Moderately Active"
        case .veryActive: "Very Active"
        }
    }

    var subtitle: String {
        switch self {
        case .sedentary: "Desk job, little or no exercise"
        case .lightlyActive: "Light exercise 1-3 days per week"
        case .moderatelyActive: "Moderate exercise 3-5 days per week"
        case .veryActive: "Hard exercise 6-7 days per week"
        }
    }

    var systemImage: String {
        switch self {
        case .sedentary: "chair.lounge.fill"
        case .lightlyActive: "figure.walk"
        case .moderatelyActive: "figure.run"
        case .veryActive: "figure.strengthtraining.traditional"
        }
    }
}

// MARK: - Budget Tier

/// The financial constraint the meal plan must respect.
///
/// `rank` exists so the swap engine can ask "is this candidate at or below the
/// ceiling I was given?" without hard-coding case comparisons — adding a third
/// tier later requires no change to the engine.
enum BudgetTier: String, Codable, CaseIterable, Identifiable, Sendable, Comparable {
    /// Cheap staples: oats, eggs, lentils, canned tuna, chicken thighs, frozen veg.
    case strict
    /// Fresh cuts, specialty proteins, organic produce.
    case moderate

    var id: String { rawValue }

    var rank: Int {
        switch self {
        case .strict: 0
        case .moderate: 1
        }
    }

    static func < (lhs: BudgetTier, rhs: BudgetTier) -> Bool { lhs.rank < rhs.rank }

    var displayName: String {
        switch self {
        case .strict: "Strict Budget"
        case .moderate: "Flexible"
        }
    }

    /// Price-level chip shown in the UI.
    var priceSymbol: String {
        switch self {
        case .strict: "$"
        case .moderate: "$$"
        }
    }

    var subtitle: String {
        switch self {
        case .strict: "Oats, eggs, lentils, canned tuna, chicken thighs, frozen veg"
        case .moderate: "Fresh cuts, specialty proteins, organic produce"
        }
    }

    /// Starting daily food allowance used to pre-fill onboarding. The user can
    /// override it; nothing in the engine depends on this staying the default.
    var defaultDailyAllowance: Double {
        switch self {
        case .strict: 9.00
        case .moderate: 20.00
        }
    }
}

// MARK: - Meal Slot

enum MealSlot: String, Codable, CaseIterable, Identifiable, Sendable {
    case breakfast
    case lunch
    case dinner
    case snack

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .breakfast: "Breakfast"
        case .lunch: "Lunch"
        case .dinner: "Dinner"
        case .snack: "Snacks"
        }
    }

    var systemImage: String {
        switch self {
        case .breakfast: "sunrise.fill"
        case .lunch: "sun.max.fill"
        case .dinner: "moon.stars.fill"
        case .snack: "leaf.fill"
        }
    }

    /// Display ordering, and the stable sort key for persisted meals.
    var sortOrder: Int {
        switch self {
        case .breakfast: 0
        case .lunch: 1
        case .dinner: 2
        case .snack: 3
        }
    }

    /// Rough share of the daily calorie target, used to label an empty planner
    /// slot with a sensible calorie budget.
    var defaultCalorieShare: Double {
        switch self {
        case .breakfast: 0.25
        case .lunch: 0.30
        case .dinner: 0.35
        case .snack: 0.10
        }
    }
}

// MARK: - Grocery Section

/// Supermarket aisle grouping for the smart grocery list.
enum GrocerySection: String, Codable, CaseIterable, Identifiable, Sendable {
    case produce
    case meatAndSeafood
    case pantry
    case frozen
    case dairy

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .produce: "Produce"
        case .meatAndSeafood: "Meat & Seafood"
        case .pantry: "Pantry"
        case .frozen: "Frozen"
        case .dairy: "Dairy & Eggs"
        }
    }

    var systemImage: String {
        switch self {
        case .produce: "carrot.fill"
        case .meatAndSeafood: "fish.fill"
        case .pantry: "shippingbox.fill"
        case .frozen: "snowflake"
        case .dairy: "takeoutbag.and.cup.and.straw.fill"
        }
    }

    /// Walk order through a typical store, so the list reads top-to-bottom the
    /// way the user actually shops.
    var aisleOrder: Int {
        switch self {
        case .produce: 0
        case .meatAndSeafood: 1
        case .dairy: 2
        case .pantry: 3
        case .frozen: 4
        }
    }
}

// MARK: - Food Category

/// The nutritional role an ingredient plays in a meal.
///
/// The swap engine only ever substitutes *within* a category, which is what
/// keeps "Salmon → Canned Tuna" sensible and "Salmon → Rolled Oats" impossible
/// even though the two could be macro-matched on paper.
enum FoodCategory: String, Codable, CaseIterable, Identifiable, Sendable {
    case proteinAnchor
    case carbBase
    case fatSource
    case vegetable
    case fruit
    case dairy
    case condiment

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .proteinAnchor: "Protein"
        case .carbBase: "Carbs"
        case .fatSource: "Fats"
        case .vegetable: "Vegetables"
        case .fruit: "Fruit"
        case .dairy: "Dairy"
        case .condiment: "Condiments"
        }
    }
}

// MARK: - Measurement System

enum MeasurementSystem: String, Codable, CaseIterable, Identifiable, Sendable {
    case metric
    case imperial

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .metric: "Metric (kg / cm)"
        case .imperial: "Imperial (lb / ft-in)"
        }
    }
}
