/*
 * MacroDimeRoot.kt
 * MacroDime
 *
 * Gates onboarding, then MacroDime Pro, then hosts the four tabs. Port of
 * RootView and MainTabView in MacroDime/App/MacroDimeApp.swift.
 *
 * Since 2026-10-04 there is no free tier: after onboarding the paywall is the
 * way into the app, and it stays the way in for anyone whose trial or
 * subscription has ended. While the store is still being asked, a subscriber
 * sees a blank frame rather than the paywall flashing past.
 */
package com.lungelo.macrodime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lungelo.macrodime.AppContainer
import com.lungelo.macrodime.billing.StoreState
import com.lungelo.macrodime.billing.TrialReminder
import com.lungelo.macrodime.billing.findActivity
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.currency
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.PaywallReason
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.openExternal
import com.lungelo.macrodime.ui.groceries.GroceryScreen
import com.lungelo.macrodime.ui.groceries.GroceryViewModel
import com.lungelo.macrodime.ui.onboarding.OnboardingScreen
import com.lungelo.macrodime.ui.onboarding.OnboardingViewModel
import com.lungelo.macrodime.ui.paywall.PaywallScreen
import com.lungelo.macrodime.ui.plan.DayPlanViewModel
import com.lungelo.macrodime.ui.plan.PlanScreen
import com.lungelo.macrodime.ui.settings.HealthAndSafetyScreen
import com.lungelo.macrodime.ui.settings.SourcesScreen
import com.lungelo.macrodime.ui.settings.SettingsScreen
import com.lungelo.macrodime.ui.today.LogMeasurementSheet
import com.lungelo.macrodime.ui.today.TodayScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A view model built from the container, scoped to the activity unless a key separates instances. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Rounded.PieChart),
    Plan("Plan", Icons.Rounded.Restaurant),
    Groceries("Groceries", Icons.Rounded.ShoppingCart),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun MacroDimeRoot(container: AppContainer, initialTab: Int = 0) {
    val app = appViewModel { AppViewModel(container.repository) }
    val state by app.state.collectAsStateWithLifecycle()
    val subscriptions = container.subscriptions
    val pro by subscriptions.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The store is asked again whenever the app comes to the front, so a
    // purchase, a cancellation or an expiry elsewhere shows up straight away.
    LifecycleResumeEffect(subscriptions) {
        subscriptions.refresh()
        // Google Play's own word on a failed payment, if there is one.
        context.findActivity()?.let(subscriptions::showPaymentMessages)
        onPauseOrDispose { }
    }
    // The trial reminder follows the store's answer: a trial schedules it, a
    // cancellation in Google Play takes it away.
    LaunchedEffect(pro.entitlement) { TrialReminder.sync(context, pro.entitlement) }

    // Set when onboarding saves: the paywall then opens on the week just planned.
    var justOnboarded by rememberSaveable { mutableStateOf(false) }

    when (val current = state) {
        // A blank frame, never the wizard: a returning user must not see
        // onboarding flash past while the profile loads.
        RootState.Loading -> Blank()

        is RootState.Onboarding -> {
            val model = appViewModel(key = "onboarding-${current.session}") { OnboardingViewModel(container.repository) }
            remember(model) { model.start(current.existing); true }
            OnboardingScreen(model, onClose = null, onSaved = { justOnboarded = true })
        }

        is RootState.Main -> CompositionLocalProvider(LocalCurrency provides current.profile.currency) {
            when {
                pro.isPro -> MainTabs(container, current.profile, initialTab, pro)
                // Still asking the store: a subscriber must not see the paywall flash past.
                pro.availability == StoreState.Availability.Connecting -> Blank()
                else -> Gate(container, current.profile, if (justOnboarded) PaywallReason.AfterOnboarding else PaywallReason.Returning)
            }
        }
    }
}

@Composable
private fun Blank() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

