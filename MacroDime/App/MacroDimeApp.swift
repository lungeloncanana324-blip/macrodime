//
//  MacroDimeApp.swift
//  MacroDime
//
//  Composition root: builds the SwiftData container, seeds the curated food
//  catalogue, and decides between onboarding and the main tab shell.
//

import SwiftUI
import SwiftData

@main
struct MacroDimeApp: App {

    /// Every persisted type, listed explicitly. SwiftData would infer the
    /// related models from the relationships, but naming them keeps the schema
    /// visible in one place and makes a missing model a compile-time question
    /// rather than a runtime surprise.
    private static let schema = Schema([
        UserProfile.self,
        BodyMeasurement.self,
        FoodItem.self,
        MealPlan.self,
        PlannedMeal.self,
        MealPortion.self,
        GroceryItem.self
    ])

    private let container: ModelContainer

    init() {
        do {
            container = try ModelContainer(
                for: Self.schema,
                configurations: ModelConfiguration(schema: Self.schema, isStoredInMemoryOnly: false)
            )
        } catch {
            // There is no meaningful recovery from a container that will not
            // open: every screen depends on it. Failing loudly in development
            // beats a silently empty app in production.
            fatalError("Could not create the model container: \(error)")
        }

        CatalogSeeder.seedIfNeeded(context: container.mainContext)

        // Fills a workable day when the process was launched asking for
        // screenshot data. Returns immediately otherwise.
        DemoData.installIfRequested(context: container.mainContext)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .brandAccent()
        }
        .modelContainer(container)
    }
}

// MARK: - Root

/// Gates onboarding, then hosts the three main tabs.
@MainActor
struct RootView: View {

    @Environment(\.modelContext) private var context
    @Query private var profiles: [UserProfile]

    private var profile: UserProfile? { profiles.first }

    var body: some View {
        Group {
            if let profile, profile.hasCompletedOnboarding {
                MainTabView(profile: profile)
            } else {
                OnboardingView(existingProfile: profile)
                    .transition(.opacity)
            }
        }
        // Money is shown in one currency at a time, and the profile decides which.
        // Injected at the root so no screen has to know the rule, and so a screen
        // that forgets to ask still shows a true dollar amount rather than a local
        // symbol on a dollar figure.
        .environment(\.currency, profile?.currency ?? .usd)
        .animation(.smooth, value: profile?.hasCompletedOnboarding)
    }
}

// MARK: - Tabs

@MainActor
struct MainTabView: View {

    let profile: UserProfile

    /// Which tab is showing. Seeded from the launch arguments so a screenshot
    /// run can open on a specific tab: a simulator cannot be tapped from a
    /// script, and the store needs a shot of each screen.
    @State private var selection: Int = DemoData.initialTab

    var body: some View {
        TabView(selection: $selection) {
            DashboardView()
                .tabItem { Label("Today", systemImage: "chart.pie.fill") }
                .tag(0)

            MealPlannerView()
                .tabItem { Label("Plan", systemImage: "fork.knife") }
                .tag(1)

            GroceryListView()
                .tabItem { Label("Groceries", systemImage: "cart.fill") }
                .tag(2)

            SettingsView(profile: profile)
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(3)
        }
    }
}

// MARK: - Settings

/// Profile review and re-entry into the onboarding wizard.
@MainActor
struct SettingsView: View {
    /// How money is shown here: which currency, and at what rate.
    @Environment(\.currency) private var prices


    @Environment(\.modelContext) private var context
    let profile: UserProfile

    @State private var isEditingProfile = false

    // MARK: Food rules

    /// What the plan is allowed to suggest, in one place. Read-only here, with
    /// the editor one tap away, so a user can see why an ingredient is missing
    /// without hunting for the reason.
    private var foodRulesSection: some View {
        let diet = profile.dietaryProfile
        let removed = DietaryFilter.rejected(FoodCatalog.all, under: diet).count

        return Section {
            LabeledContent("Pattern", value: diet.pattern.displayName)
            LabeledContent("Meals a day", value: diet.schedule.displayName)
            LabeledContent("Cooking", value: diet.prepEffort.displayName)

            if !diet.exclusions.isEmpty {
                LabeledContent(
                    "Avoiding",
                    value: diet.exclusions.map(\.displayName).sorted().joined(separator: ", ")
                )
            }
            if !diet.blockedFoodIDs.isEmpty {
                LabeledContent(
                    "Blocked",
                    value: "\(diet.blockedFoodIDs.count) food\(diet.blockedFoodIDs.count == 1 ? "" : "s")"
                )
            }
            if diet.mealsOutPerWeek > 0 {
                LabeledContent("Eating out", value: "\(diet.mealsOutPerWeek) a week")
            }
        } header: {
            Text("Food rules")
        } footer: {
            Text(
                removed == 0
                    ? "Nothing is excluded, so every ingredient in the catalogue is available to your plan."
                    : "\(removed) of \(FoodCatalog.all.count) ingredients are excluded from your plan by these rules."
            )
        }
    }

