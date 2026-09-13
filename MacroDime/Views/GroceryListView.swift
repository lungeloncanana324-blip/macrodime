//
//  GroceryListView.swift
//  MacroDime
//
//  The consolidated weekly shopping list, grouped by supermarket section and
//  ordered the way the user walks the store.
//
//  Two pieces of state belong to the user and survive regeneration: what has
//  been ticked off, and what they already own. Adding a meal mid-week must not
//  reset either.
//

import SwiftUI
import SwiftData

struct GroceryListView: View {

    @Environment(\.modelContext) private var context
    @Query private var profiles: [UserProfile]
    @Query(sort: \GroceryItem.name) private var allItems: [GroceryItem]

    @State private var weekAnchor: Date = .now
    @State private var isShowingCheckedItems = true
    @State private var errorMessage: String?

    private var profile: UserProfile? { profiles.first }

    private var weekStart: Date {
        GroceryListService.weekStart(containing: weekAnchor)
    }

    /// Items for the displayed week, grouped into store-walk order.
    private var sections: [(section: GrocerySection, items: [GroceryItem])] {
        let weekItems = allItems.filter { $0.weekStart == weekStart }
        return GrocerySection.allCases
            .sorted { $0.aisleOrder < $1.aisleOrder }
            .compactMap { section in
                let items = weekItems
                    .filter { $0.section == section }
                    .filter { isShowingCheckedItems || !$0.isChecked }
                    .sorted { lhs, rhs in
                        // Unchecked first, then most expensive — the decisions
                        // worth making float to the top of each aisle.
                        if lhs.isChecked != rhs.isChecked { return !lhs.isChecked }
                        if lhs.estimatedCost != rhs.estimatedCost { return lhs.estimatedCost > rhs.estimatedCost }
                        return lhs.name < rhs.name
                    }
                return items.isEmpty ? nil : (section, items)
            }
    }

    private var weekItems: [GroceryItem] { allItems.filter { $0.weekStart == weekStart } }
    private var outstandingTotal: Double { weekItems.reduce(0) { $0 + $1.outstandingCost } }
    private var fullTotal: Double { weekItems.reduce(0) { $0 + $1.estimatedCost } }
    private var checkedCount: Int { weekItems.filter(\.isChecked).count }

    private var weeklyBudget: Double { (profile?.dailyFoodBudget ?? 0) * 7 }

