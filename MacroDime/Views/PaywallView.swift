//
//  PaywallView.swift
//  MacroDime
//
//  MacroDime Pro on iPhone: the paywall, what a locked tab shows, and the two
//  cards Today shows around it. Every word and figure comes from Paywall and
//  EntitlementPolicy in Domain/Subscription.swift, tested there; these views
//  only lay it out. Twin of the Android ui/paywall/Paywall.kt.
//
//  Apple asks a subscription paywall for the title, length and price, and
//  working links to the Terms of Use and the privacy policy; Restore is
//  required. All of it is here.
//

import SwiftUI
import StoreKit

private enum ProLinks {
    // Literal, known-good addresses: a failed parse would only hide the link.
    static let privacyPolicy = URL(string: "https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html")
    static let termsOfUse = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")
}

/// The person's own numbers, as the paywall quotes them.
private struct PersonalNumbers {
    let calories: String
    let protein: String
    let allowance: String

    init(profile: UserProfile, prices: CurrencySettings) {
        let targets = profile.prescription.targets
        calories = DisplayFormat.calories(targets.calories)
        protein = DisplayFormat.grams(targets.protein)
        allowance = prices.format(profile.dailyFoodBudget)
    }
}

// MARK: - Paywall

/// Full screen. Closes itself once StoreKit confirms Pro, and on "Not now",
/// which is always there.
@MainActor
struct PaywallView: View {
    @Environment(SubscriptionStore.self) private var subscriptions
    @Environment(\.currency) private var prices

    let profile: UserProfile
    let reason: PaywallReason
    let savedSoFarUSD: Double
    let onClose: () -> Void

    @State private var chosen: ProPlan = .annual

    /// The yearly plan unless the store does not sell it, so the button is never dead.
    private var selected: ProPlan {
        subscriptions.offers[chosen] != nil ? chosen : (subscriptions.leadOffer?.plan ?? chosen)
    }

    private var offer: ProOffer? { subscriptions.offers[selected] }

    var body: some View {
        let numbers = PersonalNumbers(profile: profile, prices: prices)
        let savedSoFar = !subscriptions.isPro && savedSoFarUSD >= 0.01 ? prices.format(savedSoFarUSD) : nil
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 10) {
                        ProBadge()
                        Text(Paywall.headline(reason, savedSoFar: savedSoFar, dailyAllowance: numbers.allowance))
                            .font(.largeTitle.weight(.semibold))
                        Text(Paywall.subheadline(calories: numbers.calories, protein: numbers.protein, dailyAllowance: numbers.allowance))
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    }
                    CardContainer {
                        VStack(alignment: .leading, spacing: 14) {
                            ForEach(Paywall.benefits, id: \.title) { BenefitRow(benefit: $0) }
                        }
                    }
                    plans
                    if let offer, offer.freeTrial != nil {
                        timeline(offer)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        if let offer {
                            Text(Paywall.disclosure(offer, store: subscriptions.store))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        HStack(spacing: 16) {
                            if let url = ProLinks.termsOfUse { Link("Terms of Use", destination: url) }
                            if let url = ProLinks.privacyPolicy { Link("Privacy policy", destination: url) }
                        }
                        .font(.footnote)
                    }
                }
                .padding(20)
                .frame(maxWidth: 620)
                .frame(maxWidth: .infinity)
            }
            .background(Color(.systemGroupedBackground))
            .safeAreaInset(edge: .bottom) { footer }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", systemImage: "xmark", action: onClose)
                }
                if subscriptions.availability == .ready {
                    ToolbarItem(placement: .primaryAction) {
                        Button("Restore") { Task { await subscriptions.restore() } }
                    }
                }
            }
        }
        // Only a purchase made here closes it: Pro already, on opening, is not news.
        .onChange(of: subscriptions.isPro) { _, isPro in
            if isPro { onClose() }
        }
    }

    @ViewBuilder
    private var plans: some View {
        let ordered = ProPlan.allCases.compactMap { subscriptions.offers[$0] }
        if !ordered.isEmpty {
            VStack(spacing: 10) {
                ForEach(ordered, id: \.plan) { offer in
                    PlanOption(card: Paywall.card(offer, monthly: subscriptions.offers[.monthly]), isSelected: offer.plan == selected) {
                        chosen = offer.plan
                    }
                }
            }
        } else if subscriptions.availability == .connecting {
            HStack(spacing: 12) {
                ProgressView()
                Text("Loading prices from the App Store").font(.subheadline)
            }
        } else {
            CardContainer {
                VStack(alignment: .leading, spacing: 10) {
                    Text(SubscriptionStore.noPlans).font(.subheadline)
                    Button("Try again") { Task { await subscriptions.refresh() } }
                }
            }
        }
    }

    private func timeline(_ offer: ProOffer) -> some View {
        let icons = ["lock.open.fill", "calendar", "arrow.triangle.2.circlepath"]
        let steps = Paywall.timeline(offer, store: subscriptions.store)
        return CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text("How the trial works").font(.headline)
                ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: icons[min(index, icons.count - 1)])
                            .foregroundStyle(Brand.gold)
                            .frame(width: 22)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(step.title).font(.subheadline.weight(.semibold))
                            Text(step.detail).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private var footer: some View {
        VStack(spacing: 6) {
            if let message = subscriptions.message {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Brand.carbs)
                    Text(message).font(.footnote)
                    Spacer(minLength: 4)
                    Button("Dismiss", systemImage: "xmark") { subscriptions.message = nil }
                        .labelStyle(.iconOnly)
                        .buttonStyle(.plain)
                }
            }
            if offer != nil || subscriptions.availability == .connecting {
                Button {
                    if let offer { Task { await subscriptions.purchase(offer.plan) } }
                } label: {
                    Group {
                        if subscriptions.isPurchasing {
                            ProgressView()
                        } else {
                            Text(offer.map(Paywall.callToAction) ?? "Loading plans").font(.headline)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 34)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(offer == nil || subscriptions.isPurchasing || subscriptions.availability != .ready)
            }
            Button("Not now", action: onClose)
                .font(.subheadline.weight(.medium))
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .background(.bar)
    }
}

