//
//  ServingMeasure.swift
//  MacroDime
//
//  A serving description, understood well enough to be multiplied.
//
//  The catalogue describes one serving in words ("2 large eggs", "150 g raw"),
//  and the engine plans in quarter servings. Multiplying the words produced
//  "1.5 × 2 large eggs" on the plan and "1 × 1 can (142 g drained)" on the
//  grocery list. This type reads the words once into a number, a unit and a
//  noun, so 1.5 servings of eggs renders as "3 large eggs".
//
//  It is derived from the stored text rather than stored itself, so SwiftData's
//  schema does not change. `ServingMeasureTests` parses every catalogue food and
//  requires the 1x rendering to reproduce the catalogue text exactly, so a
//  serving the parser cannot read fails the build rather than the screen.
//

import Foundation

struct ServingMeasure: Hashable, Sendable {

    enum Unit: String, Hashable, Sendable {
        case grams = "g"
        case millilitres = "ml"
    }

    /// A weight or volume and the state it is weighed in: `150 g raw`.
    struct Amount: Hashable, Sendable {
        var value: Double
        var unit: Unit
        /// Trailing words such as `raw`, `dry` or `drained`. Nil when bare.
        var qualifier: String?

        func scaled(by factor: Double) -> Amount {
            Amount(value: value * factor, unit: unit, qualifier: qualifier)
        }

        var text: String {
            let base = ServingMeasure.format(value, unit)
            return qualifier.map { "\(base) \($0)" } ?? base
        }
    }

    /// The thing being counted, in both numbers: `large egg` and `large eggs`.
    struct Noun: Hashable, Sendable {
        var singular: String
        var plural: String

        /// Plural above one: "1 can", "½ can", "1¼ cans".
        func form(for count: Double) -> String { count > 1 ? plural : singular }
    }

    enum Shape: Hashable, Sendable {
        /// Weighed or poured: `150 g raw`, `240 ml`.
        case measured(Amount)
        /// Counted, with an optional weight note: `2 large eggs`,
        /// `1 can (142 g drained)`.
        case counted(Double, Noun, note: Amount?)
    }

    let shape: Shape

    // MARK: Rendering

    /// The serving multiplied by `factor`, in the catalogue's own style.
    func text(times factor: Double) -> String {
        switch shape {
        case .measured(let amount):
            return amount.scaled(by: factor).text
        case .counted(let count, let noun, let note):
            let total = count * factor
            let head = "\(Self.count(total)) \(noun.form(for: total))"
            guard let note else { return head }
            return "\(head) (\(note.scaled(by: factor).text))"
        }
    }

    /// Any serving description multiplied out. Text the parser cannot read,
    /// which only a user-created food can have, falls back to naming the
    /// servings rather than multiplying words: `1½ servings (1 bowl)`.
    static func describe(_ text: String, times factor: Double) -> String {
        if let measure = ServingMeasure(parsing: text) {
            return measure.text(times: factor)
        }
        if factor == 1 { return text }
        let unit = factor > 1 ? "servings" : "serving"
        return "\(count(factor)) \(unit) (\(text))"
    }

    // MARK: Parsing

    /// Reads `<number> g|ml [qualifier]` or `<number> <noun> [(<number> g|ml [qualifier])]`.
    /// Nil for anything else.
    init?(parsing raw: String) {
        var head = raw.trimmingCharacters(in: .whitespaces)
        var note: Amount?

        if head.hasSuffix(")"), let open = head.range(of: " (", options: .backwards) {
            let inside = String(head[open.upperBound..<head.index(before: head.endIndex)])
            guard let parsed = Self.amount(from: inside) else { return nil }
            note = parsed
            head = String(head[..<open.lowerBound])
        }

        if let measured = Self.amount(from: head) {
            guard note == nil else { return nil }
            shape = .measured(measured)
            return
        }

        let words = head.split(separator: " ").map(String.init)
        guard words.count >= 2, let count = Self.number(from: words[0]), count > 0 else { return nil }
        let phrase = words.dropFirst().joined(separator: " ")
        guard phrase.first?.isLetter == true else { return nil }

        // The catalogue writes the noun to agree with its own count: "1 can",
        // "½ medium", "2 slices". The other form is derived from that one.
        let noun = count > 1
            ? Noun(singular: Self.inflect(phrase, plural: false), plural: phrase)
            : Noun(singular: phrase, plural: Self.inflect(phrase, plural: true))
        shape = .counted(count, noun, note: note)
    }

