/*
 * ProFlowTest.kt
 *
 * MacroDime Pro driven through the real screens, with PreviewSubscriptionStore
 * standing in for Google Play. Since 2026-10-04 the paywall is the way into the
 * app: it opens on the week onboarding just planned, it cannot be skipped, the
 * trial opens the app, and what must stay reachable without paying (Delete All
 * My Data, the health statement, restore) is. Plus the honest edges: no Play
 * Store, nothing to restore, a trial about to end, a trial that lapsed.
 */
package com.lungelo.macrodime.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lungelo.macrodime.MacroDimeApplication
import com.lungelo.macrodime.billing.PlayBillingStore
import com.lungelo.macrodime.billing.PreviewSubscriptionStore
import com.lungelo.macrodime.billing.StoreState
import com.lungelo.macrodime.data.DemoData
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.Paywall
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.engine.DataSources
import com.lungelo.macrodime.ui.theme.MacroDimeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ProFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<MacroDimeApplication>().container

    private fun launch(store: PreviewSubscriptionStore, demo: Boolean = false): PreviewSubscriptionStore {
        // As if the reminder's notification permission were already allowed:
        // Robolectric has no system dialog to answer the request, so without
        // this the trial button would wait on it forever.
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.subscriptions = store
        runBlocking {
            container.repository.seedCatalog()
            if (demo) DemoData.install(container.repository)
        }
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
    private fun waitForTag(tag: String) = eventually { hasTag(tag) }
    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** The intro to Build my week. What follows depends on the store. */
    private fun onboard() {
        waitForTag("intro-hook")
        compose.onNodeWithTag("intro-start").performClick()
        waitForTag("intro-pains")
        compose.onNodeWithTag("intro-continue").performClick()
        waitForTag("intro-fixes")
        compose.onNodeWithTag("intro-continue").performClick()
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
    fun aNewAccountMeetsThePaywallOnItsOwnWeek() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your week is ready")

        // The targets just set, quoted back, and today's real meals under them.
        val profile = runBlocking { container.repository.currentProfile() }!!
        assertTrue(has(DisplayFormat.calories(profile.prescription.targets.calories)), "the paywall should quote the calorie target")
        scrollPaywallTo("paywall-preview")
        val breakfast = runBlocking { container.repository.mealsOnce(LocalDate.now()) }.first { !it.isEmpty }
        assertTrue(has(breakfast.name), "the paywall should show today's planned meals")

        compose.onNodeWithTag("paywall-cta").assertTextContains("Start my 14-day free trial")
        assertTrue(has("No payment today"))
        assertTrue(has("14 days free"))
        // One plan on sale: no savings badge to compare against, no radio to tap.
        assertTrue(!has("Save "))
        // No close button, so the paywall says plainly that the app needs a subscription.
        scrollPaywallTo("paywall-required")
        assertTrue(has(Paywall.SUBSCRIPTION_REQUIRED))
        scrollPaywallTo("paywall-timeline")
        assertTrue(has("Day 12"))
        assertTrue(has("A reminder that your trial ends in 2 days"))
        scrollPaywallTo("paywall-remind")
        scrollPaywallTo("paywall-terms")
        compose.onNodeWithTag("paywall-terms").assertTextContains("14 days free, then ${usd(29.99)} a year", substring = true)
        compose.onNodeWithTag("paywall-terms").assertTextContains("Cancel in Google Play before the trial ends", substring = true)
    }

    /** No free tier: there is no Not now, no close, and no way into the tabs without Pro. */
    @Test
    fun thePaywallIsTheWayIn() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your week is ready")
        assertTrue(!hasTag("paywall-not-now"))
        assertTrue(!hasTag("paywall-close"))
        assertTrue(!hasTag("tab-Plan"), "the tabs must not be reachable without Pro")
    }

    @Test
    fun startingTheTrialOpensTheApp() {
        val store = launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your week is ready")
        compose.onNodeWithTag("paywall-cta").performClick()

        waitForText("Today's meals")
        assertEquals(listOf(ProPlan.Annual), store.purchased)
        assertTrue(!hasTag("paywall"), "the paywall gives way once the store confirms Pro")

        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Meal Plan")

        compose.onNodeWithTag("tab-Settings").performClick()
        waitForText("Pro, yearly")
        assertTrue(has("Free trial ends"))
        assertTrue(has("Manage subscription"))
    }

    /** If a monthly base plan is ever switched on, it is a second choice, sold without a trial. */
    @Test
    fun aMonthlyPlanWhenOnSaleCarriesNoTrial() {
        val store = launch(PreviewSubscriptionStore(offers = PreviewSubscriptionStore.WITH_MONTHLY))
        onboard()
        waitForText("Your week is ready")
        assertTrue(has("Save 58%"))
        scrollPaywallTo("plan-Monthly")
        compose.onNodeWithTag("plan-Monthly").performClick()

        // The button lives in the pinned footer, composed apart from the list:
        // wait for it as the rest of this suite waits, rather than reading it
        // in the same instant as the tap.
        waitForText("Subscribe for ${usd(5.99)} a month")
        compose.onNodeWithTag("paywall-cta").assertTextContains("Subscribe for ${usd(5.99)} a month")
        assertTrue(!has("How your free trial works"))
        compose.onNodeWithTag("paywall-cta").performClick()
        waitForText("Today's meals")
        assertEquals(listOf(ProPlan.Monthly), store.purchased)
    }

    /** Without Google Play nothing can be sold, and the paywall says so instead of offering a dead button. */
    @Test
    fun withoutGooglePlayThePaywallSellsNothing() {
        launch(PreviewSubscriptionStore(availability = StoreState.Availability.Unavailable))
        onboard()
        waitForText("Your week is ready")
        scrollPaywallTo("paywall-unavailable")
        assertTrue(!hasTag("paywall-cta"))
    }

    @Test
    fun restoringWithNothingToRestoreSaysSo() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your week is ready")
        scrollPaywallTo("paywall-restore")
        compose.onNodeWithTag("paywall-restore").performClick()
        waitForText(PlayBillingStore.NOTHING_TO_RESTORE)
    }

    /** A subscription Google Play put on hold is sent to Google Play to fix, never sold a second one. */
    @Test
    fun aSubscriptionOnHoldIsSentToGooglePlay() {
        launch(PreviewSubscriptionStore(onHold = true))
        onboard()
        waitForText(Paywall.ON_HOLD_HEADLINE)
        assertTrue(has("couldn't take your last payment"))
        compose.onNodeWithTag("paywall-fix-payment").assertTextContains("Fix it in Google Play")
        assertTrue(!hasTag("paywall-cta"), "nothing new is sold to someone on hold")
        assertTrue(!hasTag("plan-Annual"))
        assertTrue(!hasTag("tab-Plan"))
        // Restore does not claim there is nothing to restore: there is, and it is on hold.
        scrollPaywallTo("paywall-restore")
        compose.onNodeWithTag("paywall-restore").performClick()
        compose.waitForIdle()
        assertTrue(!has(PlayBillingStore.NOTHING_TO_RESTORE))
    }

    /** Delete All My Data is reachable without paying, from the paywall's menu. */
    @Test
    fun everythingCanBeDeletedFromThePaywall() {
        val store = launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your week is ready")
        compose.onNodeWithTag("paywall-more").performClick()
        compose.onNodeWithTag("paywall-delete").performClick()
        compose.onNodeWithTag("paywall-confirm-delete").performClick()
        waitForTag("intro-hook")
        eventually { store.forgotten == 1 }
        assertEquals(null, runBlocking { container.repository.currentProfile() })
    }

    /**
     * Where the paywall's prices come from is reachable without paying, and
     * each source opens its original. Google Play rejected the first review
     * (6 Oct 2026) for government prices shown with no link to the source.
     */
    @Test
    fun theSourcesAreReachableFromThePaywall() {
        launch(PreviewSubscriptionStore())
        onboard()
        waitForText("Your week is ready")
        compose.onNodeWithTag("paywall-more").performClick()
        compose.onNodeWithTag("paywall-sources").performClick()
        waitForTag("sources")
        assertTrue(has(DataSources.INDEPENDENCE), "the statement that the app is not a government's")
        assertTrue(has(DataSources.AVERAGE_PRICES.shownUrl), "the address itself, so the .gov shows before a tap")

        compose.onNodeWithTag("sources").performScrollToNode(hasTestTag("source-${DataSources.AVERAGE_PRICES.title}"))
        compose.onNodeWithTag("source-${DataSources.AVERAGE_PRICES.title}").performClick()
        val opened = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened?.action)
        assertEquals(DataSources.AVERAGE_PRICES.url, opened?.dataString)

        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Your week is ready")
    }

    /** Inside the app, Settings opens Sources beside the price counts, and so does Health & Safety. */
    @Test
    fun theSourcesAreReachableFromSettingsAndHealth() {
        launch(PreviewSubscriptionStore.subscriber())
        onboard()
        waitForText("Today's meals")
        compose.onNodeWithTag("tab-Settings").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("open-sources"))
        assertTrue(has("does not represent any government"), "the price footer says it too")
        compose.onNodeWithTag("open-sources").performClick()
        waitForTag("sources")
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Your plan")

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Health & Safety"))
        compose.onNodeWithText("Health & Safety").performClick()
        waitForTag("health")
        compose.onNodeWithTag("health").performScrollToNode(hasTestTag("health-sources"))
        compose.onNodeWithTag("health-sources").performClick()
        waitForTag("sources")
        // Back from Sources returns to the statement it was opened from, not to Settings.
        compose.onNodeWithContentDescription("Back").performClick()
        waitForTag("health")
        assertTrue(!hasTag("sources") && !has("Your plan"))
    }

    /** Someone whose trial lapsed opens the app on the paywall, welcomed back, with their savings first if any. */
    @Test
    fun aLapsedAccountIsWelcomedBack() {
        launch(PreviewSubscriptionStore(), demo = true)
        waitForText("Welcome back")
        assertTrue(!hasTag("tab-Plan"))
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
        waitForText("Today's meals")
        assertTrue(!has("Your week is ready"))
    }

    @Test
    fun deletingEverythingForgetsWhatTheAppKnewAboutPro() {
        val store = launch(PreviewSubscriptionStore.subscriber())
        onboard()
        waitForText("Today's meals")
        compose.onNodeWithTag("tab-Settings").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("delete-all"))
        compose.onNodeWithTag("delete-all").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        waitForTag("intro-hook")
        eventually { store.forgotten == 1 }
    }
}
