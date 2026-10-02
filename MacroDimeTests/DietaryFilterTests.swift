//
//  DietaryFilterTests.swift
//  MacroDimeTests
//
//  The filter is only as good as the catalogue's tags, so half of these tests
//  check the *data* rather than the code: a missing trait is not a missing
//  suggestion, it is an unsafe one. The rest are adversarial, taking the
//  restrictive combinations real users choose and asking whether anything
//  edible survives.
//

import XCTest
@testable import MacroDime

final class DietaryFilterTests: XCTestCase {

    private let catalog = FoodCatalog.all

    private func food(_ id: String) throws -> FoodSnapshot {
        try XCTUnwrap(FoodCatalog.food(id: id), "Missing catalogue item: \(id)")
    }

    // MARK: Catalogue tag integrity

    /// Every id named in the annotation tables must exist. A typo in a table is
    /// otherwise silent: the food simply keeps the default, plant-only reading.
    func testAnnotationTablesOnlyNameRealFoods() {
        let ids = Set(catalog.map(\.id))
        for annotated in FoodCatalog.annotatedIDs where !ids.contains(annotated) {
            XCTFail("Annotation tables name '\(annotated)', which is not in the catalogue")
        }
    }

    /// The tags themselves, spot-checked on the foods a mistake would matter
    /// for. Beans and lentils are deliberately asserted to be plant-only, since
    /// they carry the whole vegan protein case.
    func testCatalogueTagsMatchTheFoodsTheyDescribe() throws {
        let expectations: [(String, FoodTraits)] = [
            ("eggs-large", [.egg]),
            ("whole-milk", [.dairy]),
            ("whey-isolate", [.dairy]),
            ("chicken-thighs", [.meat]),
            ("ground-beef-93-7", [.meat, .redMeat]),
            ("pork-shoulder", [.meat, .redMeat, .pork]),
            ("salmon-fillet", [.fish]),
            ("shrimp", [.shellfish]),
            ("firm-tofu", [.soy]),
            ("almonds", [.nuts]),
            ("dried-pasta", [.gluten]),
            ("soy-sauce", [.soy, .gluten]),
            ("dried-lentils", []),
            ("canned-black-beans", []),
            ("white-rice", []),
            ("olive-oil", [])
        ]

        for (id, traits) in expectations {
            XCTAssertEqual(try food(id).traits, traits, "\(id) is tagged wrongly")
        }
    }

    /// No animal-derived protein may be untagged. This is the check that catches
    /// a new ingredient added without its dietary flags.
    func testEveryAnimalDerivedFoodCarriesATrait() {
        let plantAnchors = ["dried-lentils", "canned-black-beans", "canned-chickpeas"]
        for item in catalog where item.swapGroup == .proteinAnchor || item.category == .dairy {
            if item.traits.isEmpty {
                XCTAssertTrue(
                    plantAnchors.contains(item.id),
                    "\(item.name) is a protein or dairy food with no dietary traits"
                )
            }
        }
    }

    func testPreparationTimesAreSane() {
        for item in catalog {
            XCTAssertGreaterThanOrEqual(item.prepMinutes, 0, item.name)
            XCTAssertLessThanOrEqual(item.prepMinutes, 180, item.name)
        }
        XCTAssertFalse(catalog.filter { $0.prepMinutes <= PrepEffort.noCook.maximumMinutes }.isEmpty)
    }

    // MARK: Patterns

    func testVeganKeepsNoAnimalDerivedFood() {
        let profile = DietaryProfile(pattern: .vegan)
        let allowed = DietaryFilter.allowed(catalog, under: profile)

        XCTAssertFalse(allowed.isEmpty)
        for item in allowed {
            XCTAssertTrue(item.traits.intersection(.animalDerived).isEmpty, "\(item.name) is not vegan")
        }
        // The vegan protein case has to survive: four plant anchors at least.
        let anchors = allowed.filter { $0.category == .proteinAnchor }
        XCTAssertGreaterThanOrEqual(anchors.count, 4)
        XCTAssertTrue(anchors.contains { $0.id == "dried-lentils" })
        XCTAssertTrue(anchors.contains { $0.id == "firm-tofu" })
    }

    func testPescatarianKeepsFishAndDropsMeat() throws {
        let profile = DietaryProfile(pattern: .pescatarian)
        XCTAssertTrue(DietaryFilter.allows(try food("canned-tuna-water"), under: profile))
        XCTAssertTrue(DietaryFilter.allows(try food("whole-milk"), under: profile))
        XCTAssertFalse(DietaryFilter.allows(try food("chicken-thighs"), under: profile))
        XCTAssertFalse(DietaryFilter.allows(try food("sirloin-steak"), under: profile))
    }

    func testVegetarianDropsFishAndShellfishButKeepsEggs() throws {
        let profile = DietaryProfile(pattern: .vegetarian)
        XCTAssertTrue(DietaryFilter.allows(try food("eggs-large"), under: profile))
        XCTAssertTrue(DietaryFilter.allows(try food("firm-tofu"), under: profile))
        XCTAssertFalse(DietaryFilter.allows(try food("salmon-fillet"), under: profile))
        XCTAssertFalse(DietaryFilter.allows(try food("shrimp"), under: profile))
    }

    // MARK: Exclusions

    func testExclusionsRemoveExactlyTheGroupTheyName() throws {
        let dairyFree = DietaryProfile(exclusions: [.dairy])
        for id in ["whole-milk", "cottage-cheese", "cheddar-block", "skyr", "greek-yogurt-nonfat", "whey-isolate"] {
            XCTAssertFalse(DietaryFilter.allows(try food(id), under: dairyFree), id)
        }
        XCTAssertTrue(DietaryFilter.allows(try food("eggs-large"), under: dairyFree))
        XCTAssertTrue(DietaryFilter.allows(try food("chicken-breast"), under: dairyFree))

        let porkFree = DietaryProfile(exclusions: [.pork])
        XCTAssertFalse(DietaryFilter.allows(try food("pork-shoulder"), under: porkFree))
        XCTAssertTrue(
            DietaryFilter.allows(try food("sirloin-steak"), under: porkFree),
            "Avoiding pork is not avoiding beef"
        )
    }

