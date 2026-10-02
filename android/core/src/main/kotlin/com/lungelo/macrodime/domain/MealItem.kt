/*
 * MealItem.kt
 * MacroDime
 *
 * The value types the food engine reasons about. FoodSnapshot is a read-only
 * copy of a stored food; MealItem is a composed meal. Nothing here knows about
 * Room or Compose, which is what makes the swap engine testable without a
 * database. Port of MacroDime/Domain/MealItem.swift.
 */
package com.lungelo.macrodime.domain

import java.util.UUID

/**
 * An immutable copy of one catalogue ingredient. The engine never holds a
 * database row; rows are mapped to snapshots at the boundary.
 */
data class FoodSnapshot(
    /** Stable catalogue id such as `canned-tuna-water`. Survives a database rebuild. */
    val id: String,
    val name: String,
    val section: GrocerySection,
    val category: FoodCategory,
    /**
     * The finer axis substitutions happen on. Equal to the category for every
     * category except vegetables, which are split into culinary families so
     * that broccoli is never offered in place of carrots. Defaulted from the
     * category, and a vegetable's default is Unclassified, which matches nothing.
     */
    val swapGroup: SwapGroup = SwapGroup.defaultFor(category),
    val costTier: BudgetTier,
    /** Cost of one serving, in USD. */
    val costPerServing: Double,
    /** Human-readable serving, such as `1 can (142 g drained)`. */
    val servingDescription: String,
    /** Serving mass in grams, used to build grocery quantities. */
    val servingGrams: Double,
    /** Macros for exactly one serving. */
    val nutrition: NutritionFacts,
    /**
     * Fullness per calorie, 0-100, loosely modelled on the Holt satiety index.
     * Breaks ties between candidates that are equally cheap and equally close.
     */
    val satietyIndex: Double,
    /**
     * Condiments and near-zero-calorie items are excluded from substitution:
     * scaling them produces nonsense, and swapping them saves nothing.
     */
    val isSwapCandidate: Boolean = true,
    /**
     * What the food is made of, in the terms a dietary restriction is phrased
     * in. Empty means plant-only with no declarable allergen.
     */
    val traits: FoodTraits = FoodTraits.NONE,
    /** Active preparation time in minutes. 0 means ready to eat as bought. */
    val prepMinutes: Int = 0,
) {
    /** A copy with dietary annotations filled in, the join between the catalogue and its lookup tables. */
    fun annotated(traits: FoodTraits, prepMinutes: Int) = copy(traits = traits, prepMinutes = prepMinutes)

    /** A copy at a different price per serving, used to put a published average in place of an estimate. */
    fun withCost(costPerServing: Double) = copy(costPerServing = costPerServing)

    /**
     * Protein grams bought per US dollar: the "budget powerhouse" number, and
     * what the swap engine is ultimately optimising. Showing it in another
     * currency is a conversion, not a relabelling.
     */
    val proteinPerCurrencyUnit: Double
        get() = if (costPerServing > 0) nutrition.protein / costPerServing else 0.0

    /** This food's price, tagged with the currency it is denominated in. */
    val price: Money get() = Money.catalogue(costPerServing)

    /** Calories bought per US dollar. */
    val caloriesPerCurrencyUnit: Double
        get() = if (costPerServing > 0) nutrition.calories / costPerServing else 0.0
}

/** One ingredient at a specific quantity inside a meal. */
data class Portion(
    val food: FoodSnapshot,
    /** Number of servings, quantised by the engine so nobody reads "1.37 cans of tuna". */
    val servings: Double = 1.0,
    val id: UUID = UUID.randomUUID(),
) {
    val nutrition: NutritionFacts get() = food.nutrition.scaled(servings)
    val cost: Double get() = food.costPerServing * servings
    val grams: Double get() = food.servingGrams * servings

    /** The serving multiplied out: `3 large eggs` for 1.5 servings of `2 large eggs`. */
    val quantityDescription: String
        get() = ServingMeasure.describe(food.servingDescription, servings)
}

/** A composed meal: a slot, a name, and the portions that make it up. */
data class MealItem(
    val name: String,
    val slot: MealSlot,
    val portions: List<Portion> = emptyList(),
    val id: UUID = UUID.randomUUID(),
) {
    val nutrition: NutritionFacts get() = portions.map { it.nutrition }.total()
    val cost: Double get() = portions.sumOf { it.cost }
    val isEmpty: Boolean get() = portions.isEmpty()

    /** Calorie-weighted mean satiety across the portions. An empty meal scores 0. */
    val satietyScore: Double
        get() {
            val calories = nutrition.calories
            if (calories <= 0) return 0.0
            return portions.sumOf { it.food.satietyIndex * it.nutrition.calories } / calories
        }

    /** The most expensive tier present. Drives the price chip on the meal card. */
    val effectiveTier: BudgetTier
        get() = portions.maxOfOrNull { it.food.costTier } ?: BudgetTier.Strict

    /** Replaces one portion in place, preserving order. Unchanged if the portion is not here. */
    fun replacing(portionId: UUID, replacement: Portion): MealItem {
        val index = portions.indexOfFirst { it.id == portionId }
        if (index < 0) return this
        return copy(portions = portions.toMutableList().also { it[index] = replacement })
    }

    /** Changes one portion's quantity. Used by the swap engine's rebalance pass. */
    fun updatingServings(portionId: UUID, servings: Double): MealItem {
        val index = portions.indexOfFirst { it.id == portionId }
        if (index < 0) return this
        return copy(portions = portions.toMutableList().also { it[index] = it[index].copy(servings = servings) })
    }

    fun portion(id: UUID): Portion? = portions.firstOrNull { it.id == id }
}

val Iterable<MealItem>.totalNutrition: NutritionFacts get() = map { it.nutrition }.total()
val Iterable<MealItem>.totalCost: Double get() = sumOf { it.cost }
