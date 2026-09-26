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

    /// A catalogue amount (USD) in the display currency.
    func convert(_ usd: Double) -> Double { usd * unitsPerUSD }

    /// A number the user typed in the display currency, expressed in USD for
    /// storage. The inverse of `convert(_:)`.
    func toStorage(_ displayAmount: Double) -> Double {
        guard unitsPerUSD > 0 else { return displayAmount }
        return displayAmount / unitsPerUSD
    }

    /// Formats a USD amount, converting first when the user has opted in.
    /// This is the only path a catalogue price should take to the screen.
    func format(_ usd: Double) -> String {
        DisplayFormat.currency(convert(usd), code: displayCode)
    }

    /// Formats an already-converted amount, for values that were derived in
    /// display units (a difference between two converted amounts, say).
    func formatDisplayAmount(_ amount: Double) -> String {
        DisplayFormat.currency(amount, code: displayCode)
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
