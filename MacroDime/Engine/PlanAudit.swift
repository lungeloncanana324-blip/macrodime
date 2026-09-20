//
//  PlanAudit.swift
//  MacroDime
//
//  Answers the question the app has to answer honestly: what is wrong with this
//  plan?
//
//  Two callers, one rule set:
//    * onboarding runs `feasibility(...)` before anything is planned, so an
//      impossible combination (vegan, no soy, $9 a day, 160 g protein) is named
//      while the user is still choosing rather than discovered in week three;
//    * the dashboard runs `day(...)` on the plan the user actually built.
//
//  Every gap carries a remedy, not just a diagnosis. "41 g of protein short: put
//  a protein anchor in dinner" is actionable; "your plan is unbalanced" is not.
//
//  Thresholds live in `Constants` so the tests assert against the same numbers
//  the app uses rather than against a second copy of them.
//

import Foundation

// MARK: - A single gap

struct PlanGap: Identifiable, Hashable, Sendable {

    /// How much this matters. `blocking` means the plan cannot meet what the
    /// user asked for as it stands.
    enum Severity: Int, Comparable, Sendable {
        case info = 0
        case caution = 1
        case blocking = 2

        static func < (lhs: Severity, rhs: Severity) -> Bool { lhs.rawValue < rhs.rawValue }

        var displayName: String {
            switch self {
            case .info: "Note"
            case .caution: "Worth fixing"
            case .blocking: "Blocks your plan"
            }
        }

        var systemImage: String {
            switch self {
            case .info: "info.circle.fill"
            case .caution: "exclamationmark.triangle.fill"
            case .blocking: "xmark.octagon.fill"
            }
        }
    }

    /// What kind of problem this is. Used for ordering and for tests that need
    /// to find one specific gap.
    enum Kind: String, Hashable, Sendable {
        case emptyPlan
        case unfilledSlot
        case proteinShortfall
        case calorieDrift
        case budgetOverrun
        case noVegetable
        case lowVariety
        case cookingEffort
        case restrictionConflict
        case proteinFeasibility
        case missingProteinSources
        case restrictionsCost
        case eatingOut
        case untrackedNutrients
    }

    let kind: Kind
    let severity: Severity
    let title: String
    let detail: String
    let remedy: String

    var id: String { "\(kind.rawValue)|\(title)" }
}

// MARK: - The audit

enum PlanAudit {

    /// Every threshold the audit uses, in one place.
    enum Constants {
        /// Share of the protein target that counts as met. Below 80% is worth
        /// fixing; below 60% the plan is not a plan for this target.
        static let proteinCaution = 0.80
        static let proteinBlocking = 0.60
        /// How far total calories may sit from target before it is called out.
        static let calorieCaution = 0.15
        static let calorieBlocking = 0.30
        /// Spend against allowance, as a multiple.
        static let budgetCaution = 1.00
        static let budgetBlocking = 1.25
        /// Daily cost, in USD, of the carbs, fats and vegetables that go around
        /// the protein anchor. Deliberately flat and deliberately rough: it
        /// exists to catch "this budget cannot work", not to price a meal. The
        /// same figure the onboarding budget warning uses.
        static let nonProteinDailyCost = 2.50
        /// Distinct protein anchors a day needs before the plan is called
        /// repetitive.
        static let varietyMinimum = 2
    }

    /// The result of a run: ordered worst first, so the UI can render it as-is.
    struct Report: Hashable, Sendable {
        let gaps: [PlanGap]

        init(_ gaps: [PlanGap]) {
            self.gaps = gaps.sorted {
                $0.severity == $1.severity
                    ? $0.title < $1.title
                    : $0.severity > $1.severity
            }
        }

        var isEmpty: Bool { gaps.isEmpty }
        var blocking: [PlanGap] { gaps.filter { $0.severity == .blocking } }
        var cautions: [PlanGap] { gaps.filter { $0.severity == .caution } }
        var notes: [PlanGap] { gaps.filter { $0.severity == .info } }

        var worstSeverity: PlanGap.Severity? { gaps.map(\.severity).max() }

        /// One line for a card header.
        var headline: String {
            switch worstSeverity {
            case .none: "No gaps found"
            case .some(.info): notes.count == 1 ? "1 note" : "\(notes.count) notes"
            case .some(.caution): cautions.count == 1 ? "1 thing to fix" : "\(cautions.count) things to fix"
            case .some(.blocking): blocking.count == 1 ? "1 blocking problem" : "\(blocking.count) blocking problems"
            }
        }

        func first(_ kind: PlanGap.Kind) -> PlanGap? { gaps.first { $0.kind == kind } }
        func contains(_ kind: PlanGap.Kind) -> Bool { gaps.contains { $0.kind == kind } }
    }

    // MARK: - Feasibility (no plan yet)

