/*
 * ProFlowTest.kt
 *
 * MacroDime Pro driven through the real screens, with PreviewSubscriptionStore
 * standing in for Google Play: the paywall after onboarding, what a free
 * account can and cannot open, starting the trial, and the honest edges
 * (no Play Store, nothing to restore, a trial about to end).
 */
package com.lungelo.macrodime.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lungelo.macrodime.MacroDimeApplication
import com.lungelo.macrodime.billing.PlayBillingStore
import com.lungelo.macrodime.billing.PreviewSubscriptionStore
import com.lungelo.macrodime.billing.StoreState
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.ui.theme.MacroDimeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ProFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<MacroDimeApplication>().container

    private fun launch(store: PreviewSubscriptionStore): PreviewSubscriptionStore {
        container.subscriptions = store
        runBlocking { container.repository.seedCatalog() }
        compose.setContent { MacroDimeTheme { MacroDimeRoot(container) } }
        return store
    }

    private fun eventually(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (true) {
            compose.waitForIdle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n") {
                    compose.onAllNodes(isRoot())[it].printToString(maxDepth = 40)
                }
                throw AssertionError("Condition not met within $timeout ms. Screen:\n$roots")
            }
            Thread.sleep(50)
        }
    }

    private fun waitForText(text: String) = eventually { has(text) }
    private fun waitForTag(tag: String) = eventually { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Welcome to Start Planning. What follows depends on the store. */
    private fun onboard() {
        waitForText("Welcome")
        compose.onNodeWithTag("continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("weight").performTextReplacement("82")
        repeat(6) {
            compose.onNodeWithTag("continue").assertIsEnabled().performClick()
            compose.waitForIdle()
        }
        waitForText("I understand these are estimates")
        compose.onNodeWithTag("acknowledge").performClick()
        compose.onNodeWithTag("continue").assertIsEnabled().performClick()
    }

    private fun scrollPaywallTo(tag: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }

    private val usd = { amount: Double -> DisplayFormat.currency(amount, "USD") }

    @Test
    fun aNewAccountMeetsThePaywallWithItsOwnNumbers() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your plan is ready")

        // The targets just set, quoted back.
        val profile = runBlocking { container.repository.currentProfile() }!!
        assertTrue(has(DisplayFormat.calories(profile.prescription.targets.calories)), "the paywall should quote the calorie target")

        compose.onNodeWithTag("paywall-cta").assertTextContains("Start my 14-day free trial")
        assertTrue(has("Save 58%"))
        assertTrue(has("14 days free"))
        scrollPaywallTo("paywall-timeline")
        assertTrue(has("How the trial works"))
        scrollPaywallTo("paywall-terms")
        compose.onNodeWithTag("paywall-terms").assertTextContains("14 days free, then ${usd(29.99)} a year", substring = true)
        compose.onNodeWithTag("paywall-terms").assertTextContains("Cancel in Google Play before the trial ends", substring = true)
    }

    @Test
    fun notNowKeepsTheTargetsFreeAndOffersAWayIntoEverythingElse() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your plan is ready")
        compose.onNodeWithTag("paywall-not-now").performClick()

        waitForText("Today's macros")
        waitForTag("upgrade-card")
        assertTrue(!has("Cost tracker"), "a free Today has no cost tracker to show")

        compose.onNodeWithTag("tab-Plan").performClick()
        waitForTag("locked-Planner")
        compose.onNodeWithTag("unlock-Planner").assertTextContains("Start my 14-day free trial")

        compose.onNodeWithTag("tab-Groceries").performClick()
        waitForTag("locked-Groceries")

        // The locked screen's button opens the paywall, never a charge.
        compose.onNodeWithTag("unlock-Groceries").performClick()
        waitForText("Let your grocery list build itself")
    }

    @Test
    fun startingTheTrialUnlocksEverything() {
        val store = launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your plan is ready")
        compose.onNodeWithTag("paywall-cta").performClick()

        waitForText("Cost tracker")
        assertEquals(listOf(ProPlan.Annual), store.purchased)
        assertTrue(!hasTag("paywall"), "the paywall closes once the store confirms Pro")

        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Meal Plan")
        assertTrue(!hasTag("locked-Planner"))

        compose.onNodeWithTag("tab-Settings").performClick()
        waitForText("Pro, yearly")
        assertTrue(has("Free trial ends"))
        assertTrue(has("Manage subscription"))
    }

    @Test
    fun theMonthlyPlanCarriesNoTrial() {
        val store = launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your plan is ready")
        compose.onNodeWithTag("plan-Monthly").performClick()

        compose.onNodeWithTag("paywall-cta").assertTextContains("Subscribe for ${usd(5.99)} a month")
        assertTrue(!has("How the trial works"))
        scrollPaywallTo("paywall-terms")
        compose.onNodeWithTag("paywall-terms").assertTextContains("${usd(5.99)} a month. MacroDime Pro renews every month", substring = true)

        compose.onNodeWithTag("paywall-cta").performClick()
        waitForText("Cost tracker")
        assertEquals(listOf(ProPlan.Monthly), store.purchased)
    }

    /** Without Google Play nothing can be sold, and the paywall says so instead of offering a dead button. */
    @Test
    fun withoutGooglePlayThePaywallSellsNothing() {
        launch(PreviewSubscriptionStore(availability = StoreState.Availability.Unavailable))
        onboard()
        waitForText("Your plan is ready")
        waitForTag("paywall-unavailable")
        assertTrue(!hasTag("paywall-cta"))

        compose.onNodeWithTag("paywall-not-now").performClick()
        waitForText("Today's macros")
    }

    @Test
    fun restoringWithNothingToRestoreSaysSo() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your plan is ready")
        compose.onNodeWithTag("paywall-restore").performClick()
        waitForText(PlayBillingStore.NOTHING_TO_RESTORE)
    }

    @Test
    fun aProgressPhotoNeedsProButAMeasurementDoesNot() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your plan is ready")
        compose.onNodeWithTag("paywall-not-now").performClick()
        waitForText("Today's macros")

        compose.onNodeWithContentDescription("Log measurement").performClick()
        waitForText("Log Progress")
        // Below the fold in the sheet: a click on an unscrolled node silently misses.
        compose.onNodeWithTag("add-photo").performScrollTo().assertTextContains("Add photos with Pro")
        compose.onNodeWithTag("add-photo").performClick()
        waitForText("See the change BMI can't show")
    }

    /** In the last two days of a trial, Today says what happens next and how to cancel. */
    @Test
    fun theTrialReminderShowsInItsLastDays() {
        val now = System.currentTimeMillis()
        launch(
            PreviewSubscriptionStore(
                Entitlement(isPro = true, plan = ProPlan.Annual, verifiedAtMillis = now, trialEndsAtMillis = now + 30 * 3_600_000L),
            ),
        )
        onboard()
        waitForText("Your free trial ends in 2 days")
        assertTrue(has("Then ${usd(29.99)} a year"))
        assertTrue(has("Manage in Google Play"))
    }

    @Test
    fun aSubscriberSkipsThePaywallAfterOnboarding() {
        launch(PreviewSubscriptionStore.subscriber())
        onboard()
        waitForText("Cost tracker")
        assertTrue(!has("Your plan is ready"))
    }

    @Test
    fun deletingEverythingForgetsWhatTheAppKnewAboutPro() {
        val store = launch(PreviewSubscriptionStore.subscriber())
        onboard()
        waitForText("Today's macros")
        compose.onNodeWithTag("tab-Settings").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("delete-all"))
        compose.onNodeWithTag("delete-all").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        waitForText("Welcome")
        eventually { store.forgotten == 1 }
    }
}
