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
        case diet
        case routine
        case budget
        case summary

        var id: Int { rawValue }

        var title: String {
            switch self {
            case .welcome: "Welcome"
            case .bodyMetrics: "About You"
            case .goal: "Your Goal"
            case .activity: "Activity Level"
            case .diet: "What You Eat"
            case .routine: "How You Eat"
            case .budget: "Your Budget"
            case .summary: "Your Plan"
            }
        }

        var subtitle: String {
            switch self {
            case .welcome: "Real nutrition targets that fit what you can actually spend"
            case .bodyMetrics: "Used to calculate your metabolic rate"
            case .goal: "This sets your calorie adjustment and protein target"
            case .activity: "Be honest, most people overestimate this one"
            case .diet: "So the plan never suggests something you will not eat"
            case .routine: "How many meals, and how much cooking you will actually do"
            case .budget: "Every meal suggestion respects this constraint"
            case .summary: "Here is what the numbers say, and what does not add up"
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

    // MARK: Dietary draft
    //
    // Defaults here are the *asked* defaults, not the model's unconstrained
    // ones: the wizard presents "3 meals, under 30 minutes" as the common
    // answer, and changing either is one tap. `DietaryProfile`'s own defaults
    // stay unconstrained so that a profile which never answers filters nothing.

    var dietaryPattern: DietaryPattern = .omnivore
    var foodExclusions: Set<FoodExclusion> = []
    var blockedFoodIDs: Set<String> = []
    var eatingSchedule: EatingSchedule = .threeMeals
    var prepEffort: PrepEffort = .standard
    var mealsOutPerWeek: Int = 0

    /// The draft as the engine's filter sees it.
    var dietaryProfile: DietaryProfile {
        DietaryProfile(
            pattern: dietaryPattern,
            exclusions: foodExclusions,
            blockedFoodIDs: blockedFoodIDs,
            schedule: eatingSchedule,
            prepEffort: prepEffort,
            mealsOutPerWeek: mealsOutPerWeek
        )
    }

    /// Foods the current answers would remove, for the live count on the diet
    /// step. Cheap enough to recompute on every toggle: 57 comparisons.
    var excludedFoodCount: Int {
        DietaryFilter.rejected(FoodCatalog.all, under: dietaryProfile).count
    }

    func toggle(_ exclusion: FoodExclusion) {
        if foodExclusions.contains(exclusion) {
            foodExclusions.remove(exclusion)
        } else {
            foodExclusions.insert(exclusion)
        }
    }

    func toggleBlocked(_ foodID: String) {
        if blockedFoodIDs.contains(foodID) {
            blockedFoodIDs.remove(foodID)
        } else {
            blockedFoodIDs.insert(foodID)
        }
    }

    /// What the summary step shows before anything is committed: the gaps in
    /// this combination of target, budget and restrictions.
    var feasibility: PlanAudit.Report {
        PlanAudit.feasibility(
            targets: prescription.targets,
            dailyBudgetUSD: dailyFoodBudget,
            dietary: dietaryProfile,
            currency: currency
        )
    }

    /// The currency the wizard displays amounts in. Held on the draft because
    /// onboarding runs before a profile exists to read it from.
    var currency: CurrencySettings = .usd

    /// Changed through `selectBudgetTier(_:)`, never assigned directly.
    ///
    /// This deliberately carries no `didSet`. The `@Observable` macro rewrites
    /// stored properties into computed ones backed by an observation registrar,
    /// and a property cannot have both synthesised accessors and observers, so
    /// the side effect lives in the method instead.
    private(set) var budgetTier: BudgetTier = .strict

    /// Gates the final step. Required by App Review guideline 1.4.1, and
    /// the honest thing to do for an app that prescribes a deficit.
    var hasAcknowledgedDisclaimer = false

    var dailyFoodBudget: Double = BudgetTier.strict.defaultDailyAllowance
    private(set) var hasEditedBudget = false

    /// Switches tier, carrying that tier's default allowance across, until the
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

    /// Live preview. Recomputed on every keystroke, it is a few dozen
    /// floating-point operations, well inside a frame budget.
    var prescription: BodyScienceEngine.Prescription {
        BodyScienceEngine.prescribeUnchecked(for: scienceInput)
    }

    var validationError: BodyScienceEngine.ValidationError? {
        scienceInput.validationError
    }

    /// Estimated weekly spend at the chosen daily allowance.
    var weeklyBudget: Double { dailyFoodBudget * 7 }

    /// What the allowance buys per calorie, the number that tells a user
    /// whether their budget and their goal are compatible at all.
    var costPer1000Calories: Double {
        let calories = prescription.targets.calories
        guard calories > 0 else { return 0 }
        return dailyFoodBudget / (calories / 1_000)
    }

    /// Cheapest plausible daily cost of hitting the protein target with the
    /// foods this profile may actually eat.
    ///
    /// Delegates to `PlanAudit.minimumDailyCostUSD` so the number quoted here
    /// and the number in the feasibility gap are computed once, from the same
    /// rule. A vegan who excludes soy sees a different figure from an omnivore,
    /// which is the point.
    func minimumViableDailyCost() -> Double {
        PlanAudit.minimumDailyCostUSD(targets: prescription.targets, dietary: dietaryProfile)?.cost ?? 0
    }

    /// Quoted in the draft's own currency. It used to be the one figure on the
    /// screen formatted in USD whatever the setting, so a profile edited in rand
    /// showed a dollar warning beside rand meters.
    var budgetWarning: String? {
        let minimum = minimumViableDailyCost()
        guard minimum > 0, dailyFoodBudget < minimum else { return nil }
        return "Hitting \(DisplayFormat.grams(prescription.targets.protein)) of protein a day costs around \(currency.format(minimum)) even on the cheapest foods you allow. Consider raising your allowance."
    }

    // MARK: Navigation

    var canAdvance: Bool {
        switch step {
        case .bodyMetrics: validationError == nil
        case .budget: dailyFoodBudget > 0
        case .summary: hasAcknowledgedDisclaimer && validationError == nil
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
        let diet = profile.dietaryProfile
        dietaryPattern = diet.pattern
        foodExclusions = diet.exclusions
        blockedFoodIDs = diet.blockedFoodIDs
        eatingSchedule = diet.schedule
        prepEffort = diet.prepEffort
        mealsOutPerWeek = diet.mealsOutPerWeek
        currency = profile.currency
        hasAcknowledgedDisclaimer = profile.hasAcknowledgedHealthDisclaimer
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
        profile.dietaryProfile = dietaryProfile
        profile.currency = currency
        profile.hasCompletedOnboarding = true
        profile.hasAcknowledgedHealthDisclaimer = hasAcknowledgedDisclaimer
        profile.touch()

        if existing == nil { context.insert(profile) }
        try context.save()
        return profile
    }
}
