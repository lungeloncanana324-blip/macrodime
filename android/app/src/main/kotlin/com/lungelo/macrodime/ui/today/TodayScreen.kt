/*
 * TodayScreen.kt
 * MacroDime
 *
 * The daily home screen, read top to bottom in the order a person asks the
 * questions: how much is left today (calories, protein, money), what am I
 * eating, can it be cheaper, and how am I doing. Port of
 * MacroDime/Views/DashboardView.swift, redesigned 2026-10-04 around the food:
 * every meal is shown with its photograph and its cost.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lungelo.macrodime.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lungelo.macrodime.billing.StoreState
import com.lungelo.macrodime.data.measurementSystem
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.data.waistChangeCm
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.EntitlementPolicy
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.Store
import com.lungelo.macrodime.domain.roundToIntHalfAway
import com.lungelo.macrodime.engine.MacroAxis
import com.lungelo.macrodime.engine.MealSwap
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.CardShape
import com.lungelo.macrodime.ui.components.CardTitle
import com.lungelo.macrodime.ui.components.FoodPhoto
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.MacroRingRow
import com.lungelo.macrodime.ui.components.PlanGapsCard
import com.lungelo.macrodime.ui.components.StatTile
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.components.formatted
import com.lungelo.macrodime.ui.components.photo
import com.lungelo.macrodime.ui.paywall.TrialReminderCard
import com.lungelo.macrodime.ui.plan.DayPlanState
import com.lungelo.macrodime.ui.plan.DayPlanViewModel
import com.lungelo.macrodime.ui.plan.EmptyState
import com.lungelo.macrodime.ui.plan.SwapReviewSheet
import com.lungelo.macrodime.ui.theme.Brand
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    model: DayPlanViewModel,
    onLogMeasurement: () -> Unit,
    onOpenPlan: () -> Unit = {},
    pro: StoreState = StoreState(entitlement = Entitlement(isPro = true)),
    store: Store = Store.GooglePlay,
    onManageSubscription: () -> Unit = {},
) {
    val state by model.state.collectAsStateWithLifecycle()
    var swapUnderReview by remember { mutableStateOf<MealSwap?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        // Nothing until the day has loaded. The empty state would say "No
        // targets yet", "$0.00 of $0.00" and "Nothing to flag in this plan",
        // and a false all-clear is worse than a blank frame.
        if (!state.isLoaded) return@Scaffold
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = padding.calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Greeting(state.profile?.displayName.orEmpty(), onLogMeasurement) }
            val now = System.currentTimeMillis()
            if (EntitlementPolicy.showsTrialReminder(pro.entitlement, now)) {
                item { TrialReminderCard(pro, store, now, onManageSubscription) }
            }
            item { SummaryCard(state) }
            item { MealsCard(state, onOpenPlan) }
            state.bestSwap?.let { best -> item { SwapPreviewCard(best) { swapUnderReview = best } } }
            item { MacrosCard(state) }
            item { Column(Modifier.contentWidth()) { PlanGapsCard(state.audit, title = "Today's gaps") } }
            item { ProgressCard(state, onLogMeasurement) }
            item { PowerhousesCard(state) }
        }
    }

    swapUnderReview?.let { swap ->
        SwapReviewSheet(swap, onApply = { model.apply(swap) }, onDismiss = { swapUnderReview = null })
    }
}

@Composable
private fun Greeting(name: String, onLogMeasurement: () -> Unit) {
    val hour = LocalTime.now().hour
    val part = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(Modifier.contentWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(if (name.isEmpty()) part else "$part, $name", style = MaterialTheme.typography.headlineMedium)
        }
        FilledTonalIconButton(onClick = onLogMeasurement) {
            Icon(Icons.Rounded.Straighten, contentDescription = "Log measurement")
        }
    }
}

/**
 * What is left today, in the three numbers the day is judged on, on one dark
 * card. Each figure is the shown amount, so the money left equals the
 * allowance minus the meals as shown elsewhere.
 */
@Composable
private fun SummaryCard(state: DayPlanState) {
    val targets = state.targets ?: return
    val prices = LocalCurrency.current
    val spent = prices.shownCost(state.meals)
    val allowance = prices.shown(state.dailyBudget)
    val left = allowance - spent
    val onCard = MaterialTheme.colorScheme.onPrimary
    Surface(
        modifier = Modifier.contentWidth().testTag("today-summary"),
        shape = CardShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = onCard,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                SummaryStat(
                    "Calories left",
                    if (state.remaining.calories >= 0) "${state.remaining.calories.roundToIntHalfAway()}" else "${(-state.remaining.calories).roundToIntHalfAway()} over",
                    "of ${DisplayFormat.calories(targets.calories)}",
                    Modifier.weight(1.2f),
                )
                SummaryStat(
                    "Protein left",
                    if (state.remaining.protein >= 0) DisplayFormat.grams(state.remaining.protein) else "${DisplayFormat.grams(-state.remaining.protein)} over",
                    "of ${DisplayFormat.grams(targets.protein)}",
                    Modifier.weight(1f),
                )
                // The words carry the signal: green or red would not read on
                // a card that is ink in light mode and paper in dark.
                SummaryStat(
                    if (left >= 0) "Budget left" else "Over budget",
                    prices.formatDisplayAmount(kotlin.math.abs(left)),
                    "of ${prices.formatDisplayAmount(allowance)}",
                    Modifier.weight(1f),
                )
            }
            // How much of the day's calories the plan already covers.
            val covered = if (targets.calories > 0) (state.consumed.calories / targets.calories).toFloat().coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(onCard.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(covered).fillMaxHeight().clip(CircleShape).background(Brand.colors.accent))
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, detail: String, modifier: Modifier) {
    val onCard = MaterialTheme.colorScheme.onPrimary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = onCard.copy(alpha = 0.7f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = onCard, maxLines = 1)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = onCard.copy(alpha = 0.7f), maxLines = 1)
    }
}

