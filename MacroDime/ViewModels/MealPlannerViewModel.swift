//
//  MealPlannerViewModel.swift
//  MacroDime
//
//  Owns the day being planned. Reads `MealPlan` out of SwiftData into value
//  types, lets the engine work on those, then writes the result back.
//
//  Working in value types is deliberate: the swap engine can then propose an
//  entire alternative day without touching the store, so the user sees the
//  preview *before* anything is committed.
//

import Foundation
import Observation
import SwiftData

@Observable
@MainActor
final class MealPlannerViewModel {

    // MARK: State

    private(set) var date: Date
    private(set) var meals: [MealItem] = []
    private(set) var catalog: [FoodSnapshot] = []
    /// Prescription for the current profile. `nil` until a profile is loaded.
    private(set) var targets: NutritionFacts?
    private(set) var dailyBudget: Double = 0
    private(set) var budgetTier: BudgetTier = .strict

    /// The swap the user is currently being shown, if any.
    var pendingSwap: MealSwap?
    var lastError: String?

    /// Food picker state.
    var searchText: String = ""
    var categoryFilter: FoodCategory?
    var showStrictTierOnly: Bool = false

    private var engine: BudgetFoodEngine

    init(date: Date = .now) {
        self.date = Calendar.current.startOfDay(for: date)
        self.engine = BudgetFoodEngine()
    }

    // MARK: Loading

    /// Pulls the profile, the catalogue and the day's plan into memory.
    func load(context: ModelContext, profile: UserProfile?) {
        if let profile {
            targets = profile.prescription.targets
            dailyBudget = profile.dailyFoodBudget
            budgetTier = profile.budgetTier
            engine = BudgetFoodEngine(policy: .cuttingCosts(from: profile.budgetTier))
        }

        do {
            let foods = try context.fetch(
                FetchDescriptor<FoodItem>(sortBy: [SortDescriptor(\.name)])
            )
            catalog = foods.map(\.snapshot)
            engine = BudgetFoodEngine(catalog: catalog, policy: engine.policy)

            meals = try plan(context: context, createIfMissing: false)?.mealItems ?? []
        } catch {
            lastError = "Could not load your plan: \(error.localizedDescription)"
        }
    }

    func select(date newDate: Date, context: ModelContext, profile: UserProfile?) {
        date = Calendar.current.startOfDay(for: newDate)
        load(context: context, profile: profile)
    }

    // MARK: Totals

    var consumed: NutritionFacts { meals.totalNutrition }
    var spend: Double { meals.totalCost }

    /// Remaining allowance for each macro. Negative values mean over target.
    var remaining: NutritionFacts {
        guard let targets else { return .zero }
        return targets - consumed
    }

    var budgetRemaining: Double { dailyBudget - spend }
    var isOverBudget: Bool { spend > dailyBudget && dailyBudget > 0 }

    /// 0-1+ progress against each target. Values above 1 mean over target, and
    /// the rings are expected to render that state rather than clamp silently.
    func progress(for axis: MacroAxis) -> Double {
        guard let targets else { return 0 }
        let target = axis.value(in: targets)
        guard target > 0 else { return 0 }
        return axis.value(in: consumed) / target
    }

    var budgetProgress: Double {
        guard dailyBudget > 0 else { return 0 }
        return spend / dailyBudget
    }

    func meal(for slot: MealSlot) -> MealItem? {
        meals.first { $0.slot == slot }
    }

    // MARK: Swap engine

    /// Non-destructive preview of the cheapest acceptable version of a meal.
    func swapPreview(for meal: MealItem) -> MealSwap? {
        engine.bestSwap(for: meal)
    }

    /// Alternatives for a single ingredient, for the per-portion picker.
    func alternatives(for portion: Portion, in meal: MealItem) -> [PortionSwap] {
        engine.rankedReplacements(for: portion, within: meal)
    }

    /// Total savings available across the day, without changing anything.
    var potentialSavings: Double { engine.potentialSavings(for: meals) }

    var budgetPowerhouses: [FoodSnapshot] {
        engine.budgetPowerhouses(tier: budgetTier, limit: 5)
    }

    /// Commits a proposed swap to the store.
    func apply(_ swap: MealSwap, context: ModelContext) {
        replace(mealID: swap.original.id, with: swap.swapped, context: context) { persisted in
            persisted.wasSwapped = true
            persisted.swapSavings += swap.savings
        }
        pendingSwap = nil
    }

    /// Applies a single-ingredient substitution.
    ///
    /// Uses the swap's own `resultingMeal` rather than re-applying the
    /// replacement portion: the engine may have re-portioned the meal's oil or
    /// rice to absorb the macro gap, and dropping that would leave the meal
    /// outside the tolerance the user was shown.
    func apply(_ portionSwap: PortionSwap, in meal: MealItem, context: ModelContext) {
        replace(mealID: meal.id, with: portionSwap.resultingMeal, context: context) { persisted in
            persisted.wasSwapped = true
            persisted.swapSavings += portionSwap.savings
        }
    }

    // MARK: Editing

