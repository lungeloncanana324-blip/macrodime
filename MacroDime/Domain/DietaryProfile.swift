//
//  DietaryProfile.swift
//  MacroDime
//
//  The questions onboarding has to ask, and the vocabulary the engine uses to
//  answer them.
//
//  Targets alone do not make a plan a person can follow. A 160 g protein target
//  is useless to someone who does not eat meat if every anchor in the plan is
//  chicken, and a plan that keeps suggesting the one food someone cannot stand
//  gets abandoned in week two. So the profile carries a small dietary model, and
//  the ingredient catalogue is filtered through it before the engine plans
//  anything at all. The filter lives in `DietaryFilter`; nothing here imports
//  SwiftUI or SwiftData.
//

import Foundation

// MARK: - Traits

/// What an ingredient *is*, in dietary terms.
///
/// A bit set rather than a list of booleans because the questions compose: an
/// allergen filter and a dietary pattern both read the same flags, and a food
/// can carry several (a cheese omelette is dairy, egg, and not vegan).
struct FoodTraits: OptionSet, Hashable, Sendable {
    let rawValue: Int

    init(rawValue: Int) { self.rawValue = rawValue }

    /// Flesh from a mammal or a bird. Excluded by every non-omnivore pattern.
    static let meat = FoodTraits(rawValue: 1 << 0)
    /// Beef, pork, lamb. Tagged separately from poultry because some users
    /// avoid red meat specifically, and because pork is excluded outright by
    /// several faiths while beef is not.
    static let redMeat = FoodTraits(rawValue: 1 << 1)
    static let pork = FoodTraits(rawValue: 1 << 2)
    static let fish = FoodTraits(rawValue: 1 << 3)
    static let shellfish = FoodTraits(rawValue: 1 << 4)
    static let dairy = FoodTraits(rawValue: 1 << 5)
    static let egg = FoodTraits(rawValue: 1 << 6)
    static let gluten = FoodTraits(rawValue: 1 << 7)
    static let nuts = FoodTraits(rawValue: 1 << 8)
    static let soy = FoodTraits(rawValue: 1 << 9)
    static let honey = FoodTraits(rawValue: 1 << 10)

    /// Everything that requires an animal to have died.
    static let animalFlesh: FoodTraits = [.meat, .fish, .shellfish]
    /// Everything an animal produces too, which is the line veganism draws.
    static let animalDerived: FoodTraits = [.meat, .fish, .shellfish, .dairy, .egg, .honey]

    /// Human-readable names, in a stable order, for chips and audit copy.
    var labels: [String] {
        var names: [String] = []
        for (trait, label) in Self.named where contains(trait) {
            names.append(label)
        }
        return names
    }

    static let named: [(FoodTraits, String)] = [
        (.meat, "meat"),
        (.redMeat, "red meat"),
        (.pork, "pork"),
        (.fish, "fish"),
        (.shellfish, "shellfish"),
        (.dairy, "dairy"),
        (.egg, "egg"),
        (.gluten, "gluten"),
        (.nuts, "nuts"),
        (.soy, "soy"),
        (.honey, "honey")
    ]

    var summary: String {
        let names = labels
        return names.isEmpty ? "no declared dietary flags" : names.joined(separator: ", ")
    }
}

// MARK: - Pattern

/// The broadest question: what does the user eat?
enum DietaryPattern: String, Codable, CaseIterable, Identifiable, Sendable {
    case omnivore
    case pescatarian
    case vegetarian
    case vegan

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .omnivore: "Omnivore"
        case .pescatarian: "Pescatarian"
        case .vegetarian: "Vegetarian"
        case .vegan: "Vegan"
        }
    }

    var subtitle: String {
        switch self {
        case .omnivore: "Everything, including meat and fish"
        case .pescatarian: "Fish and shellfish, no meat"
        case .vegetarian: "No fish or meat, dairy and eggs are fine"
        case .vegan: "Nothing from an animal"
        }
    }

    var systemImage: String {
        switch self {
        case .omnivore: "fork.knife"
        case .pescatarian: "fish.fill"
        case .vegetarian: "leaf.fill"
        case .vegan: "carrot.fill"
        }
    }

    /// Whether a food carrying these traits may appear in the plan.
    func allows(_ traits: FoodTraits) -> Bool {
        switch self {
        case .omnivore:
            return true
        case .pescatarian:
            return !traits.contains(.meat)
        case .vegetarian:
            return !traits.contains(.meat) && !traits.contains(.fish) && !traits.contains(.shellfish)
        case .vegan:
            return traits.intersection(.animalDerived).isEmpty
        }
    }
}