    /// The cheapest day that could meet this target using only foods the profile
    /// allows: the protein target bought at the best protein-per-dollar rate,
    /// plus a flat allowance for the carbs, fats and vegetables around it.
    ///
    /// `nil` only when the profile allows no protein source at all. The single
    /// source of this estimate, used by the feasibility check and by the
    /// onboarding budget warning, so the two can never quote different numbers.
    static func minimumDailyCostUSD(
        targets: NutritionFacts,
        dietary: DietaryProfile,
        catalog: [FoodSnapshot] = FoodCatalog.all
    ) -> (cost: Double, anchor: FoodSnapshot)? {
        let anchors = DietaryFilter.allowed(in: .proteinAnchor, foods: catalog, under: dietary)
            .filter { $0.proteinPerCurrencyUnit > 0 }
            .sorted { $0.proteinPerCurrencyUnit > $1.proteinPerCurrencyUnit }

        guard let best = anchors.first else { return nil }
        let cost = targets.protein / best.proteinPerCurrencyUnit + Constants.nonProteinDailyCost
        return (cost, best)
    }

    /// Can this profile reach this target inside this budget, using only foods
    /// it may eat? Run during onboarding, where it can still change a choice.
    static func feasibility(
        targets: NutritionFacts?,
        dailyBudgetUSD: Double,
        dietary: DietaryProfile,
        catalog: [FoodSnapshot] = FoodCatalog.all,
        currency: CurrencySettings = .usd
    ) -> Report {
        var gaps: [PlanGap] = []

        let allowed = DietaryFilter.allowed(catalog, under: dietary)
        let estimate = targets.flatMap {
            minimumDailyCostUSD(targets: $0, dietary: dietary, catalog: catalog)
        }

        if let targets, estimate == nil {
            gaps.append(
                PlanGap(
                    kind: .missingProteinSources,
                    severity: .blocking,
                    title: "No protein source fits your restrictions",
                    detail: "Nothing left in the catalogue is a protein anchor under your pattern and avoid list, so \(DisplayFormat.grams(targets.protein)) of protein a day cannot be planned.",
                    remedy: "Allow one protein group you have excluded, or switch to a less strict pattern."
                )
            )
        } else if let targets, let estimate, dailyBudgetUSD > 0 {
            if estimate.cost > dailyBudgetUSD * Constants.budgetBlocking {
                gaps.append(
                    PlanGap(
                        kind: .proteinFeasibility,
                        severity: .blocking,
                        title: "This target does not fit this budget",
                        detail: "\(DisplayFormat.grams(targets.protein)) of protein costs about \(currency.format(estimate.cost)) a day at the cheapest source you allow, \(estimate.anchor.name), against an allowance of \(currency.format(dailyBudgetUSD)).",
                        remedy: "Raise the daily allowance to at least \(currency.format(estimate.cost)), or lower the protein target."
                    )
                )
            } else if estimate.cost > dailyBudgetUSD * Constants.budgetCaution {
                gaps.append(
                    PlanGap(
                        kind: .proteinFeasibility,
                        severity: .caution,
                        title: "Your budget leaves no room to move",
                        detail: "Hitting \(DisplayFormat.grams(targets.protein)) of protein costs about \(currency.format(estimate.cost)) a day at \(estimate.anchor.name) prices, which is most of your \(currency.format(dailyBudgetUSD)) allowance.",
                        remedy: "Add a little headroom, or expect the plan to lean hard on \(estimate.anchor.name)."
                    )
                )
            }
        }

        gaps.append(contentsOf: restrictionNotes(dietary: dietary, allowed: allowed, catalog: catalog))
        gaps.append(contentsOf: disclosureNotes(dietary: dietary))

        return Report(gaps)
    }

    // MARK: - A day's plan

    /// Audits the plan the user actually built: macros against target, spend
    /// against allowance, and everything about the profile that the plan no
    /// longer respects.
    static func day(
        meals: [MealItem],
        targets: NutritionFacts?,
        dailyBudgetUSD: Double,
        dietary: DietaryProfile,
        catalog: [FoodSnapshot] = FoodCatalog.all,
        currency: CurrencySettings = .usd
    ) -> Report {
        var gaps: [PlanGap] = []

        let planned = meals.filter { !$0.isEmpty }
        let consumed = meals.totalNutrition
        let spend = meals.totalCost

        let anchors = DietaryFilter.allowed(in: .proteinAnchor, foods: catalog, under: dietary)
        let bestAnchor = anchors.first

        if planned.isEmpty {
            gaps.append(
                PlanGap(
                    kind: .emptyPlan,
                    severity: .caution,
                    title: "Nothing is planned for this day",
                    detail: "There are no meals on this date, so there are no numbers to judge.",
                    remedy: "Add a meal on the Plan tab, or start from something you already eat and let the swap engine cheapen it."
                )
            )
        }

        gaps.append(contentsOf: macroGaps(
            consumed: consumed,
            targets: targets,
            planned: planned,
            bestAnchor: bestAnchor,
            currency: currency
        ))
        gaps.append(contentsOf: budgetGaps(
            spend: spend,
            dailyBudgetUSD: dailyBudgetUSD,
            planned: planned,
            currency: currency
        ))
        gaps.append(contentsOf: compositionGaps(meals: planned, dietary: dietary))
        gaps.append(contentsOf: disclosureNotes(dietary: dietary))

        return Report(gaps)
    }

