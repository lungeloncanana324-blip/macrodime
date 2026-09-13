//
//  UserProfileViewModel.swift
//  MacroDime
//
//  Drives the onboarding wizard and the profile editor.
//
//  It holds a *draft* of the profile rather than mutating the `@Model` as the
//  user types: onboarding must be abandonable, and a half-entered height should
//  never reach the database. The draft is committed once, in `save(to:)`.
//

import Foundation
import Observation
import SwiftData

@Observable
@MainActor
final class UserProfileViewModel {

    // MARK: Wizard steps

    enum Step: Int, CaseIterable, Identifiable {
        case welcome
        case bodyMetrics
        case goal
        case activity
        case budget
        case summary

        var id: Int { rawValue }

        var title: String {
            switch self {
            case .welcome: "Welcome"
            case .bodyMetrics: "About You"
            case .goal: "Your Goal"
            case .activity: "Activity Level"
            case .budget: "Your Budget"
            case .summary: "Your Plan"
            }
        }

        var subtitle: String {
            switch self {
            case .welcome: "Real nutrition targets that fit what you can actually spend"
            case .bodyMetrics: "Used to calculate your metabolic rate"
            case .goal: "This sets your calorie adjustment and protein target"
            case .activity: "Be honest — most people overestimate this one"
            case .budget: "Every meal suggestion respects this constraint"
            case .summary: "Here is what the numbers say"
            }
        }
    }

    // MARK: Draft state

    var step: Step = .welcome

    var displayName: String = ""
    var measurementSystem: MeasurementSystem = .metric

    /// Canonical storage is always metric; the imperial fields below are
    /// projections that write back through.
    var heightCm: Double = 175
    var weightKg: Double = 75
    var age: Int = 30
    var sex: BiologicalSex = .male
    var goal: FitnessGoal = .fatLoss
    var activity: ActivityLevel = .lightlyActive

    /// Changed through `selectBudgetTier(_:)`, never assigned directly.
    ///
    /// This deliberately carries no `didSet`. The `@Observable` macro rewrites
    /// stored properties into computed ones backed by an observation registrar,
    /// and a property cannot have both synthesised accessors and observers, so
    /// the side effect lives in the method instead.
    private(set) var budgetTier: BudgetTier = .strict

    var dailyFoodBudget: Double = BudgetTier.strict.defaultDailyAllowance
    private(set) var hasEditedBudget = false

    /// Switches tier, carrying that tier's default allowance across — until the
    /// user edits the number themselves, after which their value stands.
    func selectBudgetTier(_ tier: BudgetTier) {
        budgetTier = tier
        guard !hasEditedBudget else { return }
        dailyFoodBudget = tier.defaultDailyAllowance
    }

    /// Called by the budget slider, which hands ownership of the number to the user.
    func budgetWasEdited(to value: Double) {
        hasEditedBudget = true
        dailyFoodBudget = max(value, 0)
    }

    // MARK: Imperial projections

    var weightPounds: Double {
        get { UnitConversion.pounds(fromKilograms: weightKg) }
        set { weightKg = UnitConversion.kilograms(fromPounds: newValue) }
    }

    var heightFeet: Int {
        get { UnitConversion.feetAndInches(fromCentimetres: heightCm).feet }
        set { heightCm = UnitConversion.centimetres(fromFeet: newValue, inches: heightInches) }
    }

    var heightInches: Int {
        get { UnitConversion.feetAndInches(fromCentimetres: heightCm).inches }
        set { heightCm = UnitConversion.centimetres(fromFeet: heightFeet, inches: newValue) }
    }

    // MARK: Derived

    var scienceInput: BodyScienceEngine.Input {
        BodyScienceEngine.Input(
            weightKg: weightKg,
            heightCm: heightCm,
            age: age,
            sex: sex,
            activity: activity,
            goal: goal
        )
    }

