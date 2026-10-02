//
//  MoneyTests.swift
//  MacroDimeTests
//
//  The currency bug, pinned down.
//
//  The catalogue is priced in USD. Formatting those amounts with the device
//  locale's currency put a local symbol on a dollar figure: a South African user
//  read "R9.00" for a day of food, and nobody testing in the US would ever see
//  it. The fix is that a currency must be named explicitly, and that a different
//  currency is a *conversion* with a rate the user supplied, never a relabelling.
//

import XCTest
@testable import MacroDime

final class MoneyTests: XCTestCase {

    // MARK: The catalogue's currency

    func testCatalogueAmountsAreTaggedAsUSD() {
        XCTAssertEqual(PriceBook.currencyCode, "USD")
        XCTAssertEqual(Money.catalogue(9).currencyCode, "USD")
        XCTAssertEqual(Money.catalogue(9).amount, 9)
        XCTAssertTrue(Money.catalogue(0).isZero)
    }

    func testFoodPricesCarryTheirCurrency() throws {
        let tuna = try XCTUnwrap(FoodCatalog.food(id: "canned-tuna-water"))
        XCTAssertEqual(tuna.price.currencyCode, PriceBook.currencyCode)
        XCTAssertEqual(tuna.price.amount, tuna.costPerServing, accuracy: 0.0001)
    }

    /// The default display settings claim no conversion, and the default
    /// formatter uses the catalogue's currency rather than the locale's.
    func testDefaultFormattingNeverInventsACurrency() {
        XCTAssertEqual(CurrencySettings.usd.displayCode, PriceBook.currencyCode)
        XCTAssertFalse(CurrencySettings.usd.isConverting)
        XCTAssertEqual(
            CurrencySettings.usd.format(9),
            DisplayFormat.currency(9, code: PriceBook.currencyCode)
        )
        XCTAssertTrue(DisplayFormat.currency(9).contains("9"))
    }

    // MARK: Conversion

    func testConversionUsesTheSuppliedRate() {
        let rand = CurrencySettings(displayCode: "ZAR", unitsPerUSD: 18.5)

        XCTAssertTrue(rand.isConverting)
        XCTAssertEqual(rand.convert(10), 185, accuracy: 0.0001)
        XCTAssertEqual(
            rand.format(10),
            DisplayFormat.currency(185, code: "ZAR")
        )
        XCTAssertNotEqual(rand.format(10), CurrencySettings.usd.format(10))
    }

    func testStorageConversionIsTheInverseOfDisplay() {
        let rand = CurrencySettings(displayCode: "ZAR", unitsPerUSD: 18.5)
        XCTAssertEqual(rand.toStorage(rand.convert(12)), 12, accuracy: 0.000001)
        XCTAssertEqual(rand.toStorage(185), 10, accuracy: 0.000001)
    }

    /// A user who picks their own currency but leaves the rate at 1 must not be
    /// told they are looking at converted money.
    func testSameCurrencyOrUnitRateIsNotAConversion() {
        XCTAssertFalse(CurrencySettings(displayCode: "USD", unitsPerUSD: 18.5).isConverting)
        XCTAssertFalse(CurrencySettings(displayCode: "ZAR", unitsPerUSD: 1).isConverting)
        XCTAssertTrue(CurrencySettings(displayCode: "ZAR", unitsPerUSD: 18.5).isConverting)
    }

    /// A rate left over from an earlier choice must not multiply dollar prices
    /// under a dollar sign: choose ZAR, type 18.5, switch back to USD, and
    /// $9.00 read as $166.50. Found by the Android port.
    func testALeftoverRateIsNotAppliedToDollars() {
        let leftover = CurrencySettings(displayCode: "USD", unitsPerUSD: 18.5)
        XCTAssertEqual(leftover.convert(10), 10)
        XCTAssertEqual(leftover.format(9), CurrencySettings.usd.format(9))
        XCTAssertEqual(leftover.toStorage(10), 10, "a typed dollar budget must be stored as typed")
    }

