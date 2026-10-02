/*
 * Mappers.kt
 * MacroDime
 *
 * The boundary between rows and the engine's value types, the Android
 * counterpart of FoodItem.snapshot and the typed accessors on the iOS models.
 * Every enum read falls back to a safe default rather than crashing on a value
 * it does not recognise (a downgrade, or a damaged store).
 */
package com.lungelo.macrodime.data

import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.BiologicalSex
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.CurrencySettings
import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.EatingSchedule
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.FoodTraits
import com.lungelo.macrodime.domain.GrocerySection
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.MeasurementSystem
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.domain.PriceBook
import com.lungelo.macrodime.domain.SwapGroup
import com.lungelo.macrodime.domain.rawValueOf
import com.lungelo.macrodime.engine.BodyScienceEngine
import java.util.UUID

// Lists stored in one column. Catalogue ids and raw values never contain commas.

internal fun List<String>.joinedForColumn(): String = sorted().joinToString(",")

internal fun String.splitColumn(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }

// Food

val FoodItemEntity.category: FoodCategory get() = rawValueOf<FoodCategory>(categoryRaw) ?: FoodCategory.CarbBase

/**
 * The engine's copy of a stored food. An unknown swap group falls back to the
 * category's default, which for a vegetable is Unclassified (no substitutes),
 * never "any vegetable may replace any other".
 */
fun FoodItemEntity.toSnapshot(): FoodSnapshot {
    val category = category
    return FoodSnapshot(
        id = catalogId,
        name = name,
        section = rawValueOf<GrocerySection>(sectionRaw) ?: GrocerySection.Pantry,
        category = category,
        swapGroup = rawValueOf<SwapGroup>(swapGroupRaw) ?: SwapGroup.defaultFor(category),
        costTier = rawValueOf<BudgetTier>(costTierRaw) ?: BudgetTier.Strict,
        costPerServing = costPerServing,
        servingDescription = servingDescription,
        servingGrams = servingGrams,
        nutrition = NutritionFacts(calories, proteinGrams, carbGrams, fatGrams),
        satietyIndex = satietyIndex,
        isSwapCandidate = isSwapCandidate,
        traits = FoodTraits(traitsRaw),
        prepMinutes = prepMinutes,
    )
}

/** A row for a curated food. [existing] keeps the user's own fields (the favourite flag). */
fun FoodSnapshot.toEntity(existing: FoodItemEntity? = null) = FoodItemEntity(
    catalogId = id,
    name = name,
    sectionRaw = section.rawValue,
    categoryRaw = category.rawValue,
    swapGroupRaw = swapGroup.rawValue,
    costTierRaw = costTier.rawValue,
    costPerServing = costPerServing,
    servingDescription = servingDescription,
    servingGrams = servingGrams,
    calories = nutrition.calories,
    proteinGrams = nutrition.protein,
    carbGrams = nutrition.carbs,
    fatGrams = nutrition.fat,
    satietyIndex = satietyIndex,
    isSwapCandidate = isSwapCandidate,
    traitsRaw = traits.rawValue,
    prepMinutes = prepMinutes,
    isUserCreated = existing?.isUserCreated ?: false,
    isFavourite = existing?.isFavourite ?: false,
)

// Plans

val PlannedMealEntity.slot: MealSlot get() = rawValueOf<MealSlot>(slotRaw) ?: MealSlot.Snack

/**
 * The engine's view of one meal. Portions whose food row has gone are dropped,
 * as on iOS; the order is the order they were added.
 */
fun MealWithPortions.toMealItem(): MealItem = MealItem(
    name = meal.name,
    slot = meal.slot,
    portions = portions
        .sortedWith(compareBy<PortionWithFood> { it.portion.addedAt }.thenBy { it.portion.id })
        .mapNotNull { row ->
            val food = row.food ?: return@mapNotNull null
            Portion(food = food.toSnapshot(), servings = row.portion.servings, id = UUID.fromString(row.portion.id))
        },
    id = UUID.fromString(meal.id),
)

