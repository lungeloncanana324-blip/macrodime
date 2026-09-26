//
//  SwapGroupTests.swift
//  MacroDimeTests
//
//  The vegetable gap, closed.
//
//  `FoodCategory.vegetable` says "this is the vegetable component of a meal",
//  which is too coarse to substitute on. Anchored on calories, the engine used
//  to accept any vegetable in place of any other, so a salmon dinner would be
//  offered carrots in place of fresh broccoli: 7% meal-wide protein drift, well
//  inside the 10% tolerance, and something no cook would do.
//
//  These tests are written to fail loudly if that behaviour ever comes back,
//  including through the most likely route: a new vegetable added without a
//  culinary family.
//

import XCTest
@testable import MacroDime

final class SwapGroupTests: XCTestCase {

    private let engine = BudgetFoodEngine(catalog: FoodCatalog.reference)

    private func food(_ id: String) throws -> FoodSnapshot {
        try XCTUnwrap(FoodCatalog.referenceFood(id: id), "Missing catalogue item: \(id)")
    }

    /// Fresh broccoli, white rice, olive oil and salmon: the canonical expensive
    /// dinner, and the fixture the original bug was reported against.
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

    private func broccoliPortion(in meal: MealItem) throws -> Portion {
        try XCTUnwrap(meal.portions.first { $0.food.id == "fresh-broccoli" })
    }

    // MARK: Catalogue invariants

    /// The guard against the bug coming back through a new ingredient: every
    /// vegetable must name a culinary family. The default group for a vegetable
    /// is `.unclassified`, which substitutes with nothing, so an unclassified
    /// vegetable is not dangerous, only useless. This test stops it shipping.
    func testEveryVegetableHasACulinaryFamily() {
        let vegetables = FoodCatalog.reference.filter { $0.category == .vegetable }
        XCTAssertFalse(vegetables.isEmpty)
        for vegetable in vegetables {
            XCTAssertNotEqual(
                vegetable.swapGroup, .unclassified,
                "\(vegetable.name) has no culinary family, so it can never be swapped"
            )
        }
    }

    /// A group must never claim a category other than the food's own, or the
    /// ingredient picker and the meal breakdown would classify it twice.
    func testEverySwapGroupMapsBackToItsOwnCategory() {
        for item in FoodCatalog.reference {
            XCTAssertEqual(
                item.swapGroup.category, item.category,
                "\(item.name): group \(item.swapGroup.rawValue) belongs to \(item.swapGroup.category.rawValue), not \(item.category.rawValue)"
            )
        }
    }

    /// Outside vegetables the group is a mirror of the category, so no existing
    /// swap behaviour (salmon to tuna, chicken breast to chicken thighs) changes.
    func testNonVegetableGroupsMirrorTheirCategory() {
        for item in FoodCatalog.reference where item.category != .vegetable {
            XCTAssertEqual(
                item.swapGroup, SwapGroup.default(for: item.category),
                "\(item.name) should be grouped by its category"
            )
        }
    }

    // MARK: The reported bug

    /// Both are `.vegetable`. That shared category is the whole reason the old
    /// gate let them trade places, so the assertion is worth keeping: if it ever
    /// stops holding, this test has lost its subject.
    func testBroccoliAndCarrotsShareTheCategoryThatUsedToAllowTheSwap() throws {
        let broccoli = try food("fresh-broccoli")
        let carrots = try food("carrots")

        XCTAssertEqual(broccoli.category, carrots.category)
        XCTAssertEqual(broccoli.category, .vegetable)
        XCTAssertNotEqual(broccoli.swapGroup, carrots.swapGroup)
    }

    /// No vegetable is ever offered as a replacement for a vegetable from a
    /// different family, across the whole catalogue rather than one fixture.
    func testVegetableSwapsStayInsideTheFamily() throws {
        for vegetable in FoodCatalog.reference where vegetable.category == .vegetable {
            let meal = MealItem(
                name: "Side",
                slot: .dinner,
                portions: [Portion(food: vegetable)]
            )
            let replacements = engine.rankedReplacements(for: meal.portions[0], within: meal)
            for replacement in replacements {
                XCTAssertEqual(
                    replacement.replacement.food.swapGroup, vegetable.swapGroup,
                    "\(vegetable.name) was offered \(replacement.replacement.food.name)"
                )
            }
        }
    }

    /// The specific proposal from the bug report: broccoli to carrots. Carrots
    /// cost less than a quarter as much, so they pass every gate except the
    /// culinary one, which is exactly why the bug was easy to miss.
    func testCarrotsAreNotOfferedForFreshBroccoli() throws {
        let meal = try salmonDinner()
        let portion = try broccoliPortion(in: meal)

        let replacements = engine.rankedReplacements(for: portion, within: meal)
        XCTAssertFalse(replacements.isEmpty, "The fixture no longer produces any swap at all")
        XCTAssertFalse(
            replacements.contains { $0.replacement.food.id == "carrots" },
            "Carrots are not a substitute for broccoli"
        )

        // And the meal-level plan agrees with the portion-level one.
        let applied = try XCTUnwrap(engine.bestSwap(for: meal))
        XCTAssertFalse(applied.swapped.portions.contains { $0.food.id == "carrots" })
    }

