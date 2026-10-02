/*
 * SwapGroup.kt
 * MacroDime
 *
 * The second, finer axis the swap engine reasons about. Port of
 * MacroDime/Domain/SwapGroup.swift.
 *
 * Why FoodCategory is not enough: anchored on calories, *any* vegetable can
 * stand in for any other, so the engine proposed broccoli to carrots: macro
 * valid, and not food anybody would swap. SwapGroup answers the question the
 * engine actually needs answered, "what would a person accept in place of
 * this?" For six of the seven categories the answer is the category itself.
 * For vegetables it is a culinary family.
 *
 * The failure mode is deliberately one-sided. A vegetable with no family is
 * Unclassified, and Unclassified only ever matches itself, so forgetting to
 * classify a new ingredient costs a swap; it cannot produce a nonsense meal.
 */
package com.lungelo.macrodime.domain

/** The set of ingredients that may be substituted for one another. */
enum class SwapGroup(override val rawValue: String) : RawValued {
    // Mirrors of FoodCategory: one group per category, so behaviour outside
    // vegetables is unchanged.
    ProteinAnchor("proteinAnchor"),
    CarbBase("carbBase"),
    FatSource("fatSource"),
    Fruit("fruit"),
    Dairy("dairy"),
    Condiment("condiment"),

    // Vegetable families. Culinary, not botanical: cabbage and spinach are both
    // leafy greens to a cook; broccoli and cauliflower are both brassicas.

    /** Spinach, cabbage, chard, lettuce. Wilts and cooks fast. */
    LeafyGreen("leafyGreen"),

    /** Broccoli, cauliflower, kale, sprouts. Dense florets, longer cook. */
    Cruciferous("cruciferous"),

    /** Carrots, parsnip, beetroot. Sweet, firm, roasted or boiled. */
    Root("root"),

    /** Onion, garlic, leek. Flavour base, not a portion of vegetables. */
    Allium("allium"),

    /** Peppers, tomatoes, courgette. Fleshy, watery, sears rather than wilts. */
    Fruiting("fruiting"),

    /** Asparagus, green beans. Linear green vegetables. */
    Stem("stem"),

    /**
     * Frozen mixed bags. A bag is a whole side dish, not a single vegetable, so
     * it is its own group rather than a stand-in for any of its contents.
     */
    MixedFrozen("mixedFrozen"),

    /**
     * Not classified for substitution. Matches nothing but itself, which is why
     * this is the safe default for a vegetable nobody has categorised yet.
     */
    Unclassified("unclassified");

    val displayName: String
        get() = when (this) {
            ProteinAnchor -> "Protein"
            CarbBase -> "Carbs"
            FatSource -> "Fats"
            Fruit -> "Fruit"
            Dairy -> "Dairy"
            Condiment -> "Condiments"
            LeafyGreen -> "Leafy Greens"
            Cruciferous -> "Brassicas"
            Root -> "Root Vegetables"
            Allium -> "Onions & Aromatics"
            Fruiting -> "Fruiting Vegetables"
            Stem -> "Green Stems"
            MixedFrozen -> "Frozen Mixed Vegetables"
            Unclassified -> "Not swappable"
        }

    /** The role this group plays in a meal, for anything that still groups by category. */
    val category: FoodCategory
        get() = when (this) {
            ProteinAnchor -> FoodCategory.ProteinAnchor
            CarbBase -> FoodCategory.CarbBase
            FatSource -> FoodCategory.FatSource
            Fruit -> FoodCategory.Fruit
            Dairy -> FoodCategory.Dairy
            Condiment -> FoodCategory.Condiment
            LeafyGreen, Cruciferous, Root, Allium, Fruiting, Stem, MixedFrozen, Unclassified ->
                FoodCategory.Vegetable
        }

    fun belongsTo(category: FoodCategory): Boolean = this.category == category

    /** The groups that take part in substitution at all. */
    val isSubstitutable: Boolean get() = this != Unclassified

    companion object {
        /**
         * The group a food falls into when the catalogue does not say explicitly.
         *
         * Every category maps to itself. Vegetables map to [Unclassified] on
         * purpose: treating every vegetable as one pool is the bug this type was
         * introduced to fix, so the default must not resurrect it.
         */
        fun defaultFor(category: FoodCategory): SwapGroup = when (category) {
            FoodCategory.ProteinAnchor -> ProteinAnchor
            FoodCategory.CarbBase -> CarbBase
            FoodCategory.FatSource -> FatSource
            FoodCategory.Fruit -> Fruit
            FoodCategory.Dairy -> Dairy
            FoodCategory.Condiment -> Condiment
            FoodCategory.Vegetable -> Unclassified
        }
    }
}
