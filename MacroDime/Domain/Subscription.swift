//
//  Subscription.swift
//  MacroDime
//
//  MacroDime Pro, in store-neutral terms: what is sold, what the paywall says
//  about it, and the rules for the trial reminder. StoreKit lives in
//  Commerce/SubscriptionStore.swift; everything here is pure, tested on Linux,
//  and kept in step with the Android twin,
//  android/core/src/main/kotlin/com/lungelo/macrodime/domain/Subscription.kt.
//
//  Three rules shape it:
//  1. Every figure on the paywall comes from the store's own price for this
//     person, so a price changed in App Store Connect can never leave the
//     paywall quoting an old one.
//  2. A free trial is offered only when the store says this person may take
//     one (StoreKit's isEligibleForIntroOffer), so nobody is promised a trial
//     they would be charged for.
//  3. The terms are stated in full before the button: trial length, the price
//     after it, that it renews, and how to cancel.
//
//  Unlike Android, there is no offline cache of the entitlement here: StoreKit
//  verifies and keeps transactions on the device, so Transaction.currentEntitlements
//  answers without a connection.
//

import Foundation

/// The two ways to pay for MacroDime Pro. Only the yearly plan carries a free trial.
enum ProPlan: String, CaseIterable, Sendable {
    case annual
    case monthly

    var title: String { self == .annual ? "Yearly" : "Monthly" }
    var per: String { self == .annual ? "a year" : "a month" }
    var every: String { self == .annual ? "every year" : "every month" }
}

/// A period as the stores state it: 14 days, 2 weeks, 1 month, 1 year.
struct StorePeriod: Hashable, Sendable {

    enum Span: String, Sendable {
        case day, week, month, year
    }

    let count: Int
    let unit: Span

    /// The form before "free trial": "14-day", "1-month". Weeks are said in
    /// days, because a 14-day trial is how people compare them.
    var adjective: String {
        unit == .week ? "\(count * 7)-day" : "\(count)-\(unit.rawValue)"
    }

    /// The form that stands alone: "14 days", "1 month". Weeks again in days.
    var phrase: String {
        let (n, word) = unit == .week ? (count * 7, "day") : (count, unit.rawValue)
        return "\(n) \(n == 1 ? word : word + "s")"
    }

    /// When a period starting at `start` ends, on the calendar (UTC).
    func end(after start: Date) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        let component: Calendar.Component = switch unit {
        case .day: .day
        case .week: .weekOfYear
        case .month: .month
        case .year: .year
        }
        return calendar.date(byAdding: component, value: count, to: start) ?? start
    }

    /// ISO 8601 as Play writes it (P14D, P2W, P1M, P1Y). Nil for anything else.
    static func parse(_ iso: String) -> StorePeriod? {
        let text = iso.trimmingCharacters(in: .whitespaces).uppercased()
        guard text.hasPrefix("P"), text.count >= 3 else { return nil }
        let body = text.dropFirst()
        guard let last = body.last, let count = Int(body.dropLast()), (1...999).contains(count) else { return nil }
        let unit: Span
        switch last {
        case "D": unit = .day
        case "W": unit = .week
        case "M": unit = .month
        case "Y": unit = .year
        default: return nil
        }
        return StorePeriod(count: count, unit: unit)
    }
}

/// One way to buy Pro, as the store priced it for this person. `freeTrial` is
/// set only when the store says this person may take a trial now.
struct ProOffer: Hashable, Sendable {
    let plan: ProPlan
    /// The recurring price, in `currencyCode`.
    let price: Double
    let currencyCode: String
    var freeTrial: StorePeriod? = nil
}

/// Where a subscription is managed and cancelled. The rules differ, so the copy does.
enum Store: Sendable {
    case googlePlay
    case appStore

    var displayName: String { self == .googlePlay ? "Google Play" : "the App Store" }

    var cancelBeforeTrialEnds: String {
        self == .googlePlay
            ? "Cancel in Google Play before the trial ends and you won't be charged."
            : "Cancel at least 24 hours before the trial ends and you won't be charged."
    }

    var cancelAnyTime: String {
        self == .googlePlay
            ? "Cancel any time in Google Play."
            : "Cancel any time in Settings, under your Apple Account, at least 24 hours before it renews."
    }
}