    /// Choosing a currency before typing its rate must not put that currency's
    /// code on a dollar amount, which is the original bug in a new place.
    func testACurrencyWithNoRateYetStillShowsDollars() {
        let unrated = CurrencySettings(displayCode: "ZAR", unitsPerUSD: 1)
        XCTAssertEqual(unrated.shownCode, "USD")
        XCTAssertEqual(unrated.format(9), CurrencySettings.usd.format(9))
        XCTAssertEqual(unrated.formatDisplayAmount(9), CurrencySettings.usd.format(9))
        XCTAssertEqual(CurrencySettings(displayCode: "ZAR", unitsPerUSD: 18.5).shownCode, "ZAR")
    }

    // MARK: Refusing bad rates

    /// A garbage rate produces garbage output silently, so it is refused rather
    /// than applied. Every case here would otherwise scale the whole app's
    /// numbers by nonsense.
    func testUnusableRatesAreIgnored() {
        for rate in [0, -1, -18.5, Double.nan, Double.infinity, 0.0001, 1_000_000] {
            XCTAssertFalse(Money.isUsable(rate), "\(rate) should be refused")
            XCTAssertEqual(
                Money.catalogue(10).converted(to: "ZAR", unitsPerUnit: rate).amount,
                10,
                accuracy: 0.0001
            )
            XCTAssertFalse(CurrencySettings(displayCode: "ZAR", unitsPerUSD: rate).isConverting)
        }
    }

    func testPlausibleRatesAreAccepted() {
        for rate in [0.5, 1, 1.08, 18.5, 150, 1500] {
            XCTAssertTrue(Money.isUsable(rate), "\(rate) should be accepted")
        }
    }

    func testConvertingIntoTheSameCurrencyIsAnIdentity() {
        let money = Money.catalogue(12.34)
        XCTAssertEqual(money.converted(to: "USD", unitsPerUnit: 18.5), money)
        XCTAssertEqual(money.converted(to: "usd", unitsPerUnit: 18.5), money)
    }

    // MARK: Settings hygiene

    func testEmptyCodeFallsBackAndCodeIsNormalised() {
        XCTAssertEqual(CurrencySettings(displayCode: "", unitsPerUSD: 1).displayCode, "USD")
        XCTAssertEqual(CurrencySettings(displayCode: "zar", unitsPerUSD: 18.5).displayCode, "ZAR")
        XCTAssertEqual(CurrencySettings(displayCode: "  ", unitsPerUSD: 1).displayCode, "USD")
    }

    func testLocaleSuggestionIsAUsableCode() {
        let suggestion = CurrencySettings.localeSuggestion()
        XCTAssertFalse(suggestion.isEmpty)
        XCTAssertEqual(suggestion, suggestion.uppercased())
    }

    func testPickerOptionsLeadWithUSDAndNeverRepeat() {
        let options = CurrencySettings.pickerOptions(current: "ZAR", locale: Locale(identifier: "en_ZA"))
        XCTAssertEqual(options.first, "USD")
        XCTAssertEqual(options[1], "ZAR")
        XCTAssertEqual(options.count, Set(options).count)
    }

    func testPickerOptionsAlwaysContainTheSavedCode() {
        // A saved code outside the common list must still have a row, or the
        // picker renders blank.
        let options = CurrencySettings.pickerOptions(current: " chf ", locale: Locale(identifier: "en_US"))
        XCTAssertEqual(options.filter { $0 == "CHF" }.count, 1)
        XCTAssertEqual(options.filter { $0 == "USD" }.count, 1)
        XCTAssertFalse(options.contains(""))
    }

    func testRateIsNeverZeroSoArithmeticCannotDivideByIt() {
        let settings = CurrencySettings(displayCode: "ZAR", unitsPerUSD: 0)
        XCTAssertEqual(settings.unitsPerUSD, 1)
        XCTAssertEqual(settings.toStorage(10), 10, accuracy: 0.0001)
    }

