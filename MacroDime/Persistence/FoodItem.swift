//
//  FoodItem.swift
//  MacroDime
//
//  The persisted ingredient record. The curated catalogue in `FoodCatalog` is
//  seeded into this table on first launch so that user-created foods live
//  alongside it and meal portions can hold a real relationship rather than a
//  loose string key.
//
//  The engine never sees this type — `snapshot` converts to the `Sendable`
//  value type at the boundary.
//

import Foundation
import SwiftData

@Model
final class FoodItem {

    /// Stable catalogue id (`"canned-tuna-water"`) or a generated id for
    /// user-created foods. Unique, so re-running the seeder is idempotent.
    @Attribute(.unique) var catalogID: String

    var name: String
    var sectionRaw: String
    var categoryRaw: String
    var costTierRaw: String

    var costPerServing: Double
    var servingDescription: String
    var servingGrams: Double

    // Macros for exactly one serving.
    var calories: Double
    var proteinGrams: Double
    var carbGrams: Double
    var fatGrams: Double

    var satietyIndex: Double
    var isSwapCandidate: Bool
    /// Distinguishes user-added foods from curated ones, so a catalogue
    /// refresh can update the latter without touching the former.
    var isUserCreated: Bool
    var isFavourite: Bool

    init(
        catalogID: String,
        name: String,
        section: GrocerySection,
        category: FoodCategory,
        costTier: BudgetTier,
        costPerServing: Double,
        servingDescription: String,
        servingGrams: Double,
        nutrition: NutritionFacts,
        satietyIndex: Double,
        isSwapCandidate: Bool = true,
        isUserCreated: Bool = false,
        isFavourite: Bool = false
    ) {
        self.catalogID = catalogID
        self.name = name
        self.sectionRaw = section.rawValue
        self.categoryRaw = category.rawValue
        self.costTierRaw = costTier.rawValue
        self.costPerServing = costPerServing
        self.servingDescription = servingDescription
        self.servingGrams = servingGrams
        self.calories = nutrition.calories
        self.proteinGrams = nutrition.protein
        self.carbGrams = nutrition.carbs
        self.fatGrams = nutrition.fat
        self.satietyIndex = satietyIndex
        self.isSwapCandidate = isSwapCandidate
        self.isUserCreated = isUserCreated
        self.isFavourite = isFavourite
    }

    /// Convenience initialiser used by the seeder.
    convenience init(snapshot: FoodSnapshot, isUserCreated: Bool = false) {
        self.init(
            catalogID: snapshot.id,
            name: snapshot.name,
            section: snapshot.section,
            category: snapshot.category,
            costTier: snapshot.costTier,
            costPerServing: snapshot.costPerServing,
            servingDescription: snapshot.servingDescription,
            servingGrams: snapshot.servingGrams,
            nutrition: snapshot.nutrition,
            satietyIndex: snapshot.satietyIndex,
            isSwapCandidate: snapshot.isSwapCandidate,
            isUserCreated: isUserCreated
        )
    }

    // MARK: Typed accessors

    var section: GrocerySection {
        get { GrocerySection(rawValue: sectionRaw) ?? .pantry }
        set { sectionRaw = newValue.rawValue }
    }

    var category: FoodCategory {
        get { FoodCategory(rawValue: categoryRaw) ?? .carbBase }
        set { categoryRaw = newValue.rawValue }
    }

    var costTier: BudgetTier {
        get { BudgetTier(rawValue: costTierRaw) ?? .strict }
        set { costTierRaw = newValue.rawValue }
    }

    var nutrition: NutritionFacts {
        get {
            NutritionFacts(
                calories: calories,
                protein: proteinGrams,
                carbs: carbGrams,
                fat: fatGrams
            )
        }
        set {
            calories = newValue.calories
            proteinGrams = newValue.protein
            carbGrams = newValue.carbs
            fatGrams = newValue.fat
        }
    }

    // MARK: Boundary

    /// The immutable value-type copy the engine and views work with.
    var snapshot: FoodSnapshot {
        FoodSnapshot(
            id: catalogID,
            name: name,
            section: section,
            category: category,
            costTier: costTier,
            costPerServing: costPerServing,
            servingDescription: servingDescription,
            servingGrams: servingGrams,
            nutrition: nutrition,
            satietyIndex: satietyIndex,
            isSwapCandidate: isSwapCandidate
        )
    }

    /// Applies curated catalogue values to an existing record, leaving
    /// user-owned fields (favourite state) alone.
    func update(from snapshot: FoodSnapshot) {
        name = snapshot.name
        section = snapshot.section
        category = snapshot.category
        costTier = snapshot.costTier
        costPerServing = snapshot.costPerServing
        servingDescription = snapshot.servingDescription
        servingGrams = snapshot.servingGrams
        nutrition = snapshot.nutrition
        satietyIndex = snapshot.satietyIndex
        isSwapCandidate = snapshot.isSwapCandidate
    }
}
