/*
 * GroceryListBuilder.kt
 * MacroDime
 *
 * Collapses a week of planned meals into one shopping list, aggregated per
 * ingredient and grouped by supermarket section. Pure: the app's repository
 * handles persistence and check-off state. Port of
 * MacroDime/Engine/GroceryListBuilder.swift.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.GrocerySection
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.ServingMeasure
import com.lungelo.macrodime.domain.roundToIntHalfAway

/** One ingredient's total requirement across every meal it appears in. */
data class GroceryLine(
    val food: FoodSnapshot,
    /** Sum of servings across the whole plan. */
    val totalServings: Double,
    /** Names of the meals that need it, for the "used in" caption. */
    val usedInMeals: List<String>,
) {
    /** The catalogue id: stable, and the natural aggregation key. */
    val id: String get() = food.id
    val section: GrocerySection get() = food.section
    val name: String get() = food.name
    val estimatedCost: Double get() = food.costPerServing * totalServings
    val totalGrams: Double get() = food.servingGrams * totalServings

    /** `6 cans (852 g drained)`: what to actually put in the trolley. */
    val quantityDescription: String
        get() = ServingMeasure.describe(food.servingDescription, totalServings)

    /** `852 g total`. */
    val massDescription: String
        get() = if (totalGrams >= 1_000) {
            "${DisplayFormat.number(totalGrams / 1_000, 1)} kg total"
        } else {
            "${totalGrams.roundToIntHalfAway()} g total"
        }
}

/** A section of the shopping list, ready to render as one group. */
data class GrocerySectionGroup(val section: GrocerySection, val lines: List<GroceryLine>) {
    val subtotal: Double get() = lines.sumOf { it.estimatedCost }
}

object GroceryListBuilder {

    /**
     * One line per distinct ingredient, aggregated by catalogue id, so a can of
     * tuna on Monday and two on Thursday become one "3 cans" line rather than
     * entries the user has to add up in the aisle.
     */
    fun lines(meals: List<MealItem>): List<GroceryLine> {
        val servingsByFood = LinkedHashMap<String, Double>()
        val foodsById = HashMap<String, FoodSnapshot>()
        val mealNamesByFood = HashMap<String, MutableList<String>>()

        for (meal in meals) {
            for (portion in meal.portions) {
                val key = portion.food.id
                servingsByFood[key] = (servingsByFood[key] ?: 0.0) + portion.servings
                foodsById[key] = portion.food
                val names = mealNamesByFood.getOrPut(key) { mutableListOf() }
                if (meal.name !in names) names += meal.name
            }
        }

        return servingsByFood.mapNotNull { (key, servings) ->
            val food = foodsById[key] ?: return@mapNotNull null
            if (servings <= 0) return@mapNotNull null
            GroceryLine(food, servings, mealNamesByFood[key].orEmpty())
        }
            // Within a section, most expensive first: the lines worth
            // scrutinising sit at the top.
            .sortedWith(
                compareBy<GroceryLine> { it.section.aisleOrder }
                    .thenByDescending { it.estimatedCost }
                    .thenBy { it.name },
            )
    }

    /** The same data grouped into store-walk order. */
    fun grouped(meals: List<MealItem>): List<GrocerySectionGroup> {
        val all = lines(meals)
        return GrocerySection.entries
            .sortedBy { it.aisleOrder }
            .mapNotNull { section ->
                val sectionLines = all.filter { it.section == section }
                if (sectionLines.isEmpty()) null else GrocerySectionGroup(section, sectionLines)
            }
    }

    /** Estimated total spend for the plan. */
    fun estimatedTotal(meals: List<MealItem>): Double = lines(meals).sumOf { it.estimatedCost }
}
