//
//  BudgetFoodEngineTests.swift
//  MacroDimeTests
//
//  Covers the swap engine's contract: cheaper, within tolerance, same category,
//  never trading up — plus the two bugs that made the feature not work at all
//  (energy-dominance anchoring, and substitution without rebalancing).
//

import XCTest
@testable import MacroDime

final class BudgetFoodEngineTests: XCTestCase {

    private let engine = BudgetFoodEngine()

    // MARK: Fixtures

    private func food(_ id: String) throws -> FoodSnapshot {
        try XCTUnwrap(FoodCatalog.food(id: id), "Missing catalogue item: \(id)")
    }

    /// The canonical expensive meal: salmon, rice, fresh broccoli, olive oil.
    /// 732 kcal / 43.2 P / 69.6 C / 31.5 F at $6.76.
    private func salmonDinner() throws -> MealItem {
        MealItem(
            name: "Salmon Dinner",
            slot: .dinner,
            portions: [
                Portion(food: try food("salmon-fillet")),
                Portion(food: try food("white-rice")),
                Portion(food: try food("fresh-broccoli")),
                Portion(food: try food("olive-oil"))
            ]
        )
    }

    // MARK: Baseline sanity

    func testFixtureMatchesExpectedMacros() throws {
        let meal = try salmonDinner()
        XCTAssertEqual(meal.nutrition.calories, 732, accuracy: 0.01)
        XCTAssertEqual(meal.nutrition.protein, 43.2, accuracy: 0.01)
        XCTAssertEqual(meal.nutrition.carbs, 69.6, accuracy: 0.01)
        XCTAssertEqual(meal.nutrition.fat, 31.5, accuracy: 0.01)
        XCTAssertEqual(meal.cost, 6.76, accuracy: 0.01)
    }

    func testCatalogueIDsAreUnique() {
        let ids = FoodCatalog.all.map(\.id)
        XCTAssertEqual(Set(ids).count, ids.count, "Duplicate catalogue id")
    }

    /// Label calories and Atwater calories should agree within the rounding
    /// slack real label data carries. A wild mismatch means a typo in the table.
    func testCatalogueMacrosAreInternallyConsistent() {
        for item in FoodCatalog.all {
            let implied = item.nutrition.caloriesFromMacros
            let stated = item.nutrition.calories
            guard stated > 30 else { continue }   // trace items: noise dominates
            XCTAssertEqual(
                implied, stated,
                accuracy: max(stated * 0.20, 15),
                "\(item.name): stated \(stated) kcal vs \(implied) from macros"
            )
        }
    }

    // MARK: Anchoring — regression

    /// Salmon carries more energy as fat (153 kcal) than as protein (136 kcal).
    /// An "anchor on the most energetic macro" rule therefore matched salmon on
    /// *fat* and proposed a dozen cans of tuna. A protein anchor is replaced on
    /// protein, whatever the energy split says.
    func testProteinAnchorIsMatchedOnProteinNotEnergy() throws {
        let salmon = try food("salmon-fillet")
        XCTAssertGreaterThan(
            salmon.nutrition.fat * AtwaterFactor.fat,
            salmon.nutrition.protein * AtwaterFactor.protein,
            "Fixture no longer exercises the regression"
        )
        XCTAssertEqual(engine.anchorAxis(for: salmon), .protein)

        let tuna = try food("canned-tuna-water")
        let servings = engine.matchedServings(
            replacing: Portion(food: salmon),
            with: tuna
        )
        // 34 g protein ÷ 36 g per can = 0.94 → one can, not twelve.
        XCTAssertEqual(servings, 1.0, accuracy: 0.001)
    }

    func testAnchorAxisFollowsCategory() throws {
        XCTAssertEqual(engine.anchorAxis(for: try food("white-rice")), .carbs)
        XCTAssertEqual(engine.anchorAxis(for: try food("olive-oil")), .fat)
        XCTAssertEqual(engine.anchorAxis(for: try food("greek-yogurt-nonfat")), .protein)
    }

