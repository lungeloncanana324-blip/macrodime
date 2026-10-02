/*
 * BudgetFoodEngine.kt
 * MacroDime
 *
 * The Low-Cost Swap engine. Pure, deterministic, no persistence. Port of
 * MacroDime/Engine/BudgetFoodEngine.swift.
 *
 * The problem: a meal is macro-correct but too expensive. Replace ingredients
 * with cheaper ones and keep the whole meal within a tolerance (10% by default)
 * of where it started on calories, protein, carbs and fat.
 *
 * A one-for-one swap cannot hold four macros at once: canned tuna in place of
 * salmon, matched on protein, lands the meal 49% low on fat. So a substitution
 * is followed by a rebalance pass that re-portions the fat and carb sources
 * already in the meal to close the gap.
 *
 * The three rules:
 *  1. Swap within a swap group only (the culinary family, for vegetables).
 *  2. Drift is measured against the original meal, cumulatively.
 *  3. Never trade up a tier.
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.AtwaterFactor
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.CurrencySettings
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MacroDrift
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.domain.roundedHalfAway
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A single addressable field of NutritionFacts. */
enum class MacroAxis {
    Calories,
    Protein,
    Carbs,
    Fat;

    fun valueIn(facts: NutritionFacts): Double = when (this) {
        Calories -> facts.calories
        Protein -> facts.protein
        Carbs -> facts.carbs
        Fat -> facts.fat
    }

    /** Kilocalories per gram on this axis (1 for calories, already energy). */
    val energyDensity: Double
        get() = when (this) {
            Calories -> 1.0
            Protein -> AtwaterFactor.PROTEIN
            Carbs -> AtwaterFactor.CARBOHYDRATE
            Fat -> AtwaterFactor.FAT
        }

    /** The ingredient category used as the lever when restoring this macro. */
    val leverCategory: FoodCategory?
        get() = when (this) {
            Fat -> FoodCategory.FatSource
            Carbs -> FoodCategory.CarbBase
            Protein -> FoodCategory.ProteinAnchor
            Calories -> null
        }
}

/**
 * The macro a substitution within this category is matched on. Category-driven
 * rather than "whichever macro carries the most energy": salmon is 153 kcal of
 * fat against 136 kcal of protein, so an energy rule matched it on fat and
 * proposed 12 cans of tuna. A protein anchor is replaced on protein.
 */
val FoodCategory.anchorAxis: MacroAxis
    get() = when (this) {
        FoodCategory.ProteinAnchor, FoodCategory.Dairy -> MacroAxis.Protein
        FoodCategory.CarbBase, FoodCategory.Fruit -> MacroAxis.Carbs
        FoodCategory.FatSource -> MacroAxis.Fat
        FoodCategory.Vegetable, FoodCategory.Condiment -> MacroAxis.Calories
    }

/** Every tunable knob of the swap algorithm. */
data class SwapPolicy(
    /** Maximum acceptable relative drift on the worst macro, meal-wide. */
    val macroTolerance: Double = 0.10,
    /** Servings are quantised to this step so nobody reads "1.37 cans". */
    val servingStep: Double = 0.25,
    val minimumServings: Double = 0.25,
    val maximumServings: Double = 6.0,
    /** A swap must save at least this much, meal-wide and net of any rebalance. */
    val minimumSavingsPerSwap: Double = 0.05,
    /** The ceiling tier a replacement may come from. */
    val targetTier: BudgetTier = BudgetTier.Strict,
    /** Substitutions stay inside the ingredient's SwapGroup. */
    val restrictToSameSwapGroup: Boolean = true,
    /** Re-portion existing fat and carb sources to absorb the gap a substitution opens. */
    val allowsRebalancing: Boolean = true,
    /** A macro gap smaller than this is left alone rather than chased with a quarter of oil. */
    val rebalanceThresholdGrams: Double = 1.5,
    val savingsWeight: Double = 1.0,
    val driftWeight: Double = 0.5,
    val satietyWeight: Double = 0.25,
) {
    companion object {
        val DEFAULT = SwapPolicy()

        /** For a user cutting costs. Still only swaps downward: the tier is a ceiling. */
        fun cuttingCosts(tier: BudgetTier) = SwapPolicy(targetTier = tier)
    }
}

