//
//  Money.swift
//  MacroDime
//
//  Why this file exists.
//
//  Every price in `FoodCatalog` is a US national-average supermarket price, so
//  every price in this app is in **USD**. The old display rule formatted those
//  amounts with the device locale's currency, which put a rand, euro or naira
//  sign in front of a dollar figure. That is worse than not localising at all:
//  a South African user reading "R9.00" for a day of food is being told
//  something false, and the error is invisible to anyone testing in the US.
//
//  Three rules follow, and they are enforced by tests:
//
//  1. The catalogue's currency is named, in one place (`PriceBook`), and a
//     catalogue amount is never rendered with a different code.
//  2. Conversion happens on explicit numbers the user supplied (`CurrencySettings`),
//     never on an invented exchange rate. No network, no stale table.
//  3. Storage stays in one currency: USD. Only display converts. A budget the
//     user types in rands is converted on the way in, so two currencies never
//     meet inside the arithmetic.
//

import Foundation

// MARK: - The catalogue's currency

/// The currency every price in `FoodCatalog` is denominated in.
///
/// Not a setting: it describes the data. If the catalogue is ever localised,
/// the prices change with it and this constant follows them.
enum PriceBook {
    static let currencyCode = "USD"

    /// Shown wherever a converted figure could otherwise be mistaken for a
    /// local price, so the user knows what they are looking at.
    static let rateDisclaimer = "Prices are US supermarket averages. The rate is your own estimate, so treat converted amounts as approximate."
}

// MARK: - Minor units

/// How many digits a currency is shown and rounded with: cents for dollars and
/// rand, none for yen, three for dinar (ISO 4217 minor units).
///
/// One table, read by both the formatter and the rounding, so a figure is never
/// rounded to one precision and printed at another. The Android app holds the
/// same table, so the two platforms agree on every code.
enum CurrencyDigits {
    private static let none: Set<String> = [
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG",
        "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF"
    ]
    private static let three: Set<String> = ["BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND"]

    static func minorUnits(for code: String) -> Int {
        let code = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if none.contains(code) { return 0 }
        if three.contains(code) { return 3 }
        return 2
    }

    /// `amount` to `digits` decimal places, halves away from zero. The nudge,
    /// a ten-millionth of the smallest unit, keeps a half cent that binary
    /// floating point stores just below the half (0.19 x 1.5 is held as
    /// 0.28499999...) rounding up, as a person working it out would.
    static func round(_ amount: Double, toDigits digits: Int) -> Double {
        guard amount.isFinite else { return amount }
        let scale = pow(10.0, Double(digits))
        let scaled = amount * scale
        let nudged = scaled + (scaled >= 0 ? 1e-7 : -1e-7)
        return nudged.rounded() / scale
    }
}

// MARK: - Money

/// An amount in a named currency.
///
/// Deliberately tiny. The app does not do currency arithmetic across codes
/// (storage is USD only); this type exists so that an amount cannot be
/// formatted without saying what it is denominated in.
struct Money: Hashable, Sendable {
    var amount: Double
    var currencyCode: String

    init(amount: Double, currencyCode: String) {
        self.amount = amount
        self.currencyCode = currencyCode
    }

    /// An amount taken from the catalogue, which is always USD.
    static func catalogue(_ amount: Double) -> Money {
        Money(amount: amount, currencyCode: PriceBook.currencyCode)
    }

    static let zero = Money.catalogue(0)

    var isZero: Bool { amount == 0 }

    /// Converts into another currency using a caller-supplied rate, expressed as
    /// units of `code` per one unit of this currency.
    ///
    /// A non-positive or absurd rate is refused rather than applied, because a
    /// garbage rate produces garbage output silently.
    func converted(to code: String, unitsPerUnit rate: Double) -> Money {
        let target = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard Self.isUsable(rate) else { return self }
        if target.isEmpty || target == currencyCode { return self }
        return Money(amount: amount * rate, currencyCode: target)
    }

    /// Whether a rate is present and plausible enough to use.
    static func isUsable(_ rate: Double) -> Bool {
        rate.isFinite && rate > 0.01 && rate < 10_000
    }
}

// MARK: - Display settings

/// How the user wants amounts shown: the catalogue's USD, or their own currency
/// at a rate they have entered themselves.
///
/// `unitsPerUSD` is the only conversion factor in the app. A user who never
/// touches it sees honest USD figures rather than a wrong local symbol.
struct CurrencySettings: Hashable, Sendable {
    /// The currency amounts are rendered in.
    var displayCode: String
    /// Units of `displayCode` per 1 USD. 1 when not converting.
    var unitsPerUSD: Double

    init(displayCode: String, unitsPerUSD: Double) {
        let code = displayCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        self.displayCode = code.isEmpty ? PriceBook.currencyCode : code
        self.unitsPerUSD = Money.isUsable(unitsPerUSD) ? unitsPerUSD : 1
    }