    // MARK: - Macro rules

    private static func macroGaps(
        consumed: NutritionFacts,
        targets: NutritionFacts?,
        planned: [MealItem],
        bestAnchor: FoodSnapshot?,
        currency: CurrencySettings
    ) -> [PlanGap] {
        guard let targets, !planned.isEmpty else { return [] }
        var gaps: [PlanGap] = []

        if targets.protein > 0 {
            let ratio = consumed.protein / targets.protein
            if ratio < Constants.proteinCaution {
                let short = targets.protein - consumed.protein
                let leverage = bestAnchor.map { " The cheapest source you allow is \($0.name)." } ?? ""
                gaps.append(
                    PlanGap(
                        kind: .proteinShortfall,
                        severity: ratio < Constants.proteinBlocking ? .blocking : .caution,
                        title: "\(DisplayFormat.grams(short)) of protein short",
                        detail: "You are at \(DisplayFormat.grams(consumed.protein)) of \(DisplayFormat.grams(targets.protein)).\(leverage)",
                        remedy: "Put a protein anchor in the meal with the most room, then let the swap engine rebalance the rest."
                    )
                )
            }
        }

        if targets.calories > 0 {
            let drift = abs(consumed.calories - targets.calories) / targets.calories
            if drift > Constants.calorieCaution {
                let difference = consumed.calories - targets.calories
                gaps.append(
                    PlanGap(
                        kind: .calorieDrift,
                        severity: drift > Constants.calorieBlocking ? .caution : .info,
                        title: difference < 0
                            ? "\(DisplayFormat.calories(abs(difference))) under target"
                            : "\(DisplayFormat.calories(difference)) over target",
                        detail: "\(DisplayFormat.calories(consumed.calories)) planned against a target of \(DisplayFormat.calories(targets.calories)).",
                        remedy: difference < 0
                            ? "Add a carb or fat portion to the smallest meal."
                            : "Trim a carb or fat portion rather than a protein one."
                    )
                )
            }
        }

        return gaps
    }

    // MARK: - Budget rules

    private static func budgetGaps(
        spend: Double,
        dailyBudgetUSD: Double,
        planned: [MealItem],
        currency: CurrencySettings
    ) -> [PlanGap] {
        guard dailyBudgetUSD > 0, !planned.isEmpty else { return [] }
        let ratio = spend / dailyBudgetUSD
        guard ratio > Constants.budgetCaution else { return [] }

        let over = spend - dailyBudgetUSD
        return [
            PlanGap(
                kind: .budgetOverrun,
                severity: ratio > Constants.budgetBlocking ? .blocking : .caution,
                title: "\(currency.format(over)) over your allowance",
                detail: "This day costs \(currency.format(spend)) against an allowance of \(currency.format(dailyBudgetUSD)).",
                remedy: "Run a low-cost swap on the most expensive meal; the engine only proposes changes that keep the macros within 10%."
            )
        ]
    }

    // MARK: - Composition rules