/**
 * A quantity change made to an ingredient that was *not* substituted, to
 * restore a macro after a substitution elsewhere in the meal.
 */
data class PortionAdjustment(
    val portionId: UUID,
    val foodName: String,
    val fromServings: Double,
    val toServings: Double,
    val costDelta: Double,
) {
    val isIncrease: Boolean get() = toServings > fromServings

    /** `Extra Virgin Olive Oil 1 → 2.25`. */
    val headline: String
        get() = "$foodName ${DisplayFormat.flexible(fromServings, 2)} → ${DisplayFormat.flexible(toServings, 2)}"
}

/** One ingredient substitution, together with the complete meal it produces. */
data class PortionSwap(
    val original: Portion,
    val replacement: Portion,
    /** Quantity changes made elsewhere in the meal to absorb the macro gap. */
    val rebalanced: List<PortionAdjustment>,
    /** The meal after both the substitution and the rebalance. Apply this, not just the replacement. */
    val resultingMeal: MealItem,
    /** Meal-wide drift after this swap, against the untouched original. */
    val resultingDrift: MacroDrift,
    /** Cost of the meal this swap was applied to, before the change. */
    val previousMealCost: Double,
    /** Ranking score at selection time. Exposed for tests. */
    val score: Double,
) {
    val id: UUID get() = original.id

    /** Net meal-wide saving, after paying for any rebalance. */
    val savings: Double get() = previousMealCost - resultingMeal.cost

    val headline: String get() = "${original.food.name} → ${replacement.food.name}"
}

/** A complete swapped meal plus the substitutions that produced it. */
data class MealSwap(
    val original: MealItem,
    val swapped: MealItem,
    val portionSwaps: List<PortionSwap>,
    val drift: MacroDrift,
) {
    val id: UUID get() = original.id

    val savings: Double get() = original.cost - swapped.cost

    /** Savings as a fraction of the original cost: 0.62 for "62% cheaper". */
    val savingsFraction: Double
        get() = if (original.cost > 0) savings / original.cost else 0.0

    /** Worst-case macro movement. 0.046 reads as "within 4.6%". */
    val worstDrift: Double get() = drift.worst

    /** Every rebalance made along the way, flattened for display. */
    val allAdjustments: List<PortionAdjustment> get() = portionSwaps.flatMap { it.rebalanced }

    /** What the whole swap saves as shown: the drop in the meal's shown cost. */
    fun shownSaving(prices: CurrencySettings): Double = prices.shownSaving(original, swapped)

    /**
     * What each step saves as shown, in order: the drop in the meal's shown
     * cost from the step before. The steps add up exactly to [shownSaving],
     * which is the figure a list of steps sits under.
     */
    fun shownStepSavings(prices: CurrencySettings): List<Double> {
        var before = original
        return portionSwaps.map { step ->
            prices.shownSaving(before, step.resultingMeal).also { before = step.resultingMeal }
        }
    }
}

