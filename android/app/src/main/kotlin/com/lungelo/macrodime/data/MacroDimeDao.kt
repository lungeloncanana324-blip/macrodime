/*
 * MacroDimeDao.kt
 * MacroDime
 *
 * Every query the app makes. Reads that the screens watch are Flows, so a write
 * anywhere redraws every screen that shows the data, which is what the iOS app
 * gets from @Query.
 */
package com.lungelo.macrodime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDimeDao {

    // Profile

    @Query("SELECT * FROM user_profile ORDER BY createdAt LIMIT 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile ORDER BY createdAt LIMIT 1")
    suspend fun profile(): UserProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    // Measurements

    @Query("SELECT * FROM body_measurement WHERE profileId = :profileId ORDER BY recordedAt")
    fun observeMeasurements(profileId: String): Flow<List<BodyMeasurementEntity>>

    @Insert
    suspend fun insertMeasurement(measurement: BodyMeasurementEntity)

    @Query("SELECT COUNT(*) FROM body_measurement")
    suspend fun measurementCount(): Int

    // Foods

    @Query("SELECT * FROM food_item ORDER BY name")
    fun observeFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_item WHERE isUserCreated = 0")
    suspend fun curatedFoods(): List<FoodItemEntity>

    @Query("SELECT * FROM food_item WHERE catalogId = :id")
    suspend fun food(id: String): FoodItemEntity?

    @Upsert
    suspend fun upsertFoods(foods: List<FoodItemEntity>)

    // Plans

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE day = :day")
    fun observePlan(day: Long): Flow<PlanWithMeals?>

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE day = :day")
    suspend fun planWithMeals(day: Long): PlanWithMeals?

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE day >= :start AND day < :end ORDER BY day")
    suspend fun plansBetween(start: Long, end: Long): List<PlanWithMeals>

    @Query("SELECT * FROM meal_plan WHERE day = :day")
    suspend fun planForDay(day: Long): MealPlanEntity?

    @Insert
    suspend fun insertPlan(plan: MealPlanEntity)

    @Query("SELECT * FROM planned_meal WHERE planId = :planId AND slotRaw = :slotRaw")
    suspend fun mealInSlot(planId: String, slotRaw: String): PlannedMealEntity?

    @Query("SELECT * FROM planned_meal WHERE id = :id")
    suspend fun meal(id: String): PlannedMealEntity?

    @Insert
    suspend fun insertMeal(meal: PlannedMealEntity)

    @Update
    suspend fun updateMeal(meal: PlannedMealEntity)

    @Query("SELECT * FROM meal_portion WHERE mealId = :mealId")
    suspend fun portionsInMeal(mealId: String): List<MealPortionEntity>

    @Query("SELECT * FROM meal_portion WHERE id = :id")
    suspend fun portion(id: String): MealPortionEntity?

    @Insert
    suspend fun insertPortion(portion: MealPortionEntity)

    @Update
    suspend fun updatePortion(portion: MealPortionEntity)

    @Query("DELETE FROM meal_portion WHERE id = :id")
    suspend fun deletePortion(id: String)

    // Groceries

    @Query("SELECT * FROM grocery_item WHERE weekStart = :weekStart ORDER BY name")
    fun observeGroceries(weekStart: Long): Flow<List<GroceryItemEntity>>

    @Query("SELECT * FROM grocery_item WHERE weekStart = :weekStart")
    suspend fun groceries(weekStart: Long): List<GroceryItemEntity>

    @Query("SELECT * FROM grocery_item WHERE id = :id")
    suspend fun grocery(id: String): GroceryItemEntity?

    @Upsert
    suspend fun upsertGroceries(items: List<GroceryItemEntity>)

    @Delete
    suspend fun deleteGroceries(items: List<GroceryItemEntity>)

    @Query("UPDATE grocery_item SET isChecked = 0 WHERE weekStart = :weekStart")
    suspend fun uncheckWeek(weekStart: Long)

    // Deleting everything. Children first, so the operation does not depend on
    // the cascade rules being right.

    @Query("DELETE FROM grocery_item")
    suspend fun deleteAllGroceries()

    @Query("DELETE FROM meal_portion")
    suspend fun deleteAllPortions()

    @Query("DELETE FROM planned_meal")
    suspend fun deleteAllMeals()

    @Query("DELETE FROM meal_plan")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM body_measurement")
    suspend fun deleteAllMeasurements()

    @Query("DELETE FROM user_profile")
    suspend fun deleteAllProfiles()

    @Query("DELETE FROM food_item WHERE isUserCreated = 1")
    suspend fun deleteUserFoods()
}
