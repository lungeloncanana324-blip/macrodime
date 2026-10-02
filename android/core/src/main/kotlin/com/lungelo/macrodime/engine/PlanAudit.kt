/*
 * PlanAudit.kt
 * MacroDime
 *
 * Answers the question the app has to answer honestly: what is wrong with this
 * plan? Port of MacroDime/Engine/PlanAudit.swift.
 *
 * Two callers, one rule set: onboarding runs feasibility() before anything is
 * planned, so an impossible combination is named while the user is still
 * choosing; the dashboard runs day() on the plan the user actually built.
 * Every gap carries a remedy, not just a diagnosis.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.CurrencySettings
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.totalCost
import com.lungelo.macrodime.domain.totalNutrition
import java.util.Locale
import kotlin.math.abs

data class PlanGap(
    val kind: Kind,
    val severity: Severity,
    val title: String,
    val detail: String,
    val remedy: String,
) {
    /** How much this matters. Declared mildest first, so the natural order is severity. */
    enum class Severity(val displayName: String) {
        Info("Note"),
        Caution("Worth fixing"),
        Blocking("Blocks your plan"),
    }

    enum class Kind(val rawValue: String) {
        EmptyPlan("emptyPlan"),
        UnfilledSlot("unfilledSlot"),
        ProteinShortfall("proteinShortfall"),
        CalorieDrift("calorieDrift"),
        BudgetOverrun("budgetOverrun"),
        NoVegetable("noVegetable"),
        LowVariety("lowVariety"),
        CookingEffort("cookingEffort"),
        RestrictionConflict("restrictionConflict"),
        ProteinFeasibility("proteinFeasibility"),
        MissingProteinSources("missingProteinSources"),
        RestrictionsCost("restrictionsCost"),
        EatingOut("eatingOut"),
        UntrackedNutrients("untrackedNutrients"),
    }

    val id: String get() = "${kind.rawValue}|$title"
}

object PlanAudit {

    /** Every threshold the audit uses, in one place. */
    object Constants {
        /** Share of the protein target that counts as met. Below 60% the plan is not a plan for it. */
        const val PROTEIN_CAUTION = 0.80
        const val PROTEIN_BLOCKING = 0.60

        /** How far total calories may sit from target before it is called out. */
        const val CALORIE_CAUTION = 0.15
        const val CALORIE_BLOCKING = 0.30

        /** Spend against allowance, as a multiple. */
        const val BUDGET_CAUTION = 1.00
        const val BUDGET_BLOCKING = 1.25

        /**
         * Daily cost, in USD, of the carbs, fats and vegetables around the
         * protein anchor. Deliberately flat and rough: it exists to catch "this
         * budget cannot work", not to price a meal.
         */
        const val NON_PROTEIN_DAILY_COST = 2.50

        /** Distinct protein anchors a day needs before the plan is called repetitive. */
        const val VARIETY_MINIMUM = 2
    }

    /** The result of a run, ordered worst first so the UI can render it as is. */
    class Report(gaps: List<PlanGap>) {
        val gaps: List<PlanGap> = gaps.sortedWith(
            compareByDescending<PlanGap> { it.severity }.thenBy { it.title },
        )

        val isEmpty: Boolean get() = gaps.isEmpty()
        val blocking: List<PlanGap> get() = gaps.filter { it.severity == PlanGap.Severity.Blocking }
        val cautions: List<PlanGap> get() = gaps.filter { it.severity == PlanGap.Severity.Caution }
        val notes: List<PlanGap> get() = gaps.filter { it.severity == PlanGap.Severity.Info }

        val worstSeverity: PlanGap.Severity? get() = gaps.maxOfOrNull { it.severity }

