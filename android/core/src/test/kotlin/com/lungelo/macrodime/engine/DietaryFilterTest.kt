/*
 * DietaryFilterTest.kt
 *
 * Port of MacroDimeTests/DietaryFilterTests.swift. Half of these check the
 * *data* rather than the code: a missing trait is not a missing suggestion, it
 * is an unsafe one. The rest take the restrictive combinations real users
 * choose and ask whether anything edible survives.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.FoodTraits
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.domain.SwapGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DietaryFilterTest {

    private val catalog = FoodCatalog.all

    private fun food(id: String): FoodSnapshot = assertNotNull(FoodCatalog.food(id), "Missing catalogue item: $id")

    // Catalogue tag integrity

    /** A typo in an annotation table is otherwise silent: the food keeps the plant-only reading. */
    @Test
    fun annotationTablesOnlyNameRealFoods() {
        val ids = catalog.map { it.id }.toSet()
        for (annotated in FoodCatalog.annotatedIds) {
            if (annotated !in ids) fail("Annotation tables name '$annotated', which is not in the catalogue")
        }
    }

    @Test
    fun catalogueTagsMatchTheFoodsTheyDescribe() {
        val expectations = listOf(
            "eggs-large" to FoodTraits.EGG,
            "whole-milk" to FoodTraits.DAIRY,
            "whey-isolate" to FoodTraits.DAIRY,
            "chicken-thighs" to FoodTraits.MEAT,
            "ground-beef-93-7" to FoodTraits.MEAT + FoodTraits.RED_MEAT,
            "pork-shoulder" to FoodTraits.MEAT + FoodTraits.RED_MEAT + FoodTraits.PORK,
            "salmon-fillet" to FoodTraits.FISH,
            "shrimp" to FoodTraits.SHELLFISH,
            "firm-tofu" to FoodTraits.SOY,
            "almonds" to FoodTraits.NUTS,
            "dried-pasta" to FoodTraits.GLUTEN,
            "soy-sauce" to FoodTraits.SOY + FoodTraits.GLUTEN,
            "dried-lentils" to FoodTraits.NONE,
            "canned-black-beans" to FoodTraits.NONE,
            "white-rice" to FoodTraits.NONE,
            "olive-oil" to FoodTraits.NONE,
        )
        for ((id, traits) in expectations) {
            assertEquals(traits, food(id).traits, "$id is tagged wrongly")
        }
    }

    /** No animal-derived protein may be untagged: the check that catches a new food added bare. */
    @Test
    fun everyAnimalDerivedFoodCarriesATrait() {
        val plantAnchors = listOf("dried-lentils", "canned-black-beans", "canned-chickpeas")
        for (item in catalog) {
            if (item.swapGroup != SwapGroup.ProteinAnchor && item.category != FoodCategory.Dairy) continue
            if (item.traits.isEmpty) {
                assertTrue(item.id in plantAnchors, "${item.name} is a protein or dairy food with no dietary traits")
            }
        }
    }

    @Test
    fun preparationTimesAreSane() {
        for (item in catalog) {
            assertTrue(item.prepMinutes >= 0, item.name)
            assertTrue(item.prepMinutes <= 180, item.name)
        }
        assertFalse(catalog.none { it.prepMinutes <= PrepEffort.NoCook.maximumMinutes })
    }

    // Patterns

    @Test
    fun veganKeepsNoAnimalDerivedFood() {
        val allowed = DietaryFilter.allowed(catalog, DietaryProfile(pattern = DietaryPattern.Vegan))

        assertFalse(allowed.isEmpty())
        for (item in allowed) {
            assertTrue(item.traits.intersection(FoodTraits.ANIMAL_DERIVED).isEmpty, "${item.name} is not vegan")
        }
        val anchors = allowed.filter { it.category == FoodCategory.ProteinAnchor }
        assertTrue(anchors.size >= 4)
        assertTrue(anchors.any { it.id == "dried-lentils" })
        assertTrue(anchors.any { it.id == "firm-tofu" })
    }

    @Test
    fun pescatarianKeepsFishAndDropsMeat() {
        val profile = DietaryProfile(pattern = DietaryPattern.Pescatarian)
        assertTrue(DietaryFilter.allows(food("canned-tuna-water"), profile))
        assertTrue(DietaryFilter.allows(food("whole-milk"), profile))
        assertFalse(DietaryFilter.allows(food("chicken-thighs"), profile))
        assertFalse(DietaryFilter.allows(food("sirloin-steak"), profile))
    }

    @Test
    fun vegetarianDropsFishAndShellfishButKeepsEggs() {
        val profile = DietaryProfile(pattern = DietaryPattern.Vegetarian)
        assertTrue(DietaryFilter.allows(food("eggs-large"), profile))
        assertTrue(DietaryFilter.allows(food("firm-tofu"), profile))
        assertFalse(DietaryFilter.allows(food("salmon-fillet"), profile))
        assertFalse(DietaryFilter.allows(food("shrimp"), profile))
    }

    // Exclusions

    @Test
    fun exclusionsRemoveExactlyTheGroupTheyName() {
        val dairyFree = DietaryProfile(exclusions = setOf(FoodExclusion.Dairy))
        for (id in listOf("whole-milk", "cottage-cheese", "cheddar-block", "skyr", "greek-yogurt-nonfat", "whey-isolate")) {
            assertFalse(DietaryFilter.allows(food(id), dairyFree), id)
        }
        assertTrue(DietaryFilter.allows(food("eggs-large"), dairyFree))
        assertTrue(DietaryFilter.allows(food("chicken-breast"), dairyFree))

        val porkFree = DietaryProfile(exclusions = setOf(FoodExclusion.Pork))
        assertFalse(DietaryFilter.allows(food("pork-shoulder"), porkFree))
        assertTrue(DietaryFilter.allows(food("sirloin-steak"), porkFree), "Avoiding pork is not avoiding beef")
    }

    /** The combination a coeliac vegan actually needs. */
    @Test
    fun stackedRestrictionsStillLeaveProteinAnchors() {
        val profile = DietaryProfile(
            pattern = DietaryPattern.Vegan,
            exclusions = setOf(FoodExclusion.Gluten, FoodExclusion.Soy, FoodExclusion.Nuts),
        )
        val anchors = DietaryFilter.allowedIn(FoodCategory.ProteinAnchor, catalog, profile)
        assertFalse(anchors.isEmpty(), "Vegan, gluten-free and soy-free has no protein left")
        assertTrue(anchors.any { it.id == "dried-lentils" })
    }

    /**
     * A food tripping two exclusions reports the same one every time. The
     * reason is asserted outright, from sets in both insertion orders, because
     * repeating one call in one run proves little. Twin of the iOS
     * testAFoodTrippingTwoExclusionsReportsTheSameReason.
     */
    @Test
    fun aFoodTrippingTwoExclusionsReportsTheSameReason() {
        val soySauce = food("soy-sauce")
        for (exclusions in listOf(
            setOf(FoodExclusion.Soy, FoodExclusion.Gluten),
            setOf(FoodExclusion.Gluten, FoodExclusion.Soy),
            hashSetOf(FoodExclusion.Soy, FoodExclusion.Gluten),
        )) {
            assertEquals(
                DietaryFilter.Rejection.Exclusion(FoodExclusion.Gluten),
                DietaryFilter.rejection(soySauce, DietaryProfile(exclusions = exclusions)),
                "$exclusions",
            )
        }
    }

    // Effort and blocklist

    @Test
    fun noCookProfileKeepsOnlyQuickIngredients() {
        val allowed = DietaryFilter.allowed(catalog, DietaryProfile(prepEffort = PrepEffort.NoCook))

        assertFalse(allowed.isEmpty())
        for (item in allowed) {
            assertTrue(item.prepMinutes <= PrepEffort.NoCook.maximumMinutes, item.name)
        }
        assertFalse(allowed.any { it.id == "dried-lentils" })
        assertFalse(allowed.any { it.id == "pork-shoulder" })
        assertTrue(allowed.any { it.id == "canned-tuna-water" })
    }

    /** Stating an effort filters; *not* stating one must drop nothing. */
    @Test
    fun statedEffortFiltersAndTheUnconstrainedDefaultDoesNot() {
        val under30 = DietaryFilter.allowed(catalog, DietaryProfile(prepEffort = PrepEffort.Standard))

        assertFalse(under30.any { it.id == "dried-lentils" }, "40 minutes is over the limit")
        assertFalse(under30.any { it.id == "brown-rice" }, "35 minutes is over the limit")
        assertFalse(under30.any { it.id == "pork-shoulder" }, "90 minutes is over the limit")
        assertTrue(under30.any { it.id == "chicken-thighs" }, "25 minutes is inside the limit")

        assertEquals(catalog.size, DietaryFilter.allowed(catalog, DietaryProfile.UNRESTRICTED).size)
    }

    @Test
    fun blockedFoodIsReportedAsTheUsersOwnChoice() {
        val eggs = food("eggs-large")
        val profile = DietaryProfile(pattern = DietaryPattern.Vegan, blockedFoodIds = setOf("eggs-large"))

        assertEquals(DietaryFilter.Rejection.BlockedByUser, DietaryFilter.rejection(eggs, profile))
        assertFalse(DietaryFilter.allows(eggs, profile))
        assertFalse(DietaryFilter.allowed(catalog, profile).any { it.id == "eggs-large" })
    }

    // Bookkeeping

    @Test
    fun rejectionBreakdownAccountsForEveryRejectedFood() {
        val profile = DietaryProfile(
            pattern = DietaryPattern.Vegan,
            exclusions = setOf(FoodExclusion.Gluten),
            prepEffort = PrepEffort.Standard,
        )
        val rejected = DietaryFilter.rejected(catalog, profile)
        val breakdown = DietaryFilter.rejectionBreakdown(catalog, profile)

        assertEquals(rejected.size, breakdown.sumOf { it.count })
        assertFalse(breakdown.isEmpty())
        for (entry in breakdown) {
            assertFalse(entry.label.isEmpty())
            assertTrue(entry.count > 0)
        }
    }

    @Test
    fun everyRejectionExplainsItself() {
        val profile = DietaryProfile(
            pattern = DietaryPattern.Vegan,
            exclusions = setOf(FoodExclusion.Gluten, FoodExclusion.Nuts, FoodExclusion.Soy),
            blockedFoodIds = setOf("white-rice"),
            prepEffort = PrepEffort.Quick,
        )
        for (item in catalog) {
            val rejection = DietaryFilter.rejection(item, profile) ?: continue
            assertFalse(rejection.reason.isEmpty(), item.name)
            assertFalse(rejection.label.isEmpty(), item.name)
        }
    }

    @Test
    fun unrestrictedProfileAllowsTheWholeCatalogue() {
        assertEquals(catalog.size, DietaryFilter.allowed(catalog, DietaryProfile.UNRESTRICTED).size)
        assertFalse(DietaryProfile.UNRESTRICTED.isRestricted)
    }

    /** Not in the iOS suite: a negative count from a damaged store is clamped, not believed. */
    @Test
    fun mealsOutCannotGoNegative() {
        assertEquals(0, DietaryProfile(mealsOutPerWeek = -3).mealsOutPerWeek)
    }
}