/** The day's meals with their photographs, each one a tap from the plan. */
@Composable
private fun MealsCard(state: DayPlanState, onOpenPlan: () -> Unit) {
    val prices = LocalCurrency.current
    val slots = state.dietary.schedule.slots.ifEmpty { MealSlot.entries }
    MacroCard(Modifier.contentWidth().testTag("today-meals")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Today's meals", Modifier.weight(1f))
            TextButton(onClick = onOpenPlan) { Text("Open plan") }
        }
        slots.forEach { slot ->
            val meal = state.meal(slot)?.takeIf { !it.isEmpty }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onOpenPlan).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoodPhoto(slot.photo, Modifier.size(56.dp), RoundedCornerShape(16.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(slot.displayName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (meal != null) {
                        Text(meal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption("${DisplayFormat.calories(meal.nutrition.calories)} · ${DisplayFormat.grams(meal.nutrition.protein)} protein")
                    } else {
                        Text("Nothing planned", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (meal != null) {
                    Text(prices.formatDisplayAmount(prices.shownCost(meal)), style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MacrosCard(state: DayPlanState) {
    MacroCard(Modifier.contentWidth()) {
        val targets = state.targets
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Macros", Modifier.weight(1f))
            if (targets != null) {
                Text(
                    "${state.consumed.calories.roundToIntHalfAway()} / ${targets.calories.roundToIntHalfAway()} kcal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (targets != null) {
            MacroRingRow(state.consumed, targets)
        } else {
            EmptyState(Icons.Rounded.BarChart, "No targets yet", "Finish setting up your profile to see your daily targets.")
        }
    }
}

@Composable
private fun SwapPreviewCard(best: MealSwap, onReview: () -> Unit) {
    val prices = LocalCurrency.current
    MacroCard(Modifier.contentWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Brand.colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Autorenew, contentDescription = null, tint = Brand.colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                CardTitle("Cheaper swap")
                Caption(best.original.name)
            }
            Text("Save ${prices.formatDisplayAmount(best.shownSaving(prices))}", style = MaterialTheme.typography.titleSmall, color = Brand.colors.accent)
        }
        // Old ingredient struck through on one line, its replacement under it,
        // the saving aligned on the right: long names wrap without breaking
        // the arrow away from either side. Step by step, so the lines add up
        // to the saving in the title.
        val steps = best.shownStepSavings(prices)
        best.portionSwaps.forEachIndexed { index, swap ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        swap.original.food.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "becomes", tint = Brand.colors.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(swap.replacement.food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(prices.formatDisplayAmount(steps[index]), style = MaterialTheme.typography.labelLarge, color = Brand.colors.accent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, contentDescription = null, tint = Brand.colors.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Caption("Macros stay within ${DisplayFormat.percent(best.worstDrift)} of the original")
        }
        Button(onClick = onReview, modifier = Modifier.fillMaxWidth(), shape = CircleShape) { Text("Review swap") }
    }
}

@Composable
private fun ProgressCard(state: DayPlanState, onLogMeasurement: () -> Unit) {
    val profile = state.profile
    val latest = state.measurements.maxByOrNull { it.recordedAt }
    MacroCard(Modifier.contentWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Progress", Modifier.weight(1f))
            TextButton(onClick = onLogMeasurement) {
                Icon(Icons.Rounded.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
        }
        if (profile != null && latest != null) {
            val waistChange = state.measurements.waistChangeCm()
            val recorded = Instant.ofEpochMilli(latest.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "Waist",
                    latest.waistCm?.let { "${DisplayFormat.number(it, 1)} cm" } ?: "Not set",
                    Modifier.weight(1f),
                    caption = waistChange?.let { "${DisplayFormat.number(kotlin.math.abs(it), 1)} cm ${if (it < 0) "down" else "up"} overall" } ?: "First entry",
                    icon = Icons.Rounded.Straighten,
                    tint = Brand.colors.measurement,
                )
                StatTile(
                    "Weight",
                    latest.weightKg?.let { DisplayFormat.weight(it, profile.measurementSystem) } ?: "Not set",
                    Modifier.weight(1f),
                    caption = recorded.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    icon = Icons.Rounded.MonitorWeight,
                    tint = Brand.colors.measurement,
                )
                StatTile(
                    "BMI",
                    DisplayFormat.number(profile.prescription.bmi, 1),
                    Modifier.weight(1f),
                    caption = profile.prescription.bmiCategory.displayName,
                    icon = Icons.Rounded.BarChart,
                    tint = Brand.colors.measurement,
                )
            }
            if (latest.photoFileName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Caption("Progress photo saved ${recorded.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}")
                }
            }
        } else {
            EmptyState(
                Icons.Rounded.Straighten,
                "No measurements yet",
                "Waist circumference and photos track what BMI cannot: whether the change is fat or muscle.",
            )
        }
    }
}

@Composable
private fun PowerhousesCard(state: DayPlanState) {
    val prices = LocalCurrency.current
    MacroCard(Modifier.contentWidth()) {
        CardTitle("Most protein for your money")
        Caption("Protein per ${prices.format(1.0)} on your tier")
        state.powerhouses.forEachIndexed { index, food ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${DisplayFormat.number(food.proteinPerCurrencyUnit, 1)} g",
                    style = MaterialTheme.typography.titleSmall,
                    color = Brand.colors.protein,
                )
            }
        }
    }
}

