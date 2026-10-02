/*
 * Enums.kt
 * MacroDime
 *
 * Domain vocabulary shared by the science engine, the food engine and the UI.
 * Port of MacroDime/Domain/Enums.swift.
 *
 * Every case carries the iOS raw value (`fatLoss`, `meatAndSeafood`) and that
 * string, not the Kotlin name, is what the database stores. Two reasons: raw
 * values survive a rename of the Kotlin constant, and the two apps then write
 * the same vocabulary, which is what an export format shared between them needs.
 */
package com.lungelo.macrodime.domain

/** An enum persisted by a stable string rather than by its Kotlin name or ordinal. */
interface RawValued {
    val rawValue: String
}

/** The case whose raw value is [raw], or null for an unknown or missing value. */
inline fun <reified E> rawValueOf(raw: String?): E? where E : Enum<E>, E : RawValued =
    enumValues<E>().firstOrNull { it.rawValue == raw }

// MARK: - Biological Sex

/**
 * Captured for one reason only: it is a required term of the Mifflin-St Jeor
 * equation. It is not used anywhere else in the app.
 */
enum class BiologicalSex(override val rawValue: String) : RawValued {
    Male("male"),
    Female("female");

    val displayName: String
        get() = when (this) {
            Male -> "Male"
            Female -> "Female"
        }

    /** The trailing constant of Mifflin-St Jeor: `+5` for males, `-161` for females. */
    val mifflinConstant: Double
        get() = when (this) {
            Male -> 5.0
            Female -> -161.0
        }

    /**
     * Conservative lower bound for sustained daily intake without clinical
     * supervision. The engine refuses to prescribe a target below this.
     */
    val minimumSafeCalories: Double
        get() = when (this) {
            Male -> 1_500.0
            Female -> 1_200.0
        }
}

// MARK: - Goal

/** Drives both the calorie adjustment applied to TDEE and the protein prescription. */
enum class FitnessGoal(override val rawValue: String) : RawValued {
    FatLoss("fatLoss"),
    MuscleGain("muscleGain");

    val displayName: String
        get() = when (this) {
            FatLoss -> "Fat Loss"
            MuscleGain -> "Muscle Gain"
        }

    val subtitle: String
        get() = when (this) {
            FatLoss -> "20% deficit, protein held high to protect lean mass"
            MuscleGain -> "8% surplus, lean gaining to limit fat accrual"
        }

    /** Multiplier applied to TDEE to reach the daily calorie target. */
    val calorieMultiplier: Double
        get() = when (this) {
            FatLoss -> 0.80
            MuscleGain -> 1.08
        }

    /** Protein prescription in grams per kilogram of current body weight. */
    val proteinGramsPerKilogram: Double
        get() = when (this) {
            FatLoss -> 2.0
            MuscleGain -> 1.8
        }
}

// MARK: - Activity Level

/** Standard activity multipliers applied to BMR to reach TDEE. */
enum class ActivityLevel(override val rawValue: String) : RawValued {
    Sedentary("sedentary"),
    LightlyActive("lightlyActive"),
    ModeratelyActive("moderatelyActive"),
    VeryActive("veryActive");

    val multiplier: Double
        get() = when (this) {
            Sedentary -> 1.200
            LightlyActive -> 1.375
            ModeratelyActive -> 1.550
            VeryActive -> 1.725
        }

    val displayName: String
        get() = when (this) {
            Sedentary -> "Sedentary"
            LightlyActive -> "Lightly Active"
            ModeratelyActive -> "Moderately Active"
            VeryActive -> "Very Active"
        }

    val subtitle: String
        get() = when (this) {
            Sedentary -> "Desk job, little or no exercise"
            LightlyActive -> "Light exercise 1-3 days per week"
            ModeratelyActive -> "Moderate exercise 3-5 days per week"
            VeryActive -> "Hard exercise 6-7 days per week"
        }
}

// MARK: - Budget Tier

/**
 * The financial constraint the meal plan must respect.
 *
 * Declared cheapest first, so the enum's natural order is the price order:
 * `minOf(a, b)` is the cheaper tier, and the swap engine can ask "is this
 * candidate at or below the ceiling?" with an ordinary comparison. [rank]
 * states the same order explicitly for anything that persists or displays it.
 */