    /// Proof that the family gate, and not something else, is what removes
    /// carrots: with the gate lifted the engine proposes them again, because on
    /// macros alone the swap is legal.
    func testCarrotsAreOfferedOnlyWhenTheFamilyGateIsLifted() throws {
        var policy = SwapPolicy.default
        policy.restrictToSameSwapGroup = false
        let ungated = BudgetFoodEngine(catalog: FoodCatalog.reference, policy: policy)

        let meal = try salmonDinner()
        let portion = try broccoliPortion(in: meal)

        let replacements = ungated.rankedReplacements(for: portion, within: meal)
        XCTAssertTrue(
            replacements.contains { $0.replacement.food.id == "carrots" },
            "Carrots should pass every macro gate, which is why a culinary gate is needed"
        )
    }

    /// The fix must not simply switch vegetable swaps off. Fresh broccoli is
    /// more than twice the price of the frozen bag and carries identical macros,
    /// so this swap has to survive.
    func testFrozenBroccoliStillReplacesFreshBroccoli() throws {
        let meal = try salmonDinner()
        let portion = try broccoliPortion(in: meal)

        let replacements = engine.rankedReplacements(for: portion, within: meal)
        let frozen = try XCTUnwrap(
            replacements.first { $0.replacement.food.id == "frozen-broccoli" },
            "Fresh broccoli must still be swappable for frozen broccoli"
        )
        XCTAssertGreaterThan(frozen.savings, 0)
        XCTAssertLessThanOrEqual(
            frozen.resultingDrift.worst,
            SwapPolicy.default.macroTolerance + 0.0001
        )
    }

    /// Both are leafy greens, so spinach to cabbage is allowed, and cabbage is
    /// the cheaper of the two. Judged inside a real meal, because drift is
    /// measured meal-wide and a plate of nothing but spinach is not a meal: on
    /// its own, spinach's 2.9 g of protein sits under the 8 g trace floor, so
    /// any replacement looks like a large move.
    func testLeafyGreensTradePlacesWithinTheirFamily() throws {
        let spinach = try food("baby-spinach")
        let cabbage = try food("cabbage")
        XCTAssertEqual(spinach.swapGroup, .leafyGreen)
        XCTAssertEqual(cabbage.swapGroup, .leafyGreen)
        XCTAssertLessThan(cabbage.costPerServing, spinach.costPerServing)

        let meal = MealItem(
            name: "Dinner",
            slot: .dinner,
            portions: [
                Portion(food: try food("chicken-thighs")),
                Portion(food: try food("white-rice")),
                Portion(food: spinach),
                Portion(food: try food("olive-oil"))
            ]
        )
        let portion = try XCTUnwrap(meal.portions.first { $0.food.id == "baby-spinach" })
        let replacements = engine.rankedReplacements(for: portion, within: meal)
        XCTAssertTrue(
            replacements.contains { $0.replacement.food.id == "cabbage" },
            "Cabbage is a leaf-for-leaf substitute for spinach"
        )
    }

    /// A food nobody has classified substitutes with nothing, in either
    /// direction. This is the safe failure mode that makes `.unclassified` the
    /// right default for vegetables.
    func testUnclassifiedFoodIsNeverSubstituted() throws {
        let mystery = FoodSnapshot(
            id: "mystery-vegetable",
            name: "Mystery Vegetable",
            section: .produce,
            category: .vegetable,
            // No family given, so the initialiser defaults it.
            costTier: .strict,
            costPerServing: 0.20,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 40, protein: 2, carbs: 8, fat: 0.3),
            satietyIndex: 80
        )
        XCTAssertEqual(mystery.swapGroup, .unclassified)

        let engine = BudgetFoodEngine(catalog: FoodCatalog.reference + [mystery])
        let broccoli = try food("fresh-broccoli")
        let meal = MealItem(name: "Side", slot: .dinner, portions: [Portion(food: broccoli)])

        let replacements = engine.rankedReplacements(for: meal.portions[0], within: meal)
        XCTAssertFalse(replacements.contains { $0.replacement.food.id == mystery.id })

        let other = MealItem(name: "Mystery", slot: .dinner, portions: [Portion(food: mystery)])
        XCTAssertTrue(engine.rankedReplacements(for: other.portions[0], within: other).isEmpty)
    }

    /// The headline swap from the README is still offered after the split.
    ///
    /// Asserted on the *offer*, not on the engine's final choice: `bestSwap`
    /// ranks by money saved, drift and satiety, so a cheaper protein than tuna
    /// can legitimately win the meal. What must not change is that canned tuna
    /// remains a legal replacement for salmon, and that the meal the engine
    /// commits to still obeys both gates.
    func testProteinSwapFromTheReadmeIsStillOffered() throws {
        let meal = try salmonDinner()
        let salmon = try XCTUnwrap(meal.portions.first { $0.food.id == "salmon-fillet" })

        let replacements = engine.rankedReplacements(for: salmon, within: meal)
        let tuna = try XCTUnwrap(
            replacements.first { $0.replacement.food.id == "canned-tuna-water" },
            "Salmon should still be swappable for canned tuna"
        )
        XCTAssertEqual(tuna.original.id, salmon.id)
        XCTAssertGreaterThan(tuna.savings, 0)

        let swap = try XCTUnwrap(engine.bestSwap(for: meal))
        XCTAssertLessThan(swap.swapped.cost, meal.cost)
        XCTAssertLessThanOrEqual(
            swap.worstDrift,
            SwapPolicy.default.macroTolerance + 0.0001
        )
    }
}
