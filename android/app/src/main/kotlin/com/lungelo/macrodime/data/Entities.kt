/*
 * Entities.kt
 * MacroDime
 *
 * The Room schema. One table per SwiftData model in the iOS app
 * (MacroDime/Persistence), with the same fields and the same conventions:
 *
 *  - Enums are stored as their iOS raw value strings ("fatLoss"), never as an
 *    ordinal, so reordering a Kotlin enum can never change what a row means.
 *  - Body metrics are always metric; money is always USD.
 *  - A day is stored as a LocalDate epoch day rather than a timestamp, so a
 *    plan cannot drift to the neighbouring date when the phone changes time
 *    zone.
 *
 * Delete rules match iOS: a plan owns its meals and a meal its portions
 * (cascade), while a portion only references a food (set null), so retiring a
 * food never erases the history of what was eaten.
 */
package com.lungelo.macrodime.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val hasCompletedOnboarding: Boolean,
    val hasAcknowledgedHealthDisclaimer: Boolean,
    val heightCm: Double,
    val weightKg: Double,
    val age: Int,
    val sexRaw: String,
    val goalRaw: String,
    val activityRaw: String,
    val budgetTierRaw: String,
    val measurementSystemRaw: String,
    /** Daily food allowance in USD, the catalogue's currency. Display converts. */
    val dailyFoodBudget: Double,
    val currencyCodeRaw: String,
    /** Units of [currencyCodeRaw] per 1 USD, typed by the user. 1 means no conversion. */
    val currencyUnitsPerUSD: Double,
    val dietaryPatternRaw: String,
    /** FoodExclusion raw values, comma separated, sorted. */
    val foodExclusionsRaw: String,
    /** Catalogue ids the user has banned, comma separated, sorted. */
    val blockedFoodIds: String,
    val eatingScheduleRaw: String,
    /** Defaults to the unconstrained answer: an unasked question filters nothing. */
    val prepEffortRaw: String,
    val mealsOutPerWeek: Int,
)

/** A non-BMI progress entry: waist, hips, weight and an optional photo. */
@Entity(
    tableName = "body_measurement",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val recordedAt: Long,
    val weightKg: Double?,
    val waistCm: Double?,
    val hipCm: Double?,
    val notes: String,
    /**
     * A file name inside the app's private photo folder, never a path or a
     * content URI. The image is copied in when picked, so it survives the
     * original being deleted from the gallery, and leaves with the app.
     */
    val photoFileName: String?,
)

/**
 * The persisted ingredient. The curated catalogue is seeded into this table so
 * a portion can hold a real reference; user-created foods would live alongside.
 */
@Entity(tableName = "food_item")
data class FoodItemEntity(
    @PrimaryKey val catalogId: String,
    val name: String,
    val sectionRaw: String,
    val categoryRaw: String,
    val swapGroupRaw: String,
    val costTierRaw: String,
    val costPerServing: Double,
    val servingDescription: String,
    val servingGrams: Double,
    val calories: Double,
    val proteinGrams: Double,
    val carbGrams: Double,
    val fatGrams: Double,
    val satietyIndex: Double,
    val isSwapCandidate: Boolean,
    /** FoodTraits bit pattern, the same bits as iOS. */
    val traitsRaw: Int,
    val prepMinutes: Int,
    val isUserCreated: Boolean,
    val isFavourite: Boolean,
)

/** One day's plan. */
@Entity(tableName = "meal_plan", indices = [Index(value = ["day"], unique = true)])
data class MealPlanEntity(
    @PrimaryKey val id: String,
    /** LocalDate.toEpochDay(). Unique: a day has one plan. */
    val day: Long,
    val notes: String,
    val createdAt: Long,
)

/** One slot of a day. Unique per plan and slot, as the iOS `meal(for:)` lookup assumes. */
@Entity(
    tableName = "planned_meal",
    foreignKeys = [
        ForeignKey(
            entity = MealPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["planId", "slotRaw"], unique = true)],
)
data class PlannedMealEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val name: String,
    val slotRaw: String,
    /** Set when a low-cost swap was accepted, so the change is auditable. */
    val wasSwapped: Boolean,
    val swapSavings: Double,
)

@Entity(
    tableName = "meal_portion",
    foreignKeys = [
        ForeignKey(
            entity = PlannedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["catalogId"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("mealId"), Index("foodId")],
)
data class MealPortionEntity(
    @PrimaryKey val id: String,
    val mealId: String,
    val foodId: String?,
    val servings: Double,
    val addedAt: Long,
    /** Cached so a meal stays readable if its food row is ever deleted. */
    val foodName: String,
)

/** One line of a week's shopping list. */
@Entity(tableName = "grocery_item", indices = [Index("weekStart")])
data class GroceryItemEntity(
    @PrimaryKey val id: String,
    /** Catalogue id of the source ingredient, or "" for a manually added line. */
    val sourceFoodId: String,
    val name: String,
    val sectionRaw: String,
    val quantityDescription: String,
    val totalServings: Double,
    val estimatedCost: Double,
    val isChecked: Boolean,
    /** "Already have this": excluded from what is still to spend, and kept across regeneration. */
    val isAlreadyOwned: Boolean,
    /** Epoch day of the first day of the week this list covers. */
    val weekStart: Long,
    val createdAt: Long,
) {
    /** What still has to be spent: an owned item costs nothing to buy. */
    val outstandingCost: Double get() = if (isAlreadyOwned) 0.0 else estimatedCost
}

// Relations, read as one tree per day.

data class PortionWithFood(
    @Embedded val portion: MealPortionEntity,
    @Relation(parentColumn = "foodId", entityColumn = "catalogId")
    val food: FoodItemEntity?,
)

data class MealWithPortions(
    @Embedded val meal: PlannedMealEntity,
    @Relation(entity = MealPortionEntity::class, parentColumn = "id", entityColumn = "mealId")
    val portions: List<PortionWithFood>,
)

data class PlanWithMeals(
    @Embedded val plan: MealPlanEntity,
    @Relation(entity = PlannedMealEntity::class, parentColumn = "id", entityColumn = "planId")
    val meals: List<MealWithPortions>,
)
