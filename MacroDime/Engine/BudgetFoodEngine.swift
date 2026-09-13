//
//  BudgetFoodEngine.swift
//  MacroDime
//
//  The Low-Cost Swap engine. Pure Swift, deterministic, no persistence.
//
//  The problem: a meal is macro-correct but too expensive. Replace ingredients
//  with cheaper ones and keep the whole meal within a tolerance (10% by
//  default) of where it started on calories, protein, carbs and fat.
//
//  ## Why a straight substitution is not enough
//
//  A one-for-one swap cannot hold four macros at once. Take a salmon dinner:
//  substituting canned tuna for the salmon, matched on protein, lands the meal
//  49% low on fat — because the salmon was carrying 17 g of it. Rejecting that
//  candidate would mean rejecting the single most valuable swap in the app.
//
//  So a substitution is followed by a **rebalance pass**: the fat and carb
//  sources already in the meal are re-portioned to close the gap the
//  substitution opened. On that salmon dinner the olive oil goes from 1 tbsp to
//  2.25, and the meal lands within 4.6% on every macro while costing 55% less.
//
//  ## The three rules
//
//  1. **Swap within a category only.** Salmon may become tuna or eggs; never
//     oats, even though oats could be macro-matched on paper.
//  2. **Drift is measured against the original meal, cumulatively.** Swapping
//     three ingredients one at a time must not let the meal walk 10% away three
//     times over.
//  3. **Never trade up.** A candidate must be at or below both the user's
//     budget tier and the tier of the ingredient it replaces.
//

import Foundation

// MARK: - Macro Axis

/// A single addressable field of `NutritionFacts`. Used to pick the macro a
/// substitution is scaled against, and the macro a rebalance restores.
enum MacroAxis: String, CaseIterable, Sendable {
    case calories
    case protein
    case carbs
    case fat

    func value(in facts: NutritionFacts) -> Double {
        switch self {
        case .calories: facts.calories
        case .protein: facts.protein
        case .carbs: facts.carbs
        case .fat: facts.fat
        }
    }

    /// Kilocalories per gram on this axis (1 for calories, already energy).
    var energyDensity: Double {
        switch self {
        case .calories: 1
        case .protein: AtwaterFactor.protein
        case .carbs: AtwaterFactor.carbohydrate
        case .fat: AtwaterFactor.fat
        }
    }

    /// The ingredient category used as the lever when restoring this macro.
    var leverCategory: FoodCategory? {
        switch self {
        case .fat: .fatSource
        case .carbs: .carbBase
        case .protein: .proteinAnchor
        case .calories: nil
        }
    }
}

extension FoodCategory {
    /// The macro a substitution within this category should be matched on.
    ///
    /// Category-driven rather than "whichever macro carries the most energy":
    /// salmon is 153 kcal of fat against 136 kcal of protein, so an energy-
    /// dominance rule would try to match it on *fat* and propose 12 cans of
    /// tuna. A protein anchor is replaced on protein, full stop.
    var anchorAxis: MacroAxis {
        switch self {
        case .proteinAnchor, .dairy: .protein
        case .carbBase, .fruit: .carbs
        case .fatSource: .fat
        case .vegetable, .condiment: .calories
        }
    }
}

// MARK: - Policy

/// Every tunable knob of the swap algorithm.
struct SwapPolicy: Hashable, Sendable {
    /// Maximum acceptable relative drift on the worst macro, meal-wide.
    /// The spec's 10% error margin.
    var macroTolerance: Double = 0.10
    /// Servings are quantised to this step so the UI never says "1.37 cans".
    var servingStep: Double = 0.25
    var minimumServings: Double = 0.25
    var maximumServings: Double = 6.0
    /// A swap must save at least this much, meal-wide and net of any rebalance,
    /// to be worth the disruption.
    var minimumSavingsPerSwap: Double = 0.05
    /// The ceiling tier a replacement may come from.
    var targetTier: BudgetTier = .strict
    /// Substitutions stay inside the ingredient's `FoodCategory`.
    var restrictToSameCategory: Bool = true
    /// Whether to re-portion existing fat and carb sources to absorb the macro
    /// gap a substitution opens. Off, most protein swaps are unachievable.
    var allowsRebalancing: Bool = true
    /// A macro gap smaller than this is left alone rather than chased with a
    /// quarter-serving of oil.
    var rebalanceThresholdGrams: Double = 1.5

    /// Relative weights used to rank candidates that all pass the gates.
    var savingsWeight: Double = 1.0
    var driftWeight: Double = 0.5
    var satietyWeight: Double = 0.25

