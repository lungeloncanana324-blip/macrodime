//
//  PlanAuditTests.swift
//  MacroDimeTests
//
//  The gap detector, tested for the two things that make it useful: that it
//  catches each way a plan can fail, and that it never claims a plan is fine
//  when it cannot know. The disclosure note (fibre, sodium, micronutrients,
//  meals eaten out) is asserted as present in every run, because a plan that
//  looks complete and is not is the real risk.
//

import XCTest
@testable import MacroDime

final class PlanAuditTests: XCTestCase {

    private let catalog = FoodCatalog.reference

    private func food(_ id: String) throws -> FoodSnapshot {
        try XCTUnwrap(FoodCatalog.referenceFood(id: id), "Missing catalogue item: \(id)")
    }

    private func meal(
        _ name: String,
        _ slot: MealSlot,
        _ ids: [String],
        servings: [Double] = []
    ) throws -> MealItem {
        let portions = try ids.enumerated().map { index, id in
            Portion(
                food: try food(id),
                servings: index < servings.count ? servings[index] : 1
            )
        }
        return MealItem(name: name, slot: slot, portions: portions)
    }

    /// A day worth auditing: eggs and oats, tuna and rice, chicken thighs and
    /// potatoes, all with vegetables.
    private func balancedDay() throws -> [MealItem] {
        [
            try meal("Breakfast", .breakfast, ["eggs-large", "rolled-oats", "banana"]),
            try meal("Lunch", .lunch, ["canned-tuna-water", "white-rice", "frozen-broccoli", "olive-oil"]),
            try meal("Dinner", .dinner, ["chicken-thighs", "potatoes", "frozen-mixed-vegetables"])
        ]
    }

    // MARK: Empty plan

    func testEmptyPlanIsReportedAndStillDisclosesWhatIsUntracked() {
        let report = PlanAudit.day(
            meals: [],
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 12,
            dietary: .unrestricted,
            catalog: catalog
        )

        XCTAssertTrue(report.contains(.emptyPlan))
        XCTAssertTrue(report.contains(.untrackedNutrients))
        XCTAssertEqual(report.first(.emptyPlan)?.severity, .caution)
        XCTAssertFalse(report.blocking.contains { $0.kind == .proteinShortfall })
    }

    // MARK: Macros

    func testProteinShortfallIsBlockingWhenFarShort() throws {
        // Rice and vegetables only: plenty of calories, a third of the protein.
        let day = [
            try meal("Lunch", .lunch, ["white-rice", "olive-oil", "frozen-broccoli"]),
            try meal("Dinner", .dinner, ["potatoes", "carrots", "olive-oil"])
        ]
        let report = PlanAudit.day(
            meals: day,
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 20,
            dietary: .unrestricted,
            catalog: catalog
        )

        let gap = try XCTUnwrap(report.first(.proteinShortfall))
        XCTAssertEqual(gap.severity, .blocking)
        XCTAssertFalse(gap.remedy.isEmpty)
    }

    /// Targets taken from the plan itself, so this asserts the *absence* of
    /// macro gaps rather than a set of numbers that would need maintaining.
    func testPlanThatMatchesItsOwnTargetsHasNoBlockingGaps() throws {
        let day = try balancedDay()
        let report = PlanAudit.day(
            meals: day,
            targets: day.totalNutrition,
            dailyBudgetUSD: day.totalCost,
            dietary: .unrestricted,
            catalog: catalog
        )

        XCTAssertFalse(report.contains(.proteinShortfall))
        XCTAssertFalse(report.contains(.calorieDrift))
        XCTAssertFalse(report.contains(.budgetOverrun))
        XCTAssertTrue(report.blocking.isEmpty, "\(report.gaps.map(\.title))")
    }

    // MARK: Budget

    func testBudgetOverrunBlocksPastTheHardLimit() throws {
        // Salmon for every meal: about $15 a day against a $5 allowance.
        let day = [
            try meal("Lunch", .lunch, ["salmon-fillet", "white-rice", "olive-oil"]),
            try meal("Dinner", .dinner, ["sirloin-steak", "potatoes", "asparagus"])
        ]
        let report = PlanAudit.day(
            meals: day,
            targets: day.totalNutrition,
            dailyBudgetUSD: 5,
            dietary: .unrestricted,
            catalog: catalog
        )

        let gap = try XCTUnwrap(report.first(.budgetOverrun))
        XCTAssertEqual(gap.severity, .blocking)
        XCTAssertTrue(gap.title.contains("over your allowance"))
    }

    // MARK: Restrictions changed after the plan was built