    /// Soy sauce contains soy and gluten. Reading the exclusions from a `Set`
    /// made the reported reason depend on hash order, which changes between
    /// launches, so asserting stability within one run proves little. The
    /// reason is asserted outright instead, from sets built at different
    /// capacities, whose iteration orders differ even within a run.
    func testAFoodTrippingTwoExclusionsReportsTheSameReason() throws {
        let soySauce = try food("soy-sauce")
        for capacity in [0, 2, 16, 64, 256] {
            var exclusions = Set<FoodExclusion>(minimumCapacity: capacity)
            exclusions.insert(.soy)
            exclusions.insert(.gluten)
            XCTAssertEqual(
                DietaryFilter.rejection(for: soySauce, under: DietaryProfile(exclusions: exclusions)),
                .exclusion(.gluten),
                "capacity \(capacity)"
            )
        }
    }

    /// The combination a coeliac vegan actually needs. If this leaves no protein
    /// anchor, the app has to say so rather than plan a bad diet.
    func testStackedRestrictionsStillLeaveProteinAnchors() {
        let profile = DietaryProfile(pattern: .vegan, exclusions: [.gluten, .soy, .nuts])
        let anchors = DietaryFilter.allowed(in: .proteinAnchor, foods: catalog, under: profile)
        XCTAssertFalse(anchors.isEmpty, "Vegan, gluten-free and soy-free has no protein left")
        XCTAssertTrue(anchors.contains { $0.id == "dried-lentils" })
    }

    // MARK: Effort and blocklist

    func testNoCookProfileKeepsOnlyQuickIngredients() {
        let profile = DietaryProfile(prepEffort: .noCook)
        let allowed = DietaryFilter.allowed(catalog, under: profile)

        XCTAssertFalse(allowed.isEmpty)
        for item in allowed {
            XCTAssertLessThanOrEqual(item.prepMinutes, PrepEffort.noCook.maximumMinutes, item.name)
        }
        XCTAssertFalse(allowed.contains { $0.id == "dried-lentils" })
        XCTAssertFalse(allowed.contains { $0.id == "pork-shoulder" })
        XCTAssertTrue(allowed.contains { $0.id == "canned-tuna-water" })
    }

    /// Effort is a preference the user states, never a default. Stating "under 30
    /// minutes" has to drop the slow staples; *not* stating anything must drop
    /// nothing, which is what the unconstrained default is for.
    func testStatedEffortFiltersAndTheUnconstrainedDefaultDoesNot() {
        let under30 = DietaryFilter.allowed(catalog, under: DietaryProfile(prepEffort: .standard))

        XCTAssertFalse(under30.contains { $0.id == "dried-lentils" }, "40 minutes is over the limit")
        XCTAssertFalse(under30.contains { $0.id == "brown-rice" }, "35 minutes is over the limit")
        XCTAssertFalse(under30.contains { $0.id == "pork-shoulder" }, "90 minutes is over the limit")
        XCTAssertTrue(under30.contains { $0.id == "chicken-thighs" }, "25 minutes is inside the limit")

        let unconstrained = DietaryFilter.allowed(catalog, under: .unrestricted)
        XCTAssertEqual(unconstrained.count, catalog.count)
    }

    func testBlockedFoodIsReportedAsTheUsersOwnChoice() throws {
        let eggs = try food("eggs-large")
        let profile = DietaryProfile(pattern: .vegan, blockedFoodIDs: ["eggs-large"])

        XCTAssertEqual(DietaryFilter.rejection(for: eggs, under: profile), .blockedByUser)
        XCTAssertFalse(DietaryFilter.allows(eggs, under: profile))

        let allowed = DietaryFilter.allowed(catalog, under: profile)
        XCTAssertFalse(allowed.contains { $0.id == "eggs-large" })
    }

    // MARK: Bookkeeping

    func testRejectionBreakdownAccountsForEveryRejectedFood() {
        let profile = DietaryProfile(pattern: .vegan, exclusions: [.gluten], prepEffort: .standard)
        let rejected = DietaryFilter.rejected(catalog, under: profile)
        let breakdown = DietaryFilter.rejectionBreakdown(catalog, under: profile)

        XCTAssertEqual(breakdown.reduce(0) { $0 + $1.count }, rejected.count)
        XCTAssertFalse(breakdown.isEmpty)
        for entry in breakdown {
            XCTAssertFalse(entry.label.isEmpty)
            XCTAssertGreaterThan(entry.count, 0)
        }
    }

    func testEveryRejectionExplainsItself() {
        let profile = DietaryProfile(
            pattern: .vegan,
            exclusions: [.gluten, .nuts, .soy],
            blockedFoodIDs: ["white-rice"],
            prepEffort: .quick
        )
        for item in catalog {
            guard let rejection = DietaryFilter.rejection(for: item, under: profile) else { continue }
            XCTAssertFalse(rejection.reason.isEmpty, item.name)
            XCTAssertFalse(rejection.label.isEmpty, item.name)
        }
    }

    func testUnrestrictedProfileAllowsTheWholeCatalogue() {
        let allowed = DietaryFilter.allowed(catalog, under: .unrestricted)
        XCTAssertEqual(allowed.count, catalog.count)
        XCTAssertFalse(DietaryProfile.unrestricted.isRestricted)
    }
}
