/*
 * BudgetFoodEngineTest.kt
 *
 * Port of MacroDimeTests/BudgetFoodEngineTests.swift: the swap engine's
 * contract (cheaper, within tolerance, same group, never trading up), plus the
 * two bugs that once made the feature not work at all.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.AtwaterFactor
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MacroDrift
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.domain.roundedHalfAway
import com.lungelo.macrodime.domain.totalCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetFoodEngineTest {

    private val engine = BudgetFoodEngine(catalog = FoodCatalog.reference)

    private fun food(id: String): FoodSnapshot =
        assertNotNull(FoodCatalog.referenceFood(id), "Missing catalogue item: $id")

    /** Salmon, rice, fresh broccoli, olive oil: 732 kcal / 43.2 P / 69.6 C / 31.5 F at $6.76. */
    private fun salmonDinner() = MealItem(
        name = "Salmon Dinner",
        slot = MealSlot.Dinner,
        portions = listOf("salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil").map { Portion(food(it)) },
    )

    // Baseline sanity

    @Test
    fun fixtureMatchesExpectedMacros() {
        val meal = salmonDinner()
        assertEquals(732.0, meal.nutrition.calories, 0.01)
        assertEquals(43.2, meal.nutrition.protein, 0.01)
        assertEquals(69.6, meal.nutrition.carbs, 0.01)
        assertEquals(31.5, meal.nutrition.fat, 0.01)
        assertEquals(6.76, meal.cost, 0.01)
    }

    @Test
    fun catalogueIdsAreUnique() {
        val ids = FoodCatalog.reference.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate catalogue id")
    }

    @Test
    fun catalogueHasFiftySevenFoods() {
        assertEquals(57, FoodCatalog.reference.size)
    }

    /** Label calories and Atwater calories should agree within real label slack. */
    @Test
    fun catalogueMacrosAreInternallyConsistent() {
        for (item in FoodCatalog.reference) {
            val implied = item.nutrition.caloriesFromMacros
            val stated = item.nutrition.calories
            if (stated <= 30) continue // trace items: noise dominates
            assertEquals(
                stated, implied, maxOf(stated * 0.20, 15.0),
                "${item.name}: stated $stated kcal vs $implied from macros",
            )
        }
    }

    // Anchoring: regression

    /**
     * Salmon carries more energy as fat (153 kcal) than protein (136 kcal), so
     * an energy rule matched it on fat and proposed a dozen cans of tuna.
     */
    @Test
    fun proteinAnchorIsMatchedOnProteinNotEnergy() {
        val salmon = food("salmon-fillet")
        assertTrue(
            salmon.nutrition.fat * AtwaterFactor.FAT > salmon.nutrition.protein * AtwaterFactor.PROTEIN,
            "Fixture no longer exercises the regression",
        )
        assertEquals(MacroAxis.Protein, engine.anchorAxis(salmon))

        // 34 g protein ÷ 36 g per can = 0.94: one can, not twelve.
        assertEquals(1.0, engine.matchedServings(Portion(salmon), food("canned-tuna-water")), 0.001)
    }

    @Test
    fun anchorAxisFollowsCategory() {
        assertEquals(MacroAxis.Carbs, engine.anchorAxis(food("white-rice")))
        assertEquals(MacroAxis.Fat, engine.anchorAxis(food("olive-oil")))
        assertEquals(MacroAxis.Protein, engine.anchorAxis(food("greek-yogurt-nonfat")))
    }

    @Test
    fun servingsAreQuantisedToTheStep() {
        val servings = engine.matchedServings(Portion(food("salmon-fillet")), food("eggs-large"))
        val steps = servings / SwapPolicy.DEFAULT.servingStep
        assertEquals(steps.roundedHalfAway(), steps, 0.0001, "Servings must land on a 0.25 step")
    }

    // The headline swap

    @Test
    fun salmonDinnerCanBeSwappedWithinTolerance() {
        val meal = salmonDinner()
        val swap = assertNotNull(engine.bestSwap(meal), "The flagship swap must be reachable")

        assertTrue(swap.swapped.cost < meal.cost)
        assertTrue(swap.savings > 0)
        assertTrue(swap.worstDrift <= SwapPolicy.DEFAULT.macroTolerance, "Drifted outside the 10% tolerance")
        // Verified by hand against the catalogue: better than 70% cheaper.
        assertTrue(swap.savingsFraction > 0.70)
    }

    /**
     * Parity with the iOS engine, as far as the two still agree. The README
     * prints the seven candidates the compiled Swift engine finds for the
     * salmon portion alone. Since 2026-10-04 Android groups proteins into
     * culinary families, so four of the seven (eggs, canned tuna, sardines,
     * Greek yogurt: not things a person puts on a dinner plate in place of
     * salmon) are no longer offered; iOS still offers them until it gets the
     * same change. The three that remain are hot mains, and they must still
     * match the compiled Swift engine to the cent: same servings, same saving,
     * same drift. Not in the iOS suite: it exists because this is a port.
     */
    @Test
    fun salmonCandidatesMatchTheCompiledSwiftEngine() {
        data class Row(val servings: Double, val savings: Double, val drift: Double)
        val expected = mapOf(
            "chicken-drumsticks" to Row(1.25, 3.98, 0.035),
            "pork-shoulder" to Row(1.25, 3.96, 0.032),
            "chicken-thighs" to Row(1.25, 3.69, 0.069),
        )

        val meal = salmonDinner()
        val options = engine.rankedReplacements(meal.portions.first(), meal)
        assertEquals(expected.keys, options.map { it.replacement.food.id }.toSet())
        for (option in options) {
            val row = expected.getValue(option.replacement.food.id)
            val id = option.replacement.food.id
            assertEquals(row.servings, option.replacement.servings, 0.0001, "$id servings")
            assertEquals(row.savings, option.savings, 0.005, "$id savings")
            assertEquals(row.drift, option.resultingDrift.worst, 0.0005, "$id drift")
        }
    }

    /**
     * The rebalance mechanics, on their own. Salmon to tuna is the clearest
     * case of a fat gap (about 49% short without the oil), but since culinary
     * families tuna is not offered for salmon at all, so these two tests turn
     * the families off: what they check is the arithmetic, not the menu.
     */
    private val anyGroupPolicy = SwapPolicy.DEFAULT.copy(restrictToSameSwapGroup = false)
    private val anyGroupEngine = BudgetFoodEngine(catalog = FoodCatalog.reference, policy = anyGroupPolicy)

    /** Without the rebalance pass, salmon to tuna leaves the meal about 49% short on fat. */
    @Test
    fun substitutionWithoutRebalancingFailsTheFatGate() {
        val policy = anyGroupPolicy.copy(allowsRebalancing = false)
        val plainEngine = BudgetFoodEngine(catalog = FoodCatalog.reference, policy = policy)

        val meal = salmonDinner()
        val salmonPortion = meal.portions.first()

        val tunaWithout = plainEngine.rankedReplacements(salmonPortion, meal)
            .firstOrNull { it.replacement.food.id == "canned-tuna-water" }
        assertNull(tunaWithout, "Tuna should fail the fat gate with no rebalance available")

        val tunaWith = assertNotNull(
            anyGroupEngine.rankedReplacements(salmonPortion, meal).firstOrNull { it.replacement.food.id == "canned-tuna-water" },
            "Salmon to Canned Tuna must be offered once rebalancing is allowed",
        )
        assertFalse(tunaWith.rebalanced.isEmpty(), "The swap should have re-portioned the oil")
        assertTrue(tunaWith.resultingDrift.worst <= policy.macroTolerance)
    }

    @Test
    fun rebalanceAdjustsTheFatSourceUpwards() {
        val meal = salmonDinner()
        val options = anyGroupEngine.rankedReplacements(meal.portions.first(), meal)

        val tuna = assertNotNull(options.firstOrNull { it.replacement.food.id == "canned-tuna-water" })
        val oil = assertNotNull(
            tuna.rebalanced.firstOrNull { "Olive Oil" in it.foodName },
            "Olive oil is the only fat lever in this meal",
        )
        assertTrue(oil.isIncrease, "Losing salmon's fat should raise the oil")
        assertTrue(oil.toServings > oil.fromServings)
    }

    /** Every proposed option, not just the winner, must honour the contract. */
    @Test
    fun allProposedOptionsHonourTheContract() {
        val meal = salmonDinner()
        val options = engine.rankedReplacements(meal.portions.first(), meal)

        assertFalse(options.isEmpty())
        for (option in options) {
            assertEquals(FoodCategory.ProteinAnchor, option.replacement.food.category, "Swapped outside the category")
            assertTrue(option.replacement.food.costTier <= BudgetTier.Strict, "Traded up a tier")
            assertTrue(option.resultingDrift.worst <= SwapPolicy.DEFAULT.macroTolerance)
            assertTrue(option.savings >= SwapPolicy.DEFAULT.minimumSavingsPerSwap)
            assertTrue(option.resultingMeal.cost < meal.cost)
        }
    }

    @Test
    fun optionsAreRankedBestFirst() {
        val meal = salmonDinner()
        val scores = engine.rankedReplacements(meal.portions.first(), meal).map { it.score }
        assertEquals(scores.sortedDescending(), scores, "Options must be ordered by score")
    }

    // Contract guarantees

    @Test
    fun swapNeverTradesUpATier() {
        val meal = MealItem(
            name = "Budget Breakfast",
            slot = MealSlot.Breakfast,
            portions = listOf("rolled-oats", "eggs-large", "banana").map { Portion(food(it)) },
        )
        val swap = engine.bestSwap(meal) ?: return
        for (portionSwap in swap.portionSwaps) {
            assertTrue(portionSwap.replacement.food.costTier <= portionSwap.original.food.costTier)
        }
        assertTrue(swap.swapped.cost < meal.cost)
    }

    @Test
    fun swapMealReturnsTheOriginalWhenNothingIsBetter() {
        // Canola oil is the cheapest fat in the catalogue; nothing undercuts it.
        val meal = MealItem(name = "Oil Only", slot = MealSlot.Snack, portions = listOf(Portion(food("canola-oil"))))
        assertEquals("canola-oil", engine.swapMeal(meal).portions.first().food.id)
        assertNull(engine.bestSwap(meal))
    }

    @Test
    fun emptyMealYieldsNoSwap() {
        val meal = MealItem(name = "Empty", slot = MealSlot.Lunch)
        assertNull(engine.bestSwap(meal))
        assertTrue(engine.swapMeal(meal).isEmpty)
    }

    @Test
    fun condimentsAreNeverSwapped() {
        val meal = MealItem(
            name = "Seasoned Rice",
            slot = MealSlot.Lunch,
            portions = listOf(Portion(food("white-rice")), Portion(food("soy-sauce"))),
        )
        assertTrue(engine.rankedReplacements(meal.portions.last(), meal).isEmpty())
    }

    /** The whole-meal tolerance must hold cumulatively, not per substitution. */
    @Test
    fun cumulativeDriftStaysWithinToleranceAcrossMultipleSwaps() {
        val meal = salmonDinner()
        val swap = assertNotNull(engine.bestSwap(meal))
        assertTrue(swap.portionSwaps.size > 1, "Fixture should trigger several swaps")

        val finalDrift = MacroDrift(meal.nutrition, swap.swapped.nutrition)
        assertTrue(finalDrift.worst <= SwapPolicy.DEFAULT.macroTolerance)
    }

    /** Fuzz: any protein and carb pair that yields a swap must obey both gates. */
    @Test
    fun everyGeneratedSwapObeysBothGates() {
        val proteins = FoodCatalog.reference.filter { it.category == FoodCategory.ProteinAnchor }
        val carbs = FoodCatalog.reference.filter { it.category == FoodCategory.CarbBase }

        for (protein in proteins) {
            for (carb in carbs) {
                val meal = MealItem(
                    name = "${protein.name} and ${carb.name}",
                    slot = MealSlot.Dinner,
                    portions = listOf(Portion(protein), Portion(carb)),
                )
                val swap = engine.bestSwap(meal) ?: continue

                assertTrue(swap.swapped.cost < meal.cost, "${meal.name}: swap did not save money")
                assertTrue(
                    swap.worstDrift <= SwapPolicy.DEFAULT.macroTolerance + 0.0001,
                    "${meal.name}: drifted to ${swap.worstDrift}",
                )
                for (portion in swap.swapped.portions) {
                    assertTrue(portion.servings >= SwapPolicy.DEFAULT.minimumServings)
                    assertTrue(portion.servings <= SwapPolicy.DEFAULT.maximumServings)
                }
            }
        }
    }

    // Drift maths

    @Test
    fun driftUsesAbsoluteFallbackForTraceMacros() {
        // A 1 g to 3 g fat move is 200% relatively, but trivial in practice.
        val baseline = NutritionFacts(calories = 400.0, protein = 40.0, carbs = 50.0, fat = 1.0)
        val candidate = NutritionFacts(calories = 400.0, protein = 40.0, carbs = 50.0, fat = 3.0)
        val drift = MacroDrift(baseline, candidate)
        assertEquals(0.25, drift.fat, 0.001) // 2 g ÷ 8 g floor
        assertTrue(drift.fat < 2.0)
    }

    @Test
    fun identicalMealsHaveZeroDrift() {
        val meal = salmonDinner()
        val drift = MacroDrift(meal.nutrition, meal.nutrition)
        assertEquals(0.0, drift.worst, 0.0001)
        assertTrue(drift.isWithin(0.10))
    }

    // Discovery

    @Test
    fun budgetPowerhousesAreRankedByProteinPerCurrencyUnit() {
        val ranked = engine.budgetPowerhouses(tier = BudgetTier.Strict, limit = 5)
        assertFalse(ranked.isEmpty())
        val values = ranked.map { it.proteinPerCurrencyUnit }
        assertEquals(values.sortedDescending(), values)
        for (item in ranked) {
            assertEquals(BudgetTier.Strict, item.costTier)
            assertEquals(FoodCategory.ProteinAnchor, item.category)
        }
    }

    // Grocery aggregation

    @Test
    fun groceryListAggregatesRepeatedIngredients() {
        val tuna = food("canned-tuna-water")
        val lunch = MealItem(name = "Lunch", slot = MealSlot.Lunch, portions = listOf(Portion(tuna, 1.0)))
        val dinner = MealItem(name = "Dinner", slot = MealSlot.Dinner, portions = listOf(Portion(tuna, 2.0)))

        val lines = GroceryListBuilder.lines(listOf(lunch, dinner))
        assertEquals(1, lines.size, "The same ingredient must collapse to one line")
        val line = lines.first()
        assertEquals(3.0, line.totalServings, 0.001)
        assertEquals(tuna.costPerServing * 3, line.estimatedCost, 0.001)
        assertEquals(setOf("Lunch", "Dinner"), line.usedInMeals.toSet())
    }

    @Test
    fun groceryListGroupsIntoStoreWalkOrder() {
        val meal = salmonDinner()
        val groups = GroceryListBuilder.grouped(listOf(meal))
        val order = groups.map { it.section.aisleOrder }
        assertEquals(order.sorted(), order, "Sections must follow the store walk")
        assertEquals(meal.cost, groups.sumOf { it.subtotal }, 0.001)
    }

    @Test
    fun groceryTotalMatchesMealCost() {
        val meals = listOf(salmonDinner())
        assertEquals(meals.totalCost, GroceryListBuilder.estimatedTotal(meals), 0.001)
    }
}
