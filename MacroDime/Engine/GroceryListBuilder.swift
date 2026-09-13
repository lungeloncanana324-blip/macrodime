//
//  GroceryListBuilder.swift
//  MacroDime
//
//  Collapses a week of planned meals into one shopping list, aggregated per
//  ingredient and grouped by supermarket section. Pure Swift: the SwiftData
//  side (`GroceryListService`) handles persistence and check-off state.
//

import Foundation

// MARK: - Line

/// One ingredient's total requirement across every meal it appears in.
struct GroceryLine: Identifiable, Hashable, Sendable {
    /// The catalogue id — stable, and the natural aggregation key.
    var id: String { food.id }
    let food: FoodSnapshot
    /// Sum of servings across the whole plan.
    let totalServings: Double
    /// Names of the meals that need it, for the "used in" caption.
    let usedInMeals: [String]

    var section: GrocerySection { food.section }
    var name: String { food.name }
    var estimatedCost: Double { food.costPerServing * totalServings }
    var totalGrams: Double { food.servingGrams * totalServings }

    /// e.g. `"6 × 1 can (142 g drained)"` — what to actually put in the trolley.
    var quantityDescription: String {
        let quantity = totalServings.formatted(.number.precision(.fractionLength(0...2)))
        return "\(quantity) × \(food.servingDescription)"
    }

    /// e.g. `"852 g total"`.
    var massDescription: String {
        totalGrams >= 1_000
            ? "\((totalGrams / 1_000).formatted(.number.precision(.fractionLength(1)))) kg total"
            : "\(Int(totalGrams.rounded())) g total"
    }
}

// MARK: - Section group

/// A section of the shopping list, ready to render as one `Section` in a `List`.
struct GrocerySectionGroup: Identifiable, Hashable, Sendable {
    var id: String { section.rawValue }
    let section: GrocerySection
    let lines: [GroceryLine]

    var subtotal: Double { lines.reduce(0) { $0 + $1.estimatedCost } }
}

// MARK: - Builder

enum GroceryListBuilder {

    /// Aggregates meals into one line per distinct ingredient.
    ///
    /// Aggregation is by catalogue id, so 1 can of tuna at lunch on Monday and
    /// 2 more on Thursday become a single "3 × 1 can" line rather than three
    /// entries the user has to add up in the aisle.
    static func lines(from meals: [MealItem]) -> [GroceryLine] {
        var servingsByFood: [String: Double] = [:]
        var foodsByID: [String: FoodSnapshot] = [:]
        // Ordered, de-duplicated meal names per ingredient.
        var mealNamesByFood: [String: [String]] = [:]

        for meal in meals {
            for portion in meal.portions {
                let key = portion.food.id
                servingsByFood[key, default: 0] += portion.servings
                foodsByID[key] = portion.food
                if !(mealNamesByFood[key]?.contains(meal.name) ?? false) {
                    mealNamesByFood[key, default: []].append(meal.name)
                }
            }
        }

        return servingsByFood.compactMap { key, servings in
            guard let food = foodsByID[key], servings > 0 else { return nil }
            return GroceryLine(
                food: food,
                totalServings: servings,
                usedInMeals: mealNamesByFood[key] ?? []
            )
        }
        // Within a section, most expensive first: the lines worth scrutinising
        // sit at the top.
        .sorted { lhs, rhs in
            if lhs.section.aisleOrder != rhs.section.aisleOrder {
                return lhs.section.aisleOrder < rhs.section.aisleOrder
            }
            if lhs.estimatedCost != rhs.estimatedCost {
                return lhs.estimatedCost > rhs.estimatedCost
            }
            return lhs.name < rhs.name
        }
    }

    /// The same data grouped into store-walk order.
    static func grouped(from meals: [MealItem]) -> [GrocerySectionGroup] {
        let all = lines(from: meals)
        return GrocerySection.allCases
            .sorted { $0.aisleOrder < $1.aisleOrder }
            .compactMap { section in
                let sectionLines = all.filter { $0.section == section }
                guard !sectionLines.isEmpty else { return nil }
                return GrocerySectionGroup(section: section, lines: sectionLines)
            }
    }

    /// Estimated total spend for the plan.
    static func estimatedTotal(for meals: [MealItem]) -> Double {
        lines(from: meals).reduce(0) { $0 + $1.estimatedCost }
    }
}