/** A day's meals in slot order. */
fun PlanWithMeals?.toMealItems(): List<MealItem> =
    this?.meals.orEmpty().map { it.toMealItem() }.sortedBy { it.slot.sortOrder }

// Profile

val UserProfileEntity.sex: BiologicalSex get() = rawValueOf<BiologicalSex>(sexRaw) ?: BiologicalSex.Male
val UserProfileEntity.goal: FitnessGoal get() = rawValueOf<FitnessGoal>(goalRaw) ?: FitnessGoal.FatLoss
val UserProfileEntity.activity: ActivityLevel
    get() = rawValueOf<ActivityLevel>(activityRaw) ?: ActivityLevel.LightlyActive
val UserProfileEntity.budgetTier: BudgetTier get() = rawValueOf<BudgetTier>(budgetTierRaw) ?: BudgetTier.Strict
val UserProfileEntity.measurementSystem: MeasurementSystem
    get() = rawValueOf<MeasurementSystem>(measurementSystemRaw) ?: MeasurementSystem.Metric

/** How this profile wants money shown. An empty or unusable stored value reads as plain USD. */
val UserProfileEntity.currency: CurrencySettings
    get() = CurrencySettings(currencyCodeRaw.ifEmpty { PriceBook.CURRENCY_CODE }, currencyUnitsPerUSD)

/** The dietary rules this profile plans by. Unknown values degrade to the unrestricted answer. */
val UserProfileEntity.dietaryProfile: DietaryProfile
    get() = DietaryProfile(
        pattern = rawValueOf<DietaryPattern>(dietaryPatternRaw) ?: DietaryPattern.Omnivore,
        exclusions = foodExclusionsRaw.splitColumn().mapNotNull { rawValueOf<FoodExclusion>(it) }.toSet(),
        blockedFoodIds = blockedFoodIds.splitColumn().toSet(),
        schedule = rawValueOf<EatingSchedule>(eatingScheduleRaw) ?: EatingSchedule.ThreeMeals,
        prepEffort = rawValueOf<PrepEffort>(prepEffortRaw) ?: PrepEffort.Unlimited,
        mealsOutPerWeek = mealsOutPerWeek,
    )

fun UserProfileEntity.withDietaryProfile(diet: DietaryProfile) = copy(
    dietaryPatternRaw = diet.pattern.rawValue,
    foodExclusionsRaw = diet.exclusions.map { it.rawValue }.joinedForColumn(),
    blockedFoodIds = diet.blockedFoodIds.toList().joinedForColumn(),
    eatingScheduleRaw = diet.schedule.rawValue,
    prepEffortRaw = diet.prepEffort.rawValue,
    mealsOutPerWeek = diet.mealsOutPerWeek,
)

val UserProfileEntity.scienceInput: BodyScienceEngine.Input
    get() = BodyScienceEngine.Input(weightKg, heightCm, age, sex, activity, goal)

/**
 * Recomputed on demand, as on iOS: a handful of multiplications, and a cached
 * prescription would go stale after a weight update.
 */
val UserProfileEntity.prescription: BodyScienceEngine.Prescription
    get() = BodyScienceEngine.prescribeUnchecked(scienceInput)

// Measurements

/** Waist-to-hip ratio when both are recorded: a better health signal than BMI alone. */
val BodyMeasurementEntity.waistToHipRatio: Double?
    get() = if (waistCm != null && hipCm != null && hipCm > 0) waistCm / hipCm else null

/** Change in waist since the earliest entry that recorded one, in cm. Negative is down. */
fun List<BodyMeasurementEntity>.waistChangeCm(): Double? {
    val withWaist = filter { it.waistCm != null }.sortedBy { it.recordedAt }
    if (withWaist.size < 2) return null
    return withWaist.last().waistCm!! - withWaist.first().waistCm!!
}
