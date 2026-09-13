//
//  MacroComponents.swift
//  MacroDime
//
//  Shared presentation pieces: the macro palette, progress rings, stat cards and
//  the budget meter. Kept in one file so the visual language is defined in a
//  single place rather than drifting between screens.
//

import SwiftUI

// MARK: - Palette

/// Semantic colours for the four tracked quantities, drawn from `Brand`.
///
/// Gold is the brand and carries calories, the headline metric; the three
/// macros sit in food colours. Green and red are deliberately absent here —
/// they are reserved for budget status, so the cost tracker reads at a glance.
/// Colour is never the only signal: every ring carries a label and a value.
extension MacroAxis {
    var tint: Color {
        switch self {
        case .calories: Brand.gold
        case .protein: Brand.protein
        case .carbs: Brand.carbs
        case .fat: Brand.fat
        }
    }

    var displayName: String {
        switch self {
        case .calories: "Calories"
        case .protein: "Protein"
        case .carbs: "Carbs"
        case .fat: "Fat"
        }
    }

    var shortName: String {
        switch self {
        case .calories: "kcal"
        case .protein: "P"
        case .carbs: "C"
        case .fat: "F"
        }
    }

    /// Formats a value on this axis with its unit.
    func formatted(_ value: Double) -> String {
        self == .calories ? DisplayFormat.calories(value) : DisplayFormat.grams(value)
    }
}

// MARK: - Progress Ring

/// A single circular progress indicator.
///
/// Over-target is shown explicitly — the ring fills, then a second, darker arc
/// sweeps the overflow. Clamping at 100% would hide exactly the state the user
/// most needs to see.
struct MacroRing: View {
    let progress: Double
    let tint: Color
    var lineWidth: CGFloat = 10

    private var primary: Double { min(progress, 1) }
    private var overflow: Double { max(progress - 1, 0) }

    var body: some View {
        ZStack {
            Circle()
                .stroke(tint.opacity(0.15), lineWidth: lineWidth)

            Circle()
                .trim(from: 0, to: primary)
                .stroke(tint.gradient, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))

            if overflow > 0 {
                Circle()
                    .trim(from: 0, to: min(overflow, 1))
                    .stroke(
                        Brand.overBudget.gradient,
                        style: StrokeStyle(lineWidth: lineWidth * 0.55, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
            }
        }
        .animation(.snappy, value: progress)
    }
}

/// Ring plus the label and numbers that make it readable without colour.
struct MacroRingStat: View {
    let axis: MacroAxis
    let consumed: Double
    let target: Double
    var ringSize: CGFloat = 74
    var lineWidth: CGFloat = 9

    private var progress: Double {
        guard target > 0 else { return 0 }
        return consumed / target
    }

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                MacroRing(progress: progress, tint: axis.tint, lineWidth: lineWidth)
                VStack(spacing: 0) {
                    Text(DisplayFormat.percent(min(progress, 9.99)))
                        .font(.caption.weight(.semibold))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                    Text(axis.shortName)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: ringSize, height: ringSize)

            VStack(spacing: 2) {
                Text(axis.displayName)
                    .font(.caption.weight(.medium))
                Text("\(Int(consumed.rounded())) / \(Int(target.rounded()))")
                    .font(.caption2)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(axis.displayName)
        .accessibilityValue(
            "\(axis.formatted(consumed)) of \(axis.formatted(target)), \(DisplayFormat.percent(progress))"
        )
    }
}

// MARK: - Budget Meter

/// Horizontal spend-against-allowance bar. Turns red the moment spend exceeds
/// the allowance, and states the overage in words as well as colour.
struct BudgetMeter: View {
    let spent: Double
    let allowance: Double
    var showsCaption: Bool = true

    private var progress: Double {
        guard allowance > 0 else { return 0 }
        return spent / allowance
    }

    private var isOver: Bool { spent > allowance && allowance > 0 }
    private var tint: Color { isOver ? Brand.overBudget : Brand.underBudget }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(DisplayFormat.currency(spent))
                    .font(.title2.weight(.semibold))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                Text("of \(DisplayFormat.currency(allowance))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                Label(
                    isOver
                        ? "\(DisplayFormat.currency(spent - allowance)) over"
                        : "\(DisplayFormat.currency(allowance - spent)) left",
                    systemImage: isOver ? "exclamationmark.triangle.fill" : "checkmark.circle.fill"
                )
                .font(.caption.weight(.medium))
                .foregroundStyle(tint)
                .labelStyle(.titleAndIcon)
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(.quaternary)
                    Capsule()
                        .fill(tint.gradient)
                        .frame(width: geometry.size.width * min(progress, 1))
                }
            }
            .frame(height: 10)
            .animation(.snappy, value: progress)

            if showsCaption {
                Text("Estimated cost of today's planned meals")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Food budget")
        .accessibilityValue(
            "\(DisplayFormat.currency(spent)) spent of \(DisplayFormat.currency(allowance))"
        )
    }
}

// MARK: - Cards

/// Standard grouped card. One definition so corner radius, padding and
/// background never drift between screens.
struct CardContainer<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Small labelled metric, used in grids.
struct StatTile: View {
    let title: String
    let value: String
    var caption: String?
    var systemImage: String?
    var tint: Color = .secondary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.caption)
                        .foregroundStyle(tint)
                }
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text(value)
                .font(.title3.weight(.semibold))
                .monospacedDigit()
            if let caption {
                Text(caption)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Chips

/// Price-tier chip, e.g. `$` or `$$`.
struct TierChip: View {
    let tier: BudgetTier

    var body: some View {
        Text(tier.priceSymbol)
            .font(.caption.weight(.bold))
            .monospaced()
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(
                (tier == .strict ? Brand.underBudget : Brand.gold).opacity(0.18),
                in: Capsule()
            )
            .foregroundStyle(tier == .strict ? Brand.underBudget : Brand.gold)
            .accessibilityLabel(tier.displayName)
    }
}

/// Selectable option row used throughout onboarding.
struct SelectableRow: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .frame(width: 32)
                    .foregroundStyle(isSelected ? Brand.gold : .secondary)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.body.weight(.medium))
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 8)

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Brand.gold : Color.secondary.opacity(0.4))
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(.background.secondary)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(isSelected ? Brand.gold : .clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}