    func testServingsAreQuantisedToTheStep() throws {
        let servings = engine.matchedServings(
            replacing: Portion(food: try food("salmon-fillet")),
            with: try food("eggs-large")
        )
        let steps = servings / SwapPolicy.default.servingStep
        XCTAssertEqual(steps, steps.rounded(), accuracy: 0.0001, "Servings must land on a 0.25 step")
    }

    // MARK: The headline swap

    func testSalmonDinnerCanBeSwappedWithinTolerance() throws {
        let meal = try salmonDinner()
        let swap = try XCTUnwrap(engine.bestSwap(for: meal), "The flagship swap must be reachable")

        XCTAssertLessThan(swap.swapped.cost, meal.cost)
        XCTAssertGreaterThan(swap.savings, 0)
        XCTAssertLessThanOrEqual(
            swap.worstDrift,
            SwapPolicy.default.macroTolerance,
            "Swapped meal drifted outside the 10% tolerance"
        )
        // Verified by hand against the catalogue: better than 70% cheaper.
        XCTAssertGreaterThan(swap.savingsFraction, 0.70)
    }

    /// Without the rebalance pass this swap is impossible: replacing salmon
    /// with tuna on protein leaves the meal ~49% short on fat, because the
    /// salmon was carrying 17 g of it.
    func testSubstitutionWithoutRebalancingFailsTheFatGate() throws {
        var policy = SwapPolicy.default
        policy.allowsRebalancing = false
        let plainEngine = BudgetFoodEngine(policy: policy)

        let meal = try salmonDinner()
        let salmonPortion = try XCTUnwrap(meal.portions.first)

        let withoutRebalance = plainEngine.rankedReplacements(for: salmonPortion, within: meal)
        let tunaWithout = withoutRebalance.first { $0.replacement.food.id == "canned-tuna-water" }
        XCTAssertNil(tunaWithout, "Tuna should fail the fat gate with no rebalance available")

        let withRebalance = engine.rankedReplacements(for: salmonPortion, within: meal)
        let tunaWith = try XCTUnwrap(
            withRebalance.first { $0.replacement.food.id == "canned-tuna-water" },
            "Salmon → Canned Tuna must be offered once rebalancing is allowed"
        )
        XCTAssertFalse(tunaWith.rebalanced.isEmpty, "The swap should have re-portioned the oil")
        XCTAssertLessThanOrEqual(tunaWith.resultingDrift.worst, policy.macroTolerance)
    }

    func testRebalanceAdjustsTheFatSourceUpwards() throws {
        let meal = try salmonDinner()
        let salmonPortion = try XCTUnwrap(meal.portions.first)
        let options = engine.rankedReplacements(for: salmonPortion, within: meal)

        let tuna = try XCTUnwrap(options.first { $0.replacement.food.id == "canned-tuna-water" })
        let oilAdjustment = try XCTUnwrap(
            tuna.rebalanced.first { $0.foodName.contains("Olive Oil") },
            "Olive oil is the only fat lever in this meal"
        )
        XCTAssertTrue(oilAdjustment.isIncrease, "Losing salmon's fat should raise the oil")
        XCTAssertGreaterThan(oilAdjustment.toServings, oilAdjustment.fromServings)
    }

    /// Every proposed option, not just the winner, must honour the contract.
    func testAllProposedOptionsHonourTheContract() throws {
        let meal = try salmonDinner()
        let salmonPortion = try XCTUnwrap(meal.portions.first)
        let options = engine.rankedReplacements(for: salmonPortion, within: meal)

        XCTAssertFalse(options.isEmpty)
        for option in options {
            XCTAssertEqual(option.replacement.food.category, .proteinAnchor, "Swapped outside the category")
            XCTAssertLessThanOrEqual(option.replacement.food.costTier, .strict, "Traded up a tier")
            XCTAssertLessThanOrEqual(option.resultingDrift.worst, SwapPolicy.default.macroTolerance)
            XCTAssertGreaterThanOrEqual(option.savings, SwapPolicy.default.minimumSavingsPerSwap)
            XCTAssertLessThan(option.resultingMeal.cost, meal.cost)
        }
    }