    // MARK: Currency

    /// The fix for a real bug. Prices in the catalogue are US supermarket
    /// averages, and the app used to print those amounts with whatever symbol
    /// the device locale named: a rand sign on a dollar figure. Showing a
    /// different currency is a conversion at a rate the user supplies, never a
    /// relabelling of the number.
    private var currencySection: some View {
        Section {
            Picker("Show prices in", selection: currencyCodeBinding) {
                ForEach(currencyOptions, id: \.self) { code in
                    Text(code).tag(code)
                }
            }

            LabeledContent("Rate") {
                HStack(spacing: 6) {
                    Text("1 USD =").foregroundStyle(.secondary)
                    TextField(
                        "Rate",
                        value: rateBinding,
                        format: .number.precision(.fractionLength(0...4))
                    )
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 110)
                    Text(profile.currencyCode).foregroundStyle(.secondary)
                }
            }

            if profile.currency.isConverting {
                LabeledContent(
                    "Allowance shows as",
                    value: profile.currency.format(profile.dailyFoodBudget)
                )
            }
        } header: {
            Text("Currency")
        } footer: {
            Text(PriceBook.rateDisclaimer)
        }
    }

    private var currencyCodeBinding: Binding<String> {
        Binding(
            get: { profile.currencyCode },
            set: { newValue in
                profile.currencyCode = newValue
                profile.touch()
                try? context.save()
            }
        )
    }

    /// Typed by the user, never fetched. There is no exchange-rate feed in this
    /// app, and inventing one would put a number on screen that nobody could
    /// check.
    private var rateBinding: Binding<Double> {
        Binding(
            get: { profile.currencyUnitsPerUSD },
            set: { newValue in
                guard Money.isUsable(newValue) else { return }
                profile.currencyUnitsPerUSD = newValue
                profile.touch()
                try? context.save()
            }
        )
    }


    var body: some View {
        NavigationStack {
            List {
                Section("Your plan") {
                    LabeledContent("Goal", value: profile.goal.displayName)
                    LabeledContent("Activity", value: profile.activity.displayName)
                    LabeledContent("Budget tier", value: profile.budgetTier.displayName)
                    LabeledContent("Daily allowance", value: prices.format(profile.dailyFoodBudget))
                }

                Section("Targets") {
                    let prescription = profile.prescription
                    LabeledContent("BMR", value: DisplayFormat.calories(prescription.bmr))
                    LabeledContent("TDEE", value: DisplayFormat.calories(prescription.tdee))
                    LabeledContent("Calories", value: DisplayFormat.calories(prescription.targets.calories))
                    LabeledContent("Protein", value: DisplayFormat.grams(prescription.targets.protein))
                    LabeledContent("Carbs", value: DisplayFormat.grams(prescription.targets.carbs))
                    LabeledContent("Fat", value: DisplayFormat.grams(prescription.targets.fat))
                }

                foodRulesSection
                currencySection

                Section {
                    Button("Edit profile", systemImage: "pencil") { isEditingProfile = true }
                } footer: {
                    Text("Your targets are recalculated from your current weight every time it changes. Changing what you eat re-filters the ingredient catalogue straight away.")
                }

                Section {
                    NavigationLink {
                        HealthAndSafetyView()
                    } label: {
                        Label("Health & Safety", systemImage: "heart.text.square.fill")
                    }
                } footer: {
                    Text(HealthDisclaimer.short)
                }

                Section {
                    DeleteAllDataButton()
                } header: {
                    Text("Your data")
                } footer: {
                    Text("Everything MacroDime stores stays on this device. Nothing is uploaded, and there is no account.")
                }
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $isEditingProfile) {
                OnboardingView(existingProfile: profile) { _ in
                    isEditingProfile = false
                }
            }
        }
    }
}