/** The paywall as the way in, with the health statement and Delete All My Data reachable from it. */
@Composable
private fun Gate(container: AppContainer, profile: UserProfileEntity, reason: PaywallReason) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    // Null until today's meals have loaded: the paywall waits for them, so it
    // draws once, whole, instead of jumping down when the meals arrive just as
    // a finger reaches for a plan.
    val preview by container.repository.meals(today).collectAsStateWithLifecycle(initialValue = null as List<MealItem>?)
    val savedSoFar by container.repository.totalSwapSavings.collectAsStateWithLifecycle(initialValue = 0.0)
    var isReadingHealth by rememberSaveable { mutableStateOf(false) }
    var isReadingSources by rememberSaveable { mutableStateOf(false) }

    val meals = preview
    if (meals == null) {
        Blank()
        return
    }
    // Sources first: Health & Safety opens it too, and Back returns there.
    if (isReadingSources) {
        SourcesScreen(onBack = { isReadingSources = false })
        return
    }
    if (isReadingHealth) {
        HealthAndSafetyScreen(onBack = { isReadingHealth = false }, onOpenSources = { isReadingSources = true })
        return
    }
    PaywallScreen(
        subscriptions = container.subscriptions,
        profile = profile,
        reason = reason,
        savedSoFarUsd = savedSoFar,
        preview = meals,
        onOpenHealth = { isReadingHealth = true },
        onOpenSources = { isReadingSources = true },
        onDeleteAll = {
            scope.launch {
                runCatching { container.repository.deleteAllUserData() }
                container.subscriptions.forget()
                TrialReminder.forget(context)
            }
        },
    )
}

@Composable
private fun MainTabs(container: AppContainer, profile: UserProfileEntity, initialTab: Int, pro: StoreState) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, Tab.entries.lastIndex)) }
    var isEditingProfile by rememberSaveable { mutableStateOf(false) }
    var isReadingHealth by rememberSaveable { mutableStateOf(false) }
    var isReadingSources by rememberSaveable { mutableStateOf(false) }
    var isLoggingMeasurement by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val subscriptions = container.subscriptions
    val manageSubscription = { openExternal(context, subscriptions.manageSubscriptionUrl) }

    val today = appViewModel(key = "today") { DayPlanViewModel(container.repository) }
    val plan = appViewModel(key = "plan") { DayPlanViewModel(container.repository) }
    val groceries = appViewModel { GroceryViewModel(container.repository) }

    // Today means today: an app left open overnight moves on with the clock.
    LifecycleResumeEffect(Unit) {
        today.selectDate(LocalDate.now())
        onPauseOrDispose { }
    }

    if (isEditingProfile) {
        val editor = appViewModel(key = "edit-profile") { OnboardingViewModel(container.repository) }
        // Loaded once per opening of the editor. Saveable, so after a rotation
        // the flag is restored rather than the initializer re-run, and the
        // edits in progress survive instead of being reloaded from the store.
        rememberSaveable { editor.load(profile); true }
        OnboardingScreen(editor, onClose = { isEditingProfile = false }, onSaved = { isEditingProfile = false })
        return
    }
    if (isReadingSources) {
        SourcesScreen(onBack = { isReadingSources = false })
        return
    }
    if (isReadingHealth) {
        HealthAndSafetyScreen(onBack = { isReadingHealth = false }, onOpenSources = { isReadingSources = true })
        return
    }

    // Back from any tab but Today returns to Today before it leaves the app.
    BackHandler(enabled = tab != Tab.Today.ordinal) { tab = Tab.Today.ordinal }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.ordinal,
                        onClick = { tab = item.ordinal },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.testTag("tab-${item.name}"),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        // Room for the bottom bar only. Each tab's own scaffold handles the
        // status bar, so its top app bar can draw behind it edge to edge.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (Tab.entries[tab]) {
                Tab.Today -> TodayScreen(
                    today,
                    onLogMeasurement = { isLoggingMeasurement = true },
                    onOpenPlan = { tab = Tab.Plan.ordinal },
                    pro = pro,
                    store = subscriptions.store,
                    onManageSubscription = manageSubscription,
                )
                Tab.Plan -> PlanScreen(plan)
                Tab.Groceries -> GroceryScreen(groceries, onGoToPlan = { tab = Tab.Plan.ordinal })
                Tab.Settings -> SettingsScreen(
                    profile,
                    container.repository,
                    onEditProfile = { isEditingProfile = true },
                    onOpenHealth = { isReadingHealth = true },
                    onOpenSources = { isReadingSources = true },
                    pro = pro,
                    store = subscriptions.store,
                    onManageSubscription = manageSubscription,
                    onRestore = subscriptions::restore,
                    onDeleted = {
                        subscriptions.forget()
                        TrialReminder.forget(context)
                    },
                )
            }
        }
    }

    if (isLoggingMeasurement) {
        LogMeasurementSheet(profile, container.repository, container.photos, onDismiss = { isLoggingMeasurement = false })
    }
}