    static let `default` = SwapPolicy()

    /// Policy for a user asking to cut costs. Still only ever swaps downward:
    /// `targetTier` is a ceiling, never a target to climb to.
    static func cuttingCosts(from tier: BudgetTier) -> SwapPolicy {
        var policy = SwapPolicy()
        policy.targetTier = tier
        return policy
    }
}

// MARK: - Results

/// A quantity change made to an ingredient that was *not* substituted, in order
/// to restore a macro after a substitution elsewhere in the meal.
struct PortionAdjustment: Identifiable, Hashable, Sendable {
    var id: UUID { portionID }
    let portionID: UUID
    let foodName: String
    let fromServings: Double
    let toServings: Double
    let costDelta: Double

    var isIncrease: Bool { toServings > fromServings }

    /// e.g. `"Olive oil 1 → 2.25 tbsp"`.
    var headline: String {
        let from = fromServings.formatted(.number.precision(.fractionLength(0...2)))
        let to = toServings.formatted(.number.precision(.fractionLength(0...2)))
        return "\(foodName) \(from) → \(to)"
    }
}

/// One ingredient substitution, together with the complete meal it produces.
///
/// The resulting meal is carried rather than recomputed by the caller because a
/// substitution may have triggered a rebalance — applying only the replacement
/// portion would give a different, out-of-tolerance meal.
struct PortionSwap: Identifiable, Hashable, Sendable {
    var id: UUID { original.id }
    let original: Portion
    let replacement: Portion
    /// Quantity changes made elsewhere in the meal to absorb the macro gap.
    let rebalanced: [PortionAdjustment]
    /// The meal after both the substitution and the rebalance.
    let resultingMeal: MealItem
    /// Meal-wide drift after applying this swap, against the untouched original.
    let resultingDrift: MacroDrift
    /// Cost of the meal this swap was applied to, before the change.
    let previousMealCost: Double
    /// Ranking score at selection time. Exposed for debugging and tests.
    let score: Double

    /// Net meal-wide saving, after paying for any rebalance.
    var savings: Double { previousMealCost - resultingMeal.cost }

    var headline: String { "\(original.food.name) → \(replacement.food.name)" }
}

/// A complete swapped meal plus the substitutions that produced it.
struct MealSwap: Identifiable, Hashable, Sendable {
    var id: UUID { original.id }
    let original: MealItem
    let swapped: MealItem
    let portionSwaps: [PortionSwap]
    let drift: MacroDrift

    var savings: Double { original.cost - swapped.cost }

    /// Savings as a fraction of the original meal cost, e.g. 0.62 for "62% cheaper".
    var savingsFraction: Double {
        guard original.cost > 0 else { return 0 }
        return savings / original.cost
    }

    /// Worst-case macro movement, as a fraction. `0.046` reads as "within 4.6%".
    var worstDrift: Double { drift.worst }

    /// Every rebalance made along the way, flattened for display.
    var allAdjustments: [PortionAdjustment] {
        portionSwaps.flatMap(\.rebalanced)
    }
}

// MARK: - Engine

struct BudgetFoodEngine {

    let catalog: [FoodSnapshot]
    var policy: SwapPolicy

    init(catalog: [FoodSnapshot] = FoodCatalog.all, policy: SwapPolicy = .default) {
        self.catalog = catalog
        self.policy = policy
    }

    // MARK: Required API

    /// Returns a cheaper version of `meal` whose macros stay within the policy
    /// tolerance, or the meal unchanged when no acceptable substitution exists.
    ///
    /// The convenience form. Use `bestSwap(for:)` when the UI needs to explain
    /// *what* changed and by how much.
    func swapMeal(_ meal: MealItem) -> MealItem {
        bestSwap(for: meal)?.swapped ?? meal
    }

    /// The full substitution plan for a meal, or `nil` if nothing could be
    /// improved without breaking the macro tolerance.
    ///
    /// Portions are attempted most-expensive-first, since that is where the
    /// savings are. Drift is always re-measured against the *original* meal, so
    /// the cumulative result honours the tolerance rather than each step
    /// honouring it independently.
    func bestSwap(for meal: MealItem) -> MealSwap? {
        guard !meal.isEmpty else { return nil }

        let baseline = meal.nutrition
        var working = meal
        var accepted: [PortionSwap] = []

        // Fixed at the start, from the original meal: a rebalance may change
        // quantities mid-flight, and the attempt order should not chase that.
        let attemptOrder = meal.portions
            .sorted { $0.cost > $1.cost }
            .map(\.id)

        for portionID in attemptOrder {
            guard let portion = working.portion(id: portionID) else { continue }
            guard let best = rankedReplacements(
                for: portion,
                within: working,
                baseline: baseline
            ).first else { continue }

            working = best.resultingMeal
            accepted.append(best)
        }

        guard !accepted.isEmpty else { return nil }

        return MealSwap(
            original: meal,
            swapped: working,
            portionSwaps: accepted,
            drift: MacroDrift(baseline: baseline, candidate: working.nutrition)
        )
    }

