//
//  SubscriptionStore.swift
//  MacroDime
//
//  MacroDime Pro through StoreKit 2. Apple takes the payment; the app only
//  asks StoreKit, on the device, which subscription is active. There is no
//  server of ours and no network code of ours: StoreKit verifies transactions
//  and keeps them on the device, which is also why Pro works offline without
//  a cache here.
//
//  App Store Connect: subscription group "MacroDime Pro", products
//  com.lungelo.macrodime.pro.annual (with an introductory offer: free trial,
//  2 weeks) and com.lungelo.macrodime.pro.monthly. Setup steps are in
//  docs/app-store-listing.md.
//

import Foundation
import StoreKit

@MainActor
@Observable
final class SubscriptionStore {

    enum Availability { case connecting, ready, unavailable }

    static let productIDs: [ProPlan: String] = [
        .annual: "com.lungelo.macrodime.pro.annual",
        .monthly: "com.lungelo.macrodime.pro.monthly",
    ]

    let store = Store.appStore

    private(set) var availability: Availability = .connecting
    private(set) var offers: [ProPlan: ProOffer] = [:]
    private(set) var entitlement: Entitlement = .free
    private(set) var isPurchasing = false
    var message: String?

    var isPro: Bool { entitlement.isPro }

    /// The plan the paywall leads with: yearly, when the store sells it.
    var leadOffer: ProOffer? { offers[.annual] ?? offers[.monthly] }

    @ObservationIgnored private var products: [ProPlan: Product] = [:]
    @ObservationIgnored private var updates: Task<Void, Never>?
    /// Screenshots and previews: no StoreKit, fixed offers. A constant, so never observed.
    private let isPreview: Bool

    init() {
        isPreview = false
    }

    /// A stand-in with the App Store Connect prices, for screenshots and previews.
    init(preview entitlement: Entitlement) {
        isPreview = true
        availability = .ready
        offers = [
            .annual: ProOffer(plan: .annual, price: 29.99, currencyCode: "USD", freeTrial: StorePeriod(count: 2, unit: .week)),
            .monthly: ProOffer(plan: .monthly, price: 5.99, currencyCode: "USD"),
        ]
        self.entitlement = entitlement
    }

    /// Starts listening for renewals, refunds and purchases made elsewhere, then loads.
    func start() async {
        guard !isPreview else { return }
        if updates == nil {
            updates = Task { [weak self] in
                for await update in Transaction.updates {
                    if case .verified(let transaction) = update { await transaction.finish() }
                    await self?.refresh()
                }
            }
        }
        await refresh()
    }

    func refresh() async {
        guard !isPreview else { return }
        do {
            let loaded = try await Product.products(for: Array(Self.productIDs.values))
            var byPlan: [ProPlan: Product] = [:]
            var priced: [ProPlan: ProOffer] = [:]
            for product in loaded {
                guard let plan = Self.productIDs.first(where: { $0.value == product.id })?.key,
                      let offer = await Self.offer(for: product, plan: plan) else { continue }
                byPlan[plan] = product
                priced[plan] = offer
            }
            products = byPlan
            offers = priced
            availability = .ready
            if message == Self.noPlans && !priced.isEmpty { message = nil }
        } catch {
            availability = .unavailable
        }
        entitlement = await Self.currentEntitlement()
        isPurchasing = false
    }

    func purchase(_ plan: ProPlan) async {
        guard let product = products[plan] else {
            message = Self.noPlans
            await refresh()
            return
        }
        isPurchasing = true
        defer { isPurchasing = false }
        do {
            switch try await product.purchase() {
            case .success(let verification):
                if case .verified(let transaction) = verification { await transaction.finish() }
                await refresh()
            case .pending:
                message = Self.pending
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            message = Self.purchaseFailed
        }
    }

    func restore() async {
        guard !isPreview else {
            if !isPro { message = Self.nothingToRestore }
            return
        }
        do {
            try await AppStore.sync()
        } catch {
            // Cancelled sign-in, or no connection: what StoreKit already holds still counts.
        }
        await refresh()
        if !isPro { message = Self.nothingToRestore }
    }

    // MARK: Reading StoreKit

    /// A product as the paywall sells it. Nil for anything the copy could not
    /// describe honestly: a "yearly" product that does not renew yearly, or an
    /// introductory offer that is not a plain free trial.
    private static func offer(for product: Product, plan: ProPlan) async -> ProOffer? {
        guard let subscription = product.subscription else { return nil }
        let expected: Product.SubscriptionPeriod.Unit = plan == .annual ? .year : .month
        guard subscription.subscriptionPeriod.unit == expected, subscription.subscriptionPeriod.value == 1 else { return nil }

        var trial: StorePeriod?
        // The trial belongs to the yearly plan, and only for someone who may take it.
        if plan == .annual,
           let intro = subscription.introductoryOffer,
           intro.paymentMode == .freeTrial,
           await subscription.isEligibleForIntroOffer {
            trial = storePeriod(intro.period, times: intro.periodCount)
        }
        let price = NSDecimalNumber(decimal: product.price).doubleValue
        return ProOffer(plan: plan, price: price, currencyCode: product.priceFormatStyle.currencyCode, freeTrial: trial)
    }

    private static func storePeriod(_ period: Product.SubscriptionPeriod, times: Int) -> StorePeriod? {
        let span: StorePeriod.Span
        switch period.unit {
        case .day: span = .day
        case .week: span = .week
        case .month: span = .month
        case .year: span = .year
        @unknown default: return nil
        }
        return StorePeriod(count: period.value * max(times, 1), unit: span)
    }

    /// What StoreKit says this Apple Account owns now, verified on the device.
    private static func currentEntitlement() async -> Entitlement {
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result,
                  transaction.revocationDate == nil,
                  let plan = productIDs.first(where: { $0.value == transaction.productID })?.key
            else { continue }
            if let expiry = transaction.expirationDate, expiry < .now { continue }

            var willRenew = true
            if let product = try? await Product.products(for: [transaction.productID]).first,
               let statuses = try? await product.subscription?.status {
                for status in statuses {
                    if case .verified(let renewal) = status.renewalInfo, renewal.currentProductID == transaction.productID {
                        willRenew = renewal.willAutoRenew
                    }
                }
            }
            let inTrial = transaction.offerType == .introductory
            return Entitlement(isPro: true, plan: plan, trialEndsAt: inTrial ? transaction.expirationDate : nil, willRenew: willRenew)
        }
        return .free
    }

    static let noPlans = "Plans couldn't be loaded from the App Store. Check your connection, then try again."
    static let purchaseFailed = "The purchase didn't go through. Try again in a moment."
    static let pending = "Your purchase is waiting for approval. Pro unlocks as soon as the App Store confirms it."
    static let nothingToRestore = "No MacroDime Pro subscription was found for the Apple Account on this device."
}
