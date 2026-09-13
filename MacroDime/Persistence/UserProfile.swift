//
//  UserProfile.swift
//  MacroDime
//
//  The single user record, plus the non-BMI progress measurements attached to
//  it. Enums are stored as raw strings with typed computed accessors — the
//  stored property is what SwiftData persists and predicates against, the
//  computed one is what the rest of the app uses.
//

import Foundation
import SwiftData

@Model
final class UserProfile {

    // MARK: Identity

    /// Stable id independent of the SwiftData persistent identifier, so a
    /// profile survives an export/import round trip.
    @Attribute(.unique) var id: UUID
    var displayName: String
    var createdAt: Date
    var updatedAt: Date
    var hasCompletedOnboarding: Bool
    /// Set when the user accepts the health disclaimer during onboarding.
    /// Defaulted so the property is additive for SwiftData's lightweight
    /// migration rather than a schema break.
    var hasAcknowledgedHealthDisclaimer: Bool = false

    // MARK: Body metrics (always stored metric)

    var heightCm: Double
    var weightKg: Double
    var age: Int

    // MARK: Raw-value backed enums

    var sexRaw: String
    var goalRaw: String
    var activityRaw: String
    var budgetTierRaw: String
    var measurementSystemRaw: String

    // MARK: Budget

    /// Daily food allowance in the user's currency. Seeded from the tier
    /// default at onboarding, then owned by the user.
    var dailyFoodBudget: Double

    // MARK: Progress

    @Relationship(deleteRule: .cascade, inverse: \BodyMeasurement.profile)
    var measurements: [BodyMeasurement]

    // MARK: Init

    init(
        id: UUID = UUID(),
        displayName: String = "",
        heightCm: Double = 175,
        weightKg: Double = 75,
        age: Int = 30,
        sex: BiologicalSex = .male,
        goal: FitnessGoal = .fatLoss,
        activity: ActivityLevel = .lightlyActive,
        budgetTier: BudgetTier = .strict,
        measurementSystem: MeasurementSystem = .metric,
        dailyFoodBudget: Double? = nil,
        hasCompletedOnboarding: Bool = false,
        hasAcknowledgedHealthDisclaimer: Bool = false
    ) {
        self.id = id
        self.displayName = displayName
        self.createdAt = .now
        self.updatedAt = .now
        self.hasCompletedOnboarding = hasCompletedOnboarding
        self.hasAcknowledgedHealthDisclaimer = hasAcknowledgedHealthDisclaimer
        self.heightCm = heightCm
        self.weightKg = weightKg
        self.age = age
        self.sexRaw = sex.rawValue
        self.goalRaw = goal.rawValue
        self.activityRaw = activity.rawValue
        self.budgetTierRaw = budgetTier.rawValue
        self.measurementSystemRaw = measurementSystem.rawValue
        self.dailyFoodBudget = dailyFoodBudget ?? budgetTier.defaultDailyAllowance
        self.measurements = []
    }

    // MARK: Typed accessors
    //
    // Each falls back to a safe default rather than crashing if the stored
    // string is ever unrecognised (a downgrade, or a hand-edited store).

    var sex: BiologicalSex {
        get { BiologicalSex(rawValue: sexRaw) ?? .male }
        set { sexRaw = newValue.rawValue }
    }

    var goal: FitnessGoal {
        get { FitnessGoal(rawValue: goalRaw) ?? .fatLoss }
        set { goalRaw = newValue.rawValue }
    }

    var activity: ActivityLevel {
        get { ActivityLevel(rawValue: activityRaw) ?? .lightlyActive }
        set { activityRaw = newValue.rawValue }
    }

    var budgetTier: BudgetTier {
        get { BudgetTier(rawValue: budgetTierRaw) ?? .strict }
        set { budgetTierRaw = newValue.rawValue }
    }

    var measurementSystem: MeasurementSystem {
        get { MeasurementSystem(rawValue: measurementSystemRaw) ?? .metric }
        set { measurementSystemRaw = newValue.rawValue }
    }

    // MARK: Derived

    /// The engine input this profile represents.
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

    /// Current prescription. Recomputed on demand — it is a handful of
    /// floating-point operations, so caching it would buy nothing and risk the
    /// targets going stale after a weight update.
    var prescription: BodyScienceEngine.Prescription {
        BodyScienceEngine.prescribeUnchecked(for: scienceInput)
    }

    /// Most recent measurement entry, if any.
    var latestMeasurement: BodyMeasurement? {
        measurements.max { $0.recordedAt < $1.recordedAt }
    }

    /// Change in waist since the earliest recorded measurement, in cm.
    /// Negative means the waist has come down.
    var waistChangeCm: Double? {
        let withWaist = measurements
            .filter { $0.waistCm != nil }
            .sorted { $0.recordedAt < $1.recordedAt }
        guard let first = withWaist.first?.waistCm,
              let last = withWaist.last?.waistCm,
              withWaist.count > 1
        else { return nil }
        return last - first
    }

    func touch() { updatedAt = .now }
}

// MARK: - Body Measurement

/// A non-BMI progress entry. Waist circumference and photos are tracked
/// precisely because BMI cannot distinguish muscle from fat — these are the
/// metrics that actually move when body composition changes.
@Model
final class BodyMeasurement {

    @Attribute(.unique) var id: UUID
    var recordedAt: Date
    var weightKg: Double?
    var waistCm: Double?
    var hipCm: Double?
    var notes: String

    /// Progress photo. `.externalStorage` keeps the image bytes out of the
    /// SQLite row, so fetching a year of measurements stays cheap.
    @Attribute(.externalStorage) var photoData: Data?

    var profile: UserProfile?

    init(
        id: UUID = UUID(),
        recordedAt: Date = .now,
        weightKg: Double? = nil,
        waistCm: Double? = nil,
        hipCm: Double? = nil,
        notes: String = "",
        photoData: Data? = nil
    ) {
        self.id = id
        self.recordedAt = recordedAt
        self.weightKg = weightKg
        self.waistCm = waistCm
        self.hipCm = hipCm
        self.notes = notes
        self.photoData = photoData
    }

    /// Waist-to-hip ratio when both are recorded — a better health signal than
    /// BMI alone, and the reason hip is captured at all.
    var waistToHipRatio: Double? {
        guard let waistCm, let hipCm, hipCm > 0 else { return nil }
        return waistCm / hipCm
    }

    var hasPhoto: Bool { photoData != nil }
}