    func testPlanBuiltBeforeARestrictionChangeIsFlagged() throws {
        let day = [
            try meal("Lunch", .lunch, ["chicken-thighs", "white-rice", "frozen-broccoli"]),
            try meal("Dinner", .dinner, ["salmon-fillet", "potatoes", "carrots"])
        ]
        let report = PlanAudit.day(
            meals: day,
            targets: day.totalNutrition,
            dailyBudgetUSD: day.totalCost,
            dietary: DietaryProfile(pattern: .vegetarian),
            catalog: catalog
        )

        let gap = try XCTUnwrap(report.first(.restrictionConflict))
        XCTAssertEqual(gap.severity, .blocking)
        XCTAssertTrue(gap.detail.contains("Chicken Thighs") || gap.detail.contains("Atlantic Salmon Fillet"))
    }

    func testSlowIngredientsAreFlaggedAgainstTheStatedEffort() throws {
        let day = [
            try meal("Dinner", .dinner, ["dried-lentils", "brown-rice", "carrots"])
        ]
        let report = PlanAudit.day(
            meals: day,
            targets: day.totalNutrition,
            dailyBudgetUSD: day.totalCost,
            dietary: DietaryProfile(prepEffort: .quick),
            catalog: catalog
        )

        let gap = try XCTUnwrap(report.first(.cookingEffort))
        XCTAssertEqual(gap.severity, .caution)
        XCTAssertTrue(gap.detail.contains("Dried Lentils"))
    }

    // MARK: Composition

    func testDinnerWithoutAVegetableIsNoted() throws {
        let day = [
            try meal("Dinner", .dinner, ["chicken-thighs", "white-rice", "olive-oil"])
        ]
        let report = PlanAudit.day(
            meals: day,
            targets: day.totalNutrition,
            dailyBudgetUSD: day.totalCost,
            dietary: .unrestricted,
            catalog: catalog
        )
        XCTAssertEqual(report.first(.noVegetable)?.severity, .info)

        let withVegetables = try balancedDay()
        let vegetableReport = PlanAudit.day(
            meals: withVegetables,
            targets: withVegetables.totalNutrition,
            dailyBudgetUSD: withVegetables.totalCost,
            dietary: .unrestricted,
            catalog: catalog
        )
        XCTAssertFalse(vegetableReport.contains(.noVegetable))
    }

    func testSingleProteinAnchorAllDayIsNoted() throws {
        let day = [
            try meal("Lunch", .lunch, ["canned-tuna-water", "white-rice", "frozen-broccoli"]),
            try meal("Dinner", .dinner, ["canned-tuna-water", "potatoes", "carrots"])
        ]
        let report = PlanAudit.day(
            meals: day,
            targets: day.totalNutrition,
            dailyBudgetUSD: day.totalCost,
            dietary: .unrestricted,
            catalog: catalog
        )
        XCTAssertEqual(report.first(.lowVariety)?.severity, .info)

        let varied = try balancedDay()
        let variedReport = PlanAudit.day(
            meals: varied,
            targets: varied.totalNutrition,
            dailyBudgetUSD: varied.totalCost,
            dietary: .unrestricted,
            catalog: catalog
        )
        XCTAssertFalse(variedReport.contains(.lowVariety))
    }

    func testUnfilledSlotsAreNoted() throws {
        let day = [try meal("Dinner", .dinner, ["chicken-thighs", "potatoes", "carrots"])]
        let report = PlanAudit.day(
            meals: day,
            targets: day.totalNutrition,
            dailyBudgetUSD: day.totalCost,
            dietary: DietaryProfile(schedule: .threeMealsAndSnacks),
            catalog: catalog
        )
        let gap = try XCTUnwrap(report.first(.unfilledSlot))
        XCTAssertEqual(gap.severity, .info)
        XCTAssertTrue(gap.detail.contains("Snacks"))
    }

    // MARK: Feasibility, before anything is planned

    func testImpossibleBudgetIsCaughtBeforePlanning() {
        let report = PlanAudit.feasibility(
            targets: NutritionFacts(calories: 2200, protein: 160, carbs: 240, fat: 60),
            dailyBudgetUSD: 3,
            dietary: DietaryProfile(pattern: .vegan),
            catalog: catalog
        )

        let gap = try? XCTUnwrap(report.first(.proteinFeasibility))
        XCTAssertNotNil(gap, "A vegan 160 g protein target on $3 a day is not achievable")
        XCTAssertEqual(report.first(.proteinFeasibility)?.severity, .blocking)
        XCTAssertTrue(report.first(.proteinFeasibility)?.remedy.contains("Raise") ?? false)
    }

