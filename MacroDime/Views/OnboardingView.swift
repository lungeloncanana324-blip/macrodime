//
//  OnboardingView.swift
//  MacroDime
//
//  Multi-step wizard: body metrics → goal → activity → budget → live summary.
//
//  The summary step is the point of the whole flow — the user sees their real
//  TDEE, targets and daily cost estimate *before* committing, so the numbers
//  feel earned rather than assigned.
//

import SwiftUI
import SwiftData

@MainActor
struct OnboardingView: View {

    @Environment(\.modelContext) private var context
    @State private var model = UserProfileViewModel()
    @State private var saveError: String?

    /// Existing profile when re-editing from Settings; `nil` during first run.
    var existingProfile: UserProfile?
    var onComplete: (UserProfile) -> Void = { _ in }

    /// Writable so dismissing the alert actually clears the error — a
    /// `.constant` binding leaves SwiftUI unable to lower the flag itself.
    private var isShowingSaveError: Binding<Bool> {
        Binding(
            get: { saveError != nil },
            set: { if !$0 { saveError = nil } }
        )
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                progressHeader

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        header
                        stepContent
                    }
                    .padding(20)
                    .frame(maxWidth: 560, alignment: .leading)
                    .frame(maxWidth: .infinity)
                }
                .scrollDismissesKeyboard(.interactively)

                footer
            }
            .background(Color(.systemGroupedBackground))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !model.isFirstStep {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Back", systemImage: "chevron.left") { withAnimation { model.goBack() } }
                            .labelStyle(.titleOnly)
                    }
                }
            }
            .alert("Could not save", isPresented: isShowingSaveError) {
                Button("OK", role: .cancel) { saveError = nil }
            } message: {
                Text(saveError ?? "")
            }
            .onAppear {
                if let existingProfile {
                    model.load(from: existingProfile)
                    model.step = .bodyMetrics
                }
            }
        }
    }

    // MARK: Chrome

    private var progressHeader: some View {
        ProgressView(value: model.progress)
            .progressViewStyle(.linear)
            .tint(Brand.gold)
            .padding(.horizontal, 20)
            .padding(.bottom, 4)
            .accessibilityLabel("Setup progress")
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(model.step.title)
                .font(.largeTitle.bold())
            Text(model.step.subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var stepContent: some View {
        switch model.step {
        case .welcome: welcomeStep
        case .bodyMetrics: bodyMetricsStep
        case .goal: goalStep
        case .activity: activityStep
        case .budget: budgetStep
        case .summary: summaryStep
        }
    }

    private var footer: some View {
        VStack(spacing: 12) {
            if let error = model.validationError, model.step == .bodyMetrics {
                Label(error.message, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                if model.isLastStep {
                    commit()
                } else {
                    withAnimation { model.advance() }
                }
            } label: {
                Text(model.isLastStep ? "Start Planning" : "Continue")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!model.canAdvance)
        }
        .padding(20)
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
        .background(.bar)
    }

    // MARK: Steps

    private var welcomeStep: some View {
        VStack(alignment: .leading, spacing: 20) {
            ForEach(Self.welcomePoints, id: \.title) { point in
                Label {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(point.title).font(.headline)
                        Text(point.detail)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: point.icon)
                        .font(.title2)
                        .foregroundStyle(Brand.gold)
                        .frame(width: 34)
                }
            }
        }
    }

    private static let welcomePoints: [(title: String, detail: String, icon: String)] = [
        ("Targets from real science",
         "Mifflin-St Jeor BMR, your activity multiplier, and a protein target set from your body weight.",
         "function"),
        ("Priced before you shop",
         "Every meal carries an estimated cost, checked against a daily allowance you set.",
         "cart.fill"),
        ("Low-cost swaps",
         "Swap an expensive ingredient for a budget one that keeps your macros within 10%.",
         "arrow.triangle.2.circlepath"),
        ("Progress beyond BMI",
         "Waist measurements and photos, because BMI cannot tell muscle from fat.",
         "camera.fill")
    ]

    private var bodyMetricsStep: some View {
        VStack(spacing: 16) {
            CardContainer {
                VStack(alignment: .leading, spacing: 16) {
                    Picker("Units", selection: $model.measurementSystem) {
                        ForEach(MeasurementSystem.allCases) { system in
                            Text(system.displayName).tag(system)
                        }
                    }
                    .pickerStyle(.segmented)

                    LabeledContent("Name") {
                        TextField("Optional", text: $model.displayName)
                            .multilineTextAlignment(.trailing)
                            .textInputAutocapitalization(.words)
                    }

                    Divider()
                    heightField
                    Divider()
                    weightField
                    Divider()

                    Stepper(value: $model.age, in: 13...100) {
                        LabeledContent("Age", value: "\(model.age)")
                    }

                    Divider()

                    Picker("Biological sex", selection: $model.sex) {
                        ForEach(BiologicalSex.allCases) { sex in
                            Text(sex.displayName).tag(sex)
                        }
                    }
                    .pickerStyle(.segmented)

                    Text("Biological sex is used only as a term in the BMR equation.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            livePreviewStrip
        }
    }

    @ViewBuilder
    private var heightField: some View {
        switch model.measurementSystem {
        case .metric:
            LabeledContent("Height") {
                HStack(spacing: 4) {
                    TextField(
                        "cm",
                        value: Binding(
                            get: { model.heightCm },
                            set: { model.heightCm = $0 }
                        ),
                        format: .number.precision(.fractionLength(0))
                    )
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 90)
                    Text("cm").foregroundStyle(.secondary)
                }
            }
        case .imperial:
            LabeledContent("Height") {
                HStack(spacing: 10) {
                    Picker("Feet", selection: $model.heightFeet) {
                        ForEach(3...7, id: \.self) { Text("\($0) ft").tag($0) }
                    }
                    Picker("Inches", selection: $model.heightInches) {
                        ForEach(0...11, id: \.self) { Text("\($0) in").tag($0) }
                    }
                }
                .pickerStyle(.menu)
            }
        }
    }

    @ViewBuilder
    private var weightField: some View {
        LabeledContent("Weight") {
            HStack(spacing: 4) {
                switch model.measurementSystem {
                case .metric:
                    TextField(
                        "kg",
                        value: Binding(get: { model.weightKg }, set: { model.weightKg = $0 }),
                        format: .number.precision(.fractionLength(1))
                    )
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 90)
                    Text("kg").foregroundStyle(.secondary)
                case .imperial:
                    TextField(
                        "lb",
                        value: Binding(get: { model.weightPounds }, set: { model.weightPounds = $0 }),
                        format: .number.precision(.fractionLength(1))
                    )
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 90)
                    Text("lb").foregroundStyle(.secondary)
                }
            }
        }
    }

    private var goalStep: some View {
        VStack(spacing: 12) {
            ForEach(FitnessGoal.allCases) { goal in
                SelectableRow(
                    title: goal.displayName,
                    subtitle: goal.subtitle,
                    systemImage: goal.systemImage,
                    isSelected: model.goal == goal
                ) {
                    withAnimation(.snappy) { model.goal = goal }
                }
            }
            livePreviewStrip
        }
    }

    private var activityStep: some View {
        VStack(spacing: 12) {
            ForEach(ActivityLevel.allCases) { level in
                SelectableRow(
                    title: level.displayName,
                    subtitle: "\(level.subtitle) · ×\(level.multiplier.formatted(.number.precision(.fractionLength(3))))",
                    systemImage: level.systemImage,
                    isSelected: model.activity == level
                ) {
                    withAnimation(.snappy) { model.activity = level }
                }
            }
            livePreviewStrip
        }
    }

    private var budgetStep: some View {
        VStack(spacing: 16) {
            ForEach(BudgetTier.allCases) { tier in
                SelectableRow(
                    title: "\(tier.displayName) (\(tier.priceSymbol))",
                    subtitle: tier.subtitle,
                    systemImage: tier == .strict ? "banknote.fill" : "basket.fill",
                    isSelected: model.budgetTier == tier
                ) {
                    withAnimation(.snappy) { model.selectBudgetTier(tier) }
                }
            }

            CardContainer {
                VStack(alignment: .leading, spacing: 12) {
                    LabeledContent("Daily food allowance") {
                        Text(DisplayFormat.currency(model.dailyFoodBudget))
                            .font(.headline)
                            .monospacedDigit()
                    }

                    Slider(
                        value: Binding(
                            get: { model.dailyFoodBudget },
                            set: { model.budgetWasEdited(to: $0) }
                        ),
                        in: 3...60,
                        step: 0.50
                    )
                    .tint(Brand.gold)

                    Text("About \(DisplayFormat.currency(model.weeklyBudget)) a week.")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if let warning = model.budgetWarning {
                        Label(warning, systemImage: "exclamationmark.triangle.fill")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }
            }
        }
    }

    private var summaryStep: some View {
        let prescription = model.prescription

        return VStack(spacing: 16) {
            CardContainer {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Daily targets")
                        .font(.headline)

                    HStack(spacing: 12) {
                        MacroRingStat(
                            axis: .calories,
                            consumed: prescription.targets.calories,
                            target: prescription.targets.calories
                        )
                        MacroRingStat(
                            axis: .protein,
                            consumed: prescription.targets.protein,
                            target: prescription.targets.protein
                        )
                        MacroRingStat(
                            axis: .carbs,
                            consumed: prescription.targets.carbs,
                            target: prescription.targets.carbs
                        )
                        MacroRingStat(
                            axis: .fat,
                            consumed: prescription.targets.fat,
                            target: prescription.targets.fat
                        )
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            CardContainer {
                VStack(alignment: .leading, spacing: 14) {
                    Text("How we got there")
                        .font(.headline)

                    calculationRow("BMI", DisplayFormat.number(prescription.bmi, decimals: 1), prescription.bmiCategory.displayName)
                    calculationRow("BMR (Mifflin-St Jeor)", DisplayFormat.calories(prescription.bmr), "At complete rest")
                    calculationRow("TDEE", DisplayFormat.calories(prescription.tdee), "BMR × \(model.activity.multiplier.formatted())")
                    calculationRow(
                        prescription.isDeficit ? "Deficit" : "Surplus",
                        DisplayFormat.calories(abs(prescription.calorieDelta)),
                        "\(DisplayFormat.percent(abs(1 - model.goal.calorieMultiplier))) \(prescription.isDeficit ? "below" : "above") maintenance"
                    )
                    calculationRow(
                        "Protein",
                        DisplayFormat.grams(prescription.targets.protein),
                        "\(DisplayFormat.number(prescription.proteinGramsPerKilogram(weightKg: model.weightKg), decimals: 1)) g per kg body weight"
                    )

                    if !prescription.adjustments.isEmpty {
                        Divider()
                        ForEach(prescription.adjustments) { adjustment in
                            Label(adjustment.message, systemImage: "info.circle.fill")
                                .font(.caption)
                                .foregroundStyle(.orange)
                        }
                    }
                }
            }

            CardContainer {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Your budget")
                        .font(.headline)

                    HStack {
                        StatTile(
                            title: "Per day",
                            value: DisplayFormat.currency(model.dailyFoodBudget),
                            caption: model.budgetTier.displayName,
                            systemImage: "calendar",
                            tint: Brand.underBudget
                        )
                        StatTile(
                            title: "Per week",
                            value: DisplayFormat.currency(model.weeklyBudget),
                            caption: "7 days",
                            systemImage: "cart.fill",
                            tint: Brand.underBudget
                        )
                        StatTile(
                            title: "Per 1,000 kcal",
                            value: DisplayFormat.currency(model.costPer1000Calories),
                            caption: "Energy cost",
                            systemImage: "flame.fill",
                            tint: Brand.underBudget
                        )
                    }

                    if let warning = model.budgetWarning {
                        Label(warning, systemImage: "exclamationmark.triangle.fill")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }
            }

            Text("BMI is a population statistic, not a body-composition measure. Track your waist and photos alongside it.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Pieces

    /// Compact always-visible preview so the user watches the numbers respond
    /// as they change an input, rather than waiting until the end.
    private var livePreviewStrip: some View {
        let prescription = model.prescription
        return CardContainer(padding: 14) {
            HStack {
                StatTile(
                    title: "TDEE",
                    value: DisplayFormat.calories(prescription.tdee),
                    caption: "Maintenance",
                    systemImage: "bolt.fill",
                    tint: Brand.gold
                )
                StatTile(
                    title: "Target",
                    value: DisplayFormat.calories(prescription.targets.calories),
                    caption: model.goal.displayName,
                    systemImage: model.goal.systemImage,
                    tint: Brand.gold
                )
                StatTile(
                    title: "Protein",
                    value: DisplayFormat.grams(prescription.targets.protein),
                    caption: "Per day",
                    systemImage: "fork.knife",
                    tint: Brand.protein
                )
            }
        }
        .opacity(model.validationError == nil ? 1 : 0.4)
    }

    private func calculationRow(_ title: String, _ value: String, _ caption: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.medium))
                Text(caption).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 12)
            Text(value)
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
        }
    }

    // MARK: Actions

    private func commit() {
        do {
            let profile = try model.save(to: context, existing: existingProfile)
            onComplete(profile)
        } catch let error as BodyScienceEngine.ValidationError {
            saveError = error.message
        } catch {
            saveError = error.localizedDescription
        }
    }
}

// MARK: - Preview

#Preview {
    OnboardingView()
        .modelContainer(for: [UserProfile.self, FoodItem.self, MealPlan.self, GroceryItem.self], inMemory: true)
}