        /** One line for a card header. */
        val headline: String
            get() = when (worstSeverity) {
                null -> "No gaps found"
                PlanGap.Severity.Info -> if (notes.size == 1) "1 note" else "${notes.size} notes"
                PlanGap.Severity.Caution -> if (cautions.size == 1) "1 thing to fix" else "${cautions.size} things to fix"
                PlanGap.Severity.Blocking ->
                    if (blocking.size == 1) "1 blocking problem" else "${blocking.size} blocking problems"
            }

        fun first(kind: PlanGap.Kind): PlanGap? = gaps.firstOrNull { it.kind == kind }

        operator fun contains(kind: PlanGap.Kind): Boolean = gaps.any { it.kind == kind }

        override fun equals(other: Any?): Boolean = other is Report && other.gaps == gaps

        override fun hashCode(): Int = gaps.hashCode()

        override fun toString(): String = "Report(${gaps.map { it.title }})"
    }

    data class CostEstimate(val cost: Double, val anchor: FoodSnapshot)

    /**
     * The cheapest day that could meet this target with only foods the profile
     * allows: the protein bought at the best protein-per-dollar rate, plus a
     * flat allowance for everything around it. Null only when the profile
     * allows no protein source at all. The single source of this estimate, so
     * the feasibility check and the onboarding warning cannot disagree.
     */
    fun minimumDailyCostUSD(
        targets: NutritionFacts,
        dietary: DietaryProfile,
        catalog: List<FoodSnapshot> = FoodCatalog.all,
    ): CostEstimate? {
        val best = DietaryFilter.allowedIn(FoodCategory.ProteinAnchor, catalog, dietary)
            .filter { it.proteinPerCurrencyUnit > 0 }
            .sortedByDescending { it.proteinPerCurrencyUnit }
            .firstOrNull() ?: return null
        return CostEstimate(targets.protein / best.proteinPerCurrencyUnit + Constants.NON_PROTEIN_DAILY_COST, best)
    }

    /** Can this profile reach this target inside this budget? Run during onboarding. */
    fun feasibility(
        targets: NutritionFacts?,
        dailyBudgetUSD: Double,
        dietary: DietaryProfile,
        catalog: List<FoodSnapshot> = FoodCatalog.all,
        currency: CurrencySettings = CurrencySettings.USD,
    ): Report {
        val gaps = mutableListOf<PlanGap>()

        val allowed = DietaryFilter.allowed(catalog, dietary)
        val estimate = targets?.let { minimumDailyCostUSD(it, dietary, catalog) }

        if (targets != null && estimate == null) {
            gaps += PlanGap(
                kind = PlanGap.Kind.MissingProteinSources,
                severity = PlanGap.Severity.Blocking,
                title = "No protein source fits your restrictions",
                detail = "Nothing left in the catalogue is a protein anchor under your pattern and avoid list, " +
                    "so ${DisplayFormat.grams(targets.protein)} of protein a day cannot be planned.",
                remedy = "Allow one protein group you have excluded, or switch to a less strict pattern.",
            )
        } else if (targets != null && estimate != null && dailyBudgetUSD > 0) {
            if (estimate.cost > dailyBudgetUSD * Constants.BUDGET_BLOCKING) {
                gaps += PlanGap(
                    kind = PlanGap.Kind.ProteinFeasibility,
                    severity = PlanGap.Severity.Blocking,
                    title = "This target does not fit this budget",
                    detail = "${DisplayFormat.grams(targets.protein)} of protein costs about " +
                        "${currency.format(estimate.cost)} a day at the cheapest source you allow, " +
                        "${estimate.anchor.name}, against an allowance of ${currency.format(dailyBudgetUSD)}.",
                    remedy = "Raise the daily allowance to at least ${currency.format(estimate.cost)}, " +
                        "or lower the protein target.",
                )
            } else if (estimate.cost > dailyBudgetUSD * Constants.BUDGET_CAUTION) {
                gaps += PlanGap(
                    kind = PlanGap.Kind.ProteinFeasibility,
                    severity = PlanGap.Severity.Caution,
                    title = "Your budget leaves no room to move",
                    detail = "Hitting ${DisplayFormat.grams(targets.protein)} of protein costs about " +
                        "${currency.format(estimate.cost)} a day at ${estimate.anchor.name} prices, " +
                        "which is most of your ${currency.format(dailyBudgetUSD)} allowance.",
                    remedy = "Add a little headroom, or expect the plan to lean hard on ${estimate.anchor.name}.",
                )
            }
        }

        gaps += restrictionNotes(dietary, allowed, catalog)
        gaps += disclosureNotes(dietary)
        return Report(gaps)
    }