    func testOptionsAreRankedBestFirst() throws {
        let meal = try salmonDinner()
        let salmonPortion = try XCTUnwrap(meal.portions.first)
        let scores = engine.rankedReplacements(for: salmonPortion, within: meal).map(\.score)
        XCTAssertEqual(scores, scores.sorted(by: >), "Options must be ordered by score")
    }

    // MARK: Contract guarantees

    func testSwapNeverTradesUpATier() throws {
        // A meal already built from strict staples must never be "upgraded".
        let meal = MealItem(
            name: "Budget Breakfast",
            slot: .breakfast,
            portions: [
                Portion(food: try food("rolled-oats")),
                Portion(food: try food("eggs-large")),
                Portion(food: try food("banana"))
            ]
        )
        if let swap = engine.bestSwap(for: meal) {
            for portionSwap in swap.portionSwaps {
                XCTAssertLessThanOrEqual(
                    portionSwap.replacement.food.costTier,
                    portionSwap.original.food.costTier
                )
            }
            XCTAssertLessThan(swap.swapped.cost, meal.cost)
        }
    }

    func testSwapMealReturnsTheOriginalWhenNothingIsBetter() throws {
        // Canola oil is the cheapest fat in the catalogue; nothing undercuts it.
        let meal = MealItem(
            name: "Oil Only",
            slot: .snack,
            portions: [Portion(food: try food("canola-oil"))]
        )
        XCTAssertEqual(engine.swapMeal(meal).portions.first?.food.id, "canola-oil")
        XCTAssertNil(engine.bestSwap(for: meal))
    }

    func testEmptyMealYieldsNoSwap() {
        let meal = MealItem(name: "Empty", slot: .lunch)
        XCTAssertNil(engine.bestSwap(for: meal))
        XCTAssertTrue(engine.swapMeal(meal).isEmpty)
    }

    func testCondimentsAreNeverSwapped() throws {
        let meal = MealItem(
            name: "Seasoned Rice",
            slot: .lunch,
            portions: [
                Portion(food: try food("white-rice")),
                Portion(food: try food("soy-sauce"))
            ]
        )
        let soy = try XCTUnwrap(meal.portions.last)
        XCTAssertTrue(engine.rankedReplacements(for: soy, within: meal).isEmpty)
    }

    /// The whole-meal tolerance must hold cumulatively, not per substitution.
    func testCumulativeDriftStaysWithinToleranceAcrossMultipleSwaps() throws {
        let meal = try salmonDinner()
        let swap = try XCTUnwrap(engine.bestSwap(for: meal))
        XCTAssertGreaterThan(swap.portionSwaps.count, 1, "Fixture should trigger several swaps")

        let finalDrift = MacroDrift(baseline: meal.nutrition, candidate: swap.swapped.nutrition)
        XCTAssertLessThanOrEqual(finalDrift.worst, SwapPolicy.default.macroTolerance)
    }

    /// Fuzz across the catalogue: any two-ingredient meal that yields a swap
    /// must obey both gates. Catches accidental tolerance leaks.
    func testEveryGeneratedSwapObeysBothGates() throws {
        let proteins = FoodCatalog.all.filter { $0.category == .proteinAnchor }
        let carbs = FoodCatalog.all.filter { $0.category == .carbBase }

        for protein in proteins {
            for carb in carbs {
                let meal = MealItem(
                    name: "\(protein.name) and \(carb.name)",
                    slot: .dinner,
                    portions: [Portion(food: protein), Portion(food: carb)]
                )
                guard let swap = engine.bestSwap(for: meal) else { continue }

                XCTAssertLessThan(
                    swap.swapped.cost, meal.cost,
                    "\(meal.name): swap did not save money"
                )
                XCTAssertLessThanOrEqual(
                    swap.worstDrift, SwapPolicy.default.macroTolerance + 0.0001,
                    "\(meal.name): drifted to \(swap.worstDrift)"
                )
                for portion in swap.swapped.portions {
                    XCTAssertGreaterThanOrEqual(portion.servings, SwapPolicy.default.minimumServings)
                    XCTAssertLessThanOrEqual(portion.servings, SwapPolicy.default.maximumServings)
                }
            }
        }
    }

