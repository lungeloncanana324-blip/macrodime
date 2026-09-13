//
//  DashboardView.swift
//  MacroDime
//
//  The daily home screen: macro rings, the cost tracker, a low-cost swap
//  preview, and the non-BMI progress tracker.
//
//  The two halves of the product sit side by side here on purpose — macros and
//  money are one decision, not two.
//

import SwiftUI
import SwiftData

struct DashboardView: View {

    @Environment(\.modelContext) private var context
    @Query private var profiles: [UserProfile]

    @State private var model = MealPlannerViewModel()
    @State private var isShowingMeasurementSheet = false
    @State private var swapUnderReview: MealSwap?

    private var profile: UserProfile? { profiles.first }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    macroCard
                    budgetCard
                    swapPreviewCard
                    progressCard
                    powerhousesCard
                }
                .padding(16)
                .frame(maxWidth: 620)
                .frame(maxWidth: .infinity)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(greeting)
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Log measurement", systemImage: "ruler") {
                        isShowingMeasurementSheet = true
                    }
                }
            }
            .sheet(isPresented: $isShowingMeasurementSheet) {
                if let profile {
                    LogMeasurementSheet(profile: profile)
                }
            }
            .sheet(item: $swapUnderReview) { swap in
                SwapReviewSheet(swap: swap) {
                    model.apply(swap, context: context)
                }
            }
            .task(id: profile?.id) {
                model.load(context: context, profile: profile)
            }
            .refreshable {
                model.load(context: context, profile: profile)
            }
        }
    }

    private var greeting: String {
        let name = profile?.displayName ?? ""
        return name.isEmpty ? "Today" : "Hi, \(name)"
    }

    // MARK: Macro rings

    private var macroCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text("Today's macros").font(.headline)
                    Spacer()
                    if let targets = model.targets {
                        Text("\(Int(model.consumed.calories.rounded())) / \(Int(targets.calories.rounded())) kcal")
                            .font(.subheadline.weight(.medium))
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }

                if let targets = model.targets {
                    HStack(spacing: 10) {
                        MacroRingStat(axis: .calories, consumed: model.consumed.calories, target: targets.calories)
                        MacroRingStat(axis: .protein, consumed: model.consumed.protein, target: targets.protein)
                        MacroRingStat(axis: .carbs, consumed: model.consumed.carbs, target: targets.carbs)
                        MacroRingStat(axis: .fat, consumed: model.consumed.fat, target: targets.fat)
                    }
                    .frame(maxWidth: .infinity)

                    Divider()

                    HStack {
                        remainingLabel("Calories left", model.remaining.calories, axis: .calories)
                        remainingLabel("Protein left", model.remaining.protein, axis: .protein)
                    }
                } else {
                    ContentUnavailableView(
                        "No targets yet",
                        systemImage: "figure.stand",
                        description: Text("Finish setting up your profile to see your daily targets.")
                    )
                }
            }
        }
    }

    private func remainingLabel(_ title: String, _ value: Double, axis: MacroAxis) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value >= 0 ? axis.formatted(value) : "\(axis.formatted(abs(value))) over")
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(value >= 0 ? .primary : Brand.overBudget)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Cost tracker

    private var budgetCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Cost tracker").font(.headline)
                    Spacer()
                    TierChip(tier: model.budgetTier)
                }

                BudgetMeter(spent: model.spend, allowance: model.dailyBudget)

                if model.potentialSavings > 0 {
                    Divider()
                    HStack(spacing: 10) {
                        Image(systemName: "sparkles")
                            .foregroundStyle(Brand.underBudget)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(DisplayFormat.currency(model.potentialSavings)) of swaps available")
                                .font(.subheadline.weight(.medium))
                            Text("Same macros, cheaper ingredients")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                }
            }
        }
    }

    // MARK: Swap preview

    @ViewBuilder
    private var swapPreviewCard: some View {
        let swaps = model.meals.compactMap { model.swapPreview(for: $0) }
            .sorted { $0.savings > $1.savings }

        if let best = swaps.first {
            CardContainer {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Label("Low-cost swap", systemImage: "arrow.triangle.2.circlepath")
                            .font(.headline)
                        Spacer()
                        Text("Save \(DisplayFormat.currency(best.savings))")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.underBudget)
                    }

                    Text(best.original.name)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)

                    ForEach(best.portionSwaps) { swap in
                        HStack(spacing: 8) {
                            Text(swap.original.food.name)
                                .strikethrough()
                                .foregroundStyle(.secondary)
                            Image(systemName: "arrow.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(swap.replacement.food.name)
                                .fontWeight(.medium)
                            Spacer(minLength: 4)
                            Text(DisplayFormat.currency(swap.savings))
                                .font(.caption.weight(.medium))
                                .monospacedDigit()
                                .foregroundStyle(Brand.underBudget)
                        }
                        .font(.subheadline)
                    }

                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundStyle(Brand.underBudget)
                        Text("Macros stay within \(DisplayFormat.percent(best.worstDrift)) of the original")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Button {
                        swapUnderReview = best
                    } label: {
                        Text("Review swap").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.regular)
                }
            }
        }
    }

    // MARK: Non-BMI progress

    private var progressCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Progress").font(.headline)
                    Spacer()
                    Button("Add", systemImage: "plus.circle.fill") {
                        isShowingMeasurementSheet = true
                    }
                    .font(.subheadline)
                    .labelStyle(.titleAndIcon)
                }

                if let profile, let latest = profile.latestMeasurement {
                    HStack(alignment: .top, spacing: 12) {
                        StatTile(
                            title: "Waist",
                            value: latest.waistCm.map { DisplayFormat.number($0, decimals: 1) + " cm" } ?? "—",
                            caption: waistCaption(for: profile),
                            systemImage: "ruler.fill",
                            tint: Brand.measurement
                        )
                        StatTile(
                            title: "Weight",
                            value: latest.weightKg.map {
                                DisplayFormat.weight(kilograms: $0, system: profile.measurementSystem)
                            } ?? "—",
                            caption: latest.recordedAt.formatted(.dateTime.month().day()),
                            systemImage: "scalemass.fill",
                            tint: Brand.measurement
                        )
                        StatTile(
                            title: "BMI",
                            value: DisplayFormat.number(profile.prescription.bmi, decimals: 1),
                            caption: profile.prescription.bmiCategory.displayName,
                            systemImage: "chart.bar.fill",
                            tint: Brand.measurement
                        )
                    }

                    if latest.hasPhoto {
                        Label("Progress photo saved \(latest.recordedAt.formatted(.relative(presentation: .named)))",
                              systemImage: "photo.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    ContentUnavailableView(
                        "No measurements yet",
                        systemImage: "ruler",
                        description: Text("Waist circumference and photos track what BMI cannot: whether the change is fat or muscle.")
                    )
                }
            }
        }
    }

    private func waistCaption(for profile: UserProfile) -> String {
        guard let change = profile.waistChangeCm else { return "First entry" }
        let direction = change < 0 ? "down" : "up"
        return "\(DisplayFormat.number(abs(change), decimals: 1)) cm \(direction) overall"
    }

    // MARK: Budget powerhouses

    private var powerhousesCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text("Budget powerhouses").font(.headline)
                Text("Most protein per \(DisplayFormat.currency(1)) on your tier")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                ForEach(model.budgetPowerhouses) { food in
                    HStack {
                        Text(food.name).font(.subheadline)
                        Spacer(minLength: 8)
                        Text("\(DisplayFormat.number(food.proteinPerCurrencyUnit, decimals: 1)) g")
                            .font(.subheadline.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(Brand.protein)
                    }
                }
            }
        }
    }
}

