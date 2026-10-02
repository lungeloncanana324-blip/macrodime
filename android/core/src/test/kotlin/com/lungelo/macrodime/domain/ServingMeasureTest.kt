/*
 * ServingMeasureTest.kt
 *
 * Port of MacroDimeTests/ServingMeasureTests.swift: the quantity text the first
 * screenshots exposed ("1.5 × 2 large eggs"), pinned down, plus a round-trip
 * over every catalogue serving so a new food the parser cannot read fails here.
 */
package com.lungelo.macrodime.domain

import com.lungelo.macrodime.engine.FoodCatalog
import com.lungelo.macrodime.engine.GroceryLine
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class ServingMeasureTest {

    private fun food(id: String): FoodSnapshot = assertNotNull(FoodCatalog.food(id), "no catalogue food $id")

    @Test
    fun everyCatalogueServingParsesAndRendersBackUnchanged() {
        for (food in FoodCatalog.all) {
            val measure = ServingMeasure.parse(food.servingDescription)
                ?: fail("${food.id}: cannot read the serving \"${food.servingDescription}\"")
            assertEquals(food.servingDescription, measure.text(1.0), food.id)
        }
    }

    // Counted servings

    @Test
    fun eggsMultiplyIntoACountOfEggs() {
        assertEquals("3 large eggs", ServingMeasure.describe("2 large eggs", 1.5))
        assertEquals("1 large egg", ServingMeasure.describe("2 large eggs", 0.5))
        assertEquals("2½ large eggs", ServingMeasure.describe("2 large eggs", 1.25))
    }

    @Test
    fun cansPluraliseAndCarryTheirWeight() {
        assertEquals("2 cans (284 g drained)", ServingMeasure.describe("1 can (142 g drained)", 2.0))
        assertEquals("1¼ cans (178 g drained)", ServingMeasure.describe("1 can (142 g drained)", 1.25))
        assertEquals("½ can (71 g drained)", ServingMeasure.describe("1 can (142 g drained)", 0.5))
    }

    @Test
    fun unitsThatReadTheSameInBothNumbersStayPut() {
        assertEquals("2 tbsp (28 g)", ServingMeasure.describe("1 tbsp (14 g)", 2.0))
        assertEquals("2 medium (236 g)", ServingMeasure.describe("1 medium (118 g)", 2.0))
        assertEquals("1 medium (200 g)", ServingMeasure.describe("½ medium (100 g)", 2.0))
        assertEquals("1 slice", ServingMeasure.describe("2 slices", 0.5))
    }

    // Weighed servings

    @Test
    fun weighedServingsScaleTheWeightAndKeepTheState() {
        assertEquals("300 g raw", ServingMeasure.describe("300 g raw", 1.0))
        assertEquals("141 g raw", ServingMeasure.describe("113 g raw", 1.25))
        assertEquals("1.2 kg raw", ServingMeasure.describe("300 g raw", 4.0))
        assertEquals("1.2 L", ServingMeasure.describe("240 ml", 5.0))
    }

    /**
     * Not in the iOS suite. Swift rounds a half away from zero and Kotlin's
     * round() rounds it to even: 176.5 g must read 177 g, as it does on iOS.
     */
    @Test
    fun halvesRoundAwayFromZeroAsOnIOS() {
        // 353 × 0.5 is exactly 176.5 in binary, so this tests the rounding rule
        // and not floating-point noise.
        assertEquals("177 g", ServingMeasure.describe("353 g", 0.5))
        assertEquals(177.0, 176.5.roundedHalfAway())
        assertEquals(178.0, 177.5.roundedHalfAway())
        assertEquals(-3.0, (-2.5).roundedHalfAway())
        assertEquals(0.0, 0.49999999999999994.roundedHalfAway())
    }

    // The two screens

    @Test
    fun planAndGroceryListUseTheSameWords() {
        assertEquals("3 large eggs", Portion(food("eggs-large"), 1.5).quantityDescription)

        // The grocery list used to print "1 × 300 g raw" even at one serving.
        assertEquals("300 g raw", GroceryLine(food("potatoes"), 1.0, emptyList()).quantityDescription)
        assertEquals("6 cans (852 g drained)", GroceryLine(food("canned-tuna-water"), 6.0, emptyList()).quantityDescription)
    }

    // Text the parser cannot read

    @Test
    fun userTextFallsBackToNamingTheServings() {
        assertEquals("a handful", ServingMeasure.describe("a handful", 1.0))
        assertEquals("2 servings (a handful)", ServingMeasure.describe("a handful", 2.0))
        assertEquals("½ serving (a handful)", ServingMeasure.describe("a handful", 0.5))
    }

    @Test
    fun phrasesWithOfInflectTheNounBeforeIt() {
        assertEquals("2 bowls of soup", ServingMeasure.describe("1 bowl of soup", 2.0))
        assertEquals("1 cup of rice", ServingMeasure.describe("2 cups of rice", 0.5))
    }

    // Numbers

    @Test
    fun countsReadAsKitchenFractions() {
        assertEquals("1", ServingMeasure.count(1.0))
        assertEquals("¼", ServingMeasure.count(0.25))
        assertEquals("2½", ServingMeasure.count(2.5))
        assertEquals("0.33", ServingMeasure.count(1.0 / 3.0))
    }

    @Test
    fun caloriesAndGramsAreGroupedLikeTheRestOfTheApp() {
        assertEquals("1,842 kcal", DisplayFormat.calories(1842.4, Locale.US))
        assertEquals("530 kcal", DisplayFormat.calories(530.0, Locale.US))
        assertEquals("149 g", DisplayFormat.grams(148.6, Locale.US))
    }
}
