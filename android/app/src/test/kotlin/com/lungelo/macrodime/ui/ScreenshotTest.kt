/*
 * ScreenshotTest.kt
 *
 * Renders every screen with Robolectric's native graphics and writes each to
 * app/build/screenshots, so the UI can be looked at, in light, dark and at a
 * large text size, on a machine with no emulator. Not a pixel-diff test: it
 * fails only if a screen cannot be drawn. The point is that a human looks at
 * the output before every release.
 */
package com.lungelo.macrodime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.engine.BudgetFoodEngine
import com.lungelo.macrodime.engine.FoodCatalog
import com.lungelo.macrodime.ui.plan.FoodPickerContent
import com.lungelo.macrodime.ui.plan.PortionSwapContent
import com.lungelo.macrodime.ui.plan.SwapReviewContent
import com.lungelo.macrodime.ui.today.LogMeasurementContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lungelo.macrodime.MacroDimeApplication
import com.lungelo.macrodime.billing.PreviewSubscriptionStore
import com.lungelo.macrodime.billing.StoreState
import com.lungelo.macrodime.data.DemoData
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.PaywallReason
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.domain.Store
import com.lungelo.macrodime.ui.paywall.PaywallContent
import com.lungelo.macrodime.ui.theme.MacroDimeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase {

    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<MacroDimeApplication>().container

    protected fun save(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        compose.waitForIdle()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val folder = File("build/screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // A full-screen bitmap is about 13 MB; a dozen per test adds up fast.
        bitmap.recycle()
    }

    /**
     * A bottom sheet's body on its own. A sheet is a window of its own, which
     * Robolectric's capture does not reach, so the body is rendered directly
     * on the sheet's surface colour.
     */
    protected fun sheet(name: String, content: @Composable () -> Unit) {
        runBlocking {
            container.repository.seedCatalog()
            DemoData.install(container.repository)
        }
        compose.setContent {
            MacroDimeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
            }
        }
        save(name)
    }

    /** The README's salmon dinner at the prices the app uses. */
    protected fun salmonDinner() = MealItem(
        name = "Salmon Dinner",
        slot = MealSlot.Dinner,
        portions = listOf("salmon-fillet", "white-rice", "fresh-broccoli", "olive-oil").map { Portion(FoodCatalog.food(it)!!) },
    )

    protected fun swapReview(prefix: String) {
        val swap = BudgetFoodEngine(FoodCatalog.all).bestSwap(salmonDinner())!!
        sheet("$prefix-sheet-swap-review") { SwapReviewContent(swap, onApply = {}, onDismiss = {}) }
    }

    /** Polls with the main looper idled on every pass; see AppFlowTest.eventually for why. */
    protected fun waitForText(text: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (true) {
            compose.waitForIdle()
            if (compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("\"$text\" did not appear")
            Thread.sleep(50)
        }
    }

    /** [pro] defaults to the demo: the demo is a subscriber's app, a fresh install is not. */
    protected fun launch(demo: Boolean, tab: Int = 0, pro: PreviewSubscriptionStore = if (demo) PreviewSubscriptionStore.subscriber() else PreviewSubscriptionStore()) {
        container.subscriptions = pro
        runBlocking {
            container.repository.seedCatalog()
            if (demo) DemoData.install(container.repository)
        }
        compose.setContent { MacroDimeTheme { MacroDimeRoot(container, initialTab = tab) } }
    }

    protected fun waitForTag(tag: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (true) {
            compose.waitForIdle()
            if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("\"$tag\" did not appear")
            Thread.sleep(50)
        }
    }

    protected fun onboarding(prefix: String) {
        launch(demo = false)
        waitForTag("intro-hook")
        save("$prefix-onboarding-0a-hook")
        compose.onNodeWithTag("intro-start").performClick()
        waitForTag("intro-pains")
        compose.onNodeWithTag("pain-HealthyFoodCostsTooMuch").performClick()
        compose.onNodeWithTag("pain-ProteinIsHard").performClick()
        save("$prefix-onboarding-0b-pains")
        compose.onNodeWithTag("intro-continue").performClick()
        waitForTag("intro-fixes")
        save("$prefix-onboarding-0c-fixes")
        compose.onNodeWithTag("intro-continue").performClick()
        waitForText("Welcome")
        save("$prefix-onboarding-1-welcome")
        val names = listOf("2-body", "3-goal", "4-activity", "5-diet", "6-routine", "7-budget", "8-summary")
        for (name in names) {
            compose.onNodeWithTag("continue").performClick()
            compose.waitForIdle()
            save("$prefix-onboarding-$name")
        }
    }

    protected fun tabs(prefix: String) {
        launch(demo = true)
        waitForText("Today's meals")
        save("$prefix-tab-1-today")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Review swap"))
        save("$prefix-tab-1-today-scrolled")
        compose.onNodeWithText("Review swap").performClick()
        waitForText("Substitutions")
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Meal Plan")
        save("$prefix-tab-2-plan")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("add-snack"))
        save("$prefix-tab-2-plan-scrolled")
        compose.onNodeWithTag("add-snack").performClick()
        waitForText("Add to Snacks")
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        // The demo plans the rest of this grocery week, so the first empty day,
        // which offers to plan itself, is the first day of the next week.
        val today = LocalDate.now()
        val firstEmpty = MacroDimeRepository.weekStart(today).plusDays(7)
        repeat(ChronoUnit.DAYS.between(today, firstEmpty).toInt()) {
            compose.onNodeWithContentDescription("Next day").performClick()
            compose.waitForIdle()
        }
        waitForTag("plan-day")
        save("$prefix-tab-2-plan-empty-day")

        compose.onNodeWithTag("tab-Groceries").performClick()
        waitForText("Still to buy")
        save("$prefix-tab-3-groceries")

        compose.onNodeWithTag("tab-Settings").performClick()
        waitForText("Your plan")
        save("$prefix-tab-4-settings")
    }

    /** The paywall in a given state, full screen, top and scrolled to its plans and timeline. */
    protected fun paywall(
        name: String,
        state: StoreState,
        selected: ProPlan = ProPlan.Annual,
        savedSoFar: String? = null,
        reason: PaywallReason = PaywallReason.AfterOnboarding,
    ) {
        runBlocking {
            container.repository.seedCatalog()
            DemoData.install(container.repository)
        }
        val profile = runBlocking { container.repository.currentProfile() }!!
        val preview = runBlocking { container.repository.mealsOnce(java.time.LocalDate.now()) }
        compose.setContent {
            MacroDimeTheme {
                PaywallContent(
                    state = state, store = Store.GooglePlay, profile = profile, reason = reason,
                    savedSoFar = savedSoFar, preview = preview, selected = selected, remind = true,
                    onSelect = {}, onRemindChange = {}, onPurchase = {}, onRestore = {}, onRetry = {},
                    onDismissMessage = {}, onOpenPrivacy = {}, onOpenHealth = {}, onDeleteAll = {}, onManage = {},
                )
            }
        }
        waitForText("MacroDime Pro")
        save(name)
        if (state.offers.isNotEmpty() && !state.isOnHold) {
            // By index, not by node: the list runs on behind the pinned button.
            // Items: photo, headline, today's meals, benefits, plans, timeline, reminder, terms.
            compose.onNode(hasScrollToNodeAction()).performScrollToIndex(4)
            save("$name-scrolled")
        }
    }

    protected val ready = StoreState(StoreState.Availability.Ready, PreviewSubscriptionStore.DEFAULT_OFFERS)

    /** Someone whose trial lapsed: the paywall is the way back in, welcoming them back. */
    protected fun lapsed(prefix: String) {
        launch(demo = true, pro = PreviewSubscriptionStore())
        waitForText("Welcome back")
        save("$prefix-paywall-returning")
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LightScreenshotTest : ScreenshotBase() {

    @Test
    fun onboarding() = onboarding("light")

    @Test
    fun tabs() = tabs("light")

    @Test
    fun swapReviewSheet() = swapReview("light")

    @Test
    fun portionSwapSheet() {
        val meal = salmonDinner()
        val options = BudgetFoodEngine(FoodCatalog.all).rankedReplacements(meal.portions.first(), meal)
        sheet("light-sheet-portion-swap") { PortionSwapContent(meal.portions.first(), meal, options, onSelect = {}, onDismiss = {}) }
    }

    @Test
    fun foodPickerSheet() = sheet("light-sheet-food-picker") {
        FoodPickerContent(FoodCatalog.all, MealSlot.Snack, onAdd = { _, _ -> }, onDismiss = {})
    }

    @Test
    fun measurementSheet() {
        val app = ApplicationProvider.getApplicationContext<MacroDimeApplication>().container
        sheet("light-sheet-measurement") {
            val profile = runBlocking { app.repository.currentProfile() }!!
            LogMeasurementContent(profile, app.repository, app.photos, null, onPhotoChange = {}, onCancel = {}, onSaved = {})
        }
    }

    /** The smallest common phone width, where four rings and three stat tiles are tightest. */
    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun narrowPhone() {
        launch(demo = true)
        waitForText("Today's meals")
        save("narrow-tab-1-today")
        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Meal Plan")
        save("narrow-tab-2-plan")
    }

    /** Large system text, where clipped labels and overflowing rows show up. */
    @Test
    fun largeText() {
        RuntimeEnvironment.setFontScale(1.6f)
        launch(demo = true)
        waitForText("Today's meals")
        save("large-text-tab-1-today")
        compose.onNodeWithTag("tab-Plan").performClick()
        waitForText("Meal Plan")
        save("large-text-tab-2-plan")
    }

    @Test
    fun paywall() = paywall("light-paywall", ready)

    @Test
    fun paywallMonthly() = paywall(
        "light-paywall-monthly",
        StoreState(StoreState.Availability.Ready, PreviewSubscriptionStore.WITH_MONTHLY),
        selected = ProPlan.Monthly,
    )

    /** Someone whose trial lapsed, shown their own savings first. */
    @Test
    fun paywallAfterATrial() = paywall("light-paywall-saved", ready, savedSoFar = "$4.50")

    @Test
    fun paywallWithoutGooglePlay() = paywall("light-paywall-unavailable", StoreState(StoreState.Availability.Unavailable))

    @Test
    fun paywallOnHold() = paywall("light-paywall-on-hold", ready.copy(isOnHold = true), reason = PaywallReason.Returning)

    @Test
    fun lapsed() = lapsed("light")

    @Test
    fun trialReminder() {
        val now = System.currentTimeMillis()
        launch(demo = true, pro = PreviewSubscriptionStore(Entitlement(isPro = true, plan = ProPlan.Annual, verifiedAtMillis = now, trialEndsAtMillis = now + 30 * 3_600_000L)))
        waitForText("Your free trial ends")
        save("light-trial-reminder")
    }

    /** The plan cards and the button are where long prices and large text would clip first. */
    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun paywallNarrowPhone() = paywall("narrow-paywall", ready)

    @Test
    fun paywallLargeText() {
        RuntimeEnvironment.setFontScale(1.6f)
        paywall("large-text-paywall", ready)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
class DarkScreenshotTest : ScreenshotBase() {

    @Test
    fun onboarding() = onboarding("dark")

    @Test
    fun tabs() = tabs("dark")

    @Test
    fun swapReviewSheet() = swapReview("dark")

    @Test
    fun paywall() = paywall("dark-paywall", ready)

    @Test
    fun lapsed() = lapsed("dark")
}
