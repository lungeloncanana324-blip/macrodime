//
//  DemoData.swift
//  MacroDime
//
//  Fills the store with a plausible day so the app can be screenshotted on a
//  simulator.
//
//  Why this exists: App Store screenshots cannot show an empty app, this app has
//  never been run, and there is no Mac or device to drive by hand. A CI runner
//  boots the simulator, launches with `-MacroDimeScreenshots`, and captures each
//  tab. The alternative was screenshots of the wizard, which is what a new user
//  sees first but not what the product does.
//
//  Nothing here runs in a normal launch: `installIfRequested` returns
//  immediately unless the argument is present.
//

import Foundation
import SwiftData

@MainActor
enum DemoData {

    /// Passed on the command line, for example:
    /// `xcrun simctl launch booted com.lungelo.macrodime -MacroDimeScreenshots`
    static let launchArgument = "-MacroDimeScreenshots"

    static var isRequested: Bool {
        ProcessInfo.processInfo.arguments.contains(launchArgument)
    }

    /// Lets a screenshot run open on a chosen tab, e.g. `-MacroDimeTab 2`. The
    /// simulator cannot be tapped from a script, so the tab is chosen at launch
    /// instead. Defaults to the first tab.
    static var initialTab: Int {
        let arguments = ProcessInfo.processInfo.arguments
        guard let flag = arguments.firstIndex(of: "-MacroDimeTab"),
              arguments.index(after: flag) < arguments.endIndex,
              let tab = Int(arguments[arguments.index(after: flag)])
        else { return 0 }
        return tab
    }

    static func installIfRequested(context: ModelContext) {
        guard isRequested else { return }
        do {
            let profile = try readyProfile(context: context)
            try installSampleDay(for: profile, context: context)
            try context.save()
        } catch {
            // A screenshot run that cannot seed still has to launch: a broken
            // demo day is a failed CI job, not a reason to fail on the device.
            print("MacroDime demo data failed: \(error)")
        }
    }

    /// A profile that skips onboarding, so the screenshots show the product.
    private static func readyProfile(context: ModelContext) throws -> UserProfile {
        let existing = try context.fetch(FetchDescriptor<UserProfile>())
        let profile: UserProfile
        if let found = existing.first {
            profile = found
        } else {
            profile = UserProfile()
            context.insert(profile)
        }

        profile.displayName = "Sam"
        profile.heightCm = 178
        profile.weightKg = 82
        profile.age = 31
        profile.sex = .male
        profile.goal = .fatLoss
        profile.activity = .moderatelyActive
        profile.budgetTier = .strict
        profile.dailyFoodBudget = 9
        profile.hasCompletedOnboarding = true
        profile.hasAcknowledgedHealthDisclaimer = true
        profile.touch()
        return profile
    }

    /// Three meals, a week of grocery lines, and one progress entry.
    private static func installSampleDay(for profile: UserProfile, context: ModelContext) throws {
        let day = Calendar.current.startOfDay(for: .now)

        let descriptor = FetchDescriptor<MealPlan>(predicate: #Predicate { $0.date == day })
        let plan: MealPlan
        if let found = try context.fetch(descriptor).first {
            plan = found
        } else {
            plan = MealPlan(date: day)
            context.insert(plan)
        }

        let foods = try context.fetch(FetchDescriptor<FoodItem>())
        let lookup = Dictionary(foods.map { ($0.catalogID, $0) }, uniquingKeysWith: { first, _ in first })

        let recipes: [(slot: MealSlot, name: String, items: [(String, Double)])] = [
            (.breakfast, "Oats, Eggs & Banana", [("rolled-oats", 1), ("eggs-large", 1.5), ("banana", 1)]),
            (.lunch, "Tuna Rice Bowl", [("canned-tuna-water", 1), ("white-rice", 1), ("frozen-broccoli", 1), ("olive-oil", 0.5)]),
            (.dinner, "Chicken Thighs & Potatoes", [("chicken-thighs", 1), ("potatoes", 1), ("frozen-mixed-vegetables", 1), ("olive-oil", 0.5)])
        ]

        for recipe in recipes {
            let meal: PlannedMeal
            if let found = plan.meal(for: recipe.slot) {
                meal = found
            } else {
                let created = PlannedMeal(name: recipe.name, slot: recipe.slot)
                created.plan = plan
                context.insert(created)
                plan.meals.append(created)
                meal = created
            }

            meal.name = recipe.name

            // Idempotent: a second launch on the same simulator must not double
            // every portion.
            for item in recipe.items {
                guard let food = lookup[item.0] else { continue }
                if let existing = meal.portions.first(where: { $0.food?.catalogID == item.0 }) {
                    existing.servings = item.1
                    continue
                }
                let portion = MealPortion(food: food, servings: item.1)
                portion.meal = meal
                context.insert(portion)
                meal.portions.append(portion)
            }
        }

        if profile.measurements.isEmpty {
            let measurement = BodyMeasurement(weightKg: profile.weightKg, waistCm: 88, notes: "Baseline")
            measurement.profile = profile
            context.insert(measurement)
            profile.measurements.append(measurement)
        }

        try GroceryListService.regenerate(weekContaining: day, context: context)
    }
}
