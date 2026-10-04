/*
 * PlanGeneratorTest.kt
 *
 * The starter plan, attacked from every side a user can reach in onboarding:
 * every pattern, every exclusion alone and in hard combinations, every cooking
 * effort and schedule, small and large bodies, and budgets from generous to
 * impossible. What must hold everywhere: nothing the profile refuses is ever
 * served, quantities are ones a person can buy, and the numbers land where
 * the plan says they will.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.BiologicalSex
import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.EatingSchedule
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.domain.totalCost
import com.lungelo.macrodime.domain.totalNutrition
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PlanGeneratorTest {

    private fun targets(
        weightKg: Double = 82.0,
        heightCm: Double = 178.0,
        age: Int = 31,
        sex: BiologicalSex = BiologicalSex.Male,
        activity: ActivityLevel = ActivityLevel.ModeratelyActive,
        goal: FitnessGoal = FitnessGoal.FatLoss,
    ): NutritionFacts = BodyScienceEngine.prescribeUnchecked(BodyScienceEngine.Input(weightKg, heightCm, age, sex, activity, goal)).targets

    private val defaultTargets = targets()

    private fun input(
        dietary: DietaryProfile = DietaryProfile(prepEffort = PrepEffort.Standard),
        budget: Double = 9.0,
        targets: NutritionFacts = defaultTargets,
    ) = PlanGenerator.Input(targets, budget, dietary)

    /** A meal reduced to what can be compared: ids differ on every run, contents must not. */
    private fun projection(meals: List<MealItem>) = meals.map { meal ->
        Triple(meal.slot, meal.name, meal.portions.map { it.food.id to it.servings })
    }

    /** Every profile shape worth attacking: each pattern, each exclusion, and the hard combinations. */
    private val profiles: List<DietaryProfile> = buildList {
        val exclusionSets = listOf(emptySet<FoodExclusion>()) + FoodExclusion.entries.map { setOf(it) } + listOf(
            setOf(FoodExclusion.Gluten, FoodExclusion.Dairy),
            setOf(FoodExclusion.Gluten, FoodExclusion.Soy, FoodExclusion.Nuts),
            setOf(FoodExclusion.Fish, FoodExclusion.Egg, FoodExclusion.Dairy),
            FoodExclusion.entries.toSet(),
        )
        for (pattern in DietaryPattern.entries) {
            for (exclusions in exclusionSets) {
                for (effort in PrepEffort.entries) {
                    for (schedule in EatingSchedule.entries) {
                        add(DietaryProfile(pattern, exclusions, emptySet(), schedule, effort))
                    }
                }
            }
        }
    }

    @Test
    fun everyFoodATemplateNamesIsInTheCatalogue() {
        val ids = FoodCatalog.all.map { it.id }.toSet()
        for (template in PlanGenerator.templates) {
            for (part in template.parts) {
                for (id in part.candidates) {
                    assertTrue(id in ids, "Template \"${template.pattern}\" names \"$id\", which is not in the catalogue")
                }
            }
        }
    }

    @Test
    fun nothingTheProfileRefusesIsEverServed() {
        for (profile in profiles) {
            for (meals in PlanGenerator.week(input(profile))) {
                for (portion in meals.flatMap { it.portions }) {
                    val rejection = DietaryFilter.rejection(portion.food, profile)
                    assertTrue(rejection == null, "${portion.food.name} served to $profile: ${rejection?.reason}")
                }
            }
        }
    }

    @Test
    fun mealsFillOnlyTheScheduledSlotsAndEachOnce() {
        for (profile in profiles) {
            for (meals in PlanGenerator.week(input(profile))) {
                val slots = meals.map { it.slot }
                assertEquals(slots.distinct(), slots, "a slot was filled twice for $profile")
                assertTrue(profile.schedule.slots.containsAll(slots), "unscheduled slot for $profile: $slots")
                assertTrue(meals.none { it.isEmpty }, "an empty meal was returned for $profile")
            }
        }
    }

    /**
     * The profiles a person is likely to pick must get every meal they asked
     * for, not just the omnivore default: vegans, people who cannot cook, and
     * people with a common allergy.
     */
    @Test
    fun commonRestrictionsStillGetEveryMeal() {
        val restricted = listOf(
            DietaryProfile(DietaryPattern.Vegan, schedule = EatingSchedule.ThreeMealsAndSnacks, prepEffort = PrepEffort.Standard),
            DietaryProfile(DietaryPattern.Vegetarian, schedule = EatingSchedule.ThreeMealsAndSnacks, prepEffort = PrepEffort.Quick),
            DietaryProfile(DietaryPattern.Pescatarian, schedule = EatingSchedule.ThreeMeals, prepEffort = PrepEffort.Standard),
            DietaryProfile(schedule = EatingSchedule.ThreeMealsAndSnacks, prepEffort = PrepEffort.NoCook),
            DietaryProfile(exclusions = setOf(FoodExclusion.Gluten), prepEffort = PrepEffort.Quick),
            DietaryProfile(exclusions = setOf(FoodExclusion.Dairy, FoodExclusion.Egg), prepEffort = PrepEffort.Standard),
            DietaryProfile(exclusions = setOf(FoodExclusion.Pork, FoodExclusion.Shellfish), prepEffort = PrepEffort.Unlimited),
        )
        for (profile in restricted) {
            PlanGenerator.week(input(profile)).forEachIndexed { day, meals ->
                assertEquals(profile.schedule.slots, meals.map { it.slot }, "day $day for $profile is missing a meal")
            }
        }
    }

    @Test
    fun everyQuantityIsOneAPersonCanBuy() {
        for (profile in profiles) {
            val shares = PlanGenerator.shares(profile.schedule)
            for (meals in PlanGenerator.week(input(profile, budget = 1_000.0))) {
                for (meal in meals) {
                    // The most any role allows, stretched for this meal's size.
                    val limit = 2.5 * PlanGenerator.portionScale(defaultTargets.calories * shares.getValue(meal.slot))
                    for (portion in meal.portions) {
                        val step = PlanGenerator.step(portion.food)
                        val units = portion.servings / step
                        assertTrue(abs(units - units.roundToLong()) < 1e-9, "${portion.servings} servings of ${portion.food.name} is off its $step step")
                        assertTrue(portion.servings >= step - 1e-9, "${portion.food.name} below one step")
                        assertTrue(portion.servings <= limit + 1e-9, "${portion.servings} servings of ${portion.food.name} in a ${meal.slot} is more than anyone is served")
                    }
                }
            }
        }
    }

    /** An ordinary three-meal plan keeps ordinary plates: no food beyond its everyday limit by much. */
    @Test
    fun anOrdinaryPlanServesOrdinaryPortions() {
        for (meal in PlanGenerator.week(input()).flatten()) {
            for (portion in meal.portions) {
                assertTrue(portion.grams <= 650.0, "${portion.quantityDescription} of ${portion.food.name} in ${meal.name}")
            }
        }
    }

    /** Through a budget swap the swap engine's own quarter steps apply. */
    @Test
    fun aSwappedDayStaysInQuarterServings() {
        for (meals in PlanGenerator.week(input(budget = 3.0))) {
            for (portion in meals.flatMap { it.portions }) {
                val quarters = portion.servings / 0.25
                assertTrue(abs(quarters - quarters.roundToLong()) < 1e-9, "${portion.servings} of ${portion.food.name}")
            }
        }
    }

    @Test
    fun theDefaultPlanHitsItsTargetsInsideItsBudget() {
        PlanGenerator.week(input()).forEachIndexed { day, meals ->
            val total = meals.totalNutrition
            val calorieError = abs(total.calories - defaultTargets.calories) / defaultTargets.calories
            assertTrue(calorieError <= 0.08, "day $day: ${total.calories.toInt()} kcal against ${defaultTargets.calories.toInt()}")
            assertTrue(total.protein >= defaultTargets.protein * 0.85, "day $day: ${total.protein.toInt()} g protein against ${defaultTargets.protein.toInt()}")
            assertTrue(meals.totalCost <= 9.0, "day $day costs ${meals.totalCost}")
        }
    }

    /**
     * A small body and a large one: the sizing must scale both ways, not just
     * fit the default. A very large target is allowed to fall short, honestly,
     * rather than be met with three cans of tuna and a kilo of sweet potato;
     * then the audit must say the day is short. Protein never runs far past
     * its target on the way to the calories.
     */
    @Test
    fun smallAndLargeBodiesBothLandNearTheirTargets() {
        val cases = listOf(
            targets(weightKg = 55.0, heightCm = 160.0, age = 45, sex = BiologicalSex.Female, activity = ActivityLevel.Sedentary),
            targets(weightKg = 100.0, heightCm = 190.0, age = 24, activity = ActivityLevel.VeryActive, goal = FitnessGoal.MuscleGain),
        )
        for (target in cases) {
            for (schedule in EatingSchedule.entries) {
                val profile = DietaryProfile(schedule = schedule, prepEffort = PrepEffort.Standard)
                PlanGenerator.week(input(profile, budget = 1_000.0, targets = target)).forEachIndexed { day, meals ->
                    val total = meals.totalNutrition
                    val error = abs(total.calories - target.calories) / target.calories
                    val label = "$schedule day $day: ${total.calories.toInt()} kcal against ${target.calories.toInt()}"
                    val isLarge = target.calories > 3_000
                    // An ordinary target lands within 10%. A very large one may
                    // land within the audit's own 15%, and past that the audit
                    // must say the day is short.
                    if (error > (if (isLarge) PlanAudit.Constants.CALORIE_CAUTION else 0.10)) {
                        assertTrue(isLarge, "$label is off by more than 10%")
                        val audit = PlanAudit.day(meals, target, 1_000.0, profile)
                        assertTrue(PlanGap.Kind.CalorieDrift in audit, "$label is short and the audit does not say so")
                    }
                    assertTrue(total.protein >= target.protein * 0.80, "$schedule day $day: ${total.protein.toInt()} g protein against ${target.protein.toInt()}")
                    // Bigger bases bring some protein of their own (pasta, bread, beans), so a large
                    // day can pass the generator's own 25% ceiling a little, never by a lot.
                    assertTrue(total.protein <= target.protein * 1.35, "$schedule day $day: ${total.protein.toInt()} g protein is far past ${target.protein.toInt()}")
                }
            }
        }
    }

    @Test
    fun aTightBudgetOnlyEverMakesTheDayCheaper() {
        val generous = PlanGenerator.week(input(budget = 1_000.0))
        val tight = PlanGenerator.week(input(budget = 4.0))
        generous.zip(tight).forEachIndexed { day, (loose, cheap) ->
            assertTrue(cheap.totalCost <= loose.totalCost + 1e-9, "day $day: the budget pass made the day dearer")
        }
    }

    /** A meal swapped to fit the budget is named for what it now holds. */
    @Test
    fun aMealIsNeverNamedForAProteinItDoesNotContain() {
        val named = mapOf(
            "Tuna" to "canned-tuna-water", "Sardines" to "canned-sardines", "Pork" to "pork-shoulder",
            "Beef" to "ground-beef-80-20", "Tofu" to "firm-tofu", "Lentil" to "dried-lentils",
            "Chickpea" to "canned-chickpeas", "Black Bean" to "canned-black-beans", "Drumsticks" to "chicken-drumsticks",
        )
        for (budget in listOf(1_000.0, 9.0, 5.0, 3.0)) {
            for (profile in profiles.filter { it.exclusions.size <= 1 }) {
                for (meal in PlanGenerator.week(input(profile, budget)).flatten()) {
                    for ((word, id) in named) {
                        if (Regex("""\b$word\b""").containsMatchIn(meal.name)) {
                            assertTrue(meal.portions.any { it.food.id == id }, "\"${meal.name}\" has no $id (budget $budget, $profile)")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun namesReadCleanly() {
        for (profile in profiles) {
            for (meal in PlanGenerator.week(input(profile)).flatten()) {
                assertTrue(meal.name.isNotBlank(), "a blank name for $profile")
                assertTrue('{' !in meal.name && '}' !in meal.name, "unfilled placeholder: ${meal.name}")
                assertTrue("  " !in meal.name, "double space: ${meal.name}")
                assertTrue(!meal.name.startsWith("&") && !meal.name.endsWith("&") && !meal.name.endsWith(","), "dangling joiner: ${meal.name}")
            }
        }
    }

    /**
     * Lunch and dinner differ in protein when a strong alternative exists. When
     * none does (a high target on a tight budget can leave only chicken), the
     * same protein may come twice: most days still differ, and no protein is
     * ever served at three meals in one day.
     */
    @Test
    fun proteinsVaryThroughTheDay() {
        val kinds = { meal: MealItem -> meal.portions.filter { it.food.category == FoodCategory.ProteinAnchor }.map { PlanGenerator.adjective(it.food) }.toSet() }
        var shared = 0
        PlanGenerator.week(input()).forEachIndexed { day, meals ->
            val lunch = kinds(meals.first { it.slot.name == "Lunch" })
            val dinner = kinds(meals.first { it.slot.name == "Dinner" })
            if (lunch.intersect(dinner).isNotEmpty()) shared += 1
        }
        assertTrue(shared <= 3, "lunch and dinner shared a protein on $shared of 7 days")
        // Profiles with real choice. The hardest combinations (vegetarian,
        // gluten-free, nut-free, soy-free and quick) can leave eggs as the only
        // protein some meals allow; serving them again beats an empty meal.
        for (profile in profiles.filter { it.exclusions.size <= 1 }) {
            for (meals in PlanGenerator.week(input(profile))) {
                val counts = meals.flatMap { kinds(it) }.groupingBy { it }.eachCount()
                assertTrue(counts.values.all { it <= 2 }, "a protein at three meals for $profile: $counts")
            }
        }
    }

    @Test
    fun theWeekVaries() {
        val week = PlanGenerator.week(input())
        val dinners = week.mapNotNull { day -> day.firstOrNull { it.slot.name == "Dinner" }?.name }.toSet()
        assertTrue(dinners.size >= 3, "only ${dinners.size} different dinners in a week: $dinners")
        week.zipWithNext().forEachIndexed { day, (today, tomorrow) ->
            assertTrue(projection(today) != projection(tomorrow), "day $day and day ${day + 1} are identical")
        }
    }

    @Test
    fun theSameInputAlwaysGivesTheSameWeek() {
        for (profile in profiles.take(40)) {
            assertEquals(PlanGenerator.week(input(profile)).map(::projection), PlanGenerator.week(input(profile)).map(::projection))
        }
    }

    /** Fallback dinners (something on toast) are for people with fewer than two real options, never for everyone. */
    @Test
    fun fallbackMealsAreOnlyForProfilesWithNothingElse() {
        val fallbackPatterns = PlanGenerator.templates.filter { it.fallback }.map { it.pattern }.toSet()
        assertTrue(fallbackPatterns.isNotEmpty())
        val unrestricted = PlanGenerator.week(input(DietaryProfile(prepEffort = PrepEffort.Unlimited, schedule = EatingSchedule.ThreeMealsAndSnacks))).flatten()
        for (meal in unrestricted) {
            val template = assertNotNull(PlanGenerator.templates.firstOrNull { t -> t.slot == meal.slot && matches(t.pattern, meal.name) })
            if (template.fallback) fail("an unrestricted plan was given the fallback \"${meal.name}\"")
        }
    }

    /** Whether [name] could have come from [pattern]: each placeholder stands for at least one word. */
    private fun matches(pattern: String, name: String): Boolean {
        val literals = pattern.split(Regex("""\{\w+(:adj)?\}"""))
        return Regex(literals.joinToString(".+") { Regex.escape(it) }).matches(name)
    }
}
