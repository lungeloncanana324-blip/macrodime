/*
 * PlanGenerator.kt
 * MacroDime
 *
 * The starter plan: a week of meals a person can actually cook, built for
 * their targets, their budget and what they will eat, so the planner opens
 * full rather than empty. Everything after that is theirs to change.
 *
 * How a day is built, and why each step is there:
 *  1. Meals come from hand-written templates (oats, eggs and banana; chicken,
 *     potatoes and broccoli), never from free combination. The catalogue is
 *     macro-complete but culinarily blind, and a search over it happily serves
 *     sardines with oats. Each template part lists the foods that may fill it,
 *     best first, so an excluded food falls through to the next sensible one,
 *     and a template with an empty required part is simply not offered.
 *  2. Each slot gets a share of the day's calories and protein. The protein
 *     food is sized first to the slot's protein, then the carb base fills the
 *     slot's calories: the two numbers a plan is judged on, in that order.
 *  3. The day is then balanced as a whole, a step of a carb base at a time,
 *     because slots sized one by one can land a little over or under.
 *  4. Quantities are quantised the way people shop and cook: whole eggs and
 *     slices, half cans, quarter steps of anything weighed.
 *  5. A day over budget goes through the swap engine, which only ever trades
 *     down inside a food's own swap group and keeps macros within tolerance.
 *     Whatever is still over is left for the audit to report, not hidden.
 *
 * Deterministic: the same input always gives the same week, which is what lets
 * the tests pin it and what makes "plan this day" predictable.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.EatingSchedule
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.domain.ServingMeasure
import com.lungelo.macrodime.domain.SwapGroup
import com.lungelo.macrodime.domain.roundedHalfAway
import com.lungelo.macrodime.domain.total
import com.lungelo.macrodime.domain.totalCost
import com.lungelo.macrodime.domain.totalNutrition
import java.util.UUID
import kotlin.math.abs

object PlanGenerator {

    /**
     * What a part of a meal is for, which decides how it is sized: the protein
     * is sized to protein, one energy part fills the calories, and the rest
     * keep the quantity the template gives them.
     */
    enum class Role(val maximumServings: Double) {
        Protein(2.5),
        Base(2.5),
        Vegetable(1.0),
        Fruit(2.0),
        Fat(1.5),
        Extra(1.0),
    }

    /**
     * One ingredient position in a template: the foods that may fill it, in
     * order of preference, and its starting quantity in servings.
     */
    data class Part(
        val role: Role,
        val candidates: List<String>,
        val servings: Double = 1.0,
        val required: Boolean = true,
    )

    /**
     * A meal as a cook would name it. [pattern] names the meal from what was
     * actually chosen: `{Protein}` is replaced by that part's food, so a
     * template that fell back from eggs to yogurt never calls itself eggs.
     * `{Protein:adj}` is the same food in front of a noun: "Drumsticks, Rice &
     * Broccoli" but "Chicken Stew", "Eggs on Toast" but "Egg Sandwich".
     * A [fallback] template is offered only when no other fits the slot: a
     * plan for someone who cannot cook, not a dinner anyone else is given.
     */
    data class Template(
        val slot: MealSlot,
        val pattern: String,
        val parts: List<Part>,
        val fallback: Boolean = false,
    )

    data class Input(
        val targets: NutritionFacts,
        val dailyBudgetUSD: Double,
        val dietary: DietaryProfile,
        val catalog: List<FoodSnapshot> = FoodCatalog.all,
    )

    /** The days a starter plan covers, today included. */
    const val DAYS = 7

    /** How close the day's calories must come before balancing stops. */
    const val CALORIE_TOLERANCE = 0.04

    private const val BALANCE_STEPS = 40

    /**
     * A meal is offered only if it reaches this share of its slot's protein,
     * or of the best the slot can do. Loose on purpose: the day is topped up
     * as a whole afterwards, so a slot need not carry its exact share.
     */
    private const val PROTEIN_FLOOR = 0.75

    /** How far over its share of the day's calories a meal may run and still be offered, when others fit. */
    private const val CALORIE_SLACK = 1.15

    /** How far over its share of the day's budget a meal may run and still be offered, when cheaper ones fit. */
    private const val BUDGET_SLACK = 1.2

    /** Grams of protein per kcal above which a food counts as lean: eggs at 0.088 are in, chickpeas at 0.054 are not. */
    private const val LEAN_PROTEIN_PER_KCAL = 0.08

    /** How close the day's protein must come before topping up stops. */
    const val PROTEIN_TOLERANCE = 0.03

    /** How far over its protein target a day may go when a bigger portion is the way to its calories. */
    const val PROTEIN_CEILING = 1.25

    /**
     * The meal size the serving limits are written for. A bigger meal (a
     * large target, or two meals a day) lets every limit grow in proportion,
     * up to [MAX_PORTION_SCALE] times: big eaters get big plates, and an
     * ordinary plan keeps ordinary ones.
     */
    const val REFERENCE_MEAL_CALORIES = 700.0
    const val MAX_PORTION_SCALE = 1.75

    /** How far a meal of [calories] may stretch the serving limits. */
    fun portionScale(calories: Double): Double = (calories / REFERENCE_MEAL_CALORIES).coerceIn(1.0, MAX_PORTION_SCALE)

    // Templates

    private fun part(role: Role, vararg ids: String, servings: Double = 1.0, required: Boolean = true) =
        Part(role, ids.toList(), servings, required)

    private val breads = arrayOf("whole-wheat-bread", "sprouted-grain-bread", "sourdough-bread")

    val templates: List<Template> = listOf(
        // Breakfast
        Template(
            MealSlot.Breakfast, "{Base}, {Protein} & {Fruit}",
            listOf(
                part(Role.Base, "rolled-oats"),
                part(Role.Protein, "eggs-large", "greek-yogurt-nonfat", "cottage-cheese", "skyr"),
                part(Role.Fruit, "banana", "apple", "frozen-berries"),
                part(Role.Extra, "whole-milk", required = false),
            ),
        ),
        Template(
            MealSlot.Breakfast, "{Protein} on {Base}",
            listOf(
                part(Role.Protein, "eggs-large", "cottage-cheese", "firm-tofu"),
                part(Role.Base, *breads),
                part(Role.Vegetable, "baby-spinach", "bell-pepper", servings = 0.5, required = false),
                part(Role.Extra, "cheddar-block", required = false),
            ),
        ),
        Template(
            MealSlot.Breakfast, "{Protein}, {Fruit} & {Fat}",
            listOf(
                part(Role.Protein, "greek-yogurt-nonfat", "skyr", "cottage-cheese"),
                part(Role.Fruit, "frozen-berries", "banana", "apple"),
                part(Role.Fat, "sunflower-seeds", "almonds", "peanut-butter", servings = 0.5),
                part(Role.Base, "rolled-oats", servings = 0.5, required = false),
            ),
        ),
        Template(
            MealSlot.Breakfast, "{Fat} Oats & {Fruit}",
            listOf(
                part(Role.Base, "rolled-oats"),
                part(Role.Fat, "peanut-butter", "sunflower-seeds", "almonds", servings = 0.5),
                part(Role.Fruit, "banana", "frozen-berries", "apple"),
                part(Role.Extra, "whole-milk", required = false),
            ),
        ),

        // Lunch
        Template(
            MealSlot.Lunch, "{Protein:adj} {Base} Bowl",
            listOf(
                part(Role.Protein, "canned-tuna-water", "chicken-thighs", "firm-tofu", "canned-chickpeas"),
                part(Role.Base, "white-rice", "brown-rice", "dried-pasta"),
                part(Role.Vegetable, "carrots", "frozen-mixed-vegetables", "bell-pepper", "cabbage"),
                part(Role.Extra, "soy-sauce", required = false),
            ),
        ),
        Template(
            MealSlot.Lunch, "{Protein:adj} Burrito Bowl",
            listOf(
                part(Role.Protein, "ground-beef-80-20", "chicken-thighs", "canned-black-beans", "canned-chickpeas"),
                part(Role.Base, "white-rice", "brown-rice"),
                part(Role.Extra, "canned-black-beans", servings = 0.5, required = false),
                part(Role.Vegetable, "bell-pepper", "onion", "frozen-mixed-vegetables"),
                part(Role.Fat, "cheddar-block", "sunflower-seeds", required = false),
                part(Role.Extra, "hot-sauce", required = false),
            ),
        ),
        Template(
            MealSlot.Lunch, "{Protein:adj} Sandwich",
            listOf(
                part(Role.Protein, "canned-tuna-water", "eggs-large", "canned-chickpeas", "turkey-breast-deli"),
                part(Role.Base, *breads),
                part(Role.Vegetable, "baby-spinach", "bell-pepper", servings = 0.5, required = false),
                part(Role.Fruit, "apple", "banana", required = false),
            ),
        ),
        Template(
            MealSlot.Lunch, "{Protein:adj} & {Vegetable:adj} Soup",
            listOf(
                part(Role.Protein, "chicken-thighs", "dried-lentils", "canned-chickpeas", "canned-black-beans"),
                part(Role.Vegetable, "carrots", "onion", "cabbage"),
                part(Role.Base, "whole-wheat-bread", "potatoes", servings = 0.5, required = false),
                part(Role.Fat, "olive-oil", "canola-oil", servings = 0.5, required = false),
            ),
        ),
        Template(
            MealSlot.Lunch, "{Protein:adj} & {Base:adj} Salad",
            listOf(
                part(Role.Protein, "canned-tuna-water", "canned-sardines", "eggs-large", "firm-tofu"),
                part(Role.Base, "canned-chickpeas", "canned-black-beans"),
                part(Role.Vegetable, "baby-spinach", "bell-pepper", "frozen-mixed-vegetables"),
                part(Role.Fat, "olive-oil", "canola-oil", servings = 0.5, required = false),
            ),
            fallback = true,
        ),

        // Dinner
        Template(
            MealSlot.Dinner, "{Protein}, {Base} & {Vegetable}",
            listOf(
                part(Role.Protein, "chicken-thighs", "chicken-drumsticks", "pork-shoulder", "canned-chickpeas", "firm-tofu"),
                part(Role.Base, "potatoes", "sweet-potato", "white-rice"),
                part(Role.Vegetable, "frozen-broccoli", "frozen-mixed-vegetables", "carrots"),
                part(Role.Fat, "olive-oil", "canola-oil", servings = 0.5, required = false),
            ),
        ),
        Template(
            MealSlot.Dinner, "{Protein:adj} {Base} with {Vegetable}",
            listOf(
                part(Role.Protein, "ground-beef-80-20", "canned-tuna-water", "dried-lentils", "canned-chickpeas"),
                part(Role.Base, "dried-pasta", "white-rice"),
                part(Role.Vegetable, "frozen-mixed-vegetables", "onion", "bell-pepper"),
                part(Role.Fat, "olive-oil", "canola-oil", servings = 0.5, required = false),
                part(Role.Extra, "mixed-spices", required = false),
            ),
        ),
        Template(
            MealSlot.Dinner, "{Protein:adj} Stew with {Base}",
            listOf(
                part(Role.Protein, "chicken-thighs", "chicken-drumsticks", "pork-shoulder", "dried-lentils", "canned-chickpeas", "canned-black-beans"),
                part(Role.Vegetable, "carrots", "onion", "cabbage", "frozen-mixed-vegetables"),
                part(Role.Base, "white-rice", "brown-rice", "potatoes", "whole-wheat-bread"),
                part(Role.Fat, "olive-oil", "canola-oil", servings = 0.5, required = false),
                part(Role.Extra, "mixed-spices", required = false),
            ),
        ),
        Template(
            MealSlot.Dinner, "{Protein} with {Base} & {Vegetable}",
            listOf(
                part(Role.Protein, "pork-shoulder", "chicken-drumsticks", "firm-tofu", "canned-chickpeas"),
                part(Role.Base, "sweet-potato", "potatoes"),
                part(Role.Vegetable, "cabbage", "carrots", "frozen-broccoli"),
                part(Role.Fat, "canola-oil", "olive-oil", servings = 0.5, required = false),
            ),
        ),
        Template(
            MealSlot.Dinner, "{Protein} on {Base}",
            listOf(
                part(Role.Protein, "canned-sardines", "canned-tuna-water", "eggs-large", "cottage-cheese"),
                part(Role.Base, *breads),
                part(Role.Vegetable, "baby-spinach", "bell-pepper", "frozen-broccoli", required = false),
            ),
            fallback = true,
        ),
        Template(
            MealSlot.Dinner, "{Protein:adj} & {Base:adj} Salad",
            listOf(
                part(Role.Protein, "canned-tuna-water", "canned-sardines", "eggs-large", "firm-tofu"),
                part(Role.Base, "canned-chickpeas", "canned-black-beans"),
                part(Role.Vegetable, "baby-spinach", "bell-pepper", "frozen-mixed-vegetables"),
                part(Role.Fat, "olive-oil", "canola-oil", servings = 0.5, required = false),
            ),
            fallback = true,
        ),

        // Snacks
        Template(
            MealSlot.Snack, "{Protein} & {Fruit}",
            listOf(
                part(Role.Protein, "greek-yogurt-nonfat", "skyr", "cottage-cheese"),
                part(Role.Fruit, "frozen-berries", "banana", "apple"),
            ),
        ),
        Template(
            MealSlot.Snack, "{Fruit} & {Fat}",
            listOf(
                part(Role.Fruit, "apple", "banana"),
                part(Role.Fat, "peanut-butter", "sunflower-seeds", "almonds", servings = 0.5),
            ),
        ),
        Template(
            MealSlot.Snack, "{Base} & {Fat}",
            listOf(
                part(Role.Base, "whole-wheat-bread", "sprouted-grain-bread", servings = 0.5),
                part(Role.Fat, "peanut-butter", "almonds", "sunflower-seeds", servings = 0.5),
            ),
        ),
    )

    /**
     * The most of one food a single meal gets, where the role's own limit
     * would serve too much: four eggs, two cans, three slices, a normal
     * plate of rice or potatoes.
     */
    private val servingCaps = mapOf(
        "eggs-large" to 2.0,
        "eggs-pasture-organic" to 2.0,
        "canned-tuna-water" to 2.0,
        "canned-sardines" to 2.0,
        "greek-yogurt-nonfat" to 2.0,
        "skyr" to 2.0,
        "cottage-cheese" to 2.0,
        "firm-tofu" to 2.0,
        "dried-lentils" to 2.0,
        "canned-black-beans" to 2.0,
        "canned-chickpeas" to 2.0,
        "rolled-oats" to 1.5,
        "whole-wheat-bread" to 2.0,
        "sprouted-grain-bread" to 2.0,
        "sourdough-bread" to 2.0,
        "white-rice" to 2.0,
        "brown-rice" to 2.0,
        "dried-pasta" to 2.0,
        "potatoes" to 2.0,
        "sweet-potato" to 2.0,
    )

    /** The most of [food] one meal gets in [role], for a meal [scale] times the reference size. */
    fun maximumServings(food: FoodSnapshot, role: Role, scale: Double = 1.0): Double =
        minOf(servingCaps[food.id] ?: role.maximumServings, role.maximumServings) * (if (role == Role.Fruit) 1.0 else scale)

    /** Foods as a person names them in a meal title. A food missing here uses its catalogue name. */
    private val nouns = mapOf(
        "eggs-large" to "Eggs",
        "eggs-pasture-organic" to "Eggs",
        "canned-tuna-water" to "Tuna",
        "chicken-thighs" to "Chicken",
        "chicken-drumsticks" to "Drumsticks",
        "chicken-breast" to "Chicken Breast",
        "turkey-breast-deli" to "Turkey",
        "dried-lentils" to "Lentils",
        "canned-black-beans" to "Black Beans",
        "canned-chickpeas" to "Chickpeas",
        "greek-yogurt-nonfat" to "Greek Yogurt",
        "skyr" to "Skyr",
        "cottage-cheese" to "Cottage Cheese",
        "ground-beef-80-20" to "Beef",
        "pork-shoulder" to "Pork",
        "firm-tofu" to "Tofu",
        "canned-sardines" to "Sardines",
        "whole-milk" to "Milk",
        "rolled-oats" to "Oats",
        "white-rice" to "Rice",
        "brown-rice" to "Brown Rice",
        "dried-pasta" to "Pasta",
        "potatoes" to "Potatoes",
        "sweet-potato" to "Sweet Potato",
        "whole-wheat-bread" to "Toast",
        "sprouted-grain-bread" to "Toast",
        "sourdough-bread" to "Toast",
        "frozen-broccoli" to "Broccoli",
        "frozen-mixed-vegetables" to "Mixed Veg",
        "carrots" to "Carrots",
        "onion" to "Onion",
        "cabbage" to "Cabbage",
        "bell-pepper" to "Peppers",
        "baby-spinach" to "Spinach",
        "banana" to "Banana",
        "apple" to "Apple",
        "frozen-berries" to "Berries",
        "peanut-butter" to "Peanut Butter",
        "sunflower-seeds" to "Seeds",
        "almonds" to "Almonds",
        "canola-oil" to "Oil",
        "olive-oil" to "Olive Oil",
        "cheddar-block" to "Cheddar",
    )

    /**
     * The same foods in front of another noun, where the form changes. Also
     * what counts as "the same protein" for variety: thighs and drumsticks are
     * both chicken, so lunch and dinner are not both chicken in any cut.
     */
    private val adjectives = mapOf(
        "eggs-large" to "Egg",
        "eggs-pasture-organic" to "Egg",
        "chicken-drumsticks" to "Chicken",
        "chicken-breast" to "Chicken",
        "dried-lentils" to "Lentil",
        "canned-black-beans" to "Black Bean",
        "canned-chickpeas" to "Chickpea",
        "canned-sardines" to "Sardine",
        "carrots" to "Carrot",
        "bell-pepper" to "Pepper",
    )

    fun noun(food: FoodSnapshot): String = nouns[food.id] ?: food.name.substringBefore(',')

    fun adjective(food: FoodSnapshot): String = adjectives[food.id] ?: noun(food)

    /** The title [template] gives a meal of [foods], each the food chosen for its role. */
    fun title(template: Template, foods: Map<Role, FoodSnapshot>): String =
        template.pattern.replace(Regex("""\{(\w+)(:adj)?\}""")) { match ->
            val food = foods[Role.valueOf(match.groupValues[1])] ?: return@replace ""
            if (match.groupValues[2].isEmpty()) noun(food) else adjective(food)
        }.replace(Regex("""\s+"""), " ").trim()

    // Shares

    /**
     * Each slot's share of the day's calories and protein. Dinner carries the
     * most, as it does for most people; a snack is a snack.
     */
    fun shares(schedule: EatingSchedule): Map<MealSlot, Double> = when (schedule) {
        EatingSchedule.TwoMeals -> mapOf(MealSlot.Lunch to 0.45, MealSlot.Dinner to 0.55)
        EatingSchedule.ThreeMeals -> mapOf(MealSlot.Breakfast to 0.27, MealSlot.Lunch to 0.33, MealSlot.Dinner to 0.40)
        EatingSchedule.ThreeMealsAndSnacks -> mapOf(
            MealSlot.Breakfast to 0.24,
            MealSlot.Lunch to 0.30,
            MealSlot.Dinner to 0.34,
            MealSlot.Snack to 0.12,
        )
    }

    // Building

    /**
     * A meal with the template and foods it was built from, so a swap can
     * rename it truthfully, and with the foods that are garnish rather than
     * the meal ([optional]), so a day over budget knows what to drop first.
     */
    private data class Built(
        val template: Template,
        val foods: Map<Role, FoodSnapshot>,
        val optional: Set<String>,
        val meal: MealItem,
    ) {
        /**
         * The same meal after [original] was replaced by [replacement]. The swap
         * engine works in quarter servings, so quantities are put back on each
         * food's own steps: no "1¼ bananas" in a plan the app wrote.
         */
        fun swapping(original: FoodSnapshot, replacement: FoodSnapshot, result: MealItem): Built {
            val foods = foods.mapValues { (_, food) -> if (food.id == original.id) replacement else food }
            val optional = if (original.id in optional) optional - original.id + replacement.id else optional
            val portions = result.portions.map { it.copy(servings = quantise(it.food, it.servings)) }
            return Built(template, foods, optional, result.copy(name = title(template, foods), portions = portions))
        }
    }

    /** A week, one day per element, starting with day 0. */
    fun week(input: Input, days: Int = DAYS): List<List<MealItem>> = (0 until days).map { day(input, it) }

    /**
     * The templates this profile can be given for [slot]. Fallbacks join only
     * when fewer than two regular meals fit: enough to vary a week for someone
     * who cannot cook, never offered to anyone with real choice.
     */
    fun viableTemplates(slot: MealSlot, allowed: Map<String, FoodSnapshot>): List<Template> {
        val fitting = templates.filter { template ->
            template.slot == slot && template.parts.all { part -> !part.required || part.candidates.any(allowed::containsKey) }
        }
        val regular = fitting.filter { !it.fallback }
        return if (regular.size >= 2) regular else fitting
    }

    /**
     * One day of meals, for [dayIndex] of a rotation, so consecutive days
     * differ. Slots outside the schedule are left out; a slot with no viable
     * template under this profile is left empty rather than filled badly.
     *
     * The rotation only chooses among meals that earn their place: every
     * viable template is built for the slot, and those that fall short on
     * protein, or cost well over the slot's share of the budget, are set
     * aside when others do better. A protein-poor breakfast still appears for
     * someone whose targets it can meet, never for someone whose it cannot.
     */
    fun day(input: Input, dayIndex: Int): List<MealItem> {
        val allowed = DietaryFilter.allowed(input.catalog, input.dietary).associateBy { it.id }
        val shares = shares(input.dietary.schedule)
        // How many meals today already serve each kind of protein.
        val servedProteins = mutableMapOf<String, Int>()

        var meals = input.dietary.schedule.slots.mapNotNull { slot ->
            val viable = viableTemplates(slot, allowed)
            val share = shares[slot] ?: return@mapNotNull null
            if (viable.isEmpty()) return@mapNotNull null
            val calories = input.targets.calories * share
            val protein = input.targets.protein * share

            // Each time the rotation comes round again, the next ingredient choice.
            val variant = dayIndex / viable.size
            val built = viable.map { template ->
                val varied = build(template, allowed, servedProteins.keys, calories, protein, variant)
                // Variety gives way to substance, once: if avoiding today's
                // earlier proteins leaves this meal weak, a protein served once
                // may be served again. Never a third time: nobody wants tofu at
                // every meal, whatever it does for the numbers.
                if (varied.meal.nutrition.protein >= protein * PROTEIN_FLOOR) {
                    varied
                } else {
                    build(template, allowed, servedProteins.filterValues { it >= 2 }.keys, calories, protein, variant)
                        .takeIf { it.meal.nutrition.protein > varied.meal.nutrition.protein } ?: varied
                }
            }
            val bestProtein = built.maxOf { it.meal.nutrition.protein }
            val strong = { b: Built -> b.meal.nutrition.protein >= minOf(protein, bestProtein) * PROTEIN_FLOOR }
            val fits = { b: Built -> b.meal.nutrition.calories <= calories * CALORIE_SLACK }
            val affordable = { b: Built -> input.dailyBudgetUSD <= 0 || b.meal.cost <= input.dailyBudgetUSD * share * BUDGET_SLACK }
            // Relaxed one condition at a time, money first, so a slot is never left empty.
            val pool = listOf(
                built.filter { strong(it) && fits(it) && affordable(it) },
                built.filter { strong(it) && fits(it) },
                built.filter(strong),
                built,
            ).first { it.isNotEmpty() }
            val chosen = pool[(dayIndex + slot.ordinal) % pool.size]
            proteinsIn(chosen.meal).forEach { kind -> servedProteins.merge(kind, 1, Int::plus) }
            chosen
        }

        val scales = shares.mapValues { (_, share) -> portionScale(input.targets.calories * share) }
        val toppedUp = proteinToppedUp(meals.map { it.meal }, input.targets.protein, input.dailyBudgetUSD, scales)
        val sized = balanced(toppedUp, input.targets, input.dailyBudgetUSD, scales).associateBy { it.id }
        meals = meals.map { it.copy(meal = sized.getValue(it.meal.id)) }
        if (input.dailyBudgetUSD > 0 && meals.sumOf { it.meal.cost } > input.dailyBudgetUSD) {
            meals = withinBudget(meals, input.dailyBudgetUSD, allowed.values.toList())
            meals = withoutGarnish(meals, input.dailyBudgetUSD)
            // Still over: the budget wins. The dearest protein comes down a
            // step at a time, and whatever that frees buys cheap calories back.
            // A target the budget cannot reach is the audit's to report.
            val trimmed = trimmedToBudget(meals.map { it.meal }, input.dailyBudgetUSD)
            val refilled = balanced(trimmed, input.targets, input.dailyBudgetUSD, scales).associateBy { it.id }
            meals = meals.map { it.copy(meal = refilled.getValue(it.meal.id)) }
        }
        val traded = tradedForCalories(meals.map { it.meal }, input.targets, input.dailyBudgetUSD, scales).associateBy { it.id }
        meals = meals.map { it.copy(meal = traded.getValue(it.meal.id)) }
        return meals.map { it.meal }
    }

    /** The proteins a meal serves, by kind: "Chicken" for any cut of it. */
    private fun proteinsIn(meal: MealItem): Set<String> =
        meal.portions.filter { it.food.category == FoodCategory.ProteinAnchor }.map { adjective(it.food) }.toSet()

    /** One meal from [template], sized to [calories] and [protein]. */
    private fun build(
        template: Template,
        allowed: Map<String, FoodSnapshot>,
        usedProteins: Set<String>,
        calories: Double,
        protein: Double,
        variant: Int = 0,
    ): Built {
        // Pick a food for each part. A kind of protein already served today is
        // passed over when another will do, so lunch and dinner are not both
        // chicken. On a later turn of the rotation the next choice is taken,
        // so the same template is not the same plate all week.
        val chosen = template.parts.mapNotNull { part ->
            val options = part.candidates.mapNotNull(allowed::get)
            if (options.isEmpty()) return@mapNotNull null
            val food = when (part.role) {
                Role.Protein -> {
                    val fresh = options.filter { adjective(it) !in usedProteins }.ifEmpty { options }
                    fresh[variant % minOf(2, fresh.size)]
                }
                Role.Vegetable, Role.Fruit -> options[variant % minOf(3, options.size)]
                else -> options.first()
            }
            part to food
        }.distinctBy { (_, food) -> food.id }

        val servings = chosen.associate { (part, food) -> food.id to quantise(food, part.servings) }.toMutableMap()

        fun others(id: String): NutritionFacts =
            chosen.filter { (_, food) -> food.id != id }.map { (_, food) -> food.nutrition.scaled(servings.getValue(food.id)) }.total()

        val scale = portionScale(calories)
        fun size(part: Part, food: FoodSnapshot, needed: Double, perServing: Double) {
            if (perServing <= 0) return
            // The limit on this food's own steps, so the result is still a quantity a person can buy.
            val floor = minimumServings(food, part.role)
            val limit = maxOf(floor, kotlin.math.floor(maximumServings(food, part.role, scale) / step(food) + 1e-9) * step(food))
            servings[food.id] = quantise(food, (needed / perServing).coerceIn(floor, limit)).coerceIn(floor, limit)
        }

        // Protein first: the protein food is sized to the slot's protein.
        chosen.firstOrNull { (part, _) -> part.role == Role.Protein }?.let { (part, food) ->
            size(part, food, protein - others(food.id).protein, food.nutrition.protein)
        }

        // Then the calories, filled by the carb base, or failing that the
        // fruit, or the fat: whatever the meal has that is there for energy.
        listOf(Role.Base, Role.Fruit, Role.Fat).firstNotNullOfOrNull { role ->
            chosen.firstOrNull { (part, _) -> part.role == role }
        }?.let { (part, food) ->
            size(part, food, calories - others(food.id).calories, food.nutrition.calories)
        }

        val foods = chosen.associate { (part, food) -> part.role to food }
        // Garnish: optional parts that are not the meal's base or protein. A
        // soup without its bread is still a soup; without its beans it is not.
        val optional = chosen.filter { (part, _) -> !part.required && part.role != Role.Base && part.role != Role.Protein }
            .map { (_, food) -> food.id }.toSet()
        val meal = MealItem(
            name = title(template, foods),
            slot = template.slot,
            portions = chosen.map { (_, food) -> Portion(food, servings.getValue(food.id)) },
        )
        return Built(template, foods, optional, meal)
    }

    /**
     * Moves the day's calories toward [target] one step at a time, taking
     * whichever step closes the gap most, until the day is within
     * [CALORIE_TOLERANCE] or no step helps. Carb bases move first; oil, fruit
     * and milk only when every base is at its limit, which is how a large
     * target is met without a mountain of rice, and how a small one comes
     * down once the bases are at their floor. Protein is already where it
     * should be, and vegetables are not a lever.
     */
    private fun balanced(meals: List<MealItem>, targets: NutritionFacts, budget: Double, scales: Map<MealSlot, Double>): List<MealItem> {
        val target = targets.calories
        if (target <= 0) return meals
        var day = meals
        repeat(BALANCE_STEPS) {
            val gap = target - day.totalNutrition.calories
            if (abs(gap) <= target * CALORIE_TOLERANCE) return day
            // Bases first, both ways. With every base at its limit, the
            // energy and garnish around them: oil and nuts, then fruit, then
            // milk and cheese. Protein foods only ever grow, never shrink.
            // A short day grows its main protein before its sides: a bigger
            // piece of chicken, not a third apple.
            // Growing the day costs money; it never takes the day past the budget.
            val spent = day.totalCost
            val affordable = { food: FoodSnapshot -> gap < 0 || budget <= 0 || spent + step(food) * food.costPerServing <= budget }
            // Beans and lentils count as a base here: more chickpeas in the
            // salad is how a cook fills it out.
            val step = bestStep(day, gap, Role.Base, scales) { (it.category == FoodCategory.CarbBase || it.swapGroup == SwapGroup.Legume) && affordable(it) }
                ?: (if (gap > 0) bestStep(day, gap, Role.Protein, scales) { food -> isProteinFood(food) && roomForProtein(day, food, targets) && affordable(food) } else null)
                ?: bestStep(day, gap, Role.Fat, scales) { it.category == FoodCategory.FatSource && affordable(it) }
                ?: bestStep(day, gap, Role.Fruit, scales) { it.category == FoodCategory.Fruit && affordable(it) }
                ?: bestStep(day, gap, Role.Extra, scales) { (it.swapGroup == SwapGroup.Milk || it.swapGroup == SwapGroup.Cheese) && affordable(it) }
            if (step == null) return day
            day = day.map { if (it.id == step.mealId) it.updatingServings(step.portionId, step.servings) else it }
        }
        return day
    }

    private data class Step(val mealId: UUID, val portionId: UUID, val servings: Double)

    /**
     * Raises the day's protein toward [target] one step of a protein food at a
     * time, leanest first (most protein for the calories it adds), within each
     * food's limit, until the day is within [PROTEIN_TOLERANCE]. The calorie
     * balance that follows takes the extra calories back out of the carbs. A
     * step that would take the day past [budget] is not taken: a plan the
     * app made should not open over the allowance the person set.
     */
    private fun proteinToppedUp(meals: List<MealItem>, target: Double, budget: Double, scales: Map<MealSlot, Double>): List<MealItem> {
        if (target <= 0) return meals
        var day = meals
        repeat(BALANCE_STEPS) {
            if (target - day.totalNutrition.protein <= target * PROTEIN_TOLERANCE) return day
            val spent = day.totalCost
            val best = day.flatMap { meal ->
                meal.portions.filter { isProteinFood(it.food) }.mapNotNull { portion ->
                    val step = step(portion.food)
                    val next = portion.servings + step
                    if (next > maximumServings(portion.food, Role.Protein, scales[meal.slot] ?: 1.0) + 1e-9) return@mapNotNull null
                    if (budget > 0 && spent + step * portion.food.costPerServing > budget) return@mapNotNull null
                    val leanness = portion.food.nutrition.protein / maxOf(portion.food.nutrition.calories, 1.0)
                    Step(meal.id, portion.id, next) to leanness
                }
            }.maxByOrNull { it.second } ?: return day
            val step = best.first
            day = day.map { if (it.id == step.mealId) it.updatingServings(step.portionId, step.servings) else it }
        }
        return day
    }

    /** Whether one more step of [food] keeps the day's protein under [PROTEIN_CEILING] of its target. */
    private fun roomForProtein(day: List<MealItem>, food: FoodSnapshot, targets: NutritionFacts): Boolean =
        day.totalNutrition.protein + step(food) * food.nutrition.protein <= targets.protein * PROTEIN_CEILING

    /**
     * Protein foods that are mostly protein: meat, fish, eggs, dairy, tofu.
     * Beans and lentils are protein anchors too, but in a salad or a stew
     * they are the filling as much as the protein, so trimming for money
     * takes the tuna down, not the chickpeas.
     */
    private fun isLeanProtein(food: FoodSnapshot): Boolean =
        isProteinFood(food) && food.nutrition.protein >= food.nutrition.calories * LEAN_PROTEIN_PER_KCAL

    /** The foods a plan leans on for protein: the anchors, and cottage cheese and skyr. */
    private fun isProteinFood(food: FoodSnapshot): Boolean =
        food.category == FoodCategory.ProteinAnchor || food.swapGroup == SwapGroup.CulturedDairy

    /** The single step of a portion that [qualifies] which brings the day closest to closing [gap], if any helps. */
    private fun bestStep(
        day: List<MealItem>,
        gap: Double,
        role: Role,
        scales: Map<MealSlot, Double>,
        qualifies: (FoodSnapshot) -> Boolean,
    ): Step? {
        val candidates = day.flatMap { meal ->
            meal.portions.filter { qualifies(it.food) }.mapNotNull { portion ->
                val step = step(portion.food)
                val next = if (gap > 0) portion.servings + step else portion.servings - step
                if (next < minimumServings(portion.food, role) - 1e-9) return@mapNotNull null
                if (next > maximumServings(portion.food, role, scales[meal.slot] ?: 1.0) + 1e-9) return@mapNotNull null
                val remaining = abs(gap - (next - portion.servings) * portion.food.nutrition.calories)
                Step(meal.id, portion.id, next) to remaining
            }
        }
        val best = candidates.minByOrNull { it.second } ?: return null
        return best.first.takeIf { best.second < abs(gap) }
    }

    /**
     * Brings a day under [budget] with the swap engine, one substitution at a
     * time: the dearest portion of the dearest meal first, stopping as soon as
     * the day fits. Only ever trades down, inside a food's culinary family.
     * A kind of protein another meal already serves is passed over, so a swap
     * cannot undo the rule that lunch and dinner differ. A swapped meal is
     * renamed through its template, so a bowl that lost its tuna does not
     * still say tuna.
     */
    private fun withinBudget(meals: List<Built>, budget: Double, catalog: List<FoodSnapshot>): List<Built> {
        val engine = BudgetFoodEngine(catalog)
        val result = meals.toMutableList()
        for (mealId in meals.sortedByDescending { it.meal.cost }.map { it.meal.id }) {
            for (portionId in result.first { it.meal.id == mealId }.meal.portions.sortedByDescending { it.cost }.map { it.id }) {
                if (result.sumOf { it.meal.cost } <= budget) return result
                val index = result.indexOfFirst { it.meal.id == mealId }
                val built = result[index]
                val portion = built.meal.portion(portionId) ?: continue
                // Garnish is dropped if it must be, never swapped: 38 g of
                // cabbage on eggs on toast is not a saving anyone wants.
                if (portion.food.id in built.optional) continue
                val elsewhere = result.filter { it.meal.id != mealId }.flatMap { proteinsIn(it.meal) }.toSet()
                val swap = engine.rankedReplacements(portion, built.meal)
                    .firstOrNull { it.replacement.food.category != FoodCategory.ProteinAnchor || adjective(it.replacement.food) !in elsewhere }
                    ?: continue
                result[index] = built.swapping(portion.food, swap.replacement.food, swap.resultingMeal)
            }
        }
        return result
    }


    /**
     * The last resort for a day still over budget after every swap: drop the
     * garnish (cheese, spinach, a side of fruit, milk with the oats), dearest
     * first, until the day fits. The meal itself is never touched.
     */
    private fun withoutGarnish(meals: List<Built>, budget: Double): List<Built> {
        val result = meals.toMutableList()
        val garnish = result.flatMap { built -> built.meal.portions.filter { it.food.id in built.optional }.map { built.meal.id to it } }
            .sortedByDescending { (_, portion) -> portion.cost }
        for ((mealId, portion) in garnish) {
            if (result.sumOf { it.meal.cost } <= budget) break
            val index = result.indexOfFirst { it.meal.id == mealId }
            val built = result[index]
            result[index] = built.copy(meal = built.meal.copy(portions = built.meal.portions.filter { it.id != portion.id }))
        }
        return result
    }

    /**
     * When the money has gone on protein and the calories are short (a tight
     * budget with dear proteins: sardines, cottage cheese), one step less of
     * the dearest protein per gram, and the freed money spent on cheap
     * calories through the balance. Repeated while protein stays at its target
     * and calories are still short, and only while each trade actually helps.
     */
    private fun tradedForCalories(
        meals: List<MealItem>,
        targets: NutritionFacts,
        budget: Double,
        scales: Map<MealSlot, Double>,
    ): List<MealItem> {
        if (budget <= 0 || targets.calories <= 0) return meals
        var day = meals
        repeat(BALANCE_STEPS) {
            val now = day.totalNutrition
            if (now.calories >= targets.calories * (1 - CALORIE_TOLERANCE * 2)) return day
            if (now.protein <= targets.protein) return day
            val dearest = day.flatMap { meal ->
                meal.portions.filter { isLeanProtein(it.food) }.mapNotNull { portion ->
                    val next = portion.servings - step(portion.food)
                    if (next < minimumServings(portion.food, Role.Protein) - 1e-9) return@mapNotNull null
                    if (now.protein - step(portion.food) * portion.food.nutrition.protein < targets.protein * (1 - PROTEIN_TOLERANCE * 2)) return@mapNotNull null
                    Step(meal.id, portion.id, next) to portion.food.costPerServing / portion.food.nutrition.protein
                }
            }.maxByOrNull { it.second } ?: return day
            val step = dearest.first
            val trial = balanced(
                day.map { if (it.id == step.mealId) it.updatingServings(step.portionId, step.servings) else it },
                targets, budget, scales,
            )
            if (trial.totalNutrition.calories <= now.calories) return day
            day = trial
        }
        return day
    }

    /**
     * The day brought within [budget] by taking protein down a step at a time,
     * dearest per gram first, never below half a serving. Protein is where
     * the money goes in a plan, so it is where the money comes back from.
     */
    private fun trimmedToBudget(meals: List<MealItem>, budget: Double): List<MealItem> {
        var day = meals
        repeat(BALANCE_STEPS) {
            if (day.totalCost <= budget) return day
            val dearest = day.flatMap { meal ->
                meal.portions.filter { isLeanProtein(it.food) }.mapNotNull { portion ->
                    val next = portion.servings - step(portion.food)
                    if (next < minimumServings(portion.food, Role.Protein) - 1e-9) return@mapNotNull null
                    Step(meal.id, portion.id, next) to portion.food.costPerServing / portion.food.nutrition.protein
                }
            }.maxByOrNull { it.second } ?: return day
            val step = dearest.first
            day = day.map { if (it.id == step.mealId) it.updatingServings(step.portionId, step.servings) else it }
        }
        return day
    }

    /**
     * The least of a food a meal gets: half a serving of a carb base or a
     * protein (30 g of oats, one egg, half a can), so balancing never leaves
     * a token spoonful; one step of anything else.
     */
    fun minimumServings(food: FoodSnapshot, role: Role): Double {
        val step = step(food)
        return if (role == Role.Base || role == Role.Protein) kotlin.math.ceil(0.5 / step - 1e-9) * step else step
    }

    /**
     * The smallest sensible change to this food: one whole unit of anything
     * served two or more at a time (an egg, a slice), half of anything served
     * one at a time (a can, a banana), a quarter of anything weighed.
     */
    fun step(food: FoodSnapshot): Double = when (val shape = ServingMeasure.parse(food.servingDescription)?.shape) {
        is ServingMeasure.Shape.Counted -> if (shape.count >= 2) 1.0 / shape.count else 0.5
        else -> 0.25
    }

    /** [servings] rounded to this food's [step], and never below one step. */
    fun quantise(food: FoodSnapshot, servings: Double): Double {
        val step = step(food)
        return ((servings / step).roundedHalfAway() * step).coerceAtLeast(step)
    }
}