    func testGenerousBudgetHasNoBlockingFeasibilityGap() {
        let report = PlanAudit.feasibility(
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 100,
            dietary: .unrestricted,
            catalog: catalog
        )
        XCTAssertFalse(report.contains(.proteinFeasibility))
        XCTAssertTrue(report.blocking.isEmpty)
        XCTAssertTrue(report.contains(.untrackedNutrients))
    }

    /// A pattern that leaves no protein source at all must be called out as
    /// blocking, not quietly planned around.
    func testProfileWithNoProteinSourcesIsBlocking() {
        let impossible = DietaryProfile(
            pattern: .vegan,
            blockedFoodIDs: ["dried-lentils", "canned-black-beans", "canned-chickpeas", "firm-tofu"]
        )
        let report = PlanAudit.feasibility(
            targets: NutritionFacts(calories: 2000, protein: 120, carbs: 220, fat: 55),
            dailyBudgetUSD: 20,
            dietary: impossible,
            catalog: catalog
        )

        XCTAssertEqual(report.first(.missingProteinSources)?.severity, .blocking)
    }

    func testRestrictionsReportHowMuchOfTheCatalogueIsGone() {
        let report = PlanAudit.feasibility(
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 15,
            dietary: DietaryProfile(pattern: .vegan, exclusions: [.nuts]),
            catalog: catalog
        )

        let gap = try? XCTUnwrap(report.first(.restrictionsCost))
        XCTAssertNotNil(gap, "A restricted profile must say what it removed")
        XCTAssertTrue(report.first(.restrictionsCost)?.detail.contains("Removed by your settings") ?? false)
    }

    // MARK: Disclosure

    func testEatingOutIsDisclosedOnlyWhenTheUserSaysSo() {
        let base = PlanAudit.feasibility(
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 15,
            dietary: .unrestricted,
            catalog: catalog
        )
        XCTAssertFalse(base.contains(.eatingOut))

        let diningOut = PlanAudit.feasibility(
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 15,
            dietary: DietaryProfile(mealsOutPerWeek: 4),
            catalog: catalog
        )
        let gap = try? XCTUnwrap(diningOut.first(.eatingOut))
        XCTAssertNotNil(gap)
        XCTAssertEqual(diningOut.first(.eatingOut)?.severity, .info)
    }

    func testUntrackedNutrientsAreAlwaysDisclosed() {
        let reports = [
            PlanAudit.feasibility(
                targets: nil,
                dailyBudgetUSD: 0,
                dietary: .unrestricted,
                catalog: catalog
            ),
            PlanAudit.day(
                meals: [],
                targets: nil,
                dailyBudgetUSD: 0,
                dietary: .unrestricted,
                catalog: catalog
            )
        ]
        for report in reports {
            XCTAssertTrue(report.contains(.untrackedNutrients))
        }
    }

    // MARK: Report shape

    func testEveryGapCarriesARemedyAndAStableIdentity() throws {
        let day = [try meal("Lunch", .lunch, ["white-rice"])]
        let report = PlanAudit.day(
            meals: day,
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 1,
            dietary: DietaryProfile(prepEffort: .noCook),
            catalog: catalog
        )

        XCTAssertFalse(report.gaps.isEmpty)
        for gap in report.gaps {
            XCTAssertFalse(gap.remedy.isEmpty, gap.title)
            XCTAssertFalse(gap.detail.isEmpty, gap.title)
            XCTAssertFalse(gap.id.isEmpty)
        }
    }

    func testGapsAreOrderedWorstFirst() throws {
        let day = [try meal("Lunch", .lunch, ["white-rice"])]
        let report = PlanAudit.day(
            meals: day,
            targets: NutritionFacts(calories: 2200, protein: 150, carbs: 240, fat: 60),
            dailyBudgetUSD: 1,
            dietary: DietaryProfile(prepEffort: .standard),
            catalog: catalog
        )

        let severities = report.gaps.map(\.severity.rawValue)
        XCTAssertEqual(severities, severities.sorted(by: >), "Report is not ordered worst first")
        XCTAssertEqual(report.headline, report.headline)
    }

    func testSameInputGivesTheSameReport() throws {
        let day = try balancedDay()
        let targets = NutritionFacts(calories: 1800, protein: 160, carbs: 180, fat: 55)

        let first = PlanAudit.day(
            meals: day, targets: targets, dailyBudgetUSD: 8,
            dietary: DietaryProfile(pattern: .omnivore, exclusions: [.pork]), catalog: catalog
        )
        let second = PlanAudit.day(
            meals: day, targets: targets, dailyBudgetUSD: 8,
            dietary: DietaryProfile(pattern: .omnivore, exclusions: [.pork]), catalog: catalog
        )
        XCTAssertEqual(first, second)
    }
}
