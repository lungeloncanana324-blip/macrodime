/*
 * PlanAuditTest.kt
 *
 * Port of MacroDimeTests/PlanAuditTests.swift: that the audit catches each way
 * a plan can fail, and never claims a plan is fine when it cannot know.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.EatingSchedule
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.domain.totalCost
import com.lungelo.macrodime.domain.totalNutrition
import com.lungelo.macrodime.engine.PlanGap.Kind
import com.lungelo.macrodime.engine.PlanGap.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlanAuditTest {

    private val catalog = FoodCatalog.reference
    private val dayTargets = NutritionFacts(calories = 2200.0, protein = 150.0, carbs = 240.0, fat = 60.0)

    private fun food(id: String): FoodSnapshot = assertNotNull(FoodCatalog.referenceFood(id), "Missing catalogue item: $id")

    private fun meal(name: String, slot: MealSlot, ids: List<String>, servings: List<Double> = emptyList()) =
        MealItem(
            name = name,
            slot = slot,
            portions = ids.mapIndexed { index, id -> Portion(food(id), servings.getOrElse(index) { 1.0 }) },
        )

    /** Eggs and oats, tuna and rice, chicken thighs and potatoes, all with vegetables. */
    private fun balancedDay() = listOf(
        meal("Breakfast", MealSlot.Breakfast, listOf("eggs-large", "rolled-oats", "banana")),
        meal("Lunch", MealSlot.Lunch, listOf("canned-tuna-water", "white-rice", "frozen-broccoli", "olive-oil")),
        meal("Dinner", MealSlot.Dinner, listOf("chicken-thighs", "potatoes", "frozen-mixed-vegetables")),
    )

    private fun selfTargeted(meals: List<MealItem>, dietary: DietaryProfile = DietaryProfile.UNRESTRICTED) =
        PlanAudit.day(meals, meals.totalNutrition, meals.totalCost, dietary, catalog)

    // Empty plan

    @Test
    fun emptyPlanIsReportedAndStillDisclosesWhatIsUntracked() {
        val report = PlanAudit.day(emptyList(), dayTargets, 12.0, DietaryProfile.UNRESTRICTED, catalog)

        assertTrue(Kind.EmptyPlan in report)
        assertTrue(Kind.UntrackedNutrients in report)
        assertEquals(Severity.Caution, report.first(Kind.EmptyPlan)?.severity)
        assertFalse(report.blocking.any { it.kind == Kind.ProteinShortfall })
    }

    // Macros

    @Test
    fun proteinShortfallIsBlockingWhenFarShort() {
        val day = listOf(
            meal("Lunch", MealSlot.Lunch, listOf("white-rice", "olive-oil", "frozen-broccoli")),
            meal("Dinner", MealSlot.Dinner, listOf("potatoes", "carrots", "olive-oil")),
        )
        val report = PlanAudit.day(day, dayTargets, 20.0, DietaryProfile.UNRESTRICTED, catalog)

        val gap = assertNotNull(report.first(Kind.ProteinShortfall))
        assertEquals(Severity.Blocking, gap.severity)
        assertFalse(gap.remedy.isEmpty())
    }

    @Test
    fun planThatMatchesItsOwnTargetsHasNoBlockingGaps() {
        val report = selfTargeted(balancedDay())

        assertFalse(Kind.ProteinShortfall in report)
        assertFalse(Kind.CalorieDrift in report)
        assertFalse(Kind.BudgetOverrun in report)
        assertTrue(report.blocking.isEmpty(), "${report.gaps.map { it.title }}")
    }

    // Budget

    @Test
    fun budgetOverrunBlocksPastTheHardLimit() {
        val day = listOf(
            meal("Lunch", MealSlot.Lunch, listOf("salmon-fillet", "white-rice", "olive-oil")),
            meal("Dinner", MealSlot.Dinner, listOf("sirloin-steak", "potatoes", "asparagus")),
        )
        val report = PlanAudit.day(day, day.totalNutrition, 5.0, DietaryProfile.UNRESTRICTED, catalog)

        val gap = assertNotNull(report.first(Kind.BudgetOverrun))
        assertEquals(Severity.Blocking, gap.severity)
        assertTrue("over your allowance" in gap.title)
    }

    // Restrictions changed after the plan was built

    @Test
    fun planBuiltBeforeARestrictionChangeIsFlagged() {
        val day = listOf(
            meal("Lunch", MealSlot.Lunch, listOf("chicken-thighs", "white-rice", "frozen-broccoli")),
            meal("Dinner", MealSlot.Dinner, listOf("salmon-fillet", "potatoes", "carrots")),
        )
        val report = selfTargeted(day, DietaryProfile(pattern = DietaryPattern.Vegetarian))

        val gap = assertNotNull(report.first(Kind.RestrictionConflict))
        assertEquals(Severity.Blocking, gap.severity)
        assertTrue("Chicken Thighs" in gap.detail || "Atlantic Salmon Fillet" in gap.detail)
    }

    @Test
    fun slowIngredientsAreFlaggedAgainstTheStatedEffort() {
        val day = listOf(meal("Dinner", MealSlot.Dinner, listOf("dried-lentils", "brown-rice", "carrots")))
        val report = selfTargeted(day, DietaryProfile(prepEffort = PrepEffort.Quick))

        val gap = assertNotNull(report.first(Kind.CookingEffort))
        assertEquals(Severity.Caution, gap.severity)
        assertTrue("Dried Lentils" in gap.detail)
    }

    // Composition

    @Test
    fun dinnerWithoutAVegetableIsNoted() {
        val report = selfTargeted(listOf(meal("Dinner", MealSlot.Dinner, listOf("chicken-thighs", "white-rice", "olive-oil"))))
        assertEquals(Severity.Info, report.first(Kind.NoVegetable)?.severity)

        assertFalse(Kind.NoVegetable in selfTargeted(balancedDay()))
    }

    @Test
    fun singleProteinAnchorAllDayIsNoted() {
        val day = listOf(
            meal("Lunch", MealSlot.Lunch, listOf("canned-tuna-water", "white-rice", "frozen-broccoli")),
            meal("Dinner", MealSlot.Dinner, listOf("canned-tuna-water", "potatoes", "carrots")),
        )
        assertEquals(Severity.Info, selfTargeted(day).first(Kind.LowVariety)?.severity)
        assertFalse(Kind.LowVariety in selfTargeted(balancedDay()))
    }

    @Test
    fun unfilledSlotsAreNoted() {
        val day = listOf(meal("Dinner", MealSlot.Dinner, listOf("chicken-thighs", "potatoes", "carrots")))
        val report = selfTargeted(day, DietaryProfile(schedule = EatingSchedule.ThreeMealsAndSnacks))
        val gap = assertNotNull(report.first(Kind.UnfilledSlot))
        assertEquals(Severity.Info, gap.severity)
        assertTrue("Snacks" in gap.detail)
    }

    // Feasibility, before anything is planned

    @Test
    fun impossibleBudgetIsCaughtBeforePlanning() {
        val report = PlanAudit.feasibility(
            NutritionFacts(calories = 2200.0, protein = 160.0, carbs = 240.0, fat = 60.0),
            3.0,
            DietaryProfile(pattern = DietaryPattern.Vegan),
            catalog,
        )
        val gap = assertNotNull(report.first(Kind.ProteinFeasibility), "A vegan 160 g target on $3 a day is not achievable")
        assertEquals(Severity.Blocking, gap.severity)
        assertTrue("Raise" in gap.remedy)
    }

    @Test
    fun generousBudgetHasNoBlockingFeasibilityGap() {
        val report = PlanAudit.feasibility(dayTargets, 100.0, DietaryProfile.UNRESTRICTED, catalog)
        assertFalse(Kind.ProteinFeasibility in report)
        assertTrue(report.blocking.isEmpty())
        assertTrue(Kind.UntrackedNutrients in report)
    }

    @Test
    fun profileWithNoProteinSourcesIsBlocking() {
        val impossible = DietaryProfile(
            pattern = DietaryPattern.Vegan,
            blockedFoodIds = setOf("dried-lentils", "canned-black-beans", "canned-chickpeas", "firm-tofu"),
        )
        val report = PlanAudit.feasibility(
            NutritionFacts(calories = 2000.0, protein = 120.0, carbs = 220.0, fat = 55.0),
            20.0,
            impossible,
            catalog,
        )
        assertEquals(Severity.Blocking, report.first(Kind.MissingProteinSources)?.severity)
    }

    @Test
    fun restrictionsReportHowMuchOfTheCatalogueIsGone() {
        val report = PlanAudit.feasibility(
            dayTargets,
            15.0,
            DietaryProfile(pattern = DietaryPattern.Vegan, exclusions = setOf(FoodExclusion.Nuts)),
            catalog,
        )
        val gap = assertNotNull(report.first(Kind.RestrictionsCost), "A restricted profile must say what it removed")
        assertTrue("Removed by your settings" in gap.detail)
    }

    // Disclosure

    @Test
    fun eatingOutIsDisclosedOnlyWhenTheUserSaysSo() {
        assertFalse(Kind.EatingOut in PlanAudit.feasibility(dayTargets, 15.0, DietaryProfile.UNRESTRICTED, catalog))

        val diningOut = PlanAudit.feasibility(dayTargets, 15.0, DietaryProfile(mealsOutPerWeek = 4), catalog)
        assertEquals(Severity.Info, assertNotNull(diningOut.first(Kind.EatingOut)).severity)
    }

    @Test
    fun untrackedNutrientsAreAlwaysDisclosed() {
        val reports = listOf(
            PlanAudit.feasibility(null, 0.0, DietaryProfile.UNRESTRICTED, catalog),
            PlanAudit.day(emptyList(), null, 0.0, DietaryProfile.UNRESTRICTED, catalog),
        )
        for (report in reports) assertTrue(Kind.UntrackedNutrients in report)
    }

    // Report shape

    @Test
    fun everyGapCarriesARemedyAndAStableIdentity() {
        val report = PlanAudit.day(
            listOf(meal("Lunch", MealSlot.Lunch, listOf("white-rice"))),
            dayTargets,
            1.0,
            DietaryProfile(prepEffort = PrepEffort.NoCook),
            catalog,
        )
        assertFalse(report.gaps.isEmpty())
        for (gap in report.gaps) {
            assertFalse(gap.remedy.isEmpty(), gap.title)
            assertFalse(gap.detail.isEmpty(), gap.title)
            assertFalse(gap.id.isEmpty())
        }
    }

    @Test
    fun gapsAreOrderedWorstFirst() {
        val report = PlanAudit.day(
            listOf(meal("Lunch", MealSlot.Lunch, listOf("white-rice"))),
            dayTargets,
            1.0,
            DietaryProfile(prepEffort = PrepEffort.Standard),
            catalog,
        )
        val severities = report.gaps.map { it.severity.ordinal }
        assertEquals(severities.sortedDescending(), severities, "Report is not ordered worst first")
    }

    @Test
    fun sameInputGivesTheSameReport() {
        val day = balancedDay()
        val targets = NutritionFacts(calories = 1800.0, protein = 160.0, carbs = 180.0, fat = 55.0)
        val profile = DietaryProfile(pattern = DietaryPattern.Omnivore, exclusions = setOf(FoodExclusion.Pork))
        assertEquals(
            PlanAudit.day(day, targets, 8.0, profile, catalog),
            PlanAudit.day(day, targets, 8.0, DietaryProfile(pattern = DietaryPattern.Omnivore, exclusions = setOf(FoodExclusion.Pork)), catalog),
        )
    }
}