    /**
     * Audits the plan the user actually built: macros against target, spend
     * against allowance, and everything about the profile the plan no longer
     * respects.
     */
    fun day(
        meals: List<MealItem>,
        targets: NutritionFacts?,
        dailyBudgetUSD: Double,
        dietary: DietaryProfile,
        catalog: List<FoodSnapshot> = FoodCatalog.all,
        currency: CurrencySettings = CurrencySettings.USD,
    ): Report {
        val gaps = mutableListOf<PlanGap>()

        val planned = meals.filter { !it.isEmpty }
        val consumed = meals.totalNutrition
        val spend = meals.totalCost
        val bestAnchor = DietaryFilter.allowedIn(FoodCategory.ProteinAnchor, catalog, dietary).firstOrNull()

        if (planned.isEmpty()) {
            gaps += PlanGap(
                kind = PlanGap.Kind.EmptyPlan,
                severity = PlanGap.Severity.Caution,
                title = "Nothing is planned for this day",
                detail = "There are no meals on this date, so there are no numbers to judge.",
                remedy = "Add a meal on the Plan tab, or start from something you already eat and let the " +
                    "swap engine cheapen it.",
            )
        }

        gaps += macroGaps(consumed, targets, planned, bestAnchor)
        gaps += budgetGaps(spend, dailyBudgetUSD, planned, currency)
        gaps += compositionGaps(planned, dietary)
        gaps += disclosureNotes(dietary)
        return Report(gaps)
    }

    private fun macroGaps(
        consumed: NutritionFacts,
        targets: NutritionFacts?,
        planned: List<MealItem>,
        bestAnchor: FoodSnapshot?,
    ): List<PlanGap> {
        if (targets == null || planned.isEmpty()) return emptyList()
        val gaps = mutableListOf<PlanGap>()

        if (targets.protein > 0) {
            val ratio = consumed.protein / targets.protein
            if (ratio < Constants.PROTEIN_CAUTION) {
                val short = targets.protein - consumed.protein
                val leverage = bestAnchor?.let { " The cheapest source you allow is ${it.name}." } ?: ""
                gaps += PlanGap(
                    kind = PlanGap.Kind.ProteinShortfall,
                    severity = if (ratio < Constants.PROTEIN_BLOCKING) PlanGap.Severity.Blocking else PlanGap.Severity.Caution,
                    title = "${DisplayFormat.grams(short)} of protein short",
                    detail = "You are at ${DisplayFormat.grams(consumed.protein)} of " +
                        "${DisplayFormat.grams(targets.protein)}.$leverage",
                    remedy = "Put a protein anchor in the meal with the most room, then let the swap engine " +
                        "rebalance the rest.",
                )
            }
        }

        if (targets.calories > 0) {
            val drift = abs(consumed.calories - targets.calories) / targets.calories
            if (drift > Constants.CALORIE_CAUTION) {
                val difference = consumed.calories - targets.calories
                gaps += PlanGap(
                    kind = PlanGap.Kind.CalorieDrift,
                    severity = if (drift > Constants.CALORIE_BLOCKING) PlanGap.Severity.Caution else PlanGap.Severity.Info,
                    title = if (difference < 0) {
                        "${DisplayFormat.calories(abs(difference))} under target"
                    } else {
                        "${DisplayFormat.calories(difference)} over target"
                    },
                    detail = "${DisplayFormat.calories(consumed.calories)} planned against a target of " +
                        "${DisplayFormat.calories(targets.calories)}.",
                    remedy = if (difference < 0) {
                        "Add a carb or fat portion to the smallest meal."
                    } else {
                        "Trim a carb or fat portion rather than a protein one."
                    },
                )
            }
        }
        return gaps
    }