    // MARK: Candidate search

    /// Every acceptable replacement for one portion, best first.
    ///
    /// Exposed publicly so the UI can offer a "swap this ingredient" picker
    /// rather than only the all-at-once button. Each result carries the whole
    /// resulting meal — apply that, not just the replacement portion.
    func rankedReplacements(
        for portion: Portion,
        within meal: MealItem,
        baseline: NutritionFacts? = nil
    ) -> [PortionSwap] {
        guard portion.food.isSwapCandidate else { return [] }

        let reference = baseline ?? meal.nutrition
        let previousCost = meal.cost

        // Never trade up: the ceiling is the cheaper of the user's tier and the
        // tier of what is being replaced.
        let ceiling = min(policy.targetTier, portion.food.costTier)

        var results: [PortionSwap] = []
        results.reserveCapacity(8)

        for candidate in catalog {
            guard candidate.id != portion.food.id else { continue }
            guard candidate.isSwapCandidate else { continue }
            guard candidate.costTier <= ceiling else { continue }
            if policy.restrictToSameCategory {
                guard candidate.category == portion.food.category else { continue }
            }

            let servings = matchedServings(replacing: portion, with: candidate)
            guard servings > 0 else { continue }

            let replacement = Portion(id: portion.id, food: candidate, servings: servings)
            let substituted = meal.replacing(portionID: portion.id, with: replacement)

            // Close the macro gap the substitution opened, using ingredients
            // already in the meal.
            let (rebalancedMeal, adjustments) = policy.allowsRebalancing
                ? rebalance(substituted, toward: reference, locking: portion.id)
                : (substituted, [])

            // Gate 1: the meal has to end up meaningfully cheaper, net of any
            // extra oil or rice the rebalance bought.
            guard rebalancedMeal.cost <= previousCost - policy.minimumSavingsPerSwap else { continue }

            // Gate 2: the whole meal has to stay within tolerance of the original.
            let drift = MacroDrift(baseline: reference, candidate: rebalancedMeal.nutrition)
            guard drift.isWithin(policy.macroTolerance) else { continue }

            results.append(
                PortionSwap(
                    original: portion,
                    replacement: replacement,
                    rebalanced: adjustments,
                    resultingMeal: rebalancedMeal,
                    resultingDrift: drift,
                    previousMealCost: previousCost,
                    score: score(
                        previousCost: previousCost,
                        resultingCost: rebalancedMeal.cost,
                        original: portion,
                        replacement: replacement,
                        drift: drift
                    )
                )
            )
        }

        return results.sorted { $0.score > $1.score }
    }

    // MARK: Rebalancing

    /// Re-portions the meal's existing fat and carb sources to pull it back
    /// toward `baseline`.
    ///
    /// Fat is corrected first: it is the densest macro, so fixing it moves
    /// calories the furthest, and the carb pass then works against an
    /// already-close calorie total. The substituted portion is locked — undoing
    /// the swap by re-scaling it would defeat the point.
    ///
    /// Each adjustment is kept only if it actually lowers mean drift, so a
    /// rebalance can never make a meal worse than the raw substitution.
    func rebalance(
        _ meal: MealItem,
        toward baseline: NutritionFacts,
        locking lockedPortionID: UUID
    ) -> (meal: MealItem, adjustments: [PortionAdjustment]) {
        var working = meal
        var adjustments: [PortionAdjustment] = []

        for axis in [MacroAxis.fat, .carbs] {
            guard let leverCategory = axis.leverCategory else { continue }

            let gap = axis.value(in: baseline) - axis.value(in: working.nutrition)
            guard abs(gap) >= policy.rebalanceThresholdGrams else { continue }

            // Use the portion richest in this macro — moving one ingredient a
            // long way beats nudging three.
            let lever = working.portions
                .filter { $0.id != lockedPortionID && $0.food.category == leverCategory }
                .max { axis.value(in: $0.food.nutrition) < axis.value(in: $1.food.nutrition) }

            guard let lever else { continue }
            let perServing = axis.value(in: lever.food.nutrition)
            guard perServing > 0.01 else { continue }

            let target = lever.servings + gap / perServing
            let newServings = quantise(target)
            guard newServings != lever.servings else { continue }

            let candidate = working.updatingServings(portionID: lever.id, to: newServings)

            // Keep it only if it helps.
            let before = MacroDrift(baseline: baseline, candidate: working.nutrition).mean
            let after = MacroDrift(baseline: baseline, candidate: candidate.nutrition).mean
            guard after < before else { continue }

            adjustments.append(
                PortionAdjustment(
                    portionID: lever.id,
                    foodName: lever.food.name,
                    fromServings: lever.servings,
                    toServings: newServings,
                    costDelta: (newServings - lever.servings) * lever.food.costPerServing
                )
            )
            working = candidate
        }

        return (working, adjustments)
    }

