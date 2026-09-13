//
//  MealPlan.swift
//  MacroDime
//
//  A day's plan: MealPlan → PlannedMeal → MealPortion → FoodItem.
//
//  Delete rules: a plan owns its meals and a meal owns its portions, so both
//  cascade. A portion only *references* a food — deleting a food nullifies the
//  reference rather than destroying the history of what was eaten, which is why
//  `MealPortion` caches the food's name and macros (see `foodName` below).
//

import Foundation
import SwiftData

// MARK: - Meal Plan (one day)

@Model
final class MealPlan {

    @Attribute(.unique) var id: UUID
    /// Normalised to the start of the day so a plan is addressable by date.
    var date: Date
    var notes: String
    var createdAt: Date

    @Relationship(deleteRule: .cascade, inverse: \PlannedMeal.plan)
    var meals: [PlannedMeal]

    init(id: UUID = UUID(), date: Date = .now, notes: String = "") {
        self.id = id
        self.date = Calendar.current.startOfDay(for: date)
        self.notes = notes
        self.createdAt = .now
        self.meals = []
    }

    /// Meals in slot order — SwiftData relationships are unordered sets, so
    /// never render `meals` directly.
    var orderedMeals: [PlannedMeal] {
        meals.sorted { $0.slot.sortOrder < $1.slot.sortOrder }
    }

    var totalNutrition: NutritionFacts { orderedMeals.map(\.nutrition).total }
    var totalCost: Double { orderedMeals.reduce(0) { $0 + $1.cost } }

    /// Value-type view of the day, for the engine.
    var mealItems: [MealItem] { orderedMeals.map(\.mealItem) }

    func meal(for slot: MealSlot) -> PlannedMeal? {
        meals.first { $0.slot == slot }
    }
}

// MARK: - Planned Meal (one slot)

@Model
final class PlannedMeal {

    @Attribute(.unique) var id: UUID
    var name: String
    var slotRaw: String
    /// Set when the user accepted a low-cost swap, so the UI can show a
    /// "swapped, saving X" badge and the change is auditable.
    var wasSwapped: Bool
    var swapSavings: Double

    var plan: MealPlan?

    @Relationship(deleteRule: .cascade, inverse: \MealPortion.meal)
    var portions: [MealPortion]

    init(
        id: UUID = UUID(),
        name: String,
        slot: MealSlot,
        wasSwapped: Bool = false,
        swapSavings: Double = 0
    ) {
        self.id = id
        self.name = name
        self.slotRaw = slot.rawValue
        self.wasSwapped = wasSwapped
        self.swapSavings = swapSavings
        self.portions = []
    }

    var slot: MealSlot {
        get { MealSlot(rawValue: slotRaw) ?? .snack }
        set { slotRaw = newValue.rawValue }
    }

    var orderedPortions: [MealPortion] {
        portions.sorted { $0.addedAt < $1.addedAt }
    }

    var nutrition: NutritionFacts { orderedPortions.map(\.nutrition).total }
    var cost: Double { orderedPortions.reduce(0) { $0 + $1.cost } }

    /// The engine's value-type view of this meal. Portions whose food record
    /// has been deleted are dropped.
    var mealItem: MealItem {
        MealItem(
            id: id,
            name: name,
            slot: slot,
            portions: orderedPortions.compactMap(\.portion)
        )
    }

    /// Rewrites this meal from an engine result, reusing existing portion rows
    /// where the ingredient is unchanged.
    ///
    /// `foodLookup` resolves a catalogue id to the persisted `FoodItem`; it is
    /// injected rather than fetched here so this model stays free of context
    /// plumbing.
    func apply(_ item: MealItem, foodLookup: (String) -> FoodItem?, context: ModelContext) {
        name = item.name
        slot = item.slot

        // Drop portions no longer present. Collected into a local array first:
        // `context.delete` mutates the `portions` relationship, and mutating a
        // collection while iterating it is undefined behaviour.
        let survivingIDs = Set(item.portions.map(\.id))
        let removed = portions.filter { !survivingIDs.contains($0.id) }
        for portion in removed {
            context.delete(portion)
        }

        for incoming in item.portions {
            if let existing = portions.first(where: { $0.id == incoming.id }) {
                existing.servings = incoming.servings
                if existing.food?.catalogID != incoming.food.id {
                    existing.food = foodLookup(incoming.food.id)
                    existing.foodName = incoming.food.name
                }
            } else if let food = foodLookup(incoming.food.id) {
                let portion = MealPortion(id: incoming.id, food: food, servings: incoming.servings)
                portion.meal = self
                context.insert(portion)
                portions.append(portion)
            }
        }
    }
}

// MARK: - Meal Portion

@Model
final class MealPortion {

    @Attribute(.unique) var id: UUID
    var servings: Double
    var addedAt: Date
    /// Cached display name. Keeps the meal readable if the food record is ever
    /// deleted — the relationship nullifies, but the history should not become
    /// an anonymous blank row.
    var foodName: String

    var meal: PlannedMeal?
    /// Nullify on delete (the SwiftData default): removing a food from the
    /// catalogue must not cascade into destroying past meals.
    var food: FoodItem?

    init(id: UUID = UUID(), food: FoodItem, servings: Double = 1) {
        self.id = id
        self.servings = servings
        self.addedAt = .now
        self.foodName = food.name
        self.food = food
    }

    var nutrition: NutritionFacts {
        guard let food else { return .zero }
        return food.nutrition.scaled(by: servings)
    }

    var cost: Double {
        guard let food else { return 0 }
        return food.costPerServing * servings
    }

    /// Value-type view, or `nil` if the referenced food is gone.
    var portion: Portion? {
        guard let food else { return nil }
        return Portion(id: id, food: food.snapshot, servings: servings)
    }
}
