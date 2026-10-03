//
//  SubscriptionTests.swift
//  MacroDimeTests
//
//  The paywall's promises, pinned down. Twin of the Android
//  SubscriptionTest.kt for everything the two apps share; the Play-only rules
//  (offer selection, purchase acknowledgement, the offline cache) have no iOS
//  counterpart because StoreKit verifies and keeps transactions itself.
//

import XCTest
@testable import MacroDime

final class SubscriptionTests: XCTestCase {

    private let annual = ProOffer(plan: .annual, price: 29.99, currencyCode: "USD", freeTrial: StorePeriod(count: 2, unit: .week))
    private let monthly = ProOffer(plan: .monthly, price: 5.99, currencyCode: "USD")
    private func usd(_ amount: Double) -> String { DisplayFormat.currency(amount, code: "USD") }

    // MARK: Periods

    func testPeriodsReadTheWayPeopleCompareThem() {
        XCTAssertEqual(StorePeriod(count: 2, unit: .week).adjective, "14-day")
        XCTAssertEqual(StorePeriod(count: 14, unit: .day).adjective, "14-day")
        XCTAssertEqual(StorePeriod(count: 2, unit: .week).phrase, "14 days")
        XCTAssertEqual(StorePeriod(count: 1, unit: .day).phrase, "1 day")
        XCTAssertEqual(StorePeriod(count: 1, unit: .month).adjective, "1-month")
        XCTAssertEqual(StorePeriod(count: 3, unit: .month).phrase, "3 months")
    }

    func testOnlyTheFormsTheStoresUseParse() {
        XCTAssertEqual(StorePeriod.parse("P14D"), StorePeriod(count: 14, unit: .day))
        XCTAssertEqual(StorePeriod.parse(" p2w "), StorePeriod(count: 2, unit: .week))
        for bad in ["", "P", "P0D", "P1Y2M", "PT1H", "14 days", "P14", "P1000D"] {
            XCTAssertNil(StorePeriod.parse(bad), "'\(bad)' should not parse")
        }
    }

