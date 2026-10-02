/*
 * MacroDimeRoot.kt
 * MacroDime
 *
 * Gates onboarding, then hosts the four tabs. Port of RootView and MainTabView
 * in MacroDime/App/MacroDimeApp.swift.
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lungelo.macrodime.AppContainer
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.currency
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.groceries.GroceryScreen
import com.lungelo.macrodime.ui.groceries.GroceryViewModel
import com.lungelo.macrodime.ui.onboarding.OnboardingScreen
import com.lungelo.macrodime.ui.onboarding.OnboardingViewModel
import com.lungelo.macrodime.ui.plan.DayPlanViewModel
import com.lungelo.macrodime.ui.plan.PlanScreen
import com.lungelo.macrodime.ui.settings.HealthAndSafetyScreen
import com.lungelo.macrodime.ui.settings.SettingsScreen
import com.lungelo.macrodime.ui.today.LogMeasurementSheet
import com.lungelo.macrodime.ui.today.TodayScreen
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

    when (val current = state) {
        // A blank frame, never the wizard: a returning user must not see
        // onboarding flash past while the profile loads.
        RootState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        is RootState.Onboarding -> {
            val model = appViewModel(key = "onboarding-${current.session}") { OnboardingViewModel(container.repository) }
            remember(model) { model.start(current.existing); true }
            OnboardingScreen(model, onClose = null, onSaved = {})
        }

        is RootState.Main -> CompositionLocalProvider(LocalCurrency provides current.profile.currency) {
            MainTabs(container, current.profile, initialTab)
        }
    }
}

@Composable
private fun MainTabs(container: AppContainer, profile: UserProfileEntity, initialTab: Int) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, Tab.entries.lastIndex)) }
    var isEditingProfile by rememberSaveable { mutableStateOf(false) }
    var isReadingHealth by rememberSaveable { mutableStateOf(false) }
    var isLoggingMeasurement by rememberSaveable { mutableStateOf(false) }

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
    if (isReadingHealth) {
        HealthAndSafetyScreen(onBack = { isReadingHealth = false })
        return
    }

    // Back from any tab but Today returns to Today before it leaves the app.
    BackHandler(enabled = tab != Tab.Today.ordinal) { tab = Tab.Today.ordinal }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.ordinal,
                        onClick = { tab = item.ordinal },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
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
                Tab.Today -> TodayScreen(today, onLogMeasurement = { isLoggingMeasurement = true })
                Tab.Plan -> PlanScreen(plan)
                Tab.Groceries -> GroceryScreen(groceries, onGoToPlan = { tab = Tab.Plan.ordinal })
                Tab.Settings -> SettingsScreen(
                    profile,
                    container.repository,
                    onEditProfile = { isEditingProfile = true },
                    onOpenHealth = { isReadingHealth = true },
                )
            }
        }
    }

    if (isLoggingMeasurement) {
        LogMeasurementSheet(profile, container.repository, container.photos, onDismiss = { isLoggingMeasurement = false })
    }
}