    /// The default: the catalogue's own currency, no conversion claimed.
    static let usd = CurrencySettings(displayCode: PriceBook.currencyCode, unitsPerUSD: 1)

    /// True when a real conversion is being applied.
    var isConverting: Bool {
        displayCode != PriceBook.currencyCode && unitsPerUSD != 1
    }

    /// The currency amounts are actually shown in: the chosen code only once a
    /// real rate makes it a conversion, and until then honest USD.
    ///
    /// Applying the code and the rate independently was a bug, found by the
    /// Android port. Choosing ZAR before typing a rate labelled every dollar
    /// amount as rand, and switching back to USD after typing 18.5 multiplied
    /// every dollar price by 18.5 under a dollar sign ($9.00 read $166.50).
    var shownCode: String {
        isConverting ? displayCode : PriceBook.currencyCode
    }

    /// The factor actually applied: the user's rate while converting, else 1.
    private var appliedRate: Double {
        isConverting ? unitsPerUSD : 1
    }

    /// A catalogue amount (USD) in the shown currency.
    func convert(_ usd: Double) -> Double { usd * appliedRate }

    /// A number the user typed in the shown currency, expressed in USD for
    /// storage. The inverse of `convert(_:)`.
    func toStorage(_ displayAmount: Double) -> Double {
        displayAmount / appliedRate
    }

    /// Digits after the decimal point in `shownCode`: 2 for dollars and rand,
    /// 0 for yen.
    var minorUnits: Int { CurrencyDigits.minorUnits(for: shownCode) }

    /// A catalogue amount (USD) exactly as the screen shows it: converted, then
    /// rounded to the shown currency's smallest unit.
    func shown(_ usd: Double) -> Double {
        CurrencyDigits.round(convert(usd), toDigits: minorUnits)
    }

    /// Lines added up the way a reader adds them: each as shown, then summed.
    ///
    /// Summing the unrounded costs and rounding once let a total miss a cent
    /// against the column above it: a breakfast of $0.22, $0.57 and $0.27 was
    /// headed $1.05. Rounding in USD first would not cure it either, because a
    /// converted column rounds again in the shown currency; so every total over
    /// lines is built from the lines as shown, in the shown currency.
    func shownTotal<Lines: Sequence>(_ usdLines: Lines) -> Double where Lines.Element == Double {
        CurrencyDigits.round(usdLines.reduce(0) { $0 + shown($1) }, toDigits: minorUnits)
    }

    /// A meal's cost as shown: the sum of its lines as shown, so the header
    /// equals the column.
    func shownCost(of meal: MealItem) -> Double {
        shownTotal(meal.portions.map(\.cost))
    }

    /// A day's cost as shown: every line of every meal, as shown. Equals the
    /// sum of the meal headers.
    func shownCost(of meals: [MealItem]) -> Double {
        shownTotal(meals.flatMap { $0.portions.map(\.cost) })
    }

    /// What a swap saves as shown: exactly the drop in the meal's shown cost.
    func shownSaving(from original: MealItem, to swapped: MealItem) -> Double {
        CurrencyDigits.round(shownCost(of: original) - shownCost(of: swapped), toDigits: minorUnits)
    }

    /// Formats a USD amount, converting first when the user has opted in.
    /// This is the only path a catalogue price should take to the screen.
    func format(_ usd: Double) -> String {
        DisplayFormat.currency(shown(usd), code: shownCode)
    }

    /// Formats a total of USD lines as `shownTotal` builds it.
    func formatTotal<Lines: Sequence>(_ usdLines: Lines) -> String where Lines.Element == Double {
        DisplayFormat.currency(shownTotal(usdLines), code: shownCode)
    }

    /// Formats an amount already in shown units, such as a difference of two
    /// shown totals.
    func formatDisplayAmount(_ amount: Double) -> String {
        DisplayFormat.currency(CurrencyDigits.round(amount, toDigits: minorUnits), code: shownCode)
    }

    /// The currency chosen from the device locale, offered as a starting point
    /// in Settings. Never applied automatically: adopting it without a rate
    /// would put the wrong symbol back on a dollar amount.
    static func localeSuggestion(for locale: Locale = .current) -> String {
        (locale.currency?.identifier ?? PriceBook.currencyCode).uppercased()
    }

    /// Codes offered alongside USD and the device's own currency. A short list
    /// on purpose: every one of them still needs a rate the user types in.
    static let commonCodes = ["ZAR", "GBP", "EUR", "CAD", "AUD", "NZD", "INR", "NGN", "KES"]

    /// What the Settings picker lists, in order: the catalogue's USD, the
    /// device's currency, the code already saved, then `commonCodes`, each once.
    /// The saved code is always included, because a picker whose selection has
    /// no matching row renders blank.
    static func pickerOptions(current: String, locale: Locale = .current) -> [String] {
        let candidates = [PriceBook.currencyCode, localeSuggestion(for: locale), current] + commonCodes
        var seen = Set<String>()
        return candidates
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
    }
}
