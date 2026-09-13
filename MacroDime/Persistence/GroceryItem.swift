//
//  GroceryItem.swift
//  MacroDime
//
//  One line of a persisted shopping list, plus the service that regenerates a
//  week's list from the meal plans without losing what the user has already
//  ticked off.
//

import Foundation
import SwiftData

@Model
final class GroceryItem {

    @Attribute(.unique) var id: UUID
    /// Catalogue id of the source ingredient, or `""` for a manually added line.
    var sourceFoodID: String
    var name: String
    var sectionRaw: String
    var quantityDescription: String
    var totalServings: Double
    var estimatedCost: Double

    /// Ticked off in the aisle.
    var isChecked: Bool
    /// "Already have this" — excluded from the estimated total and kept sticky
    /// across regeneration, since a full pantry stays full.
    var isAlreadyOwned: Bool
    /// Monday of the week this list covers. The list is addressed by week, so
    /// regenerating Thursday's plan updates the same list rather than making a
    /// second one.
    var weekStart: Date
    var createdAt: Date

    init(
        id: UUID = UUID(),
        sourceFoodID: String,
        name: String,
        section: GrocerySection,
        quantityDescription: String,
        totalServings: Double,
        estimatedCost: Double,
        weekStart: Date,
        isChecked: Bool = false,
        isAlreadyOwned: Bool = false
    ) {
        self.id = id
        self.sourceFoodID = sourceFoodID
        self.name = name
        self.sectionRaw = section.rawValue
        self.quantityDescription = quantityDescription
        self.totalServings = totalServings
        self.estimatedCost = estimatedCost
        self.weekStart = weekStart
        self.isChecked = isChecked
        self.isAlreadyOwned = isAlreadyOwned
        self.createdAt = .now
    }

    convenience init(line: GroceryLine, weekStart: Date) {
        self.init(
            sourceFoodID: line.food.id,
            name: line.name,
            section: line.section,
            quantityDescription: line.quantityDescription,
            totalServings: line.totalServings,
            estimatedCost: line.estimatedCost,
            weekStart: weekStart
        )
    }

    var section: GrocerySection {
        get { GrocerySection(rawValue: sectionRaw) ?? .pantry }
        set { sectionRaw = newValue.rawValue }
    }

    /// Cost that still has to be spent: an owned item costs nothing to buy.
    var outstandingCost: Double { isAlreadyOwned ? 0 : estimatedCost }

    func update(from line: GroceryLine) {
        name = line.name
        section = line.section
        quantityDescription = line.quantityDescription
        totalServings = line.totalServings
        estimatedCost = line.estimatedCost
    }
}

// MARK: - Grocery List Service

/// Regenerates the persisted list from the current meal plans.
///
/// The rule that matters: **user state survives regeneration.** Adding a meal
/// on Thursday must not untick the twelve items already bought on Monday, so
/// existing rows are matched by `sourceFoodID` and updated in place.
@MainActor
enum GroceryListService {

    /// Monday-anchored start of the week containing `date`.
    static func weekStart(containing date: Date, calendar: Calendar = .current) -> Date {
        let components = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return calendar.date(from: components) ?? calendar.startOfDay(for: date)
    }

    /// The seven days of the week containing `date`.
    static func weekRange(containing date: Date, calendar: Calendar = .current) -> Range<Date> {
        let start = weekStart(containing: date, calendar: calendar)
        let end = calendar.date(byAdding: .day, value: 7, to: start) ?? start
        return start..<end
    }

    /// Rebuilds the list for the week containing `date` from that week's plans.
    @discardableResult
    static func regenerate(
        weekContaining date: Date,
        context: ModelContext,
        calendar: Calendar = .current
    ) throws -> [GroceryItem] {
        let range = weekRange(containing: date, calendar: calendar)
        let start = range.lowerBound
        let end = range.upperBound

        let planDescriptor = FetchDescriptor<MealPlan>(
            predicate: #Predicate { $0.date >= start && $0.date < end }
        )
        let plans = try context.fetch(planDescriptor)
        let meals = plans.flatMap(\.mealItems)
        let lines = GroceryListBuilder.lines(from: meals)

        let existingDescriptor = FetchDescriptor<GroceryItem>(
            predicate: #Predicate { $0.weekStart == start }
        )
        let existing = try context.fetch(existingDescriptor)
        var existingByFood = Dictionary(
            existing.map { ($0.sourceFoodID, $0) },
            uniquingKeysWith: { first, _ in first }
        )

        var result: [GroceryItem] = []

        for line in lines {
            if let row = existingByFood.removeValue(forKey: line.id) {
                // Quantities change; ticks and "already owned" do not.
                row.update(from: line)
                result.append(row)
            } else {
                let row = GroceryItem(line: line, weekStart: start)
                context.insert(row)
                result.append(row)
            }
        }

        // Anything left over is no longer required by any meal. Manually added
        // lines (empty `sourceFoodID`) are never auto-removed.
        for (foodID, orphan) in existingByFood where !foodID.isEmpty {
            context.delete(orphan)
        }

        return result
    }
}
