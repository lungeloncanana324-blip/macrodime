//
//  PriceTableTests.swift
//  MacroDimeTests
//
//  The sourced prices, checked by formula rather than by value.
//
//  `SourcedPrices.swift` is regenerated from public data every month, so these
//  tests never assert a dollar figure. They assert that the table fits the
//  catalogue, that the unit arithmetic is right (a per-dozen price is not
//  mistaken for a per-egg one), and that a sourced price lands in a plausible
//  band around the hand-set estimate, which is what catches a unit mix-up.
//

import XCTest
@testable import MacroDime

final class PriceTableTests: XCTestCase {

    private func reference(_ id: String) throws -> FoodSnapshot {
        try XCTUnwrap(FoodCatalog.referenceFood(id: id), "no catalogue food \(id)")
    }

    private func entry(_ id: String) throws -> SourcedPrice {
        try XCTUnwrap(PriceTable.entry(for: id), "no sourced price for \(id)")
    }

    // MARK: The table fits the catalogue

    func testEveryEntryIsACatalogueFoodAndListedOnce() {
        let ids = PriceTable.entries.map(\.foodID)
        XCTAssertEqual(Set(ids).count, ids.count, "a food is priced twice")
        for id in ids {
            XCTAssertNotNil(FoodCatalog.referenceFood(id: id), "\(id) is priced but not in the catalogue")
        }
    }

    func testEveryEntryProducesACostForItsFood() throws {
        for entry in PriceTable.entries {
            let food = try reference(entry.foodID)
            let cost = try XCTUnwrap(entry.costPerServing(of: food), "\(entry.foodID): no cost in \(entry.unit)")
            XCTAssertGreaterThan(cost, 0, entry.foodID)
            XCTAssertGreaterThan(entry.yield, 0, entry.foodID)
            XCTAssertLessThanOrEqual(entry.yield, 1, "\(entry.foodID): a yield above 1 means buying less than is eaten")
            XCTAssertFalse(entry.period.isEmpty, entry.foodID)
        }
    }

    /// A unit mistake is off by a factor of 12 (dozen), 3.8 (gallon to litre)
    /// or 2.2 (pound to kilogram). Real market gaps from the hand-set
    /// estimates are well inside a factor of 3.
    func testSourcedPricesStayWithinAFactorOfThreeOfTheEstimates() throws {
        for entry in PriceTable.entries {
            let food = try reference(entry.foodID)
            let cost = try XCTUnwrap(entry.costPerServing(of: food))
            let ratio = cost / food.costPerServing
            XCTAssertTrue(
                (1.0 / 3.0 ... 3.0).contains(ratio),
                "\(entry.foodID): sourced \(cost) against estimate \(food.costPerServing)"
            )
        }
    }

    // MARK: Unit arithmetic

    func testPerDozenPriceIsSplitByTheEggsInAServing() throws {
        let eggs = try reference("eggs-large")
        let price = SourcedPrice(
            foodID: eggs.id, source: .bls(series: "test", item: "test"),
            period: "test", dollarsPerUnit: 3.00, unit: .dozen, yield: 1
        )
        // "2 large eggs" at $3.00 a dozen.
        XCTAssertEqual(try XCTUnwrap(price.costPerServing(of: eggs)), 0.50, accuracy: 0.0001)
    }

    func testPerGallonPriceUsesTheServingVolume() throws {
        let milk = try reference("whole-milk")
        let price = SourcedPrice(
            foodID: milk.id, source: .bls(series: "test", item: "test"),
            period: "test", dollarsPerUnit: 4.00, unit: .gallon, yield: 1
        )
        // "240 ml" of a 3,785.41 ml gallon.
        XCTAssertEqual(try XCTUnwrap(price.costPerServing(of: milk)), 4.00 * 240 / 3785.411784, accuracy: 0.0001)
    }