    private static func compositionGaps(meals: [MealItem], dietary: DietaryProfile) -> [PlanGap] {
        guard !meals.isEmpty else { return [] }
        var gaps: [PlanGap] = []

        let portions = meals.flatMap(\.portions)

        // Ingredients the plan uses that this profile no longer allows. Possible
        // whenever the restrictions were changed after a plan was built, which is
        // exactly when a silent contradiction would be worst.
        let conflicts = portions.filter { !DietaryFilter.allows($0.food, under: dietary) }
        if !conflicts.isEmpty {
            let names = Set(conflicts.map(\.food.name)).sorted().prefix(3).joined(separator: ", ")
            let reasons = Set(conflicts.compactMap {
                DietaryFilter.rejection(for: $0.food, under: dietary)?.reason
            }).sorted().prefix(2).joined(separator: ", ")
            gaps.append(
                PlanGap(
                    kind: .restrictionConflict,
                    severity: .blocking,
                    title: "\(conflicts.count) portion\(conflicts.count == 1 ? "" : "s") break your restrictions",
                    detail: "\(names) \(reasons.isEmpty ? "no longer fit your profile" : reasons).",
                    remedy: "Replace them, or widen your restrictions if that choice was not deliberate."
                )
            )
        }

        let tooSlow = portions.filter { $0.food.prepMinutes > dietary.prepEffort.maximumMinutes }
        if !tooSlow.isEmpty {
            let names = Set(tooSlow.map(\.food.name)).sorted().prefix(2).joined(separator: " and ")
            gaps.append(
                PlanGap(
                    kind: .cookingEffort,
                    severity: .caution,
                    title: "\(tooSlow.count) ingredient\(tooSlow.count == 1 ? "" : "s") take longer than you will cook",
                    detail: "\(names) exceed your \(dietary.prepEffort.displayName.lowercased()) limit.",
                    remedy: "Swap them for ready-to-eat or frozen equivalents, or allow yourself more time."
                )
            )
        }

        if meals.contains(where: { $0.slot == .dinner }),
           !portions.contains(where: { $0.food.category == .vegetable }) {
            gaps.append(
                PlanGap(
                    kind: .noVegetable,
                    severity: .info,
                    title: "No vegetable with dinner",
                    detail: "Dinner carries no vegetable portion. Vegetables are the cheapest way to add volume and fullness, and they are not counted as a macro here.",
                    remedy: "Add a vegetable to dinner; frozen florets cost about half of fresh."
                )
            )
        }

        let anchorIDs = Set(portions.filter { $0.food.category == .proteinAnchor }.map(\.food.id))
        if anchorIDs.count < Constants.varietyMinimum, meals.count >= 2 {
            gaps.append(
                PlanGap(
                    kind: .lowVariety,
                    severity: .info,
                    title: anchorIDs.isEmpty ? "No protein anchor all day" : "One protein source all day",
                    detail: anchorIDs.isEmpty
                        ? "No meal in the day carries a protein anchor."
                        : "Every meal draws on the same ingredient, which is dull and narrows your micronutrients.",
                    remedy: "Rotate two or three anchors across the week: eggs, canned tuna, lentils, chicken thighs, tofu and sardines are all in the budget tier."
                )
            )
        }

        let plannedSlots = Set(meals.map(\.slot))
        let unfilled = dietary.schedule.slots.filter { !plannedSlots.contains($0) }
        if !unfilled.isEmpty {
            gaps.append(
                PlanGap(
                    kind: .unfilledSlot,
                    severity: .info,
                    title: "\(unfilled.count) of your \(dietary.schedule.occasionsPerDay) meals are empty",
                    detail: "Your schedule is \(dietary.schedule.displayName.lowercased()), and \(unfilled.map(\.displayName).joined(separator: ", ")) has nothing planned.",
                    remedy: "Fill it, or change your schedule in profile so the plan matches how you actually eat."
                )
            )
        }

        return gaps
    }

    // MARK: - Shared notes

    /// What the restrictions cost: how much of the catalogue survives, and why
    /// the rest does not. Without this the exclusions are invisible.
    private static func restrictionNotes(
        dietary: DietaryProfile,
        allowed: [FoodSnapshot],
        catalog: [FoodSnapshot]
    ) -> [PlanGap] {
        guard dietary.isRestricted else { return [] }

        let removed = catalog.count - allowed.count
        guard removed > 0 else { return [] }

        let breakdown = DietaryFilter.rejectionBreakdown(catalog, under: dietary)
            .prefix(3)
            .map { "\($0.count) \($0.label.lowercased())" }
            .joined(separator: ", ")

        return [
            PlanGap(
                kind: .restrictionsCost,
                severity: removed > catalog.count / 2 ? .caution : .info,
                title: "\(removed) of \(catalog.count) ingredients are out",
                detail: "Removed by your settings: \(breakdown).",
                remedy: "This is expected; it is only worth acting on if a meal type you want has become impossible."
            )
        ]
    }

    /// The honest list of what this app does *not* model. One line, always
    /// shown, because a plan that looks complete and is not is the real risk.
    private static func disclosureNotes(dietary: DietaryProfile) -> [PlanGap] {
        var gaps = [
            PlanGap(
                kind: .untrackedNutrients,
                severity: .info,
                title: "What this plan does not track",
                detail: "Calories, protein, carbohydrate, fat and cost are checked. Fibre, sodium, micronutrients and the glycaemic index are not.",
                remedy: "Cover those with variety and whole foods rather than with this app."
            )
        ]

        if dietary.mealsOutPerWeek > 0 {
            gaps.append(
                PlanGap(
                    kind: .eatingOut,
                    severity: .info,
                    title: "\(dietary.mealsOutPerWeek) meals a week are eaten out",
                    detail: "Meals away from home are not planned, counted or budgeted here, so your real spend and intake will run above the plan.",
                    remedy: "Subtract what you know you spend eating out from the daily allowance, or plan fewer meals out."
                )
            )
        }

        return gaps
    }
}