    func testDisclaimerNamesTheSourceOfThePrices() {
        XCTAssertTrue(PriceBook.rateDisclaimer.contains("US"))
        XCTAssertTrue(PriceBook.rateDisclaimer.lowercased().contains("estimate"))
    }

    // MARK: Totals that add up (gap 22)
    //
    // A total over lines must equal the lines as shown. Twin of the same
    // section in MoneyTest.kt.

    private func meal(_ costs: Double...) throws -> MealItem {
        let food = try XCTUnwrap(FoodCatalog.all.first)
        return MealItem(name: "Test", slot: .breakfast, portions: costs.map { Portion(food: food.withCost($0)) })
    }

    /// The bug from the first emulator screenshots: lines of $0.22, $0.57 and
    /// $0.27 under a $1.05 header, because the header summed unrounded costs.
    func testAMealHeaderEqualsTheColumnUnderIt() throws {
        let usd = CurrencySettings.usd
        let breakfast = try meal(0.215, 0.565, 0.265)

        XCTAssertEqual(breakfast.portions.map { usd.shown($0.cost) }, [0.22, 0.57, 0.27])
        XCTAssertEqual(usd.shownCost(of: breakfast), 1.06, accuracy: 1e-9)
        XCTAssertEqual(usd.formatTotal(breakfast.portions.map(\.cost)), DisplayFormat.currency(1.06, code: "USD"))
    }

    func testADayEqualsTheSumOfItsMealHeaders() throws {
        let usd = CurrencySettings.usd
        let day = try [meal(0.215, 0.565), meal(0.265, 1.005), meal(2.345)]
        XCTAssertEqual(usd.shownCost(of: day), day.reduce(0) { $0 + usd.shownCost(of: $1) }, accuracy: 1e-9)
    }

    /// Rounding in USD first would not have been enough: a converted column
    /// rounds again. At R18.50 the lines show R4.07, R10.55 and R5.00, which
    /// add to R19.62; converting the dollar total would have said R19.61.
    func testAConvertedColumnAddsUpInTheShownCurrency() {
        let rand = CurrencySettings(displayCode: "ZAR", unitsPerUSD: 18.5)
        let lines = [0.22, 0.57, 0.27]

        XCTAssertEqual(lines.map { rand.shown($0) }, [4.07, 10.55, 5.0])
        XCTAssertEqual(rand.shownTotal(lines), 19.62, accuracy: 1e-9)
        XCTAssertEqual(rand.formatTotal(lines), DisplayFormat.currency(19.62, code: "ZAR"))
    }

    /// Every amount from $0.000 to $3.000 in tenths of a cent rounds as a
    /// person would, halves up.
    func testHalfCentsRoundUpAsAPersonWouldWorkItOut() {
        for k in 0...3_000 {
            let expected = Double((k + 5) / 10) / 100
            XCTAssertEqual(CurrencySettings.usd.shown(Double(k) / 1_000), expected, "\(Double(k) / 1_000)")
        }
        // Held in binary as 0.28499999..., which a plain rounding sends down.
        XCTAssertEqual(CurrencySettings.usd.shown(0.19 * 1.5), 0.29)
    }

    func testYenRoundsToWholeYenAndTheColumnStillAddsUp() {
        let yen = CurrencySettings(displayCode: "JPY", unitsPerUSD: 150)
        XCTAssertEqual(yen.minorUnits, 0)
        XCTAssertEqual(yen.shown(0.333), 50)
        // Three lines of 0.45 yen each show as nothing, so the total is
        // nothing too, rather than the 1 yen their unrounded sum would print.
        XCTAssertEqual(yen.shownTotal([0.003, 0.003, 0.003]), 0)
    }

