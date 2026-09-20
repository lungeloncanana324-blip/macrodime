//
//  DietaryFilter.swift
//  MacroDime
//
//  One gate between the catalogue and everything that plans food.
//
//  Pure and static: given a food and a `DietaryProfile`, either it is allowed or
//  it is rejected along with the reason. The reason matters as much as the
//  verdict, because both the onboarding summary and the plan audit have to
//  explain *why* the plan looks the way it does ("6 ingredients dropped: 4
//  contain dairy"), and an unexplained exclusion looks like a bug to the user.
//
//  There is one failure mode worth naming. A filter is only as good as its tags:
//  a food with no traits is treated as plant-only and unrestricted, so a missing
//  trait means an unsafe suggestion rather than a missing one. Catalogue tests
//  therefore check the tags directly (see `DietaryFilterTests`).
//

import Foundation

enum DietaryFilter {

    /// Why a food was taken out of the running.
    enum Rejection: Hashable, Sendable {
        case pattern(DietaryPattern)
        case exclusion(FoodExclusion)
        case blockedByUser
        case tooMuchPreparation(PrepEffort, minutes: Int)

        /// Plain-language reason, safe to show a user.
        var reason: String {
            switch self {
            case .pattern(let pattern):
                "not \(pattern.displayName.lowercased())"
            case .exclusion(let exclusion):
                exclusion.exclusionReason
            case .blockedByUser:
                "on your avoid list"
            case .tooMuchPreparation(let effort, let minutes):
                "takes \(minutes) min, and you asked for \(effort.displayName.lowercased())"
            }
        }

        /// A short label used to group rejections in counts.
        var label: String {
            switch self {
            case .pattern(let pattern): "Not \(pattern.displayName.lowercased())"
            case .exclusion(let exclusion): "Contains \(exclusion.displayName.lowercased())"
            case .blockedByUser: "On your avoid list"
            case .tooMuchPreparation(let effort, _): "Over your \(effort.displayName.lowercased()) limit"
            }
        }
    }

    /// The first reason this food cannot be suggested, or `nil` if it can.
    ///
    /// Order is deliberate: what a food *is* outranks how long it takes, and a
    /// food the user has explicitly banned is reported as banned rather than as
    /// a pattern violation, because the user's own reason is the useful one.
    static func rejection(for food: FoodSnapshot, under profile: DietaryProfile) -> Rejection? {
        if profile.blockedFoodIDs.contains(food.id) { return .blockedByUser }

        if let exclusion = profile.exclusions.first(where: { food.traits.contains($0.trait) }) {
            return .exclusion(exclusion)
        }

        guard profile.pattern.allows(food.traits) else {
            return .pattern(profile.pattern)
        }

        if food.prepMinutes > profile.prepEffort.maximumMinutes {
            return .tooMuchPreparation(profile.prepEffort, minutes: food.prepMinutes)
        }

        return nil
    }

    static func allows(_ food: FoodSnapshot, under profile: DietaryProfile) -> Bool {
        rejection(for: food, under: profile) == nil
    }

    /// The catalogue as this user may eat it. Ingredient order is preserved.
    static func allowed(_ foods: [FoodSnapshot], under profile: DietaryProfile) -> [FoodSnapshot] {
        foods.filter { allows($0, under: profile) }
    }

    static func rejected(_ foods: [FoodSnapshot], under profile: DietaryProfile) -> [FoodSnapshot] {
        foods.filter { !allows($0, under: profile) }
    }

    /// Counts of rejections by reason, biggest first. Used for the "what your
    /// restrictions cost you" line in the onboarding summary and the audit.
    static func rejectionBreakdown(
        _ foods: [FoodSnapshot],
        under profile: DietaryProfile
    ) -> [(label: String, count: Int)] {
        var counts: [String: Int] = [:]
        for food in foods {
            guard let rejection = rejection(for: food, under: profile) else { continue }
            counts[rejection.label, default: 0] += 1
        }
        return counts
            .map { (label: $0.key, count: $0.value) }
            .sorted { ($0.count, $1.label) > ($1.count, $0.label) }
    }

    /// Everything in one category this user may eat, cheapest first. The
    /// feasibility check asks this question about protein anchors.
    static func allowed(
        in category: FoodCategory,
        foods: [FoodSnapshot],
        under profile: DietaryProfile
    ) -> [FoodSnapshot] {
        allowed(foods, under: profile)
            .filter { $0.category == category }
            .sorted { $0.costPerServing < $1.costPerServing }
    }
}
