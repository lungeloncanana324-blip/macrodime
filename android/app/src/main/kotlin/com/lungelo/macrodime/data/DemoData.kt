/*
 * DemoData.kt
 * MacroDime
 *
 * Fills the store with a plausible day so the store screenshots can show the
 * product rather than an empty wizard. Port of MacroDime/Persistence/DemoData.swift.
 *
 * Debug builds only, and that is a security decision, not a convenience. On
 * iOS the trigger is a launch argument no other app can pass. On Android the
 * trigger is an intent extra, and any app on the phone can start an exported
 * activity with any extra it likes: in a release build this would let another
 * app overwrite the user's profile with a demo one. MainActivity checks
 * BuildConfig.DEBUG before it ever calls this.
 *
 *   adb shell am start -n com.lungelo.macrodime/.MainActivity --ez macrodime.demo true --ei macrodime.tab 1
 */
package com.lungelo.macrodime.data

import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.BiologicalSex
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.MealSlot
import java.time.LocalDate
import java.util.UUID

object DemoData {

    const val EXTRA_DEMO = "macrodime.demo"
    const val EXTRA_TAB = "macrodime.tab"

    /** Three meals, a week of grocery lines, and one progress entry. Idempotent. */
    suspend fun install(repository: MacroDimeRepository, today: LocalDate = LocalDate.now()) {
        repository.seedCatalog()

        val now = System.currentTimeMillis()
        val profile = (repository.currentProfile() ?: blankProfile(now)).copy(
            displayName = "Lungelo",
            heightCm = 178.0,
            weightKg = 82.0,
            age = 31,
            sexRaw = BiologicalSex.Male.rawValue,
            goalRaw = FitnessGoal.FatLoss.rawValue,
            activityRaw = ActivityLevel.ModeratelyActive.rawValue,
            budgetTierRaw = BudgetTier.Strict.rawValue,
            dailyFoodBudget = 9.0,
            hasCompletedOnboarding = true,
            hasAcknowledgedHealthDisclaimer = true,
        )
        repository.saveProfile(profile)

        val recipes = listOf(
            Triple(MealSlot.Breakfast, "Oats, Eggs & Banana", listOf("rolled-oats" to 1.0, "eggs-large" to 1.5, "banana" to 1.0)),
            Triple(
                MealSlot.Lunch,
                "Tuna Rice Bowl",
                listOf("canned-tuna-water" to 1.0, "white-rice" to 1.0, "frozen-broccoli" to 1.0, "olive-oil" to 0.5),
            ),
            Triple(
                MealSlot.Dinner,
                "Chicken Thighs & Potatoes",
                listOf("chicken-thighs" to 1.0, "potatoes" to 1.0, "frozen-mixed-vegetables" to 1.0, "olive-oil" to 0.5),
            ),
        )

        // A second launch must not double every portion: set, do not add.
        val existing = repository.mealsOnce(today)
        for ((slot, name, items) in recipes) {
            for ((foodId, servings) in items) {
                val portion = existing.firstOrNull { it.slot == slot }?.portions?.firstOrNull { it.food.id == foodId }
                if (portion == null) {
                    repository.addFood(today, slot, foodId, servings)
                } else {
                    repository.updateServings(portion.id, servings)
                }
            }
            repository.renameMeal(today, slot, name)
        }

        repository.logBaselineIfEmpty(profile, waistCm = 88.0)
        repository.regenerateGroceries(today)
    }

    fun blankProfile(now: Long) = UserProfileEntity(
        id = UUID.randomUUID().toString(),
        displayName = "",
        createdAt = now,
        updatedAt = now,
        hasCompletedOnboarding = false,
        hasAcknowledgedHealthDisclaimer = false,
        heightCm = 175.0,
        weightKg = 75.0,
        age = 30,
        sexRaw = BiologicalSex.Male.rawValue,
        goalRaw = FitnessGoal.FatLoss.rawValue,
        activityRaw = ActivityLevel.LightlyActive.rawValue,
        budgetTierRaw = BudgetTier.Strict.rawValue,
        measurementSystemRaw = "metric",
        dailyFoodBudget = BudgetTier.Strict.defaultDailyAllowance,
        currencyCodeRaw = "USD",
        currencyUnitsPerUSD = 1.0,
        dietaryPatternRaw = "omnivore",
        foodExclusionsRaw = "",
        blockedFoodIds = "",
        eatingScheduleRaw = "threeMeals",
        prepEffortRaw = "unlimited",
        mealsOutPerWeek = 0,
    )
}