    func testPeriodsEndOnTheCalendar() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "UTC"))
        let start = try XCTUnwrap(calendar.date(from: DateComponents(year: 2027, month: 1, day: 31)))
        let day = { (period: StorePeriod) -> [Int?] in
            let parts = calendar.dateComponents([.year, .month, .day], from: period.end(after: start))
            return [parts.year, parts.month, parts.day]
        }
        XCTAssertEqual(day(StorePeriod(count: 14, unit: .day)), [2027, 2, 14])
        // A month after 31 January is the last day of February, not 3 March.
        XCTAssertEqual(day(StorePeriod(count: 1, unit: .month)), [2027, 2, 28])
    }

    // MARK: The plan cards

    func testTheYearlyCardShowsItsMonthlyEquivalentItsSavingAndItsTrial() {
        let card = Paywall.card(annual, monthly: monthly)
        XCTAssertEqual(card.price, "\(usd(29.99)) a year")
        XCTAssertEqual(card.perMonth, "\(usd(2.50)) a month")
        XCTAssertEqual(card.badge, "Save 58%")
        XCTAssertEqual(card.trial, "14 days free")
    }

    func testTheMonthlyCardMakesNoClaimsItCannotBackUp() {
        let card = Paywall.card(monthly, monthly: monthly)
        XCTAssertNil(card.perMonth)
        XCTAssertNil(card.badge)
        XCTAssertNil(card.trial)
    }

    func testTheSavingsBadgeNeverOverstates() {
        XCTAssertEqual(Paywall.annualSavingsPercent(annual: annual, monthly: monthly), 58)
        XCTAssertEqual(Paywall.annualSavingsPercent(
            annual: ProOffer(plan: .annual, price: 30, currencyCode: "USD"),
            monthly: ProOffer(plan: .monthly, price: 5, currencyCode: "USD")
        ), 50)
        XCTAssertNil(Paywall.annualSavingsPercent(annual: ProOffer(plan: .annual, price: 71.88, currencyCode: "USD"), monthly: monthly))
        XCTAssertNil(Paywall.annualSavingsPercent(annual: annual, monthly: ProOffer(plan: .monthly, price: 5.99, currencyCode: "ZAR")))
    }

    func testYenHasNoMinorUnitOnTheCard() {
        let yen = Paywall.card(ProOffer(plan: .annual, price: 4_800, currencyCode: "JPY"), monthly: ProOffer(plan: .monthly, price: 600, currencyCode: "JPY"))
        XCTAssertEqual(yen.perMonth, "\(DisplayFormat.currency(400, code: "JPY")) a month")
        XCTAssertEqual(yen.badge, "Save 33%")
    }

    // MARK: The button and the terms

    func testTheButtonPromisesATrialOnlyWhenTheStoreOffersOne() {
        XCTAssertEqual(Paywall.callToAction(annual), "Start my 14-day free trial")
        var noTrial = annual
        noTrial.freeTrial = nil
        XCTAssertEqual(Paywall.callToAction(noTrial), "Subscribe for \(usd(29.99)) a year")
        XCTAssertEqual(Paywall.callToAction(monthly), "Subscribe for \(usd(5.99)) a month")
    }

    func testTheAppStoreTermsStateApplesCancellationWindow() {
        let terms = Paywall.disclosure(annual, store: .appStore)
        XCTAssertTrue(terms.hasPrefix("14 days free, then \(usd(29.99)) a year."))
        XCTAssertTrue(terms.contains("renews every year"))
        XCTAssertTrue(terms.contains("24 hours"))
    }

    /// Every combination states the price, that it renews and how to cancel,
    /// and nothing generated carries a dash character.
    func testEveryCombinationStatesTheFullTermsAndNoDashes() {
        let codePoints: [UInt32] = [0x2012, 0x2013, 0x2014, 0x2015, 0x2212]
        let dashes: [Character] = codePoints.compactMap { Unicode.Scalar($0) }.map { Character($0) }
        let trials: [StorePeriod?] = [nil, StorePeriod(count: 3, unit: .day), StorePeriod(count: 2, unit: .week), StorePeriod(count: 1, unit: .month)]
        for store in [Store.googlePlay, .appStore] {
            for plan in ProPlan.allCases {
                for trial in trials {
                    let offer = ProOffer(plan: plan, price: plan == .annual ? 29.99 : 5.99, currencyCode: "USD", freeTrial: trial)
                    let terms = Paywall.disclosure(offer, store: store)
                    XCTAssertTrue(terms.contains(Paywall.priceLine(offer)), terms)
                    XCTAssertTrue(terms.contains("renews"), terms)
                    XCTAssertTrue(terms.lowercased().contains("cancel"), terms)
                    let card = Paywall.card(offer, monthly: monthly)
                    let everything = [terms, Paywall.callToAction(offer), card.title, card.price, card.perMonth ?? "", card.badge ?? "", card.trial ?? ""]
                        + Paywall.timeline(offer, store: store).flatMap { [$0.title, $0.detail] }
                    for text in everything {
                        XCTAssertFalse(text.contains(where: dashes.contains), "a dash in: \(text)")
                    }
                }
            }
        }
    }

    func testTheTimelineExplainsTheTrialAndIsEmptyWithoutOne() {
        XCTAssertEqual(Paywall.timeline(annual, store: .appStore).map(\.title), ["Today", "Day 14", "Then"])
        XCTAssertTrue(Paywall.timeline(monthly, store: .appStore).isEmpty)
    }

    func testTheHeadlineLeadsWithWhatTheUserHasAlreadySaved() {
        XCTAssertEqual(Paywall.headline(.planner, savedSoFar: "$4.50", dailyAllowance: "$9.00"), "Your swaps have saved you $4.50")
        XCTAssertEqual(Paywall.headline(.afterOnboarding, savedSoFar: nil, dailyAllowance: "$9.00"), "Your plan is ready")
        XCTAssertEqual(Paywall.headline(.planner, savedSoFar: nil, dailyAllowance: "$9.00"), "Plan meals that fit $9.00 a day")
    }

    // MARK: The trial reminder

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    func testTheTrialReminderAppearsOnlyInTheLastTwoDays() {
        let trial = { (seconds: TimeInterval) in Entitlement(isPro: true, plan: .annual, trialEndsAt: self.now.addingTimeInterval(seconds)) }
        XCTAssertEqual(EntitlementPolicy.trialDaysLeft(trial(3 * 86_400), now: now), 3)
        XCTAssertFalse(EntitlementPolicy.showsTrialReminder(trial(3 * 86_400), now: now))
        XCTAssertEqual(EntitlementPolicy.trialDaysLeft(trial(36 * 3_600), now: now), 2)
        XCTAssertTrue(EntitlementPolicy.showsTrialReminder(trial(36 * 3_600), now: now))
        XCTAssertNil(EntitlementPolicy.trialDaysLeft(trial(-1), now: now))
        XCTAssertNil(EntitlementPolicy.trialDaysLeft(Entitlement(isPro: false, trialEndsAt: now.addingTimeInterval(86_400)), now: now))
    }

    func testTheReminderNeverThreatensAChargeAfterACancellation() {
        let cancelled = Entitlement(isPro: true, plan: .annual, trialEndsAt: now, willRenew: false)
        let text = EntitlementPolicy.reminderDetail(cancelled, offer: annual, store: .appStore)
        XCTAssertTrue(text.contains("won't be charged"))
        XCTAssertFalse(text.contains(usd(29.99)))
        XCTAssertEqual(EntitlementPolicy.reminderTitle(daysLeft: 1), "Your free trial ends tomorrow")
    }
}