// MARK: - Swap Review Sheet

/// Confirmation sheet for a proposed swap. Shows the macro and cost effect side
/// by side, because "cheaper" is only acceptable if the macros hold.
struct SwapReviewSheet: View {

    @Environment(\.dismiss) private var dismiss
    let swap: MealSwap
    let onApply: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CardContainer {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Substitutions").font(.headline)
                            ForEach(swap.portionSwaps) { portionSwap in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(portionSwap.headline)
                                        .font(.subheadline.weight(.medium))
                                    HStack(spacing: 8) {
                                        Text(portionSwap.replacement.quantityDescription)
                                        Text("·")
                                        Text("saves \(DisplayFormat.currency(portionSwap.savings))")
                                            .foregroundStyle(Brand.underBudget)
                                    }
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }

                    if !swap.allAdjustments.isEmpty {
                        CardContainer {
                            VStack(alignment: .leading, spacing: 10) {
                                Label("Portion adjustments", systemImage: "slider.horizontal.3")
                                    .font(.headline)
                                Text("Quantities changed to keep the macros where they were.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                ForEach(swap.allAdjustments) { adjustment in
                                    HStack {
                                        Image(systemName: adjustment.isIncrease ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                                            .foregroundStyle(adjustment.isIncrease ? Brand.gold : .secondary)
                                        Text(adjustment.headline)
                                            .font(.subheadline)
                                        Spacer(minLength: 4)
                                        Text(DisplayFormat.currency(adjustment.costDelta))
                                            .font(.caption)
                                            .monospacedDigit()
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }

                    CardContainer {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Effect").font(.headline)
                            comparisonRow("Cost",
                                          DisplayFormat.currency(swap.original.cost),
                                          DisplayFormat.currency(swap.swapped.cost),
                                          isGood: true)
                            Divider()
                            macroComparison(.calories)
                            macroComparison(.protein)
                            macroComparison(.carbs)
                            macroComparison(.fat)
                        }
                    }

                    Label(
                        "Worst-case macro change: \(DisplayFormat.percent(swap.worstDrift)) — inside the 10% tolerance.",
                        systemImage: "checkmark.seal.fill"
                    )
                    .font(.caption)
                    .foregroundStyle(Brand.underBudget)
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Save \(DisplayFormat.currency(swap.savings))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        onApply()
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }

    private func macroComparison(_ axis: MacroAxis) -> some View {
        comparisonRow(
            axis.displayName,
            axis.formatted(axis.value(in: swap.original.nutrition)),
            axis.formatted(axis.value(in: swap.swapped.nutrition)),
            isGood: false
        )
    }

    private func comparisonRow(_ title: String, _ before: String, _ after: String, isGood: Bool) -> some View {
        HStack {
            Text(title).font(.subheadline)
            Spacer()
            Text(before)
                .font(.subheadline)
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .strikethrough(isGood)
            Image(systemName: "arrow.right").font(.caption2).foregroundStyle(.secondary)
            Text(after)
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(isGood ? Brand.underBudget : .primary)
        }
    }
}

// MARK: - Preview

#Preview {
    DashboardView()
        .modelContainer(for: [UserProfile.self, FoodItem.self, MealPlan.self, GroceryItem.self], inMemory: true)
}