    /// `150 g raw`, `240 ml`, `142 g drained`.
    private static func amount(from text: String) -> Amount? {
        let words = text.split(separator: " ").map(String.init)
        guard words.count >= 2,
              let value = number(from: words[0]),
              let unit = Unit(rawValue: words[1]) else { return nil }
        let rest = words.dropFirst(2).joined(separator: " ")
        return Amount(value: value, unit: unit, qualifier: rest.isEmpty ? nil : rest)
    }

    private static let glyphs: [Character: Double] = [
        "⅛": 0.125, "¼": 0.25, "⅜": 0.375, "½": 0.5, "⅝": 0.625, "¾": 0.75, "⅞": 0.875
    ]

    /// `2`, `1.5`, `1/2`, `½`, `1½`.
    private static func number(from token: String) -> Double? {
        if let plain = Double(token) { return plain }
        if let last = token.last, let fraction = glyphs[last] {
            let whole = token.dropLast()
            if whole.isEmpty { return fraction }
            return Double(whole).map { $0 + fraction }
        }
        let parts = token.split(separator: "/")
        if parts.count == 2, let top = Double(parts[0]), let bottom = Double(parts[1]), bottom > 0 {
            return top / bottom
        }
        return nil
    }

    /// Words that read the same in both numbers: `1 tbsp`, `2 tbsp`, `2 medium`.
    private static let invariant: Set<String> = ["tbsp", "tsp", "medium", "small", "large", "oz"]

    /// Inflects the head noun of a phrase: `large eggs` to `large egg`, and
    /// `bowl of soup` to `bowls of soup` (the noun before "of", which a
    /// user-created food is likely to use). Deliberately simple: the catalogue's
    /// phrases are all covered by the round-trip test.
    private static func inflect(_ phrase: String, plural: Bool) -> String {
        if let of = phrase.range(of: " of ") {
            let head = String(phrase[..<of.lowerBound])
            return inflect(head, plural: plural) + phrase[of.lowerBound...]
        }
        var words = phrase.split(separator: " ").map(String.init)
        guard let last = words.last, !invariant.contains(last) else { return phrase }
        if plural {
            if !last.hasSuffix("s") { words[words.count - 1] = last + "s" }
        } else if last.hasSuffix("s") {
            words[words.count - 1] = String(last.dropLast())
        }
        return words.joined(separator: " ")
    }

    // MARK: Number formatting

    /// A count in kitchen fractions: `3`, `½`, `2½`, `1¼`. The engine steps in
    /// quarter servings, so eighths cover every count it produces; anything else
    /// falls back to at most two decimals.
    static func count(_ value: Double) -> String {
        let eighths = (value * 8).rounded()
        guard eighths > 0, abs(value * 8 - eighths) < 0.001 else {
            return value.formatted(.number.precision(.fractionLength(0...2)))
        }
        let whole = Int(eighths) / 8
        let glyph = ["", "⅛", "¼", "⅜", "½", "⅝", "¾", "⅞"][Int(eighths) % 8]
        return whole == 0 ? glyph : "\(whole)\(glyph)"
    }

    /// Whole grams or millilitres, switching to kg or L from 1,000.
    static func format(_ value: Double, _ unit: Unit) -> String {
        guard value >= 1_000 else { return "\(Int(value.rounded())) \(unit.rawValue)" }
        let large = (value / 1_000).formatted(.number.precision(.fractionLength(0...1)))
        return "\(large) \(unit == .grams ? "kg" : "L")"
    }
}
