//
//  FoodItem.swift
//  MacroDime
//
//  The persisted ingredient record. The curated catalogue in `FoodCatalog` is
//  seeded into this table on first launch so that user-created foods live
//  alongside it and meal portions can hold a real relationship rather than a
//  loose string key.
//
//  The engine never sees this type, `snapshot` converts to the `Sendable`
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
    /// The culinary family substitutions are restricted to. Stored raw like
    /// every other enum. Defaults to `""` so the property is additive for
    /// SwiftData's lightweight migration; the accessor falls back to the
    /// category's default group when the stored value is unknown or missing.
    var swapGroupRaw: String = ""
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
    /// Dietary flags, stored as the raw bit pattern of `FoodTraits`. An `Int`
    /// rather than a set so the value is a plain column, which keeps it
    /// predicate-safe and migration-trivial.
    var traitsRaw: Int = 0
    /// Active preparation time in minutes.
    var prepMinutes: Int = 0
    /// Distinguishes user-added foods from curated ones, so a catalogue
    /// refresh can update the latter without touching the former.
    var isUserCreated: Bool
    var isFavourite: Bool

    init(
        catalogID: String,
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
        prepMinutes: Int = 0,
        isUserCreated: Bool = false,
        isFavourite: Bool = false
    ) {
        self.catalogID = catalogID
        self.name = name
        self.sectionRaw = section.rawValue
        self.categoryRaw = category.rawValue
        self.swapGroupRaw = (swapGroup ?? SwapGroup.default(for: category)).rawValue
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
        self.traitsRaw = traits.rawValue
        self.prepMinutes = prepMinutes
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
            swapGroup: snapshot.swapGroup,
            costTier: snapshot.costTier,
            costPerServing: snapshot.costPerServing,
            servingDescription: snapshot.servingDescription,
            servingGrams: snapshot.servingGrams,
            nutrition: snapshot.nutrition,
            satietyIndex: snapshot.satietyIndex,
            isSwapCandidate: snapshot.isSwapCandidate,
            traits: snapshot.traits,
            prepMinutes: snapshot.prepMinutes,
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

    /// Falls back to the category's default group, which for a vegetable is
    /// `.unclassified` (no substitutes). A store written before this property
    /// existed therefore degrades to "no vegetable swaps", never to the old
    /// "any vegetable may replace any other".
    var swapGroup: SwapGroup {
        get {
            SwapGroup(rawValue: swapGroupRaw) ?? SwapGroup.default(for: category)
        }
        set { swapGroupRaw = newValue.rawValue }
    }

    /// Dietary flags. A store written before these existed reads as plant-only,
    /// which is the permissive direction: such a row keeps its existing
    /// behaviour for an unrestricted user, and a restricted user's own filter
    /// still applies to everything else in the catalogue.
    var traits: FoodTraits {
        get { FoodTraits(rawValue: traitsRaw) }
        set { traitsRaw = newValue.rawValue }
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

    /// Applies curated catalogue values to an existing record, leaving
    /// user-owned fields (favourite state) alone.
    func update(from snapshot: FoodSnapshot) {
        name = snapshot.name
        section = snapshot.section
        category = snapshot.category
        swapGroup = snapshot.swapGroup
        costTier = snapshot.costTier
        costPerServing = snapshot.costPerServing
        servingDescription = snapshot.servingDescription
        servingGrams = snapshot.servingGrams
        nutrition = snapshot.nutrition
        satietyIndex = snapshot.satietyIndex
        isSwapCandidate = snapshot.isSwapCandidate
        traits = snapshot.traits
        prepMinutes = snapshot.prepMinutes
    }
}
