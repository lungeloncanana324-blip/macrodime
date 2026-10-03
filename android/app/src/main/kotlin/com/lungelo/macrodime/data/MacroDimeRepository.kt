/*
 * MacroDimeRepository.kt
 * MacroDime
 *
 * Every write the app makes, in one place. The Android counterpart of the
 * persistence half of the iOS view models (MealPlannerViewModel, the grocery
 * service, data deletion), with the same rules:
 *
 *  - The engine works on value types and the repository writes the result
 *    back, so a swap can be previewed in full before anything is stored.
 *  - Regenerating a grocery list never loses what the user ticked or already
 *    owns.
 *  - Deleting all data is complete, photos included, because nothing was ever
 *    anywhere but this phone.
 */
package com.lungelo.macrodime.data

import androidx.room.withTransaction
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.engine.FoodCatalog
import com.lungelo.macrodime.engine.GroceryListBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

class MacroDimeRepository(
    private val database: MacroDimeDatabase,
    private val photos: PhotoStore?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.dao()

    private fun newId() = UUID.randomUUID().toString()

    // Reads

    val profile: Flow<UserProfileEntity?> = dao.observeProfile()

    val foods: Flow<List<FoodItemEntity>> = dao.observeFoods()

    val totalSwapSavings: Flow<Double> = dao.observeTotalSwapSavings()

    fun meals(day: LocalDate): Flow<List<MealItem>> = dao.observePlan(day.toEpochDay()).map { it.toMealItems() }

    fun measurements(profileId: String): Flow<List<BodyMeasurementEntity>> = dao.observeMeasurements(profileId)

    fun groceries(weekStart: LocalDate): Flow<List<GroceryItemEntity>> = dao.observeGroceries(weekStart.toEpochDay())

    suspend fun currentProfile(): UserProfileEntity? = dao.profile()

    /** One read of a day's meals, for code that acts rather than watches. */
    suspend fun mealsOnce(day: LocalDate): List<MealItem> = dao.planWithMeals(day.toEpochDay()).toMealItems()

    // Catalogue

    /**
     * Mirrors the curated catalogue into the database, idempotently, on every
     * launch. Compares values rather than a version number: the iOS seeder
     * only reruns when a hand-bumped catalogVersion rises, so a monthly price
     * refresh that changes no version never reaches an existing install. Here
     * any changed value is written, and the read costs one query of 57 rows.
     *
     * Rows are upserted, not replaced: REPLACE deletes the row first, which
     * would null every meal portion pointing at it. Curated foods retired from
     * the catalogue are left in place, because a past meal may still use them.
     */
    suspend fun seedCatalog(): Int {
        val existing = dao.curatedFoods().associateBy { it.catalogId }
        val changed = FoodCatalog.all.mapNotNull { food ->
            val current = existing[food.id]
            val wanted = food.toEntity(current)
            if (current == wanted) null else wanted
        }
        if (changed.isNotEmpty()) dao.upsertFoods(changed)
        return changed.size
    }

    // Profile

    suspend fun saveProfile(profile: UserProfileEntity) {
        dao.upsertProfile(profile.copy(updatedAt = clock()))
    }

    // Meals

    /** Adds a food to a slot, creating the day and the meal on first use. A food already there gains servings. */
    suspend fun addFood(day: LocalDate, slot: MealSlot, foodId: String, servings: Double) {
        database.withTransaction {
            val food = dao.food(foodId) ?: throw IllegalStateException("That ingredient is no longer in your catalogue.")
            val now = clock()
            val plan = dao.planForDay(day.toEpochDay())
                ?: MealPlanEntity(newId(), day.toEpochDay(), notes = "", createdAt = now).also { dao.insertPlan(it) }
            val meal = dao.mealInSlot(plan.id, slot.rawValue)
                ?: PlannedMealEntity(newId(), plan.id, slot.displayName, slot.rawValue, wasSwapped = false, swapSavings = 0.0)
                    .also { dao.insertMeal(it) }

            val existing = dao.portionsInMeal(meal.id).firstOrNull { it.foodId == foodId }
            if (existing != null) {
                dao.updatePortion(existing.copy(servings = existing.servings + servings))
            } else {
                dao.insertPortion(MealPortionEntity(newId(), meal.id, foodId, servings, now, food.name))
            }
        }
    }

    suspend fun removePortion(portionId: UUID) {
        dao.deletePortion(portionId.toString())
    }

    /** Zero or fewer servings removes the portion. */
    suspend fun updateServings(portionId: UUID, servings: Double) {
        if (servings <= 0) {
            removePortion(portionId)
            return
        }
        val portion = dao.portion(portionId.toString()) ?: return
        dao.updatePortion(portion.copy(servings = servings))
    }

    /**
     * Writes an engine result over its stored meal, reusing portion rows where
     * the ingredient is unchanged so their order holds. Used for a whole-meal
     * swap and for a single-ingredient one alike: either way it is the swap's
     * own resulting meal that is written, rebalanced oil and rice included,
     * because the replacement alone would leave the meal outside the tolerance
     * the user was shown.
     */
    suspend fun applySwap(mealId: UUID, result: MealItem, savings: Double) {
        database.withTransaction {
            val meal = dao.meal(mealId.toString()) ?: return@withTransaction
            val existing = dao.portionsInMeal(meal.id)
            val surviving = result.portions.map { it.id.toString() }.toSet()
            existing.filter { it.id !in surviving }.forEach { dao.deletePortion(it.id) }

            for (incoming in result.portions) {
                val row = existing.firstOrNull { it.id == incoming.id.toString() }
                if (row != null) {
                    var updated = row.copy(servings = incoming.servings)
                    if (row.foodId != incoming.food.id) {
                        updated = updated.copy(foodId = dao.food(incoming.food.id)?.catalogId, foodName = incoming.food.name)
                    }
                    dao.updatePortion(updated)
                } else if (dao.food(incoming.food.id) != null) {
                    dao.insertPortion(
                        MealPortionEntity(incoming.id.toString(), meal.id, incoming.food.id, incoming.servings, clock(), incoming.food.name),
                    )
                }
            }
            dao.updateMeal(meal.copy(name = result.name, wasSwapped = true, swapSavings = meal.swapSavings + savings))
        }
    }

    suspend fun renameMeal(day: LocalDate, slot: MealSlot, name: String) {
        val plan = dao.planForDay(day.toEpochDay()) ?: return
        val meal = dao.mealInSlot(plan.id, slot.rawValue) ?: return
        dao.updateMeal(meal.copy(name = name))
    }

    // Groceries

    /**
     * Rebuilds the list for the week containing [date] from that week's plans.
     *
     * User state survives: rows are matched by source food and updated in
     * place, so adding a meal on Thursday never unticks what was bought on
     * Monday. Lines no meal needs any more are removed; manually added lines
     * (no source food) never are.
     */
    suspend fun regenerateGroceries(date: LocalDate, locale: Locale = Locale.getDefault()) {
        val start = weekStart(date, locale)
        database.withTransaction {
            val meals = dao.plansBetween(start.toEpochDay(), start.plusDays(7).toEpochDay()).flatMap { it.toMealItems() }
            val lines = GroceryListBuilder.lines(meals)
            val existing = dao.groceries(start.toEpochDay())

            // First row per food wins; any duplicate is cleaned up below.
            val byFood = LinkedHashMap<String, GroceryItemEntity>()
            for (row in existing) byFood.putIfAbsent(row.sourceFoodId, row)

            val now = clock()
            val kept = lines.map { line ->
                val row = byFood.remove(line.id)
                row?.copy(
                    name = line.name,
                    sectionRaw = line.section.rawValue,
                    quantityDescription = line.quantityDescription,
                    totalServings = line.totalServings,
                    estimatedCost = line.estimatedCost,
                ) ?: GroceryItemEntity(
                    id = newId(),
                    sourceFoodId = line.id,
                    name = line.name,
                    sectionRaw = line.section.rawValue,
                    quantityDescription = line.quantityDescription,
                    totalServings = line.totalServings,
                    estimatedCost = line.estimatedCost,
                    isChecked = false,
                    isAlreadyOwned = false,
                    weekStart = start.toEpochDay(),
                    createdAt = now,
                )
            }
            dao.upsertGroceries(kept)

            val keptIds = kept.map { it.id }.toSet()
            val orphans = existing.filter { it.id !in keptIds && it.sourceFoodId.isNotEmpty() }
            if (orphans.isNotEmpty()) dao.deleteGroceries(orphans)
        }
    }

    suspend fun setChecked(itemId: String, checked: Boolean) {
        val item = dao.grocery(itemId) ?: return
        dao.upsertGroceries(listOf(item.copy(isChecked = checked)))
    }

    suspend fun setAlreadyOwned(itemId: String, owned: Boolean) {
        val item = dao.grocery(itemId) ?: return
        dao.upsertGroceries(listOf(item.copy(isAlreadyOwned = owned)))
    }

    suspend fun deleteGrocery(itemId: String) {
        val item = dao.grocery(itemId) ?: return
        dao.deleteGroceries(listOf(item))
    }

    suspend fun uncheckWeek(weekStart: LocalDate) {
        dao.uncheckWeek(weekStart.toEpochDay())
    }

    // Progress

    /**
     * Records a measurement. A new weight is also the profile's current weight:
     * the prescription depends on it, and leaving the profile stale would keep
     * the user on last month's targets.
     */
    suspend fun logMeasurement(
        profile: UserProfileEntity,
        weightKg: Double?,
        waistCm: Double?,
        hipCm: Double?,
        notes: String,
        photoFileName: String?,
    ) {
        database.withTransaction {
            dao.insertMeasurement(
                BodyMeasurementEntity(newId(), profile.id, clock(), weightKg, waistCm, hipCm, notes, photoFileName),
            )
            if (weightKg != null) {
                val current = dao.profile() ?: profile
                dao.upsertProfile(current.copy(weightKg = weightKg, updatedAt = clock()))
            }
        }
    }

    /** A first measurement from the profile's own weight, for the demo day. */
    internal suspend fun logBaselineIfEmpty(profile: UserProfileEntity, waistCm: Double) {
        if (dao.measurementCount() > 0) return
        dao.insertMeasurement(
            BodyMeasurementEntity(newId(), profile.id, clock(), profile.weightKg, waistCm, null, "Baseline", null),
        )
    }

    // Deleting everything

    /**
     * Removes every record the user created, and every stored photo. The curated
     * catalogue survives: it ships with the app and is not the user's data.
     */
    suspend fun deleteAllUserData() {
        database.withTransaction {
            dao.deleteAllGroceries()
            dao.deleteAllPortions()
            dao.deleteAllMeals()
            dao.deleteAllPlans()
            dao.deleteAllMeasurements()
            dao.deleteAllProfiles()
            dao.deleteUserFoods()
        }
        photos?.deleteAll()
    }

    companion object {
        /**
         * The first day of the week containing [date], by the locale's own
         * convention from CLDR: Sunday in the US and South Africa, Monday in
         * the UK and most of Europe. The same rule the iOS app gets from
         * Calendar.current.
         */
        fun weekStart(date: LocalDate, locale: Locale = Locale.getDefault()): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
    }
}
