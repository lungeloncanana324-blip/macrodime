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
        .animation(.smooth, value: profile?.hasCompletedOnboarding)
    }
}

// MARK: - Tabs

@MainActor
struct MainTabView: View {

    let profile: UserProfile

    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Today", systemImage: "chart.pie.fill") }

            MealPlannerView()
                .tabItem { Label("Plan", systemImage: "fork.knife") }

            GroceryListView()
                .tabItem { Label("Groceries", systemImage: "cart.fill") }

            SettingsView(profile: profile)
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
    }
}

// MARK: - Settings

/// Profile review and re-entry into the onboarding wizard.
@MainActor
struct SettingsView: View {

    @Environment(\.modelContext) private var context
    let profile: UserProfile

    @State private var isEditingProfile = false

    var body: some View {
        NavigationStack {
            List {
                Section("Your plan") {
                    LabeledContent("Goal", value: profile.goal.displayName)
                    LabeledContent("Activity", value: profile.activity.displayName)
                    LabeledContent("Budget tier", value: profile.budgetTier.displayName)
                    LabeledContent("Daily allowance", value: DisplayFormat.currency(profile.dailyFoodBudget))
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

                Section {
                    Button("Edit profile", systemImage: "pencil") { isEditingProfile = true }
                } footer: {
                    Text("Your targets are recalculated from your current weight every time it changes.")
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
