/*
 * PriceTableTest.kt
 *
 * Port of MacroDimeTests/PriceTableTests.swift. The sourced prices are checked
 * by formula rather than by value: the table is regenerated monthly, so these
 * assert that it fits the catalogue, that the unit arithmetic is right, and
 * that a sourced price lands in a plausible band around the estimate.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.Portion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceTableTest {

    private fun reference(id: String): FoodSnapshot =
        assertNotNull(FoodCatalog.referenceFood(id), "no catalogue food $id")

    // The table fits the catalogue

    @Test
    fun everyEntryIsACatalogueFoodAndListedOnce() {
        val ids = PriceTable.entries.map { it.foodId }
        assertEquals(ids.size, ids.toSet().size, "a food is priced twice")
        for (id in ids) {
            assertNotNull(FoodCatalog.referenceFood(id), "$id is priced but not in the catalogue")
        }
    }

    @Test
    fun everyEntryProducesACostForItsFood() {
        for (entry in PriceTable.entries) {
            val food = reference(entry.foodId)
            val cost = assertNotNull(entry.costPerServing(food), "${entry.foodId}: no cost in ${entry.unit}")
            assertTrue(cost > 0, entry.foodId)
            assertTrue(entry.servingYield > 0, entry.foodId)
            assertTrue(entry.servingYield <= 1, "${entry.foodId}: a yield above 1 means buying less than is eaten")
            assertFalse(entry.period.isEmpty(), entry.foodId)
        }
    }

    /** A unit mistake is off by 12, 3.8 or 2.2. Real market gaps sit well inside a factor of 3. */
    @Test
    fun sourcedPricesStayWithinAFactorOfThreeOfTheEstimates() {
        for (entry in PriceTable.entries) {
            val food = reference(entry.foodId)
            val cost = assertNotNull(entry.costPerServing(food))
            val ratio = cost / food.costPerServing
            assertTrue(ratio in (1.0 / 3.0)..3.0, "${entry.foodId}: sourced $cost against estimate ${food.costPerServing}")
        }
    }

    // Unit arithmetic

    @Test
    fun perDozenPriceIsSplitByTheEggsInAServing() {
        val eggs = reference("eggs-large")
        val price = SourcedPrice(eggs.id, PriceSource.Bls("test", "test"), "test", 3.00, PriceUnit.Dozen, 1.0)
        // "2 large eggs" at $3.00 a dozen.
        assertEquals(0.50, assertNotNull(price.costPerServing(eggs)), 0.0001)
    }

    @Test
    fun perGallonPriceUsesTheServingVolume() {
        val milk = reference("whole-milk")
        val price = SourcedPrice(milk.id, PriceSource.Bls("test", "test"), "test", 4.00, PriceUnit.Gallon, 1.0)
        assertEquals(4.00 * 240 / 3785.411784, assertNotNull(price.costPerServing(milk)), 0.0001)
    }

    @Test
    fun yieldChargesForWhatIsBoughtNotWhatIsEaten() {
        val beans = reference("canned-black-beans")
        val price = SourcedPrice(beans.id, PriceSource.Ers("test", "test", 2023), "test", 1.00, PriceUnit.Pound, 0.65)
        // 130 g drained is 200 g of can contents at a 0.65 drained yield.
        assertEquals(200 / PriceTable.GRAMS_PER_POUND, assertNotNull(price.costPerServing(beans)), 0.0001)
    }

    @Test
    fun aPriceInTheWrongUnitIsRefusedRatherThanGuessed() {
        val rice = reference("white-rice")
        val perDozen = SourcedPrice(rice.id, PriceSource.Bls("test", "test"), "test", 3.00, PriceUnit.Dozen, 1.0)
        assertNull(perDozen.costPerServing(rice))
    }

    // The two catalogues

    @Test
    fun theAppCatalogueDiffersFromTheReferenceOnlyInSourcedPrices() {
        assertEquals(FoodCatalog.reference.map { it.id }, FoodCatalog.all.map { it.id })
        for ((live, frozen) in FoodCatalog.all.zip(FoodCatalog.reference)) {
            assertEquals(frozen, live.withCost(frozen.costPerServing), "${live.id} changed beyond its price")
            val entry = PriceTable.entryFor(live.id)
            if (entry != null) {
                assertEquals(assertNotNull(entry.costPerServing(frozen)), live.costPerServing, 0.000001)
            } else {
                assertEquals(frozen.costPerServing, live.costPerServing, "${live.id} has no source but moved")
            }
        }
    }

    @Test
    fun aUserCreatedFoodKeepsItsOwnPrice() {
        val rice = reference("white-rice")
        val custom = FoodSnapshot(
            id = "user-rice", name = "My Rice", section = rice.section, category = rice.category,
            costTier = rice.costTier, costPerServing = 9.99, servingDescription = "1 bowl",
            servingGrams = 200.0, nutrition = rice.nutrition, satietyIndex = rice.satietyIndex,
        )
        assertEquals(9.99, PriceTable.priced(custom).costPerServing)
    }

    // The engine at live prices

    @Test
    fun theSwapContractHoldsAtLivePrices() {
        val engine = BudgetFoodEngine(catalog = FoodCatalog.all)
        val meal = MealItem(
            name = "Salmon Dinner",
            slot = MealSlot.Dinner,
            portions = listOf("salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil")
                .map { Portion(assertNotNull(FoodCatalog.food(it))) },
        )
        val swap = engine.bestSwap(meal) ?: return
        assertTrue(swap.swapped.cost < meal.cost)
        assertTrue(swap.worstDrift <= SwapPolicy.DEFAULT.macroTolerance)
    }

    // What Settings says

    @Test
    fun summaryCountsAddUpAndNameTheSources() {
        val summary = PriceTable.summary
        assertEquals(PriceTable.entries.size, summary.sourcedCount)
        assertEquals(FoodCatalog.reference.size, summary.totalCount)
        assertEquals(summary.totalCount, summary.sourcedCount + summary.estimatedCount)
        assertTrue("Bureau of Labor Statistics" in summary.explanation)
        assertTrue("USDA" in summary.explanation)
        assertTrue("never goes online" in summary.explanation)
        assertTrue(summary.period in summary.explanation)
    }
}