    var body: some View {
        NavigationStack {
            Group {
                if weekItems.isEmpty {
                    ContentUnavailableView {
                        Label("No list yet", systemImage: "cart")
                    } description: {
                        Text("Plan some meals for this week, then generate your list.")
                    } actions: {
                        Button("Generate list", action: regenerate)
                            .buttonStyle(.borderedProminent)
                    }
                } else {
                    list
                }
            }
            .navigationTitle("Grocery List")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        Button("Previous week", systemImage: "chevron.left") { shiftWeek(by: -1) }
                        Button("This week", systemImage: "calendar") { weekAnchor = .now }
                        Button("Next week", systemImage: "chevron.right") { shiftWeek(by: 1) }
                    } label: {
                        Text(weekLabel).font(.subheadline.weight(.medium))
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Regenerate from meal plan", systemImage: "arrow.clockwise", action: regenerate)
                        Toggle("Show checked items", isOn: $isShowingCheckedItems)
                        Button(role: .destructive, action: uncheckAll) {
                            Label("Uncheck all", systemImage: "arrow.uturn.backward")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    // MARK: List

    private var list: some View {
        List {
            Section {
                summaryHeader
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 12, trailing: 16))
            }

            ForEach(sections, id: \.section) { group in
                Section {
                    ForEach(group.items) { item in
                        GroceryRow(item: item) { toggleChecked(item) }
                            .swipeActions(edge: .leading) {
                                Button {
                                    item.isAlreadyOwned.toggle()
                                    save()
                                } label: {
                                    Label(
                                        item.isAlreadyOwned ? "Need it" : "Already have",
                                        systemImage: item.isAlreadyOwned ? "cart.badge.plus" : "house.fill"
                                    )
                                }
                                .tint(Brand.pantry)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    context.delete(item)
                                    save()
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                    }
                } header: {
                    HStack {
                        Label(group.section.displayName, systemImage: group.section.systemImage)
                        Spacer()
                        Text(DisplayFormat.currency(group.items.reduce(0) { $0 + $1.outstandingCost }))
                            .monospacedDigit()
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var summaryHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                StatTile(
                    title: "Still to buy",
                    value: DisplayFormat.currency(outstandingTotal),
                    caption: "\(checkedCount) of \(weekItems.count) ticked",
                    systemImage: "cart.fill",
                    tint: Brand.underBudget
                )
                StatTile(
                    title: "Full list",
                    value: DisplayFormat.currency(fullTotal),
                    caption: "Before pantry items",
                    systemImage: "sum",
                    tint: .secondary
                )
                if weeklyBudget > 0 {
                    StatTile(
                        title: "Weekly budget",
                        value: DisplayFormat.currency(weeklyBudget),
                        caption: fullTotal <= weeklyBudget ? "Within budget" : "Over by \(DisplayFormat.currency(fullTotal - weeklyBudget))",
                        systemImage: fullTotal <= weeklyBudget ? "checkmark.circle.fill" : "exclamationmark.triangle.fill",
                        tint: fullTotal <= weeklyBudget ? Brand.underBudget : Brand.carbs
                    )
                }
            }

            if weekItems.count > 0 {
                ProgressView(value: Double(checkedCount), total: Double(weekItems.count))
                    .tint(Brand.underBudget)
                    .accessibilityLabel("Shopping progress")
            }
        }
    }

    // MARK: Actions

    private var weekLabel: String {
        let end = Calendar.current.date(byAdding: .day, value: 6, to: weekStart) ?? weekStart
        return "\(weekStart.formatted(.dateTime.month(.abbreviated).day())) – \(end.formatted(.dateTime.month(.abbreviated).day()))"
    }

    private func shiftWeek(by weeks: Int) {
        weekAnchor = Calendar.current.date(byAdding: .weekOfYear, value: weeks, to: weekAnchor) ?? weekAnchor
    }

    private func toggleChecked(_ item: GroceryItem) {
        item.isChecked.toggle()
        save()
    }

    private func uncheckAll() {
        for item in weekItems { item.isChecked = false }
        save()
    }

    private func regenerate() {
        do {
            try GroceryListService.regenerate(weekContaining: weekAnchor, context: context)
            try context.save()
        } catch {
            errorMessage = "Could not build your list: \(error.localizedDescription)"
        }
    }

    private func save() {
        do {
            try context.save()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

// MARK: - Row

/// One checkable line. The whole row is the hit target — ticking things off
/// happens one-handed, in a shop, usually in a hurry.
struct GroceryRow: View {

    let item: GroceryItem
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 12) {
                Image(systemName: item.isChecked ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(item.isChecked ? Brand.underBudget : Color.secondary.opacity(0.5))
                    .contentTransition(.symbolEffect(.replace))

                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .font(.body)
                        .strikethrough(item.isChecked)
                        .foregroundStyle(item.isChecked ? .secondary : .primary)
                    HStack(spacing: 6) {
                        Text(item.quantityDescription)
                        if item.isAlreadyOwned {
                            Text("· already have")
                                .foregroundStyle(Brand.pantry)
                        }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                Spacer(minLength: 6)

                Text(DisplayFormat.currency(item.estimatedCost))
                    .font(.subheadline.weight(.medium))
                    .monospacedDigit()
                    .foregroundStyle(item.isAlreadyOwned || item.isChecked ? .secondary : .primary)
                    .strikethrough(item.isAlreadyOwned)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(item.isChecked ? [.isButton, .isSelected] : .isButton)
        .accessibilityHint("Double tap to mark as \(item.isChecked ? "not bought" : "bought")")
    }
}

// MARK: - Preview

#Preview {
    GroceryListView()
        .modelContainer(for: [UserProfile.self, FoodItem.self, MealPlan.self, GroceryItem.self], inMemory: true)
}