/// Why the paywall is showing, which decides its headline.
enum PaywallReason: String, Sendable, Identifiable {
    case afterOnboarding, planner, groceries, swaps, progressPhotos, settings
    var id: String { rawValue }
}

/// Everything the paywall says. Copy lives here, tested, not in the views.
enum Paywall {

    static let proName = "MacroDime Pro"

    struct Benefit: Hashable, Sendable {
        let title: String
        let detail: String
    }

    static let benefits = [
        Benefit(title: "Meal plans that fit your budget", detail: "Every day planned to your calorie and protein targets, priced against your allowance."),
        Benefit(title: "Cheaper swaps", detail: "Same macros within 10%, cheaper ingredients, one tap to apply."),
        Benefit(title: "A grocery list that builds itself", detail: "From the week you planned, in store-walk order."),
        Benefit(title: "Progress photos", detail: "Kept beside your waist and weight, on your phone only."),
    ]

    /// One plan, as its card on the paywall reads.
    struct PlanCard: Hashable, Sendable {
        let plan: ProPlan
        let title: String
        /// "$29.99 a year".
        let price: String
        /// "$2.50 a month", on the yearly card only.
        let perMonth: String?
        /// "Save 58%", on the yearly card, only when the yearly plan saves.
        let badge: String?
        /// "14 days free", only when this person may take the trial.
        let trial: String?
    }

    struct TimelineStep: Hashable, Sendable {
        let title: String
        let detail: String
    }

    static func money(_ amount: Double, code: String) -> String {
        DisplayFormat.currency(CurrencyDigits.round(amount, toDigits: CurrencyDigits.minorUnits(for: code)), code: code)
    }

    /// "$29.99 a year".
    static func priceLine(_ offer: ProOffer) -> String {
        "\(money(offer.price, code: offer.currencyCode)) \(offer.plan.per)"
    }

    /// Whole percent the yearly plan saves against twelve monthly payments,
    /// rounded down so the badge never overstates it. Nil across two
    /// currencies, or below 1%.
    static func annualSavingsPercent(annual: ProOffer, monthly: ProOffer) -> Int? {
        guard annual.plan == .annual, monthly.plan == .monthly,
              annual.currencyCode == monthly.currencyCode,
              monthly.price > 0, annual.price > 0 else { return nil }
        let percent = Int(((1 - annual.price / (monthly.price * 12)) * 100 + 1e-9).rounded(.down))
        return percent >= 1 ? percent : nil
    }

    static func card(_ offer: ProOffer, monthly: ProOffer?) -> PlanCard {
        PlanCard(
            plan: offer.plan,
            title: offer.plan.title,
            price: priceLine(offer),
            perMonth: offer.plan == .annual ? "\(money(offer.price / 12, code: offer.currencyCode)) a month" : nil,
            badge: offer.plan == .annual
                ? monthly.flatMap { annualSavingsPercent(annual: offer, monthly: $0) }.map { "Save \($0)%" }
                : nil,
            trial: offer.freeTrial.map { "\($0.phrase) free" }
        )
    }

    /// The button. "Start my 14-day free trial" only when the store offers this person one.
    static func callToAction(_ offer: ProOffer) -> String {
        if let trial = offer.freeTrial { return "Start my \(trial.adjective) free trial" }
        return "Subscribe for \(priceLine(offer))"
    }

    /// The terms, stated in full under the button.
    static func disclosure(_ offer: ProOffer, store: Store) -> String {
        let price = priceLine(offer)
        let renews = "\(proName) renews \(offer.plan.every) until you cancel."
        if let trial = offer.freeTrial {
            let phrase = trial.phrase.prefix(1).uppercased() + trial.phrase.dropFirst()
            return "\(phrase) free, then \(price). \(renews) \(store.cancelBeforeTrialEnds)"
        }
        return "\(price). \(renews) \(store.cancelAnyTime)"
    }

