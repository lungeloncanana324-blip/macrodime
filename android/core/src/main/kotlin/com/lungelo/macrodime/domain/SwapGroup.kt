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
 * this?" The answer is a culinary family. Vegetables were split first; since
 * 2026-10-04 (Android) proteins, carbs, fats and dairy are too, because one
 * pool per category had the engine offering 5 eggs for a salmon fillet, tuna
 * in a yogurt bowl and cheddar for skyr. A hot main swaps with a hot main,
 * mince with mince, canned fish with canned fish, beans with beans.
 *
 * The failure mode is deliberately one-sided. A vegetable with no family is
 * Unclassified, and Unclassified only ever matches itself, so forgetting to
 * classify a new ingredient costs a swap; it cannot produce a nonsense meal.
 * The category-wide groups remain only as the default for a new food, and a
 * catalogue test fails the build if any curated food is left in one.
 */
package com.lungelo.macrodime.domain

/** The set of ingredients that may be substituted for one another. */
enum class SwapGroup(override val rawValue: String) : RawValued {
    // Mirrors of FoodCategory: the default for a food not yet given a family.
    ProteinAnchor("proteinAnchor"),
    CarbBase("carbBase"),
    FatSource("fatSource"),
    Fruit("fruit"),
    Dairy("dairy"),
    Condiment("condiment"),

    // Protein families, by how the food is eaten rather than what it is.

    /** Chicken, pork, steak, salmon, cod, shrimp, tofu: the centre of a hot plate. */
    MainProtein("mainProtein"),

    /** Ground beef: sauces, burgers, tacos. */
    Mince("mince"),

    /** Canned tuna and sardines: bowls, sandwiches, salads. */
    PantryFish("pantryFish"),

    /** Lentils, beans, chickpeas: stews, soups, bowls. */
    Legume("legume"),

    Egg("egg"),

    /** Greek yogurt: breakfast and snacks, never a dinner. */
    Yogurt("yogurt"),

    ProteinPowder("proteinPowder"),

    /** Sliced deli meat: sandwiches. */
    DeliMeat("deliMeat"),

    // Carb families.

    /** Rice and quinoa. */
    Grain("grain"),
    Pasta("pasta"),
    Potato("potato"),
    Bread("bread"),
    Oats("oats"),

    // Fat families.

    /** Cooking oils. */
    Oil("oil"),

    /** Peanut butter, nuts, seeds. */
    NutAndSeed("nutAndSeed"),
    Avocado("avocado"),

    // Dairy families.

    Milk("milk"),
    Cheese("cheese"),

    /** Cottage cheese and skyr. */
    CulturedDairy("culturedDairy"),

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
            MainProtein -> "Main Proteins"
            Mince -> "Mince"
            PantryFish -> "Canned Fish"
            Legume -> "Beans & Lentils"
            Egg -> "Eggs"
            Yogurt -> "Yogurt"
            ProteinPowder -> "Protein Powder"
            DeliMeat -> "Deli Meat"
            Grain -> "Grains"
            Pasta -> "Pasta"
            Potato -> "Potatoes"
            Bread -> "Bread"
            Oats -> "Oats"
            Oil -> "Oils"
            NutAndSeed -> "Nuts & Seeds"
            Avocado -> "Avocado"
            Milk -> "Milk"
            Cheese -> "Cheese"
            CulturedDairy -> "Cultured Dairy"
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
            ProteinAnchor, MainProtein, Mince, PantryFish, Legume, Egg, Yogurt, ProteinPowder, DeliMeat ->
                FoodCategory.ProteinAnchor
            CarbBase, Grain, Pasta, Potato, Bread, Oats -> FoodCategory.CarbBase
            FatSource, Oil, NutAndSeed, Avocado -> FoodCategory.FatSource
            Fruit -> FoodCategory.Fruit
            Dairy, Milk, Cheese, CulturedDairy -> FoodCategory.Dairy
            Condiment -> FoodCategory.Condiment
            LeafyGreen, Cruciferous, Root, Allium, Fruiting, Stem, MixedFrozen, Unclassified ->
                FoodCategory.Vegetable
        }

    fun belongsTo(category: FoodCategory): Boolean = this.category == category

    /** The groups that take part in substitution at all. */
    val isSubstitutable: Boolean get() = this != Unclassified

    /** True for the category-wide groups: a food here has not been given a culinary family yet. */
    val isCategoryDefault: Boolean
        get() = this == ProteinAnchor || this == CarbBase || this == FatSource || this == Dairy

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