/// One plan as a choice. The yearly one names its trial and its saving; the monthly one claims neither.
private struct PlanOption: View {
    let card: Paywall.PlanCard
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Brand.gold : Color.secondary)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(card.title).font(.headline)
                        if let badge = card.badge {
                            Text(badge)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Brand.underBudget)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(Brand.underBudget.opacity(0.16), in: Capsule())
                        }
                    }
                    if let trial = card.trial {
                        Text(trial).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.gold)
                    }
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(card.price).font(.subheadline.weight(.semibold))
                    if let perMonth = card.perMonth {
                        Text(perMonth).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .padding(16)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(isSelected ? Brand.gold : Color.secondary.opacity(0.25), lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

private struct BenefitRow: View {
    let benefit: Paywall.Benefit

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.gold)
            VStack(alignment: .leading, spacing: 2) {
                Text(benefit.title).font(.body.weight(.medium))
                Text(benefit.detail).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}

private struct ProBadge: View {
    var body: some View {
        Label(Paywall.proName, systemImage: "star.circle.fill")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Brand.gold)
    }
}

// MARK: - Around the paywall

/// What a locked tab shows: the feature in the person's own numbers, and a way in.
@MainActor
struct ProLockedView: View {
    @Environment(SubscriptionStore.self) private var subscriptions
    @Environment(\.currency) private var prices

    let title: String
    let reason: PaywallReason
    let profile: UserProfile
    let onUnlock: () -> Void

    var body: some View {
        let numbers = PersonalNumbers(profile: profile, prices: prices)
        NavigationStack {
            ScrollView {
                CardContainer {
                    VStack(alignment: .leading, spacing: 14) {
                        ProBadge()
                        Text(Paywall.headline(reason, savedSoFar: nil, dailyAllowance: numbers.allowance))
                            .font(.title2.weight(.semibold))
                        Text(Paywall.lockedDetail(reason, calories: numbers.calories, protein: numbers.protein))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        ForEach(Paywall.benefits, id: \.title) { BenefitRow(benefit: $0) }
                        UnlockButton(onUnlock: onUnlock)
                        Text(Paywall.unlockCaption(subscriptions.store))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(16)
                .frame(maxWidth: 620)
                .frame(maxWidth: .infinity)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(title)
        }
    }
}

/// Today, for a free account: what the targets above it would get, planned.
@MainActor
struct UpgradeCard: View {
    @Environment(\.currency) private var prices
    let profile: UserProfile
    let onUpgrade: () -> Void

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Label(Paywall.upgradeTitle(dailyAllowance: prices.format(profile.dailyFoodBudget)), systemImage: "sparkles")
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(Paywall.upgradeDetail).font(.subheadline).foregroundStyle(.secondary)
                UnlockButton(onUnlock: onUpgrade)
            }
        }
    }
}

/// The last days of a trial: what happens next, plainly, with the way to cancel one tap away.
@MainActor
struct TrialReminderCard: View {
    @Environment(SubscriptionStore.self) private var subscriptions
    @State private var isManaging = false

    var body: some View {
        if let days = EntitlementPolicy.trialDaysLeft(subscriptions.entitlement) {
            CardContainer {
                VStack(alignment: .leading, spacing: 10) {
                    Label(EntitlementPolicy.reminderTitle(daysLeft: days), systemImage: "calendar.badge.clock")
                        .font(.headline)
                    Text(EntitlementPolicy.reminderDetail(
                        subscriptions.entitlement,
                        offer: subscriptions.offers[subscriptions.entitlement.plan ?? .annual],
                        store: subscriptions.store
                    ))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    Button("Manage subscription") { isManaging = true }
                        .buttonStyle(.bordered)
                }
            }
            .manageSubscriptionsSheet(isPresented: $isManaging)
        }
    }
}

@MainActor
private struct UnlockButton: View {
    @Environment(SubscriptionStore.self) private var subscriptions
    let onUnlock: () -> Void

    var body: some View {
        Button(action: onUnlock) {
            Text(subscriptions.leadOffer.map(Paywall.callToAction) ?? "See Pro plans")
                .font(.headline)
                .frame(maxWidth: .infinity, minHeight: 30)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
    }
}