    // MARK: Scaling

    /// How many servings of `candidate` best stand in for `portion`.
    ///
    /// Matched on the *category's* anchor macro — protein for a protein anchor,
    /// fat for a fat source — not on whichever macro happens to carry the most
    /// energy. See `FoodCategory.anchorAxis` for why that distinction matters.
    ///
    /// Returns `0` when no sane quantity exists.
    func matchedServings(replacing portion: Portion, with candidate: FoodSnapshot) -> Double {
        let axis = anchorAxis(for: portion.food)
        let required = axis.value(in: portion.nutrition)
        let perServing = axis.value(in: candidate.nutrition)

        let raw: Double
        if perServing > 0.01, required > 0.01 {
            raw = required / perServing
        } else if candidate.nutrition.calories > 0, portion.nutrition.calories > 0 {
            // Fallback for a candidate carrying none of the anchor macro.
            raw = portion.nutrition.calories / candidate.nutrition.calories
        } else {
            return 0
        }

        return quantise(raw)
    }

    /// The macro a substitution for this food is matched on. Falls back to
    /// energy dominance when the category's anchor macro is absent from the
    /// food itself.
    func anchorAxis(for food: FoodSnapshot) -> MacroAxis {
        let preferred = food.category.anchorAxis
        if preferred == .calories || preferred.value(in: food.nutrition) > 0.01 {
            return preferred
        }

        let ranked = [MacroAxis.protein, .carbs, .fat]
            .map { (axis: $0, energy: $0.value(in: food.nutrition) * $0.energyDensity) }
            .max { $0.energy < $1.energy }

        guard let ranked, ranked.energy > 0 else { return .calories }
        return ranked.axis
    }

    /// Rounds to the policy's serving step and clamps to its bounds.
    private func quantise(_ servings: Double) -> Double {
        let stepped = (servings / policy.servingStep).rounded() * policy.servingStep
        return min(max(stepped, policy.minimumServings), policy.maximumServings)
    }

    // MARK: Ranking

    /// Higher is better. Combines the three things a user actually cares about:
    /// money saved, how far the macros moved, and whether the replacement will
    /// keep them full.
    private func score(
        previousCost: Double,
        resultingCost: Double,
        original: Portion,
        replacement: Portion,
        drift: MacroDrift
    ) -> Double {
        let savingsFraction = previousCost > 0
            ? (previousCost - resultingCost) / previousCost
            : 0
        // Normalised into 0...1 across the allowed tolerance band.
        let driftPenalty = policy.macroTolerance > 0
            ? drift.mean / policy.macroTolerance
            : 0
        let satietyDelta = (replacement.food.satietyIndex - original.food.satietyIndex) / 100

        return savingsFraction * policy.savingsWeight
            - driftPenalty * policy.driftWeight
            + satietyDelta * policy.satietyWeight
    }

    // MARK: Discovery helpers

    /// Best protein-per-currency-unit ingredients, for the "budget powerhouses"
    /// card on the dashboard.
    func budgetPowerhouses(
        category: FoodCategory = .proteinAnchor,
        tier: BudgetTier = .strict,
        limit: Int = 5
    ) -> [FoodSnapshot] {
        catalog
            .filter { $0.category == category && $0.costTier <= tier && $0.nutrition.protein > 0 }
            .sorted { $0.proteinPerCurrencyUnit > $1.proteinPerCurrencyUnit }
            .prefix(limit)
            .map { $0 }
    }

    /// Swaps every meal in a day. Returns only the meals that actually changed.
    func swapPlan(_ meals: [MealItem]) -> [MealSwap] {
        meals.compactMap { bestSwap(for: $0) }
    }

    /// Total savings available across a day's meals, without applying anything.
    func potentialSavings(for meals: [MealItem]) -> Double {
        swapPlan(meals).reduce(0) { $0 + $1.savings }
    }
}
