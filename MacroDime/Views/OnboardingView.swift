//
//  OnboardingView.swift
//  MacroDime
//
//  Multi-step wizard: body metrics → goal → activity → budget → live summary.
//
//  The summary step is the point of the whole flow, the user sees their real
//  TDEE, targets and daily cost estimate *before* committing, so the numbers
//  feel earned rather than assigned.
//

import SwiftUI
import SwiftData

@MainActor
struct OnboardingView: View {
    /// How money is shown here: which currency, and at what rate.
    @Environment(\.currency) private var prices


    @Environment(\.modelContext) private var context
    @State private var model = UserProfileViewModel()
    @State private var saveError: String?

    /// Existing profile when re-editing from Settings; `nil` during first run.
    var existingProfile: UserProfile?
    var onComplete: (UserProfile) -> Void = { _ in }

    /// Writable so dismissing the alert actually clears the error, a
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
            // The wizard runs before a profile exists, so it injects the draft's
            // currency rather than reading one from the store.
            .currencySettings(model.currency)
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
        case .diet: dietStep
        case .routine: routineStep

        case .budget: budgetStep
        case .summary: summaryStep
        }
    }

    private var footer: some View {
        VStack(spacing: 12) {
            if model.step == .summary, !model.hasAcknowledgedDisclaimer {
                Label(
                    "Please confirm you understand the health note above.",
                    systemImage: "info.circle.fill"
                )
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            }

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

                    Stepper(value: $model.age, in: 18...100) {
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

    // MARK: Routine

    /// How the day is divided and how much cooking is realistic. Both decide
    /// what the planner may propose, and the cooking answer is the one people
    /// most often get wrong about themselves.
    private var routineStep: some View {
        VStack(spacing: 16) {
            CardContainer {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Meals a day").font(.headline)

                    ForEach(EatingSchedule.allCases) { schedule in
                        SelectableRow(
                            title: schedule.displayName,
                            subtitle: schedule.subtitle,
                            systemImage: "clock.fill",
                            isSelected: model.eatingSchedule == schedule
                        ) {
                            withAnimation(.snappy) { model.eatingSchedule = schedule }
                        }
                    }
                }
            }

            CardContainer {
                VStack(alignment: .leading, spacing: 12) {
                    Text("How much cooking?").font(.headline)

                    ForEach(PrepEffort.allCases) { effort in
                        SelectableRow(
                            title: effort.displayName,
                            subtitle: effort.subtitle,
                            systemImage: effort.systemImage,
                            isSelected: model.prepEffort == effort
                        ) {
                            withAnimation(.snappy) { model.prepEffort = effort }
                        }
                    }
                }
            }

            CardContainer {
                VStack(alignment: .leading, spacing: 10) {
                    Stepper(value: $model.mealsOutPerWeek, in: 0...21) {
                        LabeledContent("Meals eaten out", value: "\(model.mealsOutPerWeek) a week")
                    }

                    Text("Meals away from home are not planned, counted or budgeted. The plan says so rather than pretending they do not exist.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }


    // MARK: Diet

    /// What the user eats, and what they refuse. Every answer here removes food
    /// from the catalogue before the engine plans anything, which is why the
    /// exclusion count updates as they tap.
    private var dietStep: some View {
        VStack(spacing: 16) {
            CardContainer {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Your pattern").font(.headline)

                    ForEach(DietaryPattern.allCases) { pattern in
                        SelectableRow(
                            title: pattern.displayName,
                            subtitle: pattern.subtitle,
                            systemImage: pattern.systemImage,
                            isSelected: model.dietaryPattern == pattern
                        ) {
                            withAnimation(.snappy) { model.dietaryPattern = pattern }
                        }
                    }
                }
            }

            CardContainer {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Anything to avoid?").font(.headline)
                    Text("Allergies, intolerances, faith rules or plain dislike. Tap to exclude, tap again to allow. This removes ingredients from every suggestion the app makes.")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 100), spacing: 8)],
                        spacing: 8
                    ) {
                        ForEach(FoodExclusion.allCases) { exclusion in
                            exclusionChip(exclusion)
                        }
                    }

                    Text(removalSummary)
                        .font(.caption)
                        .foregroundStyle(model.excludedFoodCount == 0 ? .secondary : Brand.gold)

                    if model.excludedFoodCount > 0 {
                        blockedFoodPicker
                    }
                }
            }
        }
    }

    private func exclusionChip(_ exclusion: FoodExclusion) -> some View {
        let isOn = model.foodExclusions.contains(exclusion)
        return Button {
            withAnimation(.snappy) { model.toggle(exclusion) }
        } label: {
            Text(exclusion.displayName)
                .font(.subheadline)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 7)
                .background(
                    isOn ? Brand.gold.opacity(0.22) : Color(.secondarySystemBackground),
                    in: Capsule()
                )
                .foregroundStyle(isOn ? Brand.gold : Color.primary)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? [.isSelected] : [])
    }

    /// The escape hatch for "I know it fits, I still will not eat it". Offered
    /// only once something else is excluded, so the step stays short for the
    /// majority who have no restrictions.
    private var blockedFoodPicker: some View {
        Menu {
            ForEach(FoodCatalog.all) { food in
                Button {
                    withAnimation(.snappy) { model.toggleBlocked(food.id) }
                } label: {
                    Label(
                        food.name,
                        systemImage: model.blockedFoodIDs.contains(food.id) ? "checkmark" : "circle"
                    )
                }
            }
        } label: {
            Label(
                model.blockedFoodIDs.isEmpty
                    ? "Never suggest a specific food"
                    : "\(model.blockedFoodIDs.count) food\(model.blockedFoodIDs.count == 1 ? "" : "s") blocked",
                systemImage: "hand.raised.fill"
            )
            .font(.subheadline.weight(.medium))
            .foregroundStyle(Brand.gold)
        }
    }

    private var removalSummary: String {
        let removed = model.excludedFoodCount
        guard removed > 0 else { return "Nothing is excluded at the moment." }
        return "Removes \(removed) of \(FoodCatalog.all.count) ingredients from every suggestion."
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
                        Text(prices.format(model.dailyFoodBudget))
                            .font(.headline)
                            .monospacedDigit()
                    }

                    Slider(
                        value: Binding(
                            get: { prices.convert(model.dailyFoodBudget) },
                            set: { model.budgetWasEdited(to: prices.toStorage($0)) }
                        ),
                        in: prices.convert(3)...prices.convert(60),
                        step: 0.50
                    )
                    .tint(Brand.gold)

                    Text("About \(prices.format(model.weeklyBudget)) a week.")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Text("Ingredient prices are US supermarket averages and every meal cost is built from them. If you entered a currency and rate, amounts are converted at your rate and are approximate.")
                        .font(.caption2)
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
                            value: prices.format(model.dailyFoodBudget),
                            caption: model.budgetTier.displayName,
                            systemImage: "calendar",
                            tint: Brand.underBudget
                        )
                        StatTile(
                            title: "Per week",
                            value: prices.format(model.weeklyBudget),
                            caption: "7 days",
                            systemImage: "cart.fill",
                            tint: Brand.underBudget
                        )
                        StatTile(
                            title: "Per 1,000 kcal",
                            value: prices.format(model.costPer1000Calories),
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

            // Gaps in this combination, shown while the choices behind them can
            // still be changed: a target that the budget cannot fund, or a
            // restriction that has removed every protein source there was.
            PlanGapsCard(report: model.feasibility, title: "Before you commit")


            HealthDisclaimerCard(isAcknowledged: $model.hasAcknowledgedDisclaimer)

            Text(HealthDisclaimer.bmiCaveat)
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