    private fun budgetGaps(
        spend: Double,
        dailyBudgetUSD: Double,
        planned: List<MealItem>,
        currency: CurrencySettings,
    ): List<PlanGap> {
        if (dailyBudgetUSD <= 0 || planned.isEmpty()) return emptyList()
        val ratio = spend / dailyBudgetUSD
        if (ratio <= Constants.BUDGET_CAUTION) return emptyList()

        val over = spend - dailyBudgetUSD
        return listOf(
            PlanGap(
                kind = PlanGap.Kind.BudgetOverrun,
                severity = if (ratio > Constants.BUDGET_BLOCKING) PlanGap.Severity.Blocking else PlanGap.Severity.Caution,
                title = "${currency.format(over)} over your allowance",
                detail = "This day costs ${currency.format(spend)} against an allowance of " +
                    "${currency.format(dailyBudgetUSD)}.",
                remedy = "Run a low-cost swap on the most expensive meal; the engine only proposes changes " +
                    "that keep the macros within 10%.",
            ),
        )
    }

    private fun compositionGaps(meals: List<MealItem>, dietary: DietaryProfile): List<PlanGap> {
        if (meals.isEmpty()) return emptyList()
        val gaps = mutableListOf<PlanGap>()
        val portions = meals.flatMap { it.portions }

        // Ingredients the plan uses that this profile no longer allows: likely
        // whenever the restrictions changed after a plan was built, which is
        // exactly when a silent contradiction would be worst.
        val conflicts = portions.filter { !DietaryFilter.allows(it.food, dietary) }
        if (conflicts.isNotEmpty()) {
            val names = conflicts.map { it.food.name }.toSortedSet().take(3).joinToString(", ")
            val reasons = conflicts.mapNotNull { DietaryFilter.rejection(it.food, dietary)?.reason }
                .toSortedSet().take(2).joinToString(", ")
            gaps += PlanGap(
                kind = PlanGap.Kind.RestrictionConflict,
                severity = PlanGap.Severity.Blocking,
                title = "${conflicts.size} portion${if (conflicts.size == 1) "" else "s"} break your restrictions",
                detail = "$names ${reasons.ifEmpty { "no longer fit your profile" }}.",
                remedy = "Replace them, or widen your restrictions if that choice was not deliberate.",
            )
        }

        val tooSlow = portions.filter { it.food.prepMinutes > dietary.prepEffort.maximumMinutes }
        if (tooSlow.isNotEmpty()) {
            val names = tooSlow.map { it.food.name }.toSortedSet().take(2).joinToString(" and ")
            gaps += PlanGap(
                kind = PlanGap.Kind.CookingEffort,
                severity = PlanGap.Severity.Caution,
                title = "${tooSlow.size} ingredient${if (tooSlow.size == 1) "" else "s"} take longer than you will cook",
                detail = "$names exceed your ${dietary.prepEffort.displayName.lowercase(Locale.ROOT)} limit.",
                remedy = "Swap them for ready-to-eat or frozen equivalents, or allow yourself more time.",
            )
        }

        if (meals.any { it.slot == MealSlot.Dinner } && portions.none { it.food.category == FoodCategory.Vegetable }) {
            gaps += PlanGap(
                kind = PlanGap.Kind.NoVegetable,
                severity = PlanGap.Severity.Info,
                title = "No vegetable with dinner",
                detail = "Dinner carries no vegetable portion. Vegetables are the cheapest way to add volume and " +
                    "fullness, and they are not counted as a macro here.",
                remedy = "Add a vegetable to dinner; frozen florets cost about half of fresh.",
            )
        }

        val anchorIds = portions.filter { it.food.category == FoodCategory.ProteinAnchor }.map { it.food.id }.toSet()
        if (anchorIds.size < Constants.VARIETY_MINIMUM && meals.size >= 2) {
            gaps += PlanGap(
                kind = PlanGap.Kind.LowVariety,
                severity = PlanGap.Severity.Info,
                title = if (anchorIds.isEmpty()) "No protein anchor all day" else "One protein source all day",
                detail = if (anchorIds.isEmpty()) {
                    "No meal in the day carries a protein anchor."
                } else {
                    "Every meal draws on the same ingredient, which is dull and narrows your micronutrients."
                },
                remedy = "Rotate two or three anchors across the week: eggs, canned tuna, lentils, chicken " +
                    "thighs, tofu and sardines are all in the budget tier.",
            )
        }

        val plannedSlots = meals.map { it.slot }.toSet()
        val unfilled = dietary.schedule.slots.filter { it !in plannedSlots }
        if (unfilled.isNotEmpty()) {
            gaps += PlanGap(
                kind = PlanGap.Kind.UnfilledSlot,
                severity = PlanGap.Severity.Info,
                title = "${unfilled.size} of your ${dietary.schedule.occasionsPerDay} meals are empty",
                detail = "Your schedule is ${dietary.schedule.displayName.lowercase(Locale.ROOT)}, and " +
                    "${unfilled.joinToString(", ") { it.displayName }} has nothing planned.",
                remedy = "Fill it, or change your schedule in profile so the plan matches how you actually eat.",
            )
        }
        return gaps
    }

