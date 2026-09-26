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
    /// The finer axis substitutions happen on. Equal to `category` for every
    /// category except vegetables, which are split into culinary families so
    /// that broccoli is never offered in place of carrots. See `SwapGroup`.
    ///
    /// Defaulted from `category` when omitted, so a food declared without one is
    /// still valid. Vegetables default to `.unclassified`, which never matches
    /// anything, so an unclassified vegetable simply has no substitutes.
    let swapGroup: SwapGroup
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
    /// macro-aligned, the more filling option wins.
    let satietyIndex: Double
    /// Condiments and near-zero-calorie items are excluded from substitution:
    /// scaling them produces nonsense, and swapping them saves nothing.
    let isSwapCandidate: Bool
    /// What the food is made of, in the terms a dietary restriction is phrased
    /// in. Empty means plant-only with no declarable allergen, which covers the
    /// vegetables, fruit, oils and spices.
    let traits: FoodTraits
    /// Active preparation time in minutes. 0 means ready to eat as bought.
    /// Read by `DietaryFilter` against the user's stated `PrepEffort`.
    let prepMinutes: Int

    init(
        id: String,
        name: String,
        section: GrocerySection,
        category: FoodCategory,
        swapGroup: SwapGroup? = nil,
        costTier: BudgetTier,
        costPerServing: Double,
        servingDescription: String,
        servingGrams: Double,
        nutrition: NutritionFacts,
        satietyIndex: Double,
        isSwapCandidate: Bool = true,
        traits: FoodTraits = [],
        prepMinutes: Int = 0
    ) {
        self.id = id
        self.name = name
        self.section = section
        self.category = category
        self.swapGroup = swapGroup ?? SwapGroup.default(for: category)
        self.costTier = costTier
        self.costPerServing = costPerServing
        self.servingDescription = servingDescription
        self.servingGrams = servingGrams
        self.nutrition = nutrition
        self.satietyIndex = satietyIndex
        self.isSwapCandidate = isSwapCandidate
        self.traits = traits
        self.prepMinutes = prepMinutes
    }

    /// A copy with dietary annotations filled in.
    ///
    /// The catalogue keeps its dietary metadata in two lookup tables rather than
    /// in every literal, so that 57 food declarations stay about food and macros
    /// while the dietary view of the same data can be read, and audited, in one
    /// place. This is the join between the two.
    func annotated(traits: FoodTraits, prepMinutes: Int) -> FoodSnapshot {
        FoodSnapshot(
            id: id,
            name: name,
            section: section,
            category: category,
            swapGroup: swapGroup,
            costTier: costTier,
            costPerServing: costPerServing,
            servingDescription: servingDescription,
            servingGrams: servingGrams,
            nutrition: nutrition,
            satietyIndex: satietyIndex,
            isSwapCandidate: isSwapCandidate,
            traits: traits,
            prepMinutes: prepMinutes
        )
    }

    /// A copy at a different price per serving. `PriceTable` uses it to put a
    /// published average in place of the catalogue's hand-set figure.
    func withCost(_ costPerServing: Double) -> FoodSnapshot {
        FoodSnapshot(
            id: id,
            name: name,
            section: section,
            category: category,
            swapGroup: swapGroup,
            costTier: costTier,
            costPerServing: costPerServing,
            servingDescription: servingDescription,
            servingGrams: servingGrams,
            nutrition: nutrition,
            satietyIndex: satietyIndex,
            isSwapCandidate: isSwapCandidate,
            traits: traits,
            prepMinutes: prepMinutes
        )
    }

    /// Protein grams bought per currency unit, the headline "budget powerhouse"
    /// number, and the metric the swap engine is ultimately optimising.
    ///
    /// The unit is one USD, the currency the catalogue is priced in. Displaying
    /// it in another currency is a conversion, not a relabelling: `R18.50` of
    /// protein is the same quantity as `$1` of protein.
    var proteinPerCurrencyUnit: Double {
        guard costPerServing > 0 else { return 0 }
        return nutrition.protein / costPerServing
    }

    /// This food's price, tagged with the currency it is denominated in.
    var price: Money { .catalogue(costPerServing) }

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

    /// The serving multiplied out: `"3 large eggs"` for 1.5 servings of
    /// `"2 large eggs"`, and the catalogue text itself at 1x.
    var quantityDescription: String {
        ServingMeasure.describe(food.servingDescription, times: servings)
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
