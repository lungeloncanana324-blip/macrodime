//
//  MealPlannerView.swift
//  MacroDime
//
//  Builds a day's meals slot by slot, showing running macros and cost against
//  target, with a swap affordance at both the meal and the ingredient level.
//

import SwiftUI
import SwiftData

@MainActor
struct MealPlannerView: View {

    @Environment(\.modelContext) private var context
    @Query private var profiles: [UserProfile]

    @State private var model = MealPlannerViewModel()
    @State private var pickerSlot: MealSlot?
    @State private var swapUnderReview: MealSwap?
    @State private var portionPicker: PortionPickerContext?

    private var profile: UserProfile? { profiles.first }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    summaryCard

                    ForEach(MealSlot.allCases) { slot in
                        mealCard(for: slot)
                    }
                }
                .padding(16)
                .frame(maxWidth: 620)
                .frame(maxWidth: .infinity)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Meal Plan")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    DatePicker(
                        "Day",
                        selection: Binding(
                            get: { model.date },
                            set: { model.select(date: $0, context: context, profile: profile) }
                        ),
                        displayedComponents: .date
                    )
                    .labelsHidden()
                }
            }
            .sheet(item: $pickerSlot) { slot in
                FoodPickerSheet(model: model, slot: slot)
            }
            .sheet(item: $swapUnderReview) { swap in
                SwapReviewSheet(swap: swap) {
                    model.apply(swap, context: context)
                }
            }
            .sheet(item: $portionPicker) { picker in
                PortionSwapSheet(
                    portion: picker.portion,
                    options: model.alternatives(for: picker.portion, in: picker.meal)
                ) { chosen in
                    model.apply(chosen, in: picker.meal, context: context)
                }
            }
            .task(id: profile?.id) {
                model.load(context: context, profile: profile)
            }
        }
    }

    // MARK: Running totals

    private var summaryCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 14) {
                if let targets = model.targets {
                    HStack(spacing: 10) {
                        MacroRingStat(axis: .calories, consumed: model.consumed.calories, target: targets.calories, ringSize: 62, lineWidth: 8)
                        MacroRingStat(axis: .protein, consumed: model.consumed.protein, target: targets.protein, ringSize: 62, lineWidth: 8)
                        MacroRingStat(axis: .carbs, consumed: model.consumed.carbs, target: targets.carbs, ringSize: 62, lineWidth: 8)
                        MacroRingStat(axis: .fat, consumed: model.consumed.fat, target: targets.fat, ringSize: 62, lineWidth: 8)
                    }
                    .frame(maxWidth: .infinity)
                    Divider()
                }
                BudgetMeter(spent: model.spend, allowance: model.dailyBudget, showsCaption: false)
            }
        }
    }

    // MARK: Meal card

    @ViewBuilder
    private func mealCard(for slot: MealSlot) -> some View {
        let meal = model.meal(for: slot)
        let swap = meal.flatMap { model.swapPreview(for: $0) }

        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Label(slot.displayName, systemImage: slot.systemImage)
                        .font(.headline)
                    Spacer()
                    if let meal, !meal.isEmpty {
                        Text(DisplayFormat.currency(meal.cost))
                            .font(.subheadline.weight(.semibold))
                            .monospacedDigit()
                        TierChip(tier: meal.effectiveTier)
                    }
                }

                if let meal, !meal.isEmpty {
                    ForEach(meal.portions) { portion in
                        portionRow(portion, in: meal)
                    }

                    Divider()

                    HStack(spacing: 12) {
                        macroBadge(.calories, meal.nutrition.calories)
                        macroBadge(.protein, meal.nutrition.protein)
                        macroBadge(.carbs, meal.nutrition.carbs)
                        macroBadge(.fat, meal.nutrition.fat)
                    }

                    if let swap {
                        Button {
                            swapUnderReview = swap
                        } label: {
                            Label(
                                "Swap to save \(DisplayFormat.currency(swap.savings))",
                                systemImage: "arrow.triangle.2.circlepath"
                            )
                            .font(.subheadline.weight(.medium))
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .tint(Brand.underBudget)
                    }
                } else {
                    Text(emptySlotHint(for: slot))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Button("Add food", systemImage: "plus.circle.fill") {
                    pickerSlot = slot
                }
                .font(.subheadline)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private func portionRow(_ portion: Portion, in meal: MealItem) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(portion.food.name)
                    .font(.subheadline.weight(.medium))
                Text("\(portion.quantityDescription) · \(DisplayFormat.calories(portion.nutrition.calories)) · \(DisplayFormat.grams(portion.nutrition.protein)) protein")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 6)
            Text(DisplayFormat.currency(portion.cost))
                .font(.caption.weight(.medium))
                .monospacedDigit()
                .foregroundStyle(.secondary)
        }
        .contentShape(Rectangle())
        // These rows live in a card, not a `List`, so `swipeActions` would be
        // inert here — the context menu is the affordance.
        .contextMenu {
            Button("Find a cheaper option", systemImage: "arrow.triangle.2.circlepath") {
                portionPicker = PortionPickerContext(portion: portion, meal: meal)
            }
            Button(role: .destructive) {
                model.removePortion(id: portion.id, context: context)
            } label: {
                Label("Remove", systemImage: "trash")
            }
            Divider()
            ForEach([0.5, 1.0, 1.5, 2.0], id: \.self) { servings in
                Button("Set to \(servings.formatted()) serving\(servings == 1 ? "" : "s")") {
                    model.updateServings(portionID: portion.id, to: servings, context: context)
                }
            }
        }
    }

    private func macroBadge(_ axis: MacroAxis, _ value: Double) -> some View {
        VStack(spacing: 1) {
            Text(axis.formatted(value))
                .font(.caption.weight(.semibold))
                .monospacedDigit()
            Text(axis.displayName)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .foregroundStyle(axis.tint)
    }

    private func emptySlotHint(for slot: MealSlot) -> String {
        guard let targets = model.targets else { return "Nothing planned yet." }
        let budget = targets.calories * slot.defaultCalorieShare
        return "Nothing planned. Aim for about \(DisplayFormat.calories(budget)) here."
    }
}

