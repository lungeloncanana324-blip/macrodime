/*
 * RepositoryTest.kt
 *
 * The persistence rules, against a real SQLite database (Room in memory, under
 * Robolectric). Written to break the repository, not to confirm it: each test
 * names the failure it is looking for.
 */
package com.lungelo.macrodime.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DietaryProfile
import com.lungelo.macrodime.domain.EatingSchedule
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.engine.BudgetFoodEngine
import com.lungelo.macrodime.engine.FoodCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var database: MacroDimeDatabase
    private lateinit var repository: MacroDimeRepository
    private lateinit var photos: PhotoStore
    private val dao get() = database.dao()

    /** A Wednesday, so a week boundary never sits on the test day by accident. */
    private val day = LocalDate.of(2026, 9, 30)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        database = MacroDimeDatabase.inMemory(context)
        photos = PhotoStore(context)
        repository = MacroDimeRepository(database, photos)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seededProfile(): UserProfileEntity {
        repository.seedCatalog()
        val profile = DemoData.blankProfile(0).copy(hasCompletedOnboarding = true)
        repository.saveProfile(profile)
        return assertNotNull(dao.profile())
    }

    // Seeding

    @Test
    fun seedingWritesTheCatalogueOnceAndThenNothing() = runTest {
        assertEquals(57, repository.seedCatalog())
        assertEquals(0, repository.seedCatalog(), "an unchanged catalogue must not be rewritten on every launch")
        assertEquals(57, dao.curatedFoods().size)
    }

    /**
     * The bug this seeder exists to avoid. The iOS seeder only reruns when a
     * hand-bumped version number rises, so a monthly price refresh never
     * reaches an existing install. Here a stored row with a stale price is
     * detected by value and corrected.
     */
    @Test
    fun aStalePriceInAnExistingInstallIsCorrected() = runTest {
        repository.seedCatalog()
        val stored = assertNotNull(dao.food("eggs-large"))
        dao.upsertFoods(listOf(stored.copy(costPerServing = 99.0)))

        assertEquals(1, repository.seedCatalog())
        assertEquals(FoodCatalog.food("eggs-large")!!.costPerServing, dao.food("eggs-large")!!.costPerServing)
    }

    /**
     * Upsert, not REPLACE: REPLACE deletes the row first, and the foreign key
     * would then null every meal portion pointing at it, emptying the user's
     * history on the first launch after a price change.
     */
    @Test
    fun reseedingAChangedFoodKeepsTheMealsThatUseIt() = runTest {
        seededProfile()
        repository.addFood(day, MealSlot.Lunch, "canned-tuna-water", 1.0)
        val stored = assertNotNull(dao.food("canned-tuna-water"))
        dao.upsertFoods(listOf(stored.copy(costPerServing = 0.01)))

        repository.seedCatalog()

        val meal = repository.mealsOnce(day).single()
        assertEquals("canned-tuna-water", meal.portions.single().food.id)
    }

    @Test
    fun aUsersFavouriteSurvivesReseeding() = runTest {
        repository.seedCatalog()
        val stored = assertNotNull(dao.food("rolled-oats"))
        dao.upsertFoods(listOf(stored.copy(isFavourite = true, costPerServing = 5.0)))
        repository.seedCatalog()
        assertTrue(dao.food("rolled-oats")!!.isFavourite)
    }

    // Meals

    @Test
    fun addingTheSameFoodTwiceAddsServingsRatherThanARow() = runTest {
        seededProfile()
        repository.addFood(day, MealSlot.Breakfast, "eggs-large", 1.0)
        repository.addFood(day, MealSlot.Breakfast, "eggs-large", 0.5)
        repository.addFood(day, MealSlot.Breakfast, "rolled-oats", 1.0)

        val meals = repository.mealsOnce(day)
        assertEquals(1, meals.size)
        val eggs = meals.single().portions.first { it.food.id == "eggs-large" }
        assertEquals(1.5, eggs.servings)
        assertEquals(listOf("eggs-large", "rolled-oats"), meals.single().portions.map { it.food.id }, "order is the order added")
    }

    @Test
    fun eachSlotIsItsOwnMealAndDaysDoNotLeak() = runTest {
        seededProfile()
        repository.addFood(day, MealSlot.Lunch, "white-rice", 1.0)
        repository.addFood(day, MealSlot.Dinner, "potatoes", 1.0)
        repository.addFood(day.plusDays(1), MealSlot.Lunch, "apple", 1.0)

        assertEquals(listOf(MealSlot.Lunch, MealSlot.Dinner), repository.mealsOnce(day).map { it.slot })
        assertEquals(listOf("apple"), repository.mealsOnce(day.plusDays(1)).flatMap { it.portions }.map { it.food.id })
    }

    @Test
    fun zeroServingsRemovesThePortion() = runTest {
        seededProfile()
        repository.addFood(day, MealSlot.Lunch, "white-rice", 1.0)
        val portion = repository.mealsOnce(day).single().portions.single()
        repository.updateServings(portion.id, 0.0)
        assertTrue(repository.mealsOnce(day).single().portions.isEmpty())
    }

    @Test
    fun addingAFoodThatIsNotInTheDatabaseFailsLoudly() = runTest {
        seededProfile()
        val failure = runCatching { repository.addFood(day, MealSlot.Lunch, "no-such-food", 1.0) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(repository.mealsOnce(day).isEmpty(), "a failed add must not leave an empty plan or meal behind")
    }

    /**
     * The swap writes the engine's whole resulting meal: the replacement and the
     * re-portioned oil. Writing only the replacement would leave the stored meal
     * outside the tolerance the user was shown.
     */
    @Test
    fun applyingASwapStoresTheRebalancedMealExactly() = runTest {
        seededProfile()
        for (id in listOf("salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil")) {
            repository.addFood(day, MealSlot.Dinner, id, 1.0)
        }
        val meal = repository.mealsOnce(day).single()
        val swap = assertNotNull(BudgetFoodEngine(FoodCatalog.all).bestSwap(meal))

        repository.applySwap(meal.id, swap.swapped, swap.savings)

        val stored = repository.mealsOnce(day).single()
        assertEquals(swap.swapped.portions.map { it.food.id to it.servings }, stored.portions.map { it.food.id to it.servings })
        assertEquals(swap.swapped.cost, stored.cost, 1e-9)
        val row = assertNotNull(dao.meal(meal.id.toString()))
        assertTrue(row.wasSwapped)
        assertEquals(swap.savings, row.swapSavings, 1e-9)
    }

    // Groceries

    /**
     * The locale's own convention, from CLDR, as iOS's Calendar.current uses.
     * Sunday in the US and, per CLDR, in South Africa; Monday in the UK.
     */
    @Test
    fun theWeekRunsOnTheLocalesOwnFirstDay() {
        assertEquals(DayOfWeek.SUNDAY, MacroDimeRepository.weekStart(day, Locale.US).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, MacroDimeRepository.weekStart(day, Locale.forLanguageTag("en-ZA")).dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, MacroDimeRepository.weekStart(day, Locale.UK).dayOfWeek)
        // A Sunday in the UK belongs to the week that started the Monday before.
        val sunday = LocalDate.of(2026, 10, 4)
        assertEquals(LocalDate.of(2026, 9, 28), MacroDimeRepository.weekStart(sunday, Locale.UK))
    }

    @Test
    fun theListAggregatesTheWholeWeekAndNothingOutsideIt() = runTest {
        seededProfile()
        val locale = Locale.UK
        val monday = MacroDimeRepository.weekStart(day, locale)
        repository.addFood(monday, MealSlot.Lunch, "canned-tuna-water", 1.0)
        repository.addFood(monday.plusDays(3), MealSlot.Dinner, "canned-tuna-water", 2.0)
        repository.addFood(monday.plusDays(7), MealSlot.Lunch, "canned-tuna-water", 5.0) // next week

        repository.regenerateGroceries(day, locale)

        val items = dao.groceries(monday.toEpochDay())
        val tuna = items.single { it.sourceFoodId == "canned-tuna-water" }
        assertEquals(3.0, tuna.totalServings)
        assertEquals("3 cans (426 g drained)", tuna.quantityDescription)
    }

    /** Adding a meal on Thursday must not untick what was bought on Monday. */
    @Test
    fun ticksAndPantryItemsSurviveRegeneration() = runTest {
        seededProfile()
        val locale = Locale.US
        repository.addFood(day, MealSlot.Lunch, "white-rice", 1.0)
        repository.addFood(day, MealSlot.Lunch, "olive-oil", 1.0)
        repository.regenerateGroceries(day, locale)
        val week = MacroDimeRepository.weekStart(day, locale).toEpochDay()
        val rice = dao.groceries(week).single { it.sourceFoodId == "white-rice" }
        val oil = dao.groceries(week).single { it.sourceFoodId == "olive-oil" }
        repository.setChecked(rice.id, true)
        repository.setAlreadyOwned(oil.id, true)

        repository.addFood(day.plusDays(1), MealSlot.Dinner, "white-rice", 2.0)
        repository.regenerateGroceries(day, locale)

        val after = dao.groceries(week)
        val riceAfter = after.single { it.sourceFoodId == "white-rice" }
        assertEquals(rice.id, riceAfter.id, "the row is updated in place, not replaced")
        assertTrue(riceAfter.isChecked)
        assertEquals(3.0, riceAfter.totalServings)
        assertTrue(after.single { it.sourceFoodId == "olive-oil" }.isAlreadyOwned)
        assertEquals(0.0, after.single { it.sourceFoodId == "olive-oil" }.outstandingCost)
    }

    @Test
    fun aLineNoMealNeedsIsRemovedButAManualLineIsNot() = runTest {
        seededProfile()
        val locale = Locale.US
        val week = MacroDimeRepository.weekStart(day, locale).toEpochDay()
        repository.addFood(day, MealSlot.Lunch, "white-rice", 1.0)
        repository.regenerateGroceries(day, locale)
        dao.upsertGroceries(
            listOf(GroceryItemEntity("manual", "", "Coffee", "pantry", "1 bag", 1.0, 8.0, false, false, week, 0)),
        )

        val portion = repository.mealsOnce(day).single().portions.single()
        repository.removePortion(portion.id)
        repository.regenerateGroceries(day, locale)

        assertEquals(listOf("Coffee"), dao.groceries(week).map { it.name })
    }

    /** iOS keeps the first of two rows for one food and leaves the other to go stale. */
    @Test
    fun duplicateRowsForOneFoodAreCleanedUp() = runTest {
        seededProfile()
        val locale = Locale.US
        val week = MacroDimeRepository.weekStart(day, locale).toEpochDay()
        repository.addFood(day, MealSlot.Lunch, "white-rice", 1.0)
        dao.upsertGroceries(
            listOf(
                GroceryItemEntity("a", "white-rice", "Rice", "pantry", "x", 1.0, 1.0, true, false, week, 0),
                GroceryItemEntity("b", "white-rice", "Rice", "pantry", "x", 1.0, 1.0, false, false, week, 1),
            ),
        )
        repository.regenerateGroceries(day, locale)
        assertEquals(1, dao.groceries(week).count { it.sourceFoodId == "white-rice" })
    }

    // Profile mapping

    @Test
    fun theDietaryProfileRoundTripsThroughItsColumns() = runTest {
        val profile = seededProfile()
        val diet = DietaryProfile(
            pattern = DietaryPattern.Vegan,
            exclusions = setOf(FoodExclusion.Soy, FoodExclusion.Gluten),
            blockedFoodIds = setOf("white-rice", "apple"),
            schedule = EatingSchedule.TwoMeals,
            prepEffort = PrepEffort.Quick,
            mealsOutPerWeek = 3,
        )
        repository.saveProfile(profile.withDietaryProfile(diet))
        assertEquals(diet, dao.profile()!!.dietaryProfile)
    }

    /** A damaged or downgraded store reads as safe defaults, never a crash. */
    @Test
    fun unknownStoredValuesDegradeToSafeDefaults() = runTest {
        val profile = seededProfile().copy(
            sexRaw = "garbage",
            dietaryPatternRaw = "carnivore",
            foodExclusionsRaw = "dairy,,unknown",
            prepEffortRaw = "",
            currencyCodeRaw = "",
            currencyUnitsPerUSD = -4.0,
        )
        repository.saveProfile(profile)
        val read = dao.profile()!!
        assertEquals(DietaryPattern.Omnivore, read.dietaryProfile.pattern)
        assertEquals(setOf(FoodExclusion.Dairy), read.dietaryProfile.exclusions)
        assertEquals(PrepEffort.Unlimited, read.dietaryProfile.prepEffort, "an unasked question must not filter food")
        assertEquals("USD", read.currency.displayCode)
        assertFalse(read.currency.isConverting)
        read.prescription // must not throw
    }

    // Progress

    @Test
    fun aLoggedWeightBecomesTheProfilesWeight() = runTest {
        val profile = seededProfile()
        repository.logMeasurement(profile, weightKg = 79.5, waistCm = 86.0, hipCm = null, notes = "", photoFileName = null)
        assertEquals(79.5, dao.profile()!!.weightKg)
        assertEquals(1, repository.measurements(profile.id).first().size)
    }

    @Test
    fun aMeasurementWithoutAWeightLeavesTheProfileAlone() = runTest {
        val profile = seededProfile()
        repository.logMeasurement(profile, weightKg = null, waistCm = 86.0, hipCm = 99.0, notes = "", photoFileName = null)
        assertEquals(profile.weightKg, dao.profile()!!.weightKg)
    }

    // Deleting everything

    @Test
    fun deletingEverythingLeavesOnlyTheCatalogue() = runTest {
        val profile = seededProfile()
        repository.addFood(day, MealSlot.Lunch, "white-rice", 1.0)
        repository.regenerateGroceries(day)
        val photo = photos.file("test.jpg").apply { parentFile?.mkdirs(); writeText("not really a jpeg") }
        repository.logMeasurement(profile, 80.0, 90.0, null, "", "test.jpg")

        repository.deleteAllUserData()

        assertNull(dao.profile())
        assertTrue(repository.mealsOnce(day).isEmpty())
        assertTrue(dao.groceries(MacroDimeRepository.weekStart(day).toEpochDay()).isEmpty())
        assertEquals(0, dao.measurementCount())
        assertEquals(57, dao.curatedFoods().size, "the catalogue ships with the app and is not the user's data")
        assertFalse(photo.exists(), "progress photos are part of the user's data")
        assertFalse(File(photo.parentFile, "").exists() && photo.parentFile!!.listFiles()!!.isNotEmpty())
    }
}