// MARK: - Exclusions

/// A food group the user avoids, for reasons they are not asked to explain:
/// allergy, intolerance, faith, or taste.
enum FoodExclusion: String, Codable, CaseIterable, Identifiable, Sendable {
    case dairy
    case egg
    case gluten
    case nuts
    case soy
    case shellfish
    case fish
    case pork
    case redMeat

    var id: String { rawValue }

    /// The trait that disqualifies a food.
    var trait: FoodTraits {
        switch self {
        case .dairy: .dairy
        case .egg: .egg
        case .gluten: .gluten
        case .nuts: .nuts
        case .soy: .soy
        case .shellfish: .shellfish
        case .fish: .fish
        case .pork: .pork
        case .redMeat: .redMeat
        }
    }

    var displayName: String {
        switch self {
        case .dairy: "Dairy"
        case .egg: "Eggs"
        case .gluten: "Gluten"
        case .nuts: "Nuts"
        case .soy: "Soy"
        case .shellfish: "Shellfish"
        case .fish: "Fish"
        case .pork: "Pork"
        case .redMeat: "Red meat"
        }
    }

    /// The phrase used when explaining why an ingredient was dropped.
    var exclusionReason: String {
        switch self {
        case .dairy: "contains dairy"
        case .egg: "contains egg"
        case .gluten: "contains gluten"
        case .nuts: "contains nuts"
        case .soy: "contains soy"
        case .shellfish: "is shellfish"
        case .fish: "is fish"
        case .pork: "is pork"
        case .redMeat: "is red meat"
        }
    }
}

// MARK: - Schedule

/// Which slots the plan should fill. Three meals plus snacks is the common
/// answer, and two meals is what most people actually eat on a deficit.
enum EatingSchedule: String, Codable, CaseIterable, Identifiable, Sendable {
    case twoMeals
    case threeMeals
    case threeMealsAndSnacks

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .twoMeals: "2 meals"
        case .threeMeals: "3 meals"
        case .threeMealsAndSnacks: "3 meals + snacks"
        }
    }

    var subtitle: String {
        switch self {
        case .twoMeals: "Lunch and dinner"
        case .threeMeals: "Breakfast, lunch, dinner"
        case .threeMealsAndSnacks: "Three meals plus planned snacks"
        }
    }

    /// The slots the user is expected to plan for.
    var slots: [MealSlot] {
        switch self {
        case .twoMeals: [.lunch, .dinner]
        case .threeMeals: [.breakfast, .lunch, .dinner]
        case .threeMealsAndSnacks: [.breakfast, .lunch, .dinner, .snack]
        }
    }

    /// How many eating occasions a day this represents, snacks included. Used to
    /// split the calorie target across slots.
    var occasionsPerDay: Int {
        switch self {
        case .twoMeals: 2
        case .threeMeals: 3
        case .threeMealsAndSnacks: 4
        }
    }

    func includes(_ slot: MealSlot) -> Bool { slots.contains(slot) }
}

// MARK: - Cooking effort

/// How much time the user will actually spend cooking.
///
/// Exists because a plan full of 40-minute lentils is not a plan for someone who
/// eats out of a microwave, and "you did not follow the plan" is the wrong
/// diagnosis for a plan that was never followable.
enum PrepEffort: String, Codable, CaseIterable, Identifiable, Sendable {
    case noCook
    case quick
    case standard
    case unlimited

    var id: String { rawValue }