    // MARK: Drift maths

    func testDriftUsesAbsoluteFallbackForTraceMacros() {
        // A 1 g → 3 g fat move is 200% relatively, but trivial in practice.
        // The trace floor must stop that from vetoing an otherwise good swap.
        let baseline = NutritionFacts(calories: 400, protein: 40, carbs: 50, fat: 1)
        let candidate = NutritionFacts(calories: 400, protein: 40, carbs: 50, fat: 3)
        let drift = MacroDrift(baseline: baseline, candidate: candidate)
        XCTAssertEqual(drift.fat, 0.25, accuracy: 0.001)   // 2 g ÷ 8 g floor
        XCTAssertLessThan(drift.fat, 2.0)
    }

    func testIdenticalMealsHaveZeroDrift() throws {
        let meal = try salmonDinner()
        let drift = MacroDrift(baseline: meal.nutrition, candidate: meal.nutrition)
        XCTAssertEqual(drift.worst, 0, accuracy: 0.0001)
        XCTAssertTrue(drift.isWithin(0.10))
    }

    // MARK: Discovery

    func testBudgetPowerhousesAreRankedByProteinPerCurrencyUnit() {
        let ranked = engine.budgetPowerhouses(tier: .strict, limit: 5)
        XCTAssertFalse(ranked.isEmpty)
        XCTAssertEqual(
            ranked.map(\.proteinPerCurrencyUnit),
            ranked.map(\.proteinPerCurrencyUnit).sorted(by: >)
        )
        for item in ranked {
            XCTAssertEqual(item.costTier, .strict)
            XCTAssertEqual(item.category, .proteinAnchor)
        }
    }

    // MARK: Grocery aggregation

    func testGroceryListAggregatesRepeatedIngredients() throws {
        let tuna = try food("canned-tuna-water")
        let lunch = MealItem(name: "Lunch", slot: .lunch, portions: [Portion(food: tuna, servings: 1)])
        let dinner = MealItem(name: "Dinner", slot: .dinner, portions: [Portion(food: tuna, servings: 2)])

        let lines = GroceryListBuilder.lines(from: [lunch, dinner])
        XCTAssertEqual(lines.count, 1, "The same ingredient must collapse to one line")
        let line = try XCTUnwrap(lines.first)
        XCTAssertEqual(line.totalServings, 3, accuracy: 0.001)
        XCTAssertEqual(line.estimatedCost, tuna.costPerServing * 3, accuracy: 0.001)
        XCTAssertEqual(Set(line.usedInMeals), ["Lunch", "Dinner"])
    }

    func testGroceryListGroupsIntoStoreWalkOrder() throws {
        let meal = try salmonDinner()
        let groups = GroceryListBuilder.grouped(from: [meal])
        let order = groups.map(\.section.aisleOrder)
        XCTAssertEqual(order, order.sorted(), "Sections must follow the store walk")

        let total = groups.reduce(0) { $0 + $1.subtotal }
        XCTAssertEqual(total, meal.cost, accuracy: 0.001)
    }

    func testGroceryTotalMatchesMealCost() throws {
        let meals = [try salmonDinner()]
        XCTAssertEqual(
            GroceryListBuilder.estimatedTotal(for: meals),
            meals.totalCost,
            accuracy: 0.001
        )
    }
}
