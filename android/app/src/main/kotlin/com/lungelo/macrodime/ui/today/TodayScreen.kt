/*
 * TodayScreen.kt
 * MacroDime
 *
 * The daily home screen: macro rings, the cost tracker, the plan's gaps, a
 * low-cost swap preview, non-BMI progress and the budget powerhouses. Port of
 * MacroDime/Views/DashboardView.swift. Macros and money sit side by side on
 * purpose: they are one decision, not two.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lungelo.macrodime.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lungelo.macrodime.data.measurementSystem
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.data.waistChangeCm
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.roundToIntHalfAway
import com.lungelo.macrodime.engine.MacroAxis
import com.lungelo.macrodime.engine.MealSwap
import com.lungelo.macrodime.ui.components.BudgetMeter
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.CardTitle
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.MacroRingRow
import com.lungelo.macrodime.ui.components.PlanGapsCard
import com.lungelo.macrodime.ui.components.StatTile
import com.lungelo.macrodime.ui.components.TierChip
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.components.formatted
import com.lungelo.macrodime.ui.plan.DayPlanState
import com.lungelo.macrodime.ui.plan.DayPlanViewModel
import com.lungelo.macrodime.ui.plan.EmptyState
import com.lungelo.macrodime.ui.plan.SwapReviewSheet
import com.lungelo.macrodime.ui.theme.Brand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(model: DayPlanViewModel, onLogMeasurement: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var swapUnderReview by remember { mutableStateOf<MealSwap?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val name = state.profile?.displayName.orEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (name.isEmpty()) "Today" else "Hi, $name", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onLogMeasurement) {
                        Icon(Icons.Rounded.Straighten, contentDescription = "Log measurement")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // Nothing until the day has loaded. The empty state would say "No
        // targets yet", "$0.00 of $0.00" and "Nothing to flag in this plan",
        // and a false all-clear is worse than a blank frame.
        if (!state.isLoaded) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { MacrosCard(state) }
            item { CostCard(state) }
            item { Column(Modifier.contentWidth()) { PlanGapsCard(state.audit, title = "Today's gaps") } }
            state.bestSwap?.let { best -> item { SwapPreviewCard(best) { swapUnderReview = best } } }
            item { ProgressCard(state, onLogMeasurement) }
            item { PowerhousesCard(state) }
        }
    }

    swapUnderReview?.let { swap ->
        SwapReviewSheet(swap, onApply = { model.apply(swap) }, onDismiss = { swapUnderReview = null })
    }
}

@Composable
private fun MacrosCard(state: DayPlanState) {
    MacroCard(Modifier.contentWidth()) {
        val targets = state.targets
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Today's macros", Modifier.weight(1f))
            if (targets != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "${state.consumed.calories.roundToIntHalfAway()} / ${targets.calories.roundToIntHalfAway()} kcal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (targets != null) {
            MacroRingRow(state.consumed, targets)
            HorizontalDivider()
            Row {
                Remaining("Calories left", state.remaining.calories, MacroAxis.Calories, Modifier.weight(1f))
                Remaining("Protein left", state.remaining.protein, MacroAxis.Protein, Modifier.weight(1f))
            }
        } else {
            EmptyState(Icons.Rounded.BarChart, "No targets yet", "Finish setting up your profile to see your daily targets.")
        }
    }
}

@Composable
private fun Remaining(title: String, value: Double, axis: MacroAxis, modifier: Modifier) {
    Column(modifier) {
        Caption(title)
        Text(
            if (value >= 0) axis.formatted(value) else "${axis.formatted(-value)} over",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (value >= 0) MaterialTheme.colorScheme.onSurface else Brand.colors.overBudget,
        )
    }
}

@Composable
private fun CostCard(state: DayPlanState) {
    val prices = LocalCurrency.current
    MacroCard(Modifier.contentWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Cost tracker", Modifier.weight(1f))
            TierChip(state.budgetTier)
        }
        BudgetMeter(state.meals, state.dailyBudget)
        // Each swap's saving as shown on its own card, added up.
        val available = state.swaps.values.sumOf { it.shownSaving(prices) }
        if (available > 0) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Brand.colors.underBudget, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${prices.formatDisplayAmount(available)} of swaps available", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Caption("Same macros, cheaper ingredients")
                }
            }
        }
    }
}

@Composable
private fun SwapPreviewCard(best: MealSwap, onReview: () -> Unit) {
    val prices = LocalCurrency.current
    MacroCard(Modifier.contentWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            CardTitle("Low-cost swap", Modifier.weight(1f))
            Text("Save ${prices.formatDisplayAmount(best.shownSaving(prices))}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Brand.colors.underBudget)
        }
        Caption(best.original.name)
        // Old ingredient struck through on one line, its replacement under it,
        // the saving aligned on the right: long names wrap without breaking
        // the arrow away from either side.
        // Step by step, so the lines add up to the saving in the title.
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
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "becomes",
                            tint = Brand.colors.underBudget,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(swap.replacement.food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    prices.formatDisplayAmount(steps[index]),
                    style = MaterialTheme.typography.labelLarge,
                    color = Brand.colors.underBudget,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, contentDescription = null, tint = Brand.colors.underBudget, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Caption("Macros stay within ${DisplayFormat.percent(best.worstDrift)} of the original")
        }
        Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review swap") }
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
        CardTitle("Budget powerhouses")
        Caption("Most protein per ${prices.format(1.0)} on your tier")
        state.powerhouses.forEach { food ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${DisplayFormat.number(food.proteinPerCurrencyUnit, 1)} g",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand.colors.protein,
                )
            }
        }
    }
}
