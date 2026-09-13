//
//  MealItem.swift
//  MacroDime
//
//  The value types the food engine reasons about. `FoodSnapshot` is a read-only
//  copy of a `FoodItem` record; `MealItem` is a composed meal. Nothing here
//  imports SwiftData or SwiftUI, which is what makes the swap engine testable
//  without a ModelContainer.
//

import Foundation

// MARK: - Food Snapshot

/// An immutable, thread-safe copy of one catalogue ingredient.
///
/// The engine never holds `FoodItem` (a SwiftData `@Model`, which is neither
/// `Sendable` nor safe to read off its own context). Views map models to
/// snapshots at the boundary via `FoodItem.snapshot`.
struct FoodSnapshot: Identifiable, Hashable, Sendable {
    /// Stable catalogue identifier, e.g. `"canned-tuna-water"`. Survives a
    /// database rebuild, unlike the SwiftData persistent identifier.
    let id: String
    let name: String
    let section: GrocerySection
    let category: FoodCategory
    let costTier: BudgetTier

    /// Cost of one serving in the user's currency.
    let costPerServing: Double
    /// Human-readable serving, e.g. `"1 can (142 g drained)"`.
    let servingDescription: String
    /// Serving mass in grams, used to build grocery quantities.
    let servingGrams: Double
    /// Macros for exactly one serving.
    let nutrition: NutritionFacts
    /// Fullness per calorie, 0-100, loosely modelled on the Holt satiety index.
    /// Used to break ties between candidates that are equally cheap and equally
    /// macro-aligned — the more filling option wins.
    let satietyIndex: Double
    /// Condiments and near-zero-calorie items are excluded from substitution:
    /// scaling them produces nonsense, and swapping them saves nothing.
    let isSwapCandidate: Bool

    init(
        id: String,
        name: String,
        section: GrocerySection,
        category: FoodCategory,
        costTier: BudgetTier,
        costPerServing: Double,
        servingDescription: String,
        servingGrams: Double,
        nutrition: NutritionFacts,
        satietyIndex: Double,
        isSwapCandidate: Bool = true
    ) {
        self.id = id
        self.name = name
        self.section = section
        self.category = category
        self.costTier = costTier
        self.costPerServing = costPerServing
        self.servingDescription = servingDescription
        self.servingGrams = servingGrams
        self.nutrition = nutrition
        self.satietyIndex = satietyIndex
        self.isSwapCandidate = isSwapCandidate
    }

    /// Protein grams bought per currency unit — the headline "budget powerhouse"
    /// number, and the metric the swap engine is ultimately optimising.
    var proteinPerCurrencyUnit: Double {
        guard costPerServing > 0 else { return 0 }
        return nutrition.protein / costPerServing
    }

    /// Calories bought per currency unit.
    var caloriesPerCurrencyUnit: Double {
        guard costPerServing > 0 else { return 0 }
        return nutrition.calories / costPerServing
    }
}

// MARK: - Portion

/// One ingredient at a specific quantity inside a meal.
struct Portion: Identifiable, Hashable, Sendable {
    let id: UUID
    var food: FoodSnapshot
    /// Number of servings. Quantised to `SwapPolicy.servingStep` by the engine
    /// so the UI never has to render "1.37 cans of tuna".
    var servings: Double

    init(id: UUID = UUID(), food: FoodSnapshot, servings: Double = 1) {
        self.id = id
        self.food = food
        self.servings = servings
    }

    var nutrition: NutritionFacts { food.nutrition.scaled(by: servings) }
    var cost: Double { food.costPerServing * servings }
    var grams: Double { food.servingGrams * servings }

    /// e.g. `"2 × 1 can (142 g drained)"`, or just the serving text at 1×.
    var quantityDescription: String {
        let quantity = servings.formatted(.number.precision(.fractionLength(0...2)))
        return servings == 1 ? food.servingDescription : "\(quantity) × \(food.servingDescription)"
    }
}

// MARK: - Meal Item

/// A composed meal: a slot, a name, and the portions that make it up.
struct MealItem: Identifiable, Hashable, Sendable {
    let id: UUID
    var name: String
    var slot: MealSlot
    var portions: [Portion]

    init(id: UUID = UUID(), name: String, slot: MealSlot, portions: [Portion] = []) {
        self.id = id
        self.name = name
        self.slot = slot
        self.portions = portions
    }

    var nutrition: NutritionFacts { portions.map(\.nutrition).total }
    var cost: Double { portions.reduce(0) { $0 + $1.cost } }
    var isEmpty: Bool { portions.isEmpty }

    /// Calorie-weighted mean satiety across the portions. An empty meal scores 0.
    var satietyScore: Double {
        let calories = nutrition.calories
        guard calories > 0 else { return 0 }
        let weighted = portions.reduce(0.0) { $0 + $1.food.satietyIndex * $1.nutrition.calories }
        return weighted / calories
    }

    /// The most expensive tier present. Drives the price chip on the meal card.
    var effectiveTier: BudgetTier {
        portions.map(\.food.costTier).max() ?? .strict
    }

    /// Replaces one portion in place, preserving order. Returns the meal
    /// unchanged if the portion is not part of it.
    func replacing(portionID: UUID, with replacement: Portion) -> MealItem {
        guard let index = portions.firstIndex(where: { $0.id == portionID }) else { return self }
        var copy = self
        copy.portions[index] = replacement
        return copy
    }

    /// Changes one portion's quantity. Used by the swap engine's rebalance
    /// pass to restore a macro after a substitution.
    func updatingServings(portionID: UUID, to servings: Double) -> MealItem {
        guard let index = portions.firstIndex(where: { $0.id == portionID }) else { return self }
        var copy = self
        copy.portions[index].servings = servings
        return copy
    }

    func portion(id: UUID) -> Portion? {
        portions.first { $0.id == id }
    }
}

extension Sequence where Element == MealItem {
    var totalNutrition: NutritionFacts { map(\.nutrition).total }
    var totalCost: Double { reduce(0) { $0 + $1.cost } }
}