    /// What happens when, for an offer with a trial. Empty without one.
    static func timeline(_ offer: ProOffer, store: Store) -> [TimelineStep] {
        guard let trial = offer.freeTrial else { return [] }
        let endTitle = (trial.unit == .day || trial.unit == .week)
            ? "Day \(trial.phrase.split(separator: " ").first ?? "")"
            : "After \(trial.phrase)"
        return [
            TimelineStep(title: "Today", detail: "Everything in Pro, free."),
            TimelineStep(title: endTitle, detail: "Your trial ends. \(store.cancelBeforeTrialEnds)"),
            TimelineStep(title: "Then", detail: "\(priceLine(offer)), renewing \(offer.plan.every) until you cancel."),
        ]
    }

    /// The headline, in the person's own numbers. Someone whose swaps have
    /// already saved money is shown that figure first.
    static func headline(_ reason: PaywallReason, savedSoFar: String?, dailyAllowance: String) -> String {
        if let savedSoFar { return "Your swaps have saved you \(savedSoFar)" }
        switch reason {
        case .afterOnboarding: return "Your plan is ready"
        case .planner: return "Plan meals that fit \(dailyAllowance) a day"
        case .groceries: return "Let your grocery list build itself"
        case .swaps: return "Pay less for the same macros"
        case .progressPhotos: return "See the change BMI can't show"
        case .settings: return proName
        }
    }

    static func subheadline(calories: String, protein: String, dailyAllowance: String) -> String {
        "\(calories) and \(protein) of protein a day, planned within \(dailyAllowance), with a grocery list to match."
    }

    static func lockedDetail(_ reason: PaywallReason, calories: String, protein: String) -> String {
        switch reason {
        case .groceries:
            return "Pro turns the week you plan into one shopping list, in the order you walk the store, with what you already have kept off it."
        case .swaps:
            return "Pro checks every meal for cheaper ingredients that keep your macros within 10%, and applies a swap in one tap."
        case .progressPhotos:
            return "Pro keeps progress photos beside your waist and weight, on this phone only, so you can see what the scale misses."
        default:
            return "Pro plans every meal to your \(calories) and \(protein) of protein, prices each one against your allowance, and finds cheaper swaps when it can."
        }
    }

    static func upgradeTitle(dailyAllowance: String) -> String { "Plan today within \(dailyAllowance)" }

    static let upgradeDetail = "Pro plans your meals to these targets, finds cheaper swaps and builds your grocery list."

    static func unlockCaption(_ store: Store) -> String {
        "You see the full terms before anything starts. \(store.cancelAnyTime)"
    }
}

/// Whether this person has Pro, as the store says, with what the trial
/// reminder needs.
struct Entitlement: Hashable, Sendable {
    var isPro: Bool
    var plan: ProPlan? = nil
    /// When the current free trial ends; nil outside one.
    var trialEndsAt: Date? = nil
    /// False once the person has cancelled: Pro runs to the end and stops.
    var willRenew: Bool = true

    static let free = Entitlement(isPro: false)
}

enum EntitlementPolicy {

    /// How many days before a trial ends the in-app reminder appears.
    static let reminderDays = 2

    /// Whole days left in a trial, counting part of a day as a day; nil outside one.
    static func trialDaysLeft(_ entitlement: Entitlement, now: Date = .now) -> Int? {
        guard entitlement.isPro, let endsAt = entitlement.trialEndsAt, endsAt > now else { return nil }
        return Int((endsAt.timeIntervalSince(now) / 86_400).rounded(.up))
    }

    static func showsTrialReminder(_ entitlement: Entitlement, now: Date = .now) -> Bool {
        (trialDaysLeft(entitlement, now: now) ?? Int.max) <= reminderDays
    }

    static func reminderTitle(daysLeft: Int) -> String {
        switch daysLeft {
        case ...0: return "Your free trial ends today"
        case 1: return "Your free trial ends tomorrow"
        default: return "Your free trial ends in \(daysLeft) days"
        }
    }

    /// What happens next, honestly: someone who has cancelled is told they
    /// will not be charged, not warned that they will be.
    static func reminderDetail(_ entitlement: Entitlement, offer: ProOffer?, store: Store) -> String {
        if !entitlement.willRenew { return "You cancelled, so you won't be charged. Pro ends when the trial does." }
        if let offer { return "Then \(Paywall.priceLine(offer)), renewing \(offer.plan.every). \(store.cancelBeforeTrialEnds)" }
        return "Then it renews at the price you agreed to. \(store.cancelBeforeTrialEnds)"
    }
}