    /// Live preview. Recomputed on every keystroke — it is a few dozen
    /// floating-point operations, well inside a frame budget.
    var prescription: BodyScienceEngine.Prescription {
        BodyScienceEngine.prescribeUnchecked(for: scienceInput)
    }

    var validationError: BodyScienceEngine.ValidationError? {
        scienceInput.validationError
    }

    /// Estimated weekly spend at the chosen daily allowance.
    var weeklyBudget: Double { dailyFoodBudget * 7 }

    /// What the allowance buys per calorie — the number that tells a user
    /// whether their budget and their goal are compatible at all.
    var costPer1000Calories: Double {
        let calories = prescription.targets.calories
        guard calories > 0 else { return 0 }
        return dailyFoodBudget / (calories / 1_000)
    }

    /// Cheapest plausible daily cost of hitting the protein target on the
    /// selected tier, used to warn when the budget simply cannot work.
    func minimumViableDailyCost(engine: BudgetFoodEngine = BudgetFoodEngine()) -> Double {
        let powerhouses = engine.budgetPowerhouses(tier: budgetTier, limit: 3)
        guard let best = powerhouses.max(by: { $0.proteinPerCurrencyUnit < $1.proteinPerCurrencyUnit }),
              best.proteinPerCurrencyUnit > 0
        else { return 0 }

        // Protein from the single most efficient source, plus a flat allowance
        // for the carbs, fats and vegetables around it. Deliberately rough —
        // it exists to catch "this budget is impossible", not to plan meals.
        let proteinCost = prescription.targets.protein / best.proteinPerCurrencyUnit
        let everythingElse = 2.50
        return proteinCost + everythingElse
    }

    var budgetWarning: String? {
        let minimum = minimumViableDailyCost()
        guard minimum > 0, dailyFoodBudget < minimum else { return nil }
        return "Hitting \(DisplayFormat.grams(prescription.targets.protein)) of protein a day costs around \(DisplayFormat.currency(minimum)) even on the cheapest staples. Consider raising your allowance."
    }

    // MARK: Navigation

    var canAdvance: Bool {
        switch step {
        case .bodyMetrics: validationError == nil
        case .budget: dailyFoodBudget > 0
        default: true
        }
    }

    var isFirstStep: Bool { step == Step.allCases.first }
    var isLastStep: Bool { step == Step.allCases.last }

    var progress: Double {
        guard Step.allCases.count > 1 else { return 1 }
        return Double(step.rawValue) / Double(Step.allCases.count - 1)
    }

    func advance() {
        guard canAdvance,
              let next = Step(rawValue: step.rawValue + 1)
        else { return }
        step = next
    }

    func goBack() {
        guard let previous = Step(rawValue: step.rawValue - 1) else { return }
        step = previous
    }

    // MARK: Loading and saving

    /// Populates the draft from an existing profile, for the editor.
    func load(from profile: UserProfile) {
        displayName = profile.displayName
        measurementSystem = profile.measurementSystem
        heightCm = profile.heightCm
        weightKg = profile.weightKg
        age = profile.age
        sex = profile.sex
        goal = profile.goal
        activity = profile.activity
        hasEditedBudget = true      // an existing budget is the user's, not a default
        budgetTier = profile.budgetTier
        dailyFoodBudget = profile.dailyFoodBudget
    }

    /// Commits the draft. Creates the profile row if one does not exist yet.
    @discardableResult
    func save(to context: ModelContext, existing: UserProfile? = nil) throws -> UserProfile {
        if let error = validationError { throw error }

        let profile = existing ?? UserProfile()
        profile.displayName = displayName
        profile.measurementSystem = measurementSystem
        profile.heightCm = heightCm
        profile.weightKg = weightKg
        profile.age = age
        profile.sex = sex
        profile.goal = goal
        profile.activity = activity
        profile.budgetTier = budgetTier
        profile.dailyFoodBudget = dailyFoodBudget
        profile.hasCompletedOnboarding = true
        profile.touch()

        if existing == nil { context.insert(profile) }
        try context.save()
        return profile
    }
}