/// Identifies which ingredient the per-portion swap sheet is working on.
struct PortionPickerContext: Identifiable {
    var id: UUID { portion.id }
    let portion: Portion
    let meal: MealItem
}

// MARK: - Food Picker

/// Searchable catalogue with tier and category filters.
@MainActor
struct FoodPickerSheet: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Bindable var model: MealPlannerViewModel
    let slot: MealSlot

    @State private var servings: Double = 1

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Stepper(value: $servings, in: 0.25...6, step: 0.25) {
                        LabeledContent("Servings", value: servings.formatted(.number.precision(.fractionLength(0...2))))
                    }
                    Toggle("Budget staples only", isOn: $model.showStrictTierOnly)
                }

                Section {
                    Picker("Category", selection: $model.categoryFilter) {
                        Text("All").tag(FoodCategory?.none)
                        ForEach(FoodCategory.allCases) { category in
                            Text(category.displayName).tag(FoodCategory?.some(category))
                        }
                    }
                    .pickerStyle(.menu)
                }

                Section("Ingredients") {
                    if model.filteredCatalog.isEmpty {
                        ContentUnavailableView.search
                    } else {
                        ForEach(model.filteredCatalog) { food in
                            Button {
                                model.addFood(food, servings: servings, to: slot, context: context)
                                dismiss()
                            } label: {
                                foodRow(food)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .searchable(text: $model.searchText, prompt: "Search ingredients")
            .navigationTitle("Add to \(slot.displayName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func foodRow(_ food: FoodSnapshot) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(food.name)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                    TierChip(tier: food.costTier)
                }
                Text("\(food.servingDescription) · \(DisplayFormat.calories(food.nutrition.calories)) · \(DisplayFormat.grams(food.nutrition.protein))P \(DisplayFormat.grams(food.nutrition.carbs))C \(DisplayFormat.grams(food.nutrition.fat))F")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 6)
            VStack(alignment: .trailing, spacing: 2) {
                Text(DisplayFormat.currency(food.costPerServing))
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
                Text("\(DisplayFormat.number(food.proteinPerCurrencyUnit, decimals: 1)) g/\(DisplayFormat.currency(1))")
                    .font(.caption2)
                    .foregroundStyle(Brand.protein)
            }
        }
    }
}

// MARK: - Per-portion swap sheet

/// Ranked alternatives for a single ingredient, so the user can choose rather
/// than accept the engine's top pick.
@MainActor
struct PortionSwapSheet: View {

    @Environment(\.dismiss) private var dismiss

    let portion: Portion
    let options: [PortionSwap]
    let onSelect: (PortionSwap) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section("Replacing") {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(portion.food.name).font(.subheadline.weight(.medium))
                        Text("\(portion.quantityDescription) · \(DisplayFormat.currency(portion.cost))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                if options.isEmpty {
                    ContentUnavailableView(
                        "No cheaper match",
                        systemImage: "magnifyingglass",
                        description: Text("Nothing in the catalogue is cheaper while keeping this meal's macros within 10%.")
                    )
                } else {
                    Section("Cheaper alternatives") {
                        ForEach(options) { option in
                            Button {
                                onSelect(option)
                                dismiss()
                            } label: {
                                optionRow(option)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .navigationTitle("Low-Cost Swap")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func optionRow(_ option: PortionSwap) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(option.replacement.food.name)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                    TierChip(tier: option.replacement.food.costTier)
                }
                Text("\(option.replacement.quantityDescription) · macros within \(DisplayFormat.percent(option.resultingDrift.worst))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 6)
            Text("−\(DisplayFormat.currency(option.savings))")
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(Brand.underBudget)
        }
    }
}

// MARK: - Preview

#Preview {
    MealPlannerView()
        .modelContainer(for: [UserProfile.self, FoodItem.self, MealPlan.self, GroceryItem.self], inMemory: true)
}
