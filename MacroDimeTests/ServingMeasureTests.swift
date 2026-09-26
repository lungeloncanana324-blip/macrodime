//
//  ServingMeasureTests.swift
//  MacroDimeTests
//
//  The quantity text the first screenshots exposed, pinned down.
//
//  The plan showed "1.5 × 2 large eggs" and the grocery list "1 × 1 can (142 g
//  drained)": a serving count multiplied into free text. These tests hold the
//  words a person would write instead, and require every catalogue serving to
//  be readable, so a new food with an unparseable serving fails here.
//

import XCTest
@testable import MacroDime

final class ServingMeasureTests: XCTestCase {

    private func food(_ id: String) throws -> FoodSnapshot {
        try XCTUnwrap(FoodCatalog.food(id: id), "no catalogue food \(id)")
    }

    // MARK: The catalogue

    func testEveryCatalogueServingParsesAndRendersBackUnchanged() {
        for food in FoodCatalog.all {
            guard let measure = ServingMeasure(parsing: food.servingDescription) else {
                XCTFail("\(food.id): cannot read the serving \"\(food.servingDescription)\"")
                continue
            }
            XCTAssertEqual(measure.text(times: 1), food.servingDescription, food.id)
        }
    }

    // MARK: Counted servings

    func testEggsMultiplyIntoACountOfEggs() {
        XCTAssertEqual(ServingMeasure.describe("2 large eggs", times: 1.5), "3 large eggs")
        XCTAssertEqual(ServingMeasure.describe("2 large eggs", times: 0.5), "1 large egg")
        XCTAssertEqual(ServingMeasure.describe("2 large eggs", times: 1.25), "2½ large eggs")
    }

    func testCansPluraliseAndCarryTheirWeight() {
        XCTAssertEqual(ServingMeasure.describe("1 can (142 g drained)", times: 2), "2 cans (284 g drained)")
        XCTAssertEqual(ServingMeasure.describe("1 can (142 g drained)", times: 1.25), "1¼ cans (178 g drained)")
        XCTAssertEqual(ServingMeasure.describe("1 can (142 g drained)", times: 0.5), "½ can (71 g drained)")
    }

    func testUnitsThatReadTheSameInBothNumbersStayPut() {
        XCTAssertEqual(ServingMeasure.describe("1 tbsp (14 g)", times: 2), "2 tbsp (28 g)")
        XCTAssertEqual(ServingMeasure.describe("1 medium (118 g)", times: 2), "2 medium (236 g)")
        XCTAssertEqual(ServingMeasure.describe("½ medium (100 g)", times: 2), "1 medium (200 g)")
        XCTAssertEqual(ServingMeasure.describe("2 slices", times: 0.5), "1 slice")
    }

    // MARK: Weighed servings

    func testWeighedServingsScaleTheWeightAndKeepTheState() {
        XCTAssertEqual(ServingMeasure.describe("300 g raw", times: 1), "300 g raw")
        XCTAssertEqual(ServingMeasure.describe("113 g raw", times: 1.25), "141 g raw")
        XCTAssertEqual(ServingMeasure.describe("300 g raw", times: 4), "1.2 kg raw")
        XCTAssertEqual(ServingMeasure.describe("240 ml", times: 5), "1.2 L")
    }

    // MARK: The two screens

    func testPlanAndGroceryListUseTheSameWords() throws {
        let eggs = try food("eggs-large")
        XCTAssertEqual(Portion(food: eggs, servings: 1.5).quantityDescription, "3 large eggs")

        // The grocery list used to print "1 × 300 g raw" even at one serving.
        let potatoes = try food("potatoes")
        let line = GroceryLine(food: potatoes, totalServings: 1, usedInMeals: [])
        XCTAssertEqual(line.quantityDescription, "300 g raw")

        let tuna = try food("canned-tuna-water")
        let week = GroceryLine(food: tuna, totalServings: 6, usedInMeals: [])
        XCTAssertEqual(week.quantityDescription, "6 cans (852 g drained)")
    }

    // MARK: Text the parser cannot read

    func testUserTextFallsBackToNamingTheServings() {
        // A user-created food can say anything. Rather than multiply words,
        // name the servings and quote the text.
        XCTAssertEqual(ServingMeasure.describe("a handful", times: 1), "a handful")
        XCTAssertEqual(ServingMeasure.describe("a handful", times: 2), "2 servings (a handful)")
        XCTAssertEqual(ServingMeasure.describe("a handful", times: 0.5), "½ serving (a handful)")
    }

    func testPhrasesWithOfInflectTheNounBeforeIt() {
        XCTAssertEqual(ServingMeasure.describe("1 bowl of soup", times: 2), "2 bowls of soup")
        XCTAssertEqual(ServingMeasure.describe("2 cups of rice", times: 0.5), "1 cup of rice")
    }

    // MARK: Numbers

    func testCountsReadAsKitchenFractions() {
        XCTAssertEqual(ServingMeasure.count(1), "1")
        XCTAssertEqual(ServingMeasure.count(0.25), "¼")
        XCTAssertEqual(ServingMeasure.count(2.5), "2½")
        XCTAssertEqual(ServingMeasure.count(1.0 / 3.0), "0.33")
    }

    func testCaloriesAndGramsAreGroupedLikeTheRestOfTheApp() {
        // Settings printed "1783 kcal" while Today printed "1,681".
        let us = Locale(identifier: "en_US")
        XCTAssertEqual(DisplayFormat.calories(1842.4, locale: us), "1,842 kcal")
        XCTAssertEqual(DisplayFormat.calories(530, locale: us), "530 kcal")
        XCTAssertEqual(DisplayFormat.grams(148.6, locale: us), "149 g")
    }
}