    func testYieldChargesForWhatIsBoughtNotWhatIsEaten() throws {
        let beans = try reference("canned-black-beans")
        let price = SourcedPrice(
            foodID: beans.id, source: .ers(product: "test", form: "test", year: 2023),
            period: "test", dollarsPerUnit: 1.00, unit: .pound, yield: 0.65
        )
        // 130 g drained is 200 g of can contents at a 0.65 drained yield.
        XCTAssertEqual(try XCTUnwrap(price.costPerServing(of: beans)), 200 / PriceTable.gramsPerPound, accuracy: 0.0001)
    }

    func testAPriceInTheWrongUnitIsRefusedRatherThanGuessed() throws {
        let rice = try reference("white-rice")
        let perDozen = SourcedPrice(
            foodID: rice.id, source: .bls(series: "test", item: "test"),
            period: "test", dollarsPerUnit: 3.00, unit: .dozen, yield: 1
        )
        // "75 g dry" is not counted, so there is no honest per-dozen cost.
        XCTAssertNil(perDozen.costPerServing(of: rice))
    }

    // MARK: The two catalogues

    func testTheAppCatalogueDiffersFromTheReferenceOnlyInSourcedPrices() throws {
        XCTAssertEqual(FoodCatalog.all.map(\.id), FoodCatalog.reference.map(\.id))
        for (live, frozen) in zip(FoodCatalog.all, FoodCatalog.reference) {
            XCTAssertEqual(live.withCost(frozen.costPerServing), frozen, "\(live.id) changed beyond its price")
            if let entry = PriceTable.entry(for: live.id) {
                XCTAssertEqual(live.costPerServing, try XCTUnwrap(entry.costPerServing(of: frozen)), accuracy: 0.000001)
            } else {
                XCTAssertEqual(live.costPerServing, frozen.costPerServing, "\(live.id) has no source but moved")
            }
        }
    }

    func testAUserCreatedFoodKeepsItsOwnPrice() throws {
        let mine = try reference("white-rice").withCost(9.99)
        let custom = FoodSnapshot(
            id: "user-rice", name: "My Rice", section: mine.section, category: mine.category,
            costTier: mine.costTier, costPerServing: 9.99, servingDescription: "1 bowl",
            servingGrams: 200, nutrition: mine.nutrition, satietyIndex: mine.satietyIndex
        )
        XCTAssertEqual(PriceTable.priced(custom).costPerServing, 9.99)
    }

    // MARK: The engine at live prices

    /// Market prices may change which swap wins, but never the contract: a
    /// swap is cheaper and stays inside the macro tolerance.
    func testTheSwapContractHoldsAtLivePrices() throws {
        let engine = BudgetFoodEngine(catalog: FoodCatalog.all)
        let meal = MealItem(
            name: "Salmon Dinner",
            slot: .dinner,
            portions: try ["salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil"].map {
                Portion(food: try XCTUnwrap(FoodCatalog.food(id: $0)))
            }
        )
        guard let swap = engine.bestSwap(for: meal) else { return }
        XCTAssertLessThan(swap.swapped.cost, meal.cost)
        XCTAssertLessThanOrEqual(swap.worstDrift, SwapPolicy.default.macroTolerance)
    }

    // MARK: What Settings says

    func testSummaryCountsAddUpAndNameTheSources() {
        let summary = PriceTable.summary
        XCTAssertEqual(summary.sourcedCount, PriceTable.entries.count)
        XCTAssertEqual(summary.totalCount, FoodCatalog.reference.count)
        XCTAssertEqual(summary.sourcedCount + summary.estimatedCount, summary.totalCount)
        XCTAssertTrue(summary.explanation.contains("Bureau of Labor Statistics"))
        XCTAssertTrue(summary.explanation.contains("USDA"))
        XCTAssertTrue(summary.explanation.contains("never goes online"))
        XCTAssertTrue(summary.explanation.contains(summary.period))
    }
}