    /** What the restrictions cost: how much of the catalogue survives, and why the rest does not. */
    private fun restrictionNotes(
        dietary: DietaryProfile,
        allowed: List<FoodSnapshot>,
        catalog: List<FoodSnapshot>,
    ): List<PlanGap> {
        if (!dietary.isRestricted) return emptyList()
        val removed = catalog.size - allowed.size
        if (removed <= 0) return emptyList()

        val breakdown = DietaryFilter.rejectionBreakdown(catalog, dietary)
            .take(3)
            .joinToString(", ") { "${it.count} ${it.label.lowercase(Locale.ROOT)}" }

        return listOf(
            PlanGap(
                kind = PlanGap.Kind.RestrictionsCost,
                severity = if (removed > catalog.size / 2) PlanGap.Severity.Caution else PlanGap.Severity.Info,
                title = "$removed of ${catalog.size} ingredients are out",
                detail = "Removed by your settings: $breakdown.",
                remedy = "This is expected; it is only worth acting on if a meal type you want has become impossible.",
            ),
        )
    }

    /**
     * The honest list of what this app does *not* model. Always shown, because
     * a plan that looks complete and is not is the real risk.
     */
    private fun disclosureNotes(dietary: DietaryProfile): List<PlanGap> {
        val gaps = mutableListOf(
            PlanGap(
                kind = PlanGap.Kind.UntrackedNutrients,
                severity = PlanGap.Severity.Info,
                title = "What this plan does not track",
                detail = "Calories, protein, carbohydrate, fat and cost are checked. Fibre, sodium, " +
                    "micronutrients and the glycaemic index are not.",
                remedy = "Cover those with variety and whole foods rather than with this app.",
            ),
        )
        if (dietary.mealsOutPerWeek > 0) {
            gaps += PlanGap(
                kind = PlanGap.Kind.EatingOut,
                severity = PlanGap.Severity.Info,
                title = "${dietary.mealsOutPerWeek} meals a week are eaten out",
                detail = "Meals away from home are not planned, counted or budgeted here, so your real spend " +
                    "and intake will run above the plan.",
                remedy = "Subtract what you know you spend eating out from the daily allowance, or plan fewer " +
                    "meals out.",
            )
        }
        return gaps
    }
}
