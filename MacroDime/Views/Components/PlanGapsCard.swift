//
//  PlanGapsCard.swift
//  MacroDime
//
//  The gap list, shared by the onboarding summary and the dashboard.
//
//  One component for both, deliberately: the same rule set produces these
//  messages, and a plan that reads "nothing to flag" while the dashboard says
//  otherwise would destroy trust in both screens at once.
//
//  Every row carries three things: what the problem is, the evidence, and what
//  to do about it. A severity icon alone would be a diagnosis without treatment.
//

import SwiftUI

@MainActor
struct PlanGapsCard: View {

    let report: PlanAudit.Report
    var title: String = "Plan gaps"
    /// Rows shown before the list needs expanding. The blocking ones sort first,
    /// so the important rows are never the hidden ones.
    var collapsedLimit: Int = 3

    @State private var showsEverything = false

    private var visible: [PlanGap] {
        showsEverything ? report.gaps : Array(report.gaps.prefix(collapsedLimit))
    }

    private var hiddenCount: Int { max(report.gaps.count - collapsedLimit, 0) }

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 14) {
                header

                if report.isEmpty {
                    Label("Nothing to flag in this plan.", systemImage: "checkmark.seal.fill")
                        .font(.subheadline)
                        .foregroundStyle(Brand.underBudget)
                } else {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(visible) { gap in
                            row(gap)
                        }
                    }

                    if hiddenCount > 0 {
                        Button(showsEverything ? "Show fewer" : "Show \(hiddenCount) more") {
                            withAnimation(.snappy) { showsEverything.toggle() }
                        }
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Brand.gold)
                    }
                }
            }
        }
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).font(.headline)
            Spacer(minLength: 8)
            if let severity = report.worstSeverity {
                Label(report.headline, systemImage: severity.systemImage)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(tint(for: severity))
                    .labelStyle(.titleAndIcon)
            } else {
                Text(report.headline)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(Brand.underBudget)
            }
        }
    }

    private func row(_ gap: PlanGap) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: gap.severity.systemImage)
                .font(.footnote)
                .foregroundStyle(tint(for: gap.severity))
                .frame(width: 16, alignment: .center)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 3) {
                Text(gap.title)
                    .font(.subheadline.weight(.medium))
                Text(gap.detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(gap.remedy)
                    .font(.caption)
                    .foregroundStyle(tint(for: gap.severity))
            }

            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(gap.severity.displayName). \(gap.title). \(gap.detail) \(gap.remedy)")
    }

    /// Colour is never the only signal here: each row also carries an icon and
    /// the remedy in words.
    private func tint(for severity: PlanGap.Severity) -> Color {
        switch severity {
        case .info: .secondary
        case .caution: Brand.gold
        case .blocking: Brand.overBudget
        }
    }
}

#Preview {
    PlanGapsCard(
        report: PlanAudit.feasibility(
            targets: NutritionFacts(calories: 2200, protein: 160, carbs: 240, fat: 60),
            dailyBudgetUSD: 3,
            dietary: DietaryProfile(pattern: .vegan)
        )
    )
    .padding()
}
