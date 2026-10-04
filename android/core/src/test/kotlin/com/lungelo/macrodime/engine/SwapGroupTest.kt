/*
 * SwapGroupTest.kt
 *
 * Port of MacroDimeTests/SwapGroupTests.swift: the vegetable gap, closed. The
 * engine used to accept any vegetable in place of any other, so a salmon dinner
 * was offered carrots for fresh broccoli. These fail loudly if that comes back,
 * including through a new vegetable added without a culinary family.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.GrocerySection
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.domain.SwapGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwapGroupTest {

    private val engine = BudgetFoodEngine(catalog = FoodCatalog.reference)

    private fun food(id: String): FoodSnapshot = assertNotNull(FoodCatalog.referenceFood(id), "Missing catalogue item: $id")

    private fun salmonDinner() = MealItem(
        name = "Salmon Dinner",
        slot = MealSlot.Dinner,
        portions = listOf("salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil").map { Portion(food(it)) },
    )

    private fun broccoliPortion(meal: MealItem): Portion =
        assertNotNull(meal.portions.firstOrNull { it.food.id == "fresh-broccoli" })

    // Catalogue invariants

    @Test
    fun everyVegetableHasACulinaryFamily() {
        val vegetables = FoodCatalog.reference.filter { it.category == FoodCategory.Vegetable }
        assertFalse(vegetables.isEmpty())
        for (vegetable in vegetables) {
            assertNotEquals(SwapGroup.Unclassified, vegetable.swapGroup, "${vegetable.name} has no culinary family")
        }
    }

    @Test
    fun everySwapGroupMapsBackToItsOwnCategory() {
        for (item in FoodCatalog.reference) {
            assertEquals(item.category, item.swapGroup.category, "${item.name}: group ${item.swapGroup} belongs elsewhere")
        }
    }

    /**
     * Since 2026-10-04 proteins, carbs, fats and dairy have culinary families
     * too. A curated food left in a category-wide group would swap with every
     * other food in its category, which is the bug the families fix.
     */
    @Test
    fun everyProteinCarbFatAndDairyHasACulinaryFamily() {
        for (item in FoodCatalog.reference) {
            assertFalse(item.swapGroup.isCategoryDefault, "${item.name} has no culinary family (${item.swapGroup})")
        }
    }

    // Swaps a person would accept

    private fun offeredFor(id: String, slot: MealSlot, vararg others: String): Set<String> {
        val meal = MealItem("Test", slot, (listOf(id) + others).map { Portion(food(it)) })
        return engine.rankedReplacements(meal.portions.first(), meal).map { it.replacement.food.id }.toSet()
    }

    @Test
    fun aSalmonDinnerIsNeverOfferedEggsYogurtOrCannedFish() {
        val offered = offeredFor("salmon-fillet", MealSlot.Dinner, "white-rice", "fresh-broccoli", "olive-oil")
        assertTrue(offered.isNotEmpty(), "salmon should still have cheaper hot mains to swap to")
        for (wrong in listOf("eggs-large", "greek-yogurt-nonfat", "canned-tuna-water", "canned-sardines")) {
            assertFalse(wrong in offered, "salmon was offered $wrong")
        }
        for (id in offered) assertEquals(SwapGroup.MainProtein, food(id).swapGroup, "salmon was offered $id")
    }

    @Test
    fun aYogurtBowlIsNeverOfferedFishOrMeat() {
        val offered = offeredFor("greek-yogurt-nonfat", MealSlot.Breakfast, "frozen-berries", "sunflower-seeds")
        for (id in offered) assertEquals(SwapGroup.Yogurt, food(id).swapGroup, "yogurt was offered $id")
    }

    @Test
    fun cannedTunaIsNeverOfferedEggs() {
        val offered = offeredFor("canned-tuna-water", MealSlot.Lunch, "white-rice", "carrots")
        assertFalse("eggs-large" in offered)
        for (id in offered) assertEquals(SwapGroup.PantryFish, food(id).swapGroup, "tuna was offered $id")
    }

    @Test
    fun dairyNeverSwapsAcrossFamilies() {
        val offered = offeredFor("skyr", MealSlot.Snack, "frozen-berries")
        assertFalse("cheddar-block" in offered || "whole-milk" in offered, "skyr was offered $offered")
    }

    // The reported bug

    @Test
    fun broccoliAndCarrotsShareTheCategoryThatUsedToAllowTheSwap() {
        val broccoli = food("fresh-broccoli")
        val carrots = food("carrots")
        assertEquals(broccoli.category, carrots.category)
        assertEquals(FoodCategory.Vegetable, broccoli.category)
        assertNotEquals(broccoli.swapGroup, carrots.swapGroup)
    }

    @Test
    fun vegetableSwapsStayInsideTheFamily() {
        for (vegetable in FoodCatalog.reference.filter { it.category == FoodCategory.Vegetable }) {
            val meal = MealItem(name = "Side", slot = MealSlot.Dinner, portions = listOf(Portion(vegetable)))
            for (replacement in engine.rankedReplacements(meal.portions[0], meal)) {
                assertEquals(
                    vegetable.swapGroup,
                    replacement.replacement.food.swapGroup,
                    "${vegetable.name} was offered ${replacement.replacement.food.name}",
                )
            }
        }
    }

    @Test
    fun carrotsAreNotOfferedForFreshBroccoli() {
        val meal = salmonDinner()
        val replacements = engine.rankedReplacements(broccoliPortion(meal), meal)
        assertFalse(replacements.isEmpty(), "The fixture no longer produces any swap at all")
        assertFalse(replacements.any { it.replacement.food.id == "carrots" }, "Carrots are not a substitute for broccoli")

        val applied = assertNotNull(engine.bestSwap(meal))
        assertFalse(applied.swapped.portions.any { it.food.id == "carrots" })
    }

    /** Proof that the family gate, and nothing else, is what removes carrots. */
    @Test
    fun carrotsAreOfferedOnlyWhenTheFamilyGateIsLifted() {
        val ungated = BudgetFoodEngine(FoodCatalog.reference, SwapPolicy.DEFAULT.copy(restrictToSameSwapGroup = false))
        val meal = salmonDinner()
        assertTrue(
            ungated.rankedReplacements(broccoliPortion(meal), meal).any { it.replacement.food.id == "carrots" },
            "Carrots should pass every macro gate, which is why a culinary gate is needed",
        )
    }

    @Test
    fun frozenBroccoliStillReplacesFreshBroccoli() {
        val meal = salmonDinner()
        val frozen = assertNotNull(
            engine.rankedReplacements(broccoliPortion(meal), meal).firstOrNull { it.replacement.food.id == "frozen-broccoli" },
            "Fresh broccoli must still be swappable for frozen broccoli",
        )
        assertTrue(frozen.savings > 0)
        assertTrue(frozen.resultingDrift.worst <= SwapPolicy.DEFAULT.macroTolerance + 0.0001)
    }

    @Test
    fun leafyGreensTradePlacesWithinTheirFamily() {
        val spinach = food("baby-spinach")
        val cabbage = food("cabbage")
        assertEquals(SwapGroup.LeafyGreen, spinach.swapGroup)
        assertEquals(SwapGroup.LeafyGreen, cabbage.swapGroup)
        assertTrue(cabbage.costPerServing < spinach.costPerServing)

        val meal = MealItem(
            name = "Dinner",
            slot = MealSlot.Dinner,
            portions = listOf(Portion(food("chicken-thighs")), Portion(food("white-rice")), Portion(spinach), Portion(food("olive-oil"))),
        )
        val portion = assertNotNull(meal.portions.firstOrNull { it.food.id == "baby-spinach" })
        assertTrue(
            engine.rankedReplacements(portion, meal).any { it.replacement.food.id == "cabbage" },
            "Cabbage is a leaf-for-leaf substitute for spinach",
        )
    }

    @Test
    fun unclassifiedFoodIsNeverSubstituted() {
        val mystery = FoodSnapshot(
            id = "mystery-vegetable",
            name = "Mystery Vegetable",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            costTier = BudgetTier.Strict,
            costPerServing = 0.20,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 40.0, protein = 2.0, carbs = 8.0, fat = 0.3),
            satietyIndex = 80.0,
        )
        assertEquals(SwapGroup.Unclassified, mystery.swapGroup)

        val withMystery = BudgetFoodEngine(catalog = FoodCatalog.reference + mystery)
        val side = MealItem(name = "Side", slot = MealSlot.Dinner, portions = listOf(Portion(food("fresh-broccoli"))))
        assertFalse(withMystery.rankedReplacements(side.portions[0], side).any { it.replacement.food.id == mystery.id })

        val other = MealItem(name = "Mystery", slot = MealSlot.Dinner, portions = listOf(Portion(mystery)))
        assertTrue(withMystery.rankedReplacements(other.portions[0], other).isEmpty())
    }

    /** The README's salmon dinner still saves money, now with a hot main rather than a tin. */
    @Test
    fun proteinSwapFromTheReadmeIsStillOffered() {
        val meal = salmonDinner()
        val salmon = assertNotNull(meal.portions.firstOrNull { it.food.id == "salmon-fillet" })

        val thighs = assertNotNull(
            engine.rankedReplacements(salmon, meal).firstOrNull { it.replacement.food.id == "chicken-thighs" },
            "Salmon should still be swappable for chicken thighs",
        )
        assertEquals(salmon.id, thighs.original.id)
        assertTrue(thighs.savings > 0)

        val swap = assertNotNull(engine.bestSwap(meal))
        assertTrue(swap.swapped.cost < meal.cost)
        assertTrue(swap.worstDrift <= SwapPolicy.DEFAULT.macroTolerance + 0.0001)
    }
}
