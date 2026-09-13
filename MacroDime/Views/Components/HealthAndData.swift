//
//  HealthAndData.swift
//  MacroDime
//
//  Two things App Review requires of an app like this one, and that a user is
//  owed regardless:
//
//  1. A plain statement that calorie targets are estimates, not medical advice
//     (Guideline 1.4.1 — physical-harm). It is acknowledged during onboarding
//     and remains readable afterwards in Settings.
//  2. A way to delete everything the app knows about you (Guideline 5.1.1(v)).
//     All data here is local, which makes deletion genuinely complete rather
//     than a request to a server.
//

import SwiftUI
import SwiftData

// MARK: - Disclaimer copy

enum HealthDisclaimer {

    /// One line, for the onboarding acknowledgement.
    static let short = "Estimates, not medical advice."

    /// The full statement. Written plainly on purpose: a disclaimer nobody can
    /// read protects nobody.
    static let body = """
        MacroDime estimates your energy needs with the Mifflin-St Jeor equation \
        and standard activity multipliers. These are population averages. Your \
        real metabolic rate can differ from the estimate by 10% or more, and no \
        formula can account for your medical history, medication, or training.

        Treat the targets as a starting point to adjust from, not a prescription \
        to obey.

        Talk to a doctor or registered dietitian before starting a calorie \
        deficit if you are pregnant or breastfeeding, managing diabetes, thyroid \
        or heart conditions, taking medication affecting appetite or metabolism, \
        or have any history of disordered eating.

        MacroDime is for adults. It is not a medical device, and it does not \
        diagnose, treat, or prevent any condition.

        Ingredient prices are approximate supermarket averages used to compare \
        ingredients against one another. They are not a quote, and they are not \
        converted to your local currency.
        """

    /// Shown beside the BMI figure, since BMI is the number most often misread.
    static let bmiCaveat = """
        BMI is a population statistic. It cannot tell muscle from fat, so a \
        muscular person often reads as "overweight". Track your waist and \
        photos alongside it.
        """
}

// MARK: - Onboarding acknowledgement

/// The disclaimer card on the final onboarding step, with the toggle that gates
/// the finish button.
struct HealthDisclaimerCard: View {
    @Binding var isAcknowledged: Bool
    @State private var isExpanded = false

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Label("Before you start", systemImage: "heart.text.square.fill")
                    .font(.headline)
                    .foregroundStyle(Brand.overBudget)

                Text(isExpanded ? HealthDisclaimer.body : String(HealthDisclaimer.body.prefix(180)) + "…")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .animation(.snappy, value: isExpanded)

                Button(isExpanded ? "Show less" : "Read the full statement") {
                    withAnimation { isExpanded.toggle() }
                }
                .font(.caption.weight(.medium))

                Divider()

                Toggle(isOn: $isAcknowledged) {
                    Text("I understand these are estimates, not medical advice.")
                        .font(.subheadline)
                }
                .tint(Brand.gold)
            }
        }
    }
}

// MARK: - Settings screen

/// Readable at any time from Settings, so the disclaimer is not a one-time
/// dialog the user tapped past during setup.
struct HealthAndSafetyView: View {
    var body: some View {
        List {
            Section {
                Text(HealthDisclaimer.body)
                    .font(.callout)
            } header: {
                Text("Health & safety")
            }

            Section("On BMI") {
                Text(HealthDisclaimer.bmiCaveat)
                    .font(.callout)
            }

            Section {
                Link(destination: URL(string: "https://www.nedic.ca/")!) {
                    Label("Eating disorder support (NEDIC)", systemImage: "lifepreserver.fill")
                }
            } footer: {
                Text("If tracking food is making your relationship with eating worse, stop and talk to someone.")
            }
        }
        .navigationTitle("Health & Safety")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Data deletion

/// Deletes everything the app stores. All of it is on-device, so this is
/// complete rather than a request to a server.
@MainActor
enum DataManagement {

    /// Removes every record the user created. The curated ingredient catalogue
    /// survives — it ships with the app and is not the user's data.
    static func deleteAllUserData(context: ModelContext) throws {
        // Children first. The cascade rules would handle this, but being
        // explicit means the operation does not depend on relationship
        // configuration being right.
        try context.delete(model: GroceryItem.self)
        try context.delete(model: MealPortion.self)
        try context.delete(model: PlannedMeal.self)
        try context.delete(model: MealPlan.self)
        try context.delete(model: BodyMeasurement.self)
        try context.delete(model: UserProfile.self)
        try context.delete(model: FoodItem.self, where: #Predicate { $0.isUserCreated })
        try context.save()
    }

    /// What deletion covers, listed for the confirmation dialog so the user is
    /// told what they are about to lose.
    static let summary = """
        This erases your profile and targets, every planned meal, all grocery \
        lists, and all measurements and progress photos.

        Everything is stored on this device only, so this cannot be undone and \
        there is no backup to restore from.
        """
}

/// The destructive row in Settings, with a two-step confirmation.
struct DeleteAllDataButton: View {

    @Environment(\.modelContext) private var context
    @State private var isConfirming = false
    @State private var errorMessage: String?

    var body: some View {
        Button(role: .destructive) {
            isConfirming = true
        } label: {
            Label("Delete All My Data", systemImage: "trash.fill")
        }
        .confirmationDialog(
            "Delete everything?",
            isPresented: $isConfirming,
            titleVisibility: .visible
        ) {
            Button("Delete Everything", role: .destructive, action: deleteEverything)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(DataManagement.summary)
        }
        .alert("Could not delete", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func deleteEverything() {
        do {
            try DataManagement.deleteAllUserData(context: context)
            // No navigation needed: `RootView` watches the profile query, so
            // removing it returns the app to onboarding on its own.
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