    func testDinarKeepsThreeDecimalsInRoundingAndPrinting() {
        XCTAssertEqual(CurrencyDigits.minorUnits(for: "KWD"), 3)
        XCTAssertEqual(CurrencyDigits.minorUnits(for: " kwd "), 3)
        XCTAssertEqual(CurrencyDigits.minorUnits(for: "ZAR"), 2)
        XCTAssertEqual(CurrencyDigits.minorUnits(for: "not a code"), 2)
        XCTAssertEqual(CurrencySettings(displayCode: "KWD", unitsPerUSD: 0.31).shown(0.005), 0.002, accuracy: 1e-12)
    }

    /// Two thousand random columns in five currencies, checked in whole
    /// smallest units: every line shown is a whole number of them, and the
    /// total shown is exactly their sum.
    func testRandomColumnsAlwaysAddUp() {
        var random = SplitMix64(seed: 22)
        let currencies = [
            CurrencySettings.usd,
            CurrencySettings(displayCode: "ZAR", unitsPerUSD: 18.5),
            CurrencySettings(displayCode: "GBP", unitsPerUSD: 0.79),
            CurrencySettings(displayCode: "JPY", unitsPerUSD: 151.3),
            CurrencySettings(displayCode: "KWD", unitsPerUSD: 0.307)
        ]
        for _ in 0..<2_000 {
            let lines = (0..<Int.random(in: 1...7, using: &random)).map { _ in
                Double.random(in: 0..<6, using: &random)
            }
            for settings in currencies {
                let scale = pow(10.0, Double(settings.minorUnits))
                var units = 0
                for line in lines {
                    let scaled = settings.shown(line) * scale
                    XCTAssertEqual(scaled, scaled.rounded(), accuracy: 1e-6, "a shown line is whole \(settings.shownCode) units")
                    units += Int(scaled.rounded())
                }
                XCTAssertEqual(Int((settings.shownTotal(lines) * scale).rounded()), units, "\(lines) in \(settings.shownCode)")
                XCTAssertEqual(
                    settings.formatTotal(lines),
                    DisplayFormat.currency(Double(units) / scale, code: settings.shownCode)
                )
            }
        }
    }

    /// A whole-meal swap is a chain of single swaps, and the review lists each
    /// step's saving under a title with the total. Each step is the drop in
    /// the shown cost, so the steps add up to the title exactly, in any
    /// currency.
    func testSwapStepsAddUpToTheSavingInTheTitle() throws {
        let engine = BudgetFoodEngine(catalog: FoodCatalog.all)
        let dinner = MealItem(
            name: "Salmon dinner",
            slot: .dinner,
            portions: try ["salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil"].map {
                Portion(food: try XCTUnwrap(FoodCatalog.food(id: $0)))
            }
        )
        let swap = try XCTUnwrap(engine.bestSwap(for: dinner))
        XCTAssertFalse(swap.portionSwaps.isEmpty)

        for prices in [CurrencySettings.usd, CurrencySettings(displayCode: "ZAR", unitsPerUSD: 18.5)] {
            // The review's premise: the last step's meal is the swapped meal.
            let last = try XCTUnwrap(swap.portionSwaps.last)
            XCTAssertEqual(prices.shownCost(of: swap.swapped), prices.shownCost(of: last.resultingMeal), accuracy: 1e-9)

            let steps = swap.shownStepSavings(in: prices)
            XCTAssertEqual(steps.count, swap.portionSwaps.count)
            XCTAssertEqual(steps.reduce(0, +), swap.shownSaving(in: prices), accuracy: 1e-9)
            XCTAssertEqual(
                swap.shownSaving(in: prices),
                prices.shownCost(of: swap.original) - prices.shownCost(of: swap.swapped),
                accuracy: 1e-9
            )
            XCTAssertGreaterThan(swap.shownSaving(in: prices), 0, "a swap the engine offers must show a saving")
        }
    }
}

/// A seeded generator, so the random column test checks the same columns on
/// every run and on every platform.
private struct SplitMix64: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) { state = seed }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
