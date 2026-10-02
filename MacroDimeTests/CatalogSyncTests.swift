//
//  CatalogSyncTests.swift
//  MacroDimeTests
//
//  The seeder used to rerun only when a hand-bumped version number rose, and the
//  monthly price job never bumped it, so a refreshed price would have reached
//  new installs only. These tests pin the replacement: the store is compared
//  with the catalogue by value, every launch.
//

import XCTest
@testable import MacroDime

final class CatalogSyncTests: XCTestCase {

    private let catalogue = FoodCatalog.all

    /// A copy of `food` with one value changed, as an older install would hold it.
    private func stored(_ food: FoodSnapshot, price: Double? = nil, swapGroup: SwapGroup? = nil) -> FoodSnapshot {
        FoodSnapshot(
            id: food.id,
            name: food.name,
            section: food.section,
            category: food.category,
            swapGroup: swapGroup ?? food.swapGroup,
            costTier: food.costTier,
            costPerServing: price ?? food.costPerServing,
            servingDescription: food.servingDescription,
            servingGrams: food.servingGrams,
            nutrition: food.nutrition,
            satietyIndex: food.satietyIndex,
            isSwapCandidate: food.isSwapCandidate,
            traits: food.traits,
            prepMinutes: food.prepMinutes
        )
    }

    func testAnEmptyStoreReceivesTheWholeCatalogue() {
        let changes = CatalogSync.changes(stored: [], catalogue: catalogue)
        XCTAssertEqual(changes.inserts, catalogue)
        XCTAssertTrue(changes.updates.isEmpty)
    }

    /// The common launch: nothing changed, so nothing is written.
    func testAnUpToDateStoreWritesNothing() {
        XCTAssertTrue(CatalogSync.changes(stored: catalogue, catalogue: catalogue).isEmpty)
    }

    /// The bug itself. A store seeded before a price refresh must receive the
    /// new price without anyone bumping a number.
    func testARefreshedPriceReachesAnExistingInstall() throws {
        let tuna = try XCTUnwrap(FoodCatalog.food(id: "canned-tuna-water"))
        var store = catalogue
        let index = try XCTUnwrap(store.firstIndex { $0.id == tuna.id })
        store[index] = stored(tuna, price: tuna.costPerServing + 0.25)

        let changes = CatalogSync.changes(stored: store, catalogue: catalogue)
        XCTAssertTrue(changes.inserts.isEmpty)
        XCTAssertEqual(changes.updates, [tuna], "only the changed food is rewritten, with the catalogue's price")
    }

    /// The real case, end to end: an install seeded with the frozen hand prices
    /// (`reference`), launched by a build carrying official averages (`all`).
    /// Every food whose price moved is rewritten, and no other.
    func testMovingFromHandPricesToSourcedPricesRewritesExactlyTheRepricedFoods() {
        var repriced = Set<String>()
        for (hand, sourced) in zip(FoodCatalog.reference, FoodCatalog.all)
        where hand.costPerServing != sourced.costPerServing {
            repriced.insert(sourced.id)
        }
        XCTAssertFalse(repriced.isEmpty, "fixture: sourced prices should differ from the hand prices somewhere")

        let changes = CatalogSync.changes(stored: FoodCatalog.reference, catalogue: FoodCatalog.all)
        XCTAssertTrue(changes.inserts.isEmpty)
        XCTAssertEqual(Set(changes.updates.map(\.id)), repriced)
        for food in changes.updates {
            XCTAssertEqual(food, FoodCatalog.food(id: food.id), "\(food.id) must be written with the current value")
        }
    }

    /// A store written before vegetables had culinary families reads them as
    /// `.unclassified`. That is a value difference, so they are corrected
    /// rather than left without swaps forever.
    func testAVegetableStoredWithoutItsFamilyIsCorrected() throws {
        let broccoli = try XCTUnwrap(catalogue.first { $0.swapGroup == .cruciferous })
        let store = catalogue.map { $0.id == broccoli.id ? stored($0, swapGroup: .unclassified) : $0 }

        XCTAssertEqual(CatalogSync.changes(stored: store, catalogue: catalogue).updates, [broccoli])
    }

    func testAFoodNewToTheCatalogueIsInsertedNotUpdated() throws {
        let newest = try XCTUnwrap(catalogue.last)
        let changes = CatalogSync.changes(stored: Array(catalogue.dropLast()), catalogue: catalogue)
        XCTAssertEqual(changes.inserts, [newest])
        XCTAssertTrue(changes.updates.isEmpty)
    }

    /// A curated food dropped from the catalogue stays in the store: a past
    /// meal may point at it, and deleting it would erase that history.
    func testARetiredFoodIsLeftAlone() throws {
        let template = try XCTUnwrap(catalogue.first)
        let ghost = FoodSnapshot(
            id: "retired-food",
            name: template.name,
            section: template.section,
            category: template.category,
            costTier: template.costTier,
            costPerServing: 1,
            servingDescription: template.servingDescription,
            servingGrams: template.servingGrams,
            nutrition: template.nutrition,
            satietyIndex: template.satietyIndex
        )
        XCTAssertTrue(CatalogSync.changes(stored: catalogue + [ghost], catalogue: catalogue).isEmpty)
    }

    /// The seeder looks rows up by id keeping the first, so the comparison
    /// must judge that same row. A stale duplicate behind a current row must
    /// not cause a rewrite on every launch.
    func testWithDuplicateRowsTheFirstIsTheOneCompared() throws {
        let first = try XCTUnwrap(catalogue.first)
        let staleDuplicate = stored(first, price: first.costPerServing + 5)

        XCTAssertTrue(CatalogSync.changes(stored: catalogue + [staleDuplicate], catalogue: catalogue).isEmpty)
        XCTAssertEqual(
            CatalogSync.changes(stored: [staleDuplicate] + catalogue, catalogue: catalogue).updates,
            [first]
        )
    }

    /// Running the result through twice must converge: once the changes are
    /// applied, the next launch finds nothing to do.
    func testApplyingTheChangesConverges() {
        var store = Array(FoodCatalog.reference.dropLast(3))
        let changes = CatalogSync.changes(stored: store, catalogue: catalogue)
        for update in changes.updates {
            if let index = store.firstIndex(where: { $0.id == update.id }) { store[index] = update }
        }
        store += changes.inserts

        XCTAssertTrue(CatalogSync.changes(stored: store, catalogue: catalogue).isEmpty)
    }
}