    /// The longest single ingredient preparation this effort allows, in minutes.
    /// A food's `prepMinutes` is compared against it.
    var maximumMinutes: Int {
        switch self {
        case .noCook: 5
        case .quick: 15
        case .standard: 30
        case .unlimited: 180
        }
    }

    var displayName: String {
        switch self {
        case .noCook: "No cooking"
        case .quick: "Under 15 min"
        case .standard: "Under 30 min"
        case .unlimited: "Any effort"
        }
    }

    var subtitle: String {
        switch self {
        case .noCook: "Assembly only: tinned, frozen, raw or ready to eat"
        case .quick: "One pan, nothing that needs simmering"
        case .standard: "Normal weeknight cooking"
        case .unlimited: "Include anything, long simmers and roasts too"
        }
    }

    var systemImage: String {
        switch self {
        case .noCook: "bolt.fill"
        case .quick: "timer"
        case .standard: "frying.pan.fill"
        case .unlimited: "flame.fill"
        }
    }
}

// MARK: - The profile

/// Everything the engine needs to know about what the user will and will not
/// eat, and when.
///
/// A value type so the onboarding draft and the persisted profile describe
/// themselves the same way, and so the filter can be tested without a store.
struct DietaryProfile: Hashable, Sendable {
    var pattern: DietaryPattern
    /// Groups avoided beyond the pattern: allergies, intolerances, faith.
    var exclusions: Set<FoodExclusion>
    /// Individual catalogue ids the user has banned. The escape hatch for
    /// "I know it fits, I still will not eat it".
    var blockedFoodIDs: Set<String>
    var schedule: EatingSchedule
    var prepEffort: PrepEffort
    /// Meals per week eaten away from home. Captured because it is a real part
    /// of a food budget, and disclosed in the audit as *not* modelled: the
    /// planner budgets home-cooked meals only.
    var mealsOutPerWeek: Int

    init(
        pattern: DietaryPattern = .omnivore,
        exclusions: Set<FoodExclusion> = [],
        blockedFoodIDs: Set<String> = [],
        schedule: EatingSchedule = .threeMeals,
        prepEffort: PrepEffort = .unlimited,
        mealsOutPerWeek: Int = 0
    ) {
        self.pattern = pattern
        self.exclusions = exclusions
        self.blockedFoodIDs = blockedFoodIDs
        self.schedule = schedule
        self.prepEffort = prepEffort
        self.mealsOutPerWeek = max(0, mealsOutPerWeek)
    }

    /// No restrictions at all: the default before onboarding asks anything.
    ///
    /// Note the effort default. It is `.unlimited`, not `.standard`, because a
    /// question nobody has been asked yet must not remove food from the
    /// catalogue: with `.standard` the "unrestricted" profile silently dropped
    /// dried lentils, brown rice and pork shoulder, which is a restriction the
    /// user never chose. `testUnrestrictedProfileAllowsTheWholeCatalogue` pins
    /// this down.
    static let unrestricted = DietaryProfile()

    /// True when the user has asked for anything to be left out, which is what
    /// decides whether the plan needs to be checked against the catalogue at all.
    var isRestricted: Bool {
        pattern != .omnivore || !exclusions.isEmpty || !blockedFoodIDs.isEmpty
    }

    /// Traits this profile refuses, from exclusions plus the pattern's own rules.
    var refusedTraits: FoodTraits {
        exclusions.reduce(into: FoodTraits([])) { $0.formUnion($1.trait) }
    }

    /// One-line description, used in the summary and the audit.
    var summary: String {
        var parts: [String] = [pattern.displayName]
        if !exclusions.isEmpty {
            parts.append(exclusions.map(\.displayName).sorted().joined(separator: ", ") + " avoided")
        }
        if !blockedFoodIDs.isEmpty {
            parts.append("\(blockedFoodIDs.count) food\(blockedFoodIDs.count == 1 ? "" : "s") blocked")
        }
        parts.append(schedule.displayName.lowercased())
        parts.append(prepEffort.displayName.lowercased())
        return parts.joined(separator: " · ")
    }
}
