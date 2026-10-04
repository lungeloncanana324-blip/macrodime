/*
 * AppFlowTest.kt
 *
 * The app driven through its real screens, on the JVM: Robolectric supplies
 * Android, Compose's test rule supplies the taps. This is the "tap through it"
 * the iOS app could only get from a human in Appetize, run on every build.
 *
 * Each test starts from a fresh install: Robolectric gives every test its own
 * application and its own empty data directory.
 */
package com.lungelo.macrodime.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lungelo.macrodime.MacroDimeApplication
import com.lungelo.macrodime.billing.PreviewSubscriptionStore
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.ui.theme.MacroDimeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<MacroDimeApplication>().container
    private val repository get() = container.repository

    private fun launch() {
        // A subscriber, so these flows reach the planner. ProFlowTest covers a free account.
        container.subscriptions = PreviewSubscriptionStore.subscriber()
        runBlocking { repository.seedCatalog() }
        compose.setContent { MacroDimeTheme { MacroDimeRoot(container) } }
    }

    /**
     * Polls until [condition] holds, idling the main looper on every pass.
     * Results computed on background threads (Room, the engines) reach the UI
     * through the main looper, which Robolectric only runs when a test idles
     * it; Compose's own waitUntil does not, and waits out its timeout instead.
     */
    private fun eventually(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (true) {
            compose.waitForIdle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                // The screen as it stood, so a timeout explains itself.
                val newline = System.lineSeparator()
                val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString(newline) {
                    compose.onAllNodes(isRoot())[it].printToString(maxDepth = 40)
                }
                throw AssertionError("Condition not met within $timeout ms. Screen:$newline$roots")
            }
            Thread.sleep(50)
        }
    }

    private fun waitForText(text: String) = eventually {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun next() {
        compose.onNodeWithTag("continue").assertIsEnabled().performClick()
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) = eventually { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    /** The three intro screens, picking nothing, to Welcome. */
    private fun passIntro() {
        waitForTag("intro-hook")
        compose.onNodeWithTag("intro-start").performClick()
        waitForTag("intro-pains")
        compose.onNodeWithTag("intro-continue").performClick()
        waitForTag("intro-fixes")
        compose.onNodeWithTag("intro-continue").performClick()
        waitForText("Welcome")
    }

    /** The intro to Build my week, with the defaults except a weight. */
    private fun completeOnboarding(weight: String = "82") {
        passIntro()
        next()
        compose.onNodeWithTag("weight").performTextReplacement(weight)
        repeat(6) { next() }
        waitForText("I understand these are estimates")
        compose.onNodeWithTag("acknowledge").performClick()
        next()
        waitForText("Today's meals")
    }

    /** Empties the week onboarding planned, for tests that need to know exactly what is on it. */
    private fun clearPlannedWeek() = runBlocking {
        for (offset in 0L until 7L) {
            repository.mealsOnce(LocalDate.now().plusDays(offset)).flatMap { it.portions }.forEach { repository.removePortion(it.id) }
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun onboardingEndToEndStoresTheProfileAndOpensToday() {
        launch()
        completeOnboarding(weight = "82")

        val profile = assertNotNull(runBlocking { repository.currentProfile() })
        assertEquals(82.0, profile.weightKg)
        assertTrue(profile.hasCompletedOnboarding)
        assertTrue(profile.hasAcknowledgedHealthDisclaimer)
        // The wizard's own asked default, not the unconstrained model default.
        assertEquals("standard", profile.prepEffortRaw)
    }

    /** Onboarding ends with a week already planned: every scheduled meal of every day, named, inside the allowance. */
    @Test
    fun onboardingPlansTheFirstWeek() {
        launch()
        completeOnboarding()
        val profile = assertNotNull(runBlocking { repository.currentProfile() })
        for (offset in 0L until 7L) {
            val meals = runBlocking { repository.mealsOnce(LocalDate.now().plusDays(offset)) }.filter { !it.isEmpty }
            assertEquals(listOf(MealSlot.Breakfast, MealSlot.Lunch, MealSlot.Dinner), meals.map { it.slot }.sorted(), "day $offset")
            assertTrue(meals.none { it.name == it.slot.displayName }, "day $offset has an unnamed meal: ${meals.map { it.name }}")
            assertTrue(meals.sumOf { it.cost } <= profile.dailyFoodBudget + 0.005, "day $offset is over the allowance")
        }
        // Today shows them with their names.
        val breakfast = runBlocking { repository.mealsOnce(LocalDate.now()) }.first { it.slot == MealSlot.Breakfast }
        waitForText(breakfast.name)
    }

    /** The answers screen answers what was picked, and only that. */
    @Test
    fun theIntroAnswersWhatWasPicked() {
        launch()
        waitForTag("intro-hook")
        compose.onNodeWithTag("intro-start").performClick()
        waitForTag("intro-pains")
        compose.onNodeWithTag("pain-ProteinIsHard").performClick()
        compose.onNodeWithTag("pain-FoodGoesToWaste").performClick()
        compose.onNodeWithTag("intro-continue").performClick()
        waitForTag("intro-fixes")
        waitForText("Here's how MacroDime fixes that")
        waitForText("Protein planned into every day")
        waitForText("A shopping list that matches")
        assertTrue(compose.onAllNodesWithText("Cheaper swaps, same macros").fetchSemanticsNodes().isEmpty())
    }

    /** A day past the planned week offers to plan itself, and does. */
    @Test
    fun anEmptyDayPlansItselfOnRequest() {
        launch()
        completeOnboarding()
        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Meal Plan")
        repeat(7) { compose.onNodeWithContentDescription("Next day").performClick() }
        waitForTag("plan-day")
        compose.onNodeWithTag("plan-day-button").performClick()
        eventually { runBlocking { repository.mealsOnce(LocalDate.now().plusDays(7)) }.any { !it.isEmpty } }
    }

    /**
     * The first bug a human found on iOS: Start Planning disabled with no
     * visible reason. Here the switch that enables it sits beside it, and the
     * button really is disabled until it is on.
     */
    @Test
    fun startPlanningWaitsForTheHealthAcknowledgement() {
        launch()
        passIntro()
        repeat(7) { next() }
        waitForText("Your plan")
        compose.onNodeWithTag("continue").assertIsNotEnabled()
        compose.onNodeWithText("I understand these are estimates, not medical advice.").assertExists()
        compose.onNodeWithTag("acknowledge").performClick()
        compose.onNodeWithTag("continue").assertIsEnabled()
    }

    @Test
    fun aClearedWeightBlocksTheStepAndSaysWhy() {
        launch()
        passIntro()
        next()
        compose.onNodeWithTag("weight").performTextReplacement("")
        compose.onNodeWithText("Enter a weight between 25 kg and 350 kg.").assertExists()
        compose.onNodeWithTag("continue").assertIsNotEnabled()

        compose.onNodeWithTag("weight").performTextReplacement("12")
        compose.onNodeWithTag("continue").assertIsNotEnabled()

        compose.onNodeWithTag("weight").performTextReplacement("70,5")
        compose.onNodeWithTag("continue").assertIsEnabled()
    }

    @Test
    fun aFoodAddedThroughThePickerAppearsInItsMeal() {
        launch()
        completeOnboarding()
        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Meal Plan")

        // Snacks: the one slot a three-meal plan leaves empty.
        scrollTo("add-snack")
        compose.onNodeWithTag("add-snack").performClick()
        waitForText("Add to Snacks")
        compose.onNodeWithTag("food-search").performTextInput("oats")
        compose.onNodeWithText("Rolled Oats").performClick()

        eventually {
            runBlocking { repository.mealsOnce(LocalDate.now()) }.firstOrNull { it.slot == MealSlot.Snack }?.portions?.map { it.food.id } == listOf("rolled-oats")
        }
    }

    @Test
    fun aReviewedSwapIsAppliedToTheStoredMeal() {
        launch()
        completeOnboarding()
        clearPlannedWeek()
        runBlocking {
            for (id in listOf("salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil")) {
                repository.addFood(LocalDate.now(), MealSlot.Dinner, id, 1.0)
            }
        }
        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Swap to save")
        // Below the fold on a phone: scroll to it as a person would, or the
        // click lands outside the window and nothing happens.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Swap to save", substring = true))
        compose.onNode(hasText("Swap to save", substring = true)).performClick()
        waitForText("Substitutions")
        compose.onNodeWithTag("apply-swap").performClick()

        eventually {
            runBlocking { repository.mealsOnce(LocalDate.now()) }
                .single { it.slot == MealSlot.Dinner }.portions.none { it.food.id == "salmon-fillet" }
        }
    }

    @Test
    fun theGroceryListBuildsItselfFromThePlan() {
        launch()
        completeOnboarding()
        clearPlannedWeek()
        runBlocking {
            repository.addFood(LocalDate.now(), MealSlot.Lunch, "canned-tuna-water", 2.0)
            repository.regenerateGroceries(LocalDate.now())
        }
        compose.onNodeWithTag("tab-Groceries").performClick()
        waitForText("Canned Tuna in Water")
        compose.onNodeWithText("2 cans (284 g drained)").assertExists()
    }

    /**
     * Deleting everything must land on a fresh start (the intro), not on the
     * summary step of the draft the user finished an hour ago.
     */
    @Test
    fun deletingEverythingStartsOnboardingAfresh() {
        launch()
        completeOnboarding()
        compose.onNodeWithTag("tab-Settings").performClick()
        waitForText("Settings")
        scrollTo("delete-all")
        compose.onNodeWithTag("delete-all").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()

        waitForTag("intro-hook")
        compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TestTag, "acknowledge")).assertDoesNotExist()
        assertEquals(null, runBlocking { repository.currentProfile() })
    }

    /** Editing the profile changes the stored targets and comes back to the tabs. */
    @Test
    fun editingTheProfileSavesAndCloses() {
        launch()
        completeOnboarding(weight = "82")
        compose.onNodeWithTag("tab-Settings").performClick()
        waitForText("Settings")
        scrollTo("edit-profile")
        compose.onNodeWithTag("edit-profile").performClick()
        waitForText("About you")
        compose.onNodeWithTag("weight").performTextReplacement("90")
        repeat(6) { next() }
        waitForText("Save changes")
        next()
        waitForText("Your plan")
        assertEquals(90.0, runBlocking { repository.currentProfile() }!!.weightKg)
    }
}