    func addFood(_ snapshot: FoodSnapshot, servings: Double = 1, to slot: MealSlot, context: ModelContext) {
        do {
            guard let plan = try plan(context: context, createIfMissing: true) else { return }

            let persistedMeal: PlannedMeal
            if let existingMeal = plan.meal(for: slot) {
                persistedMeal = existingMeal
            } else {
                let meal = PlannedMeal(name: slot.displayName, slot: slot)
                meal.plan = plan
                context.insert(meal)
                plan.meals.append(meal)
                persistedMeal = meal
            }

            guard let foodRecord = try food(withID: snapshot.id, context: context) else {
                lastError = "That ingredient is no longer in your catalogue."
                return
            }

            if let existing = persistedMeal.portions.first(where: { $0.food?.catalogID == snapshot.id }) {
                existing.servings += servings
            } else {
                let portion = MealPortion(food: foodRecord, servings: servings)
                portion.meal = persistedMeal
                context.insert(portion)
                persistedMeal.portions.append(portion)
            }

            try context.save()
            reload(context: context)
        } catch {
            lastError = "Could not add that food: \(error.localizedDescription)"
        }
    }

    func removePortion(id portionID: UUID, context: ModelContext) {
        do {
            let descriptor = FetchDescriptor<MealPortion>(
                predicate: #Predicate { $0.id == portionID }
            )
            guard let portion = try context.fetch(descriptor).first else { return }
            context.delete(portion)
            try context.save()
            reload(context: context)
        } catch {
            lastError = "Could not remove that ingredient: \(error.localizedDescription)"
        }
    }

    func updateServings(portionID: UUID, to servings: Double, context: ModelContext) {
        guard servings > 0 else {
            removePortion(id: portionID, context: context)
            return
        }
        do {
            let descriptor = FetchDescriptor<MealPortion>(
                predicate: #Predicate { $0.id == portionID }
            )
            guard let portion = try context.fetch(descriptor).first else { return }
            portion.servings = servings
            try context.save()
            reload(context: context)
        } catch {
            lastError = "Could not update that portion: \(error.localizedDescription)"
        }
    }

    // MARK: Food picker

    /// Catalogue filtered by the picker's current search, category and tier.
    var filteredCatalog: [FoodSnapshot] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return catalog.filter { food in
            if showStrictTierOnly, food.costTier != .strict { return false }
            if let categoryFilter, food.category != categoryFilter { return false }
            if !query.isEmpty, !food.name.lowercased().contains(query) { return false }
            return true
        }
        .sorted { $0.name < $1.name }
    }

    // MARK: Grocery list

    /// Rebuilds the shopping list for the week containing the selected day.
    func regenerateGroceryList(context: ModelContext) {
        do {
            try GroceryListService.regenerate(weekContaining: date, context: context)
            try context.save()
        } catch {
            lastError = "Could not build your grocery list: \(error.localizedDescription)"
        }
    }

    // MARK: Persistence helpers

    private func reload(context: ModelContext) {
        do {
            meals = try plan(context: context, createIfMissing: false)?.mealItems ?? []
        } catch {
            lastError = "Could not refresh your plan: \(error.localizedDescription)"
        }
    }

    /// The `MealPlan` row for the selected day.
    private func plan(context: ModelContext, createIfMissing: Bool) throws -> MealPlan? {
        let day = date
        let descriptor = FetchDescriptor<MealPlan>(
            predicate: #Predicate { $0.date == day }
        )
        if let existing = try context.fetch(descriptor).first { return existing }
        guard createIfMissing else { return nil }

        let plan = MealPlan(date: day)
        context.insert(plan)
        return plan
    }

    private func food(withID id: String, context: ModelContext) throws -> FoodItem? {
        let descriptor = FetchDescriptor<FoodItem>(
            predicate: #Predicate { $0.catalogID == id }
        )
        return try context.fetch(descriptor).first
    }

    /// Writes a value-type meal back over its persisted counterpart.
    private func replace(
        mealID: UUID,
        with item: MealItem,
        context: ModelContext,
        configure: (PlannedMeal) -> Void = { _ in }
    ) {
        do {
            let descriptor = FetchDescriptor<PlannedMeal>(
                predicate: #Predicate { $0.id == mealID }
            )
            guard let persisted = try context.fetch(descriptor).first else { return }

            // Resolve catalogue ids to records once, rather than per portion.
            // An `Array` rather than a `Set`: `#Predicate` translates
            // `Array.contains` to a SQL `IN` reliably, `Set.contains` does not.
            let ids = Array(Set(item.portions.map(\.food.id)))
            let foods = try context.fetch(
                FetchDescriptor<FoodItem>(predicate: #Predicate { ids.contains($0.catalogID) })
            )
            let lookup = Dictionary(foods.map { ($0.catalogID, $0) }, uniquingKeysWith: { first, _ in first })

            persisted.apply(item, foodLookup: { lookup[$0] }, context: context)
            configure(persisted)

            try context.save()
            reload(context: context)
        } catch {
            lastError = "Could not save that change: \(error.localizedDescription)"
        }
    }
}