enum class BudgetTier(override val rawValue: String) : RawValued {
    /** Cheap staples: oats, eggs, lentils, canned tuna, chicken thighs, frozen veg. */
    Strict("strict"),

    /** Fresh cuts, specialty proteins, organic produce. */
    Moderate("moderate");

    val rank: Int get() = ordinal

    val displayName: String
        get() = when (this) {
            Strict -> "Strict Budget"
            Moderate -> "Flexible"
        }

    /** Price-level chip shown in the UI. */
    val priceSymbol: String
        get() = when (this) {
            Strict -> "$"
            Moderate -> "$$"
        }

    val subtitle: String
        get() = when (this) {
            Strict -> "Oats, eggs, lentils, canned tuna, chicken thighs, frozen veg"
            Moderate -> "Fresh cuts, specialty proteins, organic produce"
        }

    /**
     * Starting daily food allowance used to pre-fill onboarding. The user can
     * override it; nothing in the engine depends on this staying the default.
     */
    val defaultDailyAllowance: Double
        get() = when (this) {
            Strict -> 9.00
            Moderate -> 20.00
        }
}

// MARK: - Meal Slot

enum class MealSlot(override val rawValue: String) : RawValued {
    Breakfast("breakfast"),
    Lunch("lunch"),
    Dinner("dinner"),
    Snack("snack");

    val displayName: String
        get() = when (this) {
            Breakfast -> "Breakfast"
            Lunch -> "Lunch"
            Dinner -> "Dinner"
            Snack -> "Snacks"
        }

    /** Display ordering, and the stable sort key for persisted meals. */
    val sortOrder: Int get() = ordinal

    /**
     * Rough share of the daily calorie target, used to label an empty planner
     * slot with a sensible calorie budget.
     */
    val defaultCalorieShare: Double
        get() = when (this) {
            Breakfast -> 0.25
            Lunch -> 0.30
            Dinner -> 0.35
            Snack -> 0.10
        }
}

// MARK: - Grocery Section

/** Supermarket aisle grouping for the smart grocery list. */
enum class GrocerySection(override val rawValue: String) : RawValued {
    Produce("produce"),
    MeatAndSeafood("meatAndSeafood"),
    Pantry("pantry"),
    Frozen("frozen"),
    Dairy("dairy");

    val displayName: String
        get() = when (this) {
            Produce -> "Produce"
            MeatAndSeafood -> "Meat & Seafood"
            Pantry -> "Pantry"
            Frozen -> "Frozen"
            Dairy -> "Dairy & Eggs"
        }

    /**
     * Walk order through a typical store, so the list reads top to bottom the
     * way the user actually shops.
     */
    val aisleOrder: Int
        get() = when (this) {
            Produce -> 0
            MeatAndSeafood -> 1
            Dairy -> 2
            Pantry -> 3
            Frozen -> 4
        }
}

// MARK: - Food Category

/**
 * The nutritional role an ingredient plays in a meal.
 *
 * The swap engine only ever substitutes *within* a category (or, for
 * vegetables, within a culinary family: see [SwapGroup]), which is what keeps
 * "Salmon to Canned Tuna" sensible and "Salmon to Rolled Oats" impossible even
 * though the two could be macro-matched on paper.
 */
enum class FoodCategory(override val rawValue: String) : RawValued {
    ProteinAnchor("proteinAnchor"),
    CarbBase("carbBase"),
    FatSource("fatSource"),
    Vegetable("vegetable"),
    Fruit("fruit"),
    Dairy("dairy"),
    Condiment("condiment");

    val displayName: String
        get() = when (this) {
            ProteinAnchor -> "Protein"
            CarbBase -> "Carbs"
            FatSource -> "Fats"
            Vegetable -> "Vegetables"
            Fruit -> "Fruit"
            Dairy -> "Dairy"
            Condiment -> "Condiments"
        }
}

// MARK: - Measurement System

enum class MeasurementSystem(override val rawValue: String) : RawValued {
    Metric("metric"),
    Imperial("imperial");

    val displayName: String
        get() = when (this) {
            Metric -> "Metric (kg / cm)"
            Imperial -> "Imperial (lb / ft-in)"
        }
}