class BudgetFoodEngine(
    val catalog: List<FoodSnapshot> = FoodCatalog.all,
    val policy: SwapPolicy = SwapPolicy.DEFAULT,
) {

    /** A cheaper version of [meal] inside the tolerance, or the meal unchanged. */
    fun swapMeal(meal: MealItem): MealItem = bestSwap(meal)?.swapped ?: meal

    /**
     * The full substitution plan for a meal, or null if nothing can be improved
     * without breaking the macro tolerance. Portions are attempted most
     * expensive first, and drift is always measured against the *original*
     * meal, so the cumulative result honours the tolerance.
     */
    fun bestSwap(meal: MealItem): MealSwap? {
        if (meal.isEmpty) return null

        val baseline = meal.nutrition
        var working = meal
        val accepted = mutableListOf<PortionSwap>()

        // Fixed from the original meal: a rebalance may change quantities
        // mid-flight, and the attempt order should not chase that.
        val attemptOrder = meal.portions.sortedByDescending { it.cost }.map { it.id }

        for (portionId in attemptOrder) {
            val portion = working.portion(portionId) ?: continue
            val best = rankedReplacements(portion, working, baseline).firstOrNull() ?: continue
            working = best.resultingMeal
            accepted += best
        }

        if (accepted.isEmpty()) return null

        return MealSwap(
            original = meal,
            swapped = working,
            portionSwaps = accepted,
            drift = MacroDrift(baseline, working.nutrition),
        )
    }

    /**
     * Every acceptable replacement for one portion, best first. Each carries the
     * whole resulting meal: apply that, not just the replacement portion.
     */
    fun rankedReplacements(
        portion: Portion,
        meal: MealItem,
        baseline: NutritionFacts? = null,
    ): List<PortionSwap> {
        if (!portion.food.isSwapCandidate) return emptyList()

        val reference = baseline ?: meal.nutrition
        val previousCost = meal.cost

        // Never trade up: the ceiling is the cheaper of the user's tier and the
        // tier of what is being replaced.
        val ceiling = minOf(policy.targetTier, portion.food.costTier)

        val results = ArrayList<PortionSwap>(8)

        for (candidate in catalog) {
            if (candidate.id == portion.food.id) continue
            if (!candidate.isSwapCandidate) continue
            if (candidate.costTier > ceiling) continue
            // The family gate, not the category gate: broccoli and carrots are
            // both vegetables, and only one belongs in a pan of broccoli.
            if (policy.restrictToSameSwapGroup && candidate.swapGroup != portion.food.swapGroup) continue

            val servings = matchedServings(portion, candidate)
            if (servings <= 0) continue

            val replacement = Portion(food = candidate, servings = servings, id = portion.id)
            val substituted = meal.replacing(portion.id, replacement)

            val (rebalancedMeal, adjustments) = if (policy.allowsRebalancing) {
                rebalance(substituted, reference, lockedPortionId = portion.id)
            } else {
                Rebalanced(substituted, emptyList())
            }

            // Gate 1: meaningfully cheaper, net of any extra oil or rice bought.
            if (rebalancedMeal.cost > previousCost - policy.minimumSavingsPerSwap) continue

            // Gate 2: the whole meal stays within tolerance of the original.
            val drift = MacroDrift(reference, rebalancedMeal.nutrition)
            if (!drift.isWithin(policy.macroTolerance)) continue

            results += PortionSwap(
                original = portion,
                replacement = replacement,
                rebalanced = adjustments,
                resultingMeal = rebalancedMeal,
                resultingDrift = drift,
                previousMealCost = previousCost,
                score = score(previousCost, rebalancedMeal.cost, portion, replacement, drift),
            )
        }

        return results.sortedByDescending { it.score }
    }

    data class Rebalanced(val meal: MealItem, val adjustments: List<PortionAdjustment>)

    /**
     * Re-portions the meal's existing fat and carb sources toward [baseline].
     * Fat first: it is the densest macro, so fixing it moves calories furthest.
     * The substituted portion is locked. Each adjustment is kept only if it
     * lowers mean drift, so a rebalance never makes a meal worse.
     */
    fun rebalance(meal: MealItem, baseline: NutritionFacts, lockedPortionId: UUID): Rebalanced {
        var working = meal
        val adjustments = mutableListOf<PortionAdjustment>()

        for (axis in listOf(MacroAxis.Fat, MacroAxis.Carbs)) {
            val leverCategory = axis.leverCategory ?: continue

            val gap = axis.valueIn(baseline) - axis.valueIn(working.nutrition)
            if (abs(gap) < policy.rebalanceThresholdGrams) continue

            // The portion richest in this macro: moving one ingredient a long
            // way beats nudging three.
            val lever = working.portions
                .filter { it.id != lockedPortionId && it.food.category == leverCategory }
                .maxByOrNull { axis.valueIn(it.food.nutrition) }
                ?: continue
            val perServing = axis.valueIn(lever.food.nutrition)
            if (perServing <= 0.01) continue

            val newServings = quantise(lever.servings + gap / perServing)
            if (newServings == lever.servings) continue

            val candidate = working.updatingServings(lever.id, newServings)

            val before = MacroDrift(baseline, working.nutrition).mean
            val after = MacroDrift(baseline, candidate.nutrition).mean
            if (after >= before) continue

            adjustments += PortionAdjustment(
                portionId = lever.id,
                foodName = lever.food.name,
                fromServings = lever.servings,
                toServings = newServings,
                costDelta = (newServings - lever.servings) * lever.food.costPerServing,
            )
            working = candidate
        }

        return Rebalanced(working, adjustments)
    }

    /**
     * How many servings of [candidate] best stand in for [portion], matched on
     * the category's anchor macro. 0 when no sane quantity exists.
     */
    fun matchedServings(portion: Portion, candidate: FoodSnapshot): Double {
        val axis = anchorAxis(portion.food)
        val required = axis.valueIn(portion.nutrition)
        val perServing = axis.valueIn(candidate.nutrition)

        val raw = when {
            perServing > 0.01 && required > 0.01 -> required / perServing
            // Fallback for a candidate carrying none of the anchor macro.
            candidate.nutrition.calories > 0 && portion.nutrition.calories > 0 ->
                portion.nutrition.calories / candidate.nutrition.calories
            else -> return 0.0
        }
        return quantise(raw)
    }

    /**
     * The macro a substitution for this food is matched on. Falls back to
     * energy dominance only when the category's anchor macro is absent from the
     * food itself.
     */
    fun anchorAxis(food: FoodSnapshot): MacroAxis {
        val preferred = food.category.anchorAxis
        if (preferred == MacroAxis.Calories || preferred.valueIn(food.nutrition) > 0.01) return preferred

        val ranked = listOf(MacroAxis.Protein, MacroAxis.Carbs, MacroAxis.Fat)
            .map { it to it.valueIn(food.nutrition) * it.energyDensity }
            .maxByOrNull { it.second }
        if (ranked == null || ranked.second <= 0) return MacroAxis.Calories
        return ranked.first
    }

    /** Rounds to the policy's serving step and clamps to its bounds. */
    private fun quantise(servings: Double): Double {
        val stepped = (servings / policy.servingStep).roundedHalfAway() * policy.servingStep
        return min(max(stepped, policy.minimumServings), policy.maximumServings)
    }

    /** Higher is better: money saved, how far the macros moved, and fullness. */
    private fun score(
        previousCost: Double,
        resultingCost: Double,
        original: Portion,
        replacement: Portion,
        drift: MacroDrift,
    ): Double {
        val savingsFraction = if (previousCost > 0) (previousCost - resultingCost) / previousCost else 0.0
        // Normalised into 0..1 across the allowed tolerance band.
        val driftPenalty = if (policy.macroTolerance > 0) drift.mean / policy.macroTolerance else 0.0
        val satietyDelta = (replacement.food.satietyIndex - original.food.satietyIndex) / 100

        return savingsFraction * policy.savingsWeight -
            driftPenalty * policy.driftWeight +
            satietyDelta * policy.satietyWeight
    }

    /** Best protein-per-dollar ingredients, for the "budget powerhouses" card. */
    fun budgetPowerhouses(
        category: FoodCategory = FoodCategory.ProteinAnchor,
        tier: BudgetTier = BudgetTier.Strict,
        limit: Int = 5,
    ): List<FoodSnapshot> =
        catalog
            .filter { it.category == category && it.costTier <= tier && it.nutrition.protein > 0 }
            .sortedByDescending { it.proteinPerCurrencyUnit }
            .take(limit)

    /** Swaps every meal in a day. Only the meals that actually changed. */
    fun swapPlan(meals: List<MealItem>): List<MealSwap> = meals.mapNotNull { bestSwap(it) }

    /** Total savings available across a day's meals, without applying anything. */
    fun potentialSavings(meals: List<MealItem>): Double = swapPlan(meals).sumOf { it.savings }
}
