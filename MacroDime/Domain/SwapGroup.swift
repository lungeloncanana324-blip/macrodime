//
//  SwapGroup.swift
//  MacroDime
//
//  The second, finer axis the swap engine reasons about.
//
//  ## Why `FoodCategory` is not enough
//
//  `FoodCategory` answers "what nutritional role does this play in the meal":
//  protein, carb, fat, vegetable. That is the right vocabulary for the UI and
//  for a grocery list, but it is too coarse to substitute on. Anchored on
//  calories, *any* vegetable can stand in for any other, so the engine happily
//  proposed broccoli → carrots: macro-valid, and not food anybody would swap.
//
//  `SwapGroup` answers the question the engine actually needs answered: "what
//  would a person accept in place of this?" For six of the seven categories the
//  answer is the category itself. For vegetables it is a culinary family, which
//  is why this type exists.
//
//  The failure mode is deliberately one-sided. A vegetable with no family is
//  `.unclassified`, and `.unclassified` only ever matches itself, so an
//  unclassified food is *never* substituted. Forgetting to classify a new
//  ingredient costs you a swap; it cannot produce a nonsense meal. A catalogue
//  test enforces that every vegetable carries a real family.
//

import Foundation

/// The set of ingredients that may be substituted for one another.
enum SwapGroup: String, Codable, CaseIterable, Identifiable, Sendable {
    // MARK: Mirrors of FoodCategory
    //
    // One group per category, so behaviour outside vegetables is unchanged.
    case proteinAnchor
    case carbBase
    case fatSource
    case fruit
    case dairy
    case condiment

    // MARK: Vegetable families
    //
    // Culinary families, not botanical ones. Cabbage and spinach are both leafy
    // greens to a cook; broccoli and cauliflower are both brassicas. Anyone
    // replacing one with the other in a pan would call it a sensible swap.

    /// Spinach, cabbage, chard, lettuce. Wilts and cooks fast.
    case leafyGreen
    /// Broccoli, cauliflower, kale, sprouts. Dense florets, longer cook.
    case cruciferous
    /// Carrots, parsnip, beetroot. Sweet, firm, roasted or boiled.
    case root
    /// Onion, garlic, leek. Flavour base, not a portion of vegetables.
    case allium
    /// Peppers, tomatoes, courgette. Fleshy, watery, sears rather than wilts.
    case fruiting
    /// Asparagus, green beans. Linear green vegetables.
    case stem
    /// Frozen mixed bags. A bag is a whole side dish, not a single vegetable,
    /// so it is its own group rather than a stand-in for any of its contents.
    case mixedFrozen
    /// Not classified for substitution. Matches nothing but itself, which is
    /// why this is the safe default for a vegetable nobody has categorised yet.
    case unclassified

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .proteinAnchor: "Protein"
        case .carbBase: "Carbs"
        case .fatSource: "Fats"
        case .fruit: "Fruit"
        case .dairy: "Dairy"
        case .condiment: "Condiments"
        case .leafyGreen: "Leafy Greens"
        case .cruciferous: "Brassicas"
        case .root: "Root Vegetables"
        case .allium: "Onions & Aromatics"
        case .fruiting: "Fruiting Vegetables"
        case .stem: "Green Stems"
        case .mixedFrozen: "Frozen Mixed Vegetables"
        case .unclassified: "Not swappable"
        }
    }

    /// The role this group plays in a meal, for anything that still groups by
    /// category: the ingredient picker, the grocery list, the meal breakdown.
    var category: FoodCategory {
        switch self {
        case .proteinAnchor: .proteinAnchor
        case .carbBase: .carbBase
        case .fatSource: .fatSource
        case .fruit: .fruit
        case .dairy: .dairy
        case .condiment: .condiment
        case .leafyGreen, .cruciferous, .root, .allium, .fruiting, .stem, .mixedFrozen,
             .unclassified:
            .vegetable
        }
    }

    /// Whether this group belongs to the given category. Used by the picker to
    /// show "Vegetables" as one filter without losing the families behind it.
    func belongs(to category: FoodCategory) -> Bool { self.category == category }

    /// The group a food falls into when the catalogue does not say explicitly.
    ///
    /// Every category maps to itself. Vegetables map to `.unclassified` on
    /// purpose: the old behaviour (treat every vegetable as one pool) is the bug
    /// this type was introduced to fix, so the default must not resurrect it.
    static func `default`(for category: FoodCategory) -> SwapGroup {
        switch category {
        case .proteinAnchor: .proteinAnchor
        case .carbBase: .carbBase
        case .fatSource: .fatSource
        case .fruit: .fruit
        case .dairy: .dairy
        case .condiment: .condiment
        case .vegetable: .unclassified
        }
    }

    /// The groups that take part in substitution at all.
    var isSubstitutable: Bool { self != .unclassified }
}
