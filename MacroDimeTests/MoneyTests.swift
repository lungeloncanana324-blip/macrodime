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
}
