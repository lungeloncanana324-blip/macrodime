/*
 * PlanScreen.kt
 * MacroDime
 *
 * Builds a day's meals slot by slot, with running macros and cost against
 * target, and a swap at both the meal and the ingredient level. Port of
 * MacroDime/Views/MealPlannerView.swift.
 *
 * iOS hides ingredient actions in a long-press context menu. Android shows a
 * visible menu button on every row instead: long-press is undiscoverable on
 * Android, and the cheaper-option action is the product's headline feature.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lungelo.macrodime.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.engine.MacroAxis
import com.lungelo.macrodime.engine.MealSwap
import com.lungelo.macrodime.ui.components.BudgetMeter
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.MacroRingRow
import com.lungelo.macrodime.ui.components.TierChip
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.components.displayName
import com.lungelo.macrodime.ui.components.formatted
import com.lungelo.macrodime.ui.components.icon
import com.lungelo.macrodime.ui.components.tint
import com.lungelo.macrodime.ui.theme.Brand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/** Which ingredient the per-portion swap sheet is working on. */
private data class PortionTarget(val portion: Portion, val meal: MealItem)

@Composable
fun PlanScreen(model: DayPlanViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var pickerSlot by rememberSaveable { mutableStateOf<MealSlot?>(null) }
    var swapUnderReview by remember { mutableStateOf<MealSwap?>(null) }
    var portionTarget by remember { mutableStateOf<PortionTarget?>(null) }
    var isPickingDate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meal Plan", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { model.selectDate(state.date.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    TextButton(onClick = { isPickingDate = true }) { Text(dayLabel(state.date)) }
                    IconButton(onClick = { model.selectDate(state.date.plusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next day")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // Nothing until the day has loaded: an empty day with "$0.00 of $0.00"
        // would be a wrong answer, not a placeholder.
        if (!state.isLoaded) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                MacroCard(Modifier.contentWidth()) {
                    state.targets?.let { targets ->
                        MacroRingRow(state.consumed, targets, ringSize = 62.dp, lineWidth = 8.dp)
                        HorizontalDivider()
                    }
                    BudgetMeter(state.meals, state.dailyBudget, showsCaption = false)
                }
            }
            items(MealSlot.entries, key = { it.rawValue }) { slot ->
                MealCard(
                    slot = slot,
                    meal = state.meal(slot),
                    swap = state.meal(slot)?.let { state.swaps[it.id] },
                    targetsCalories = state.targets?.calories,
                    onAdd = { pickerSlot = slot },
                    onReviewSwap = { swapUnderReview = it },
                    onFindCheaper = { portion, meal -> portionTarget = PortionTarget(portion, meal) },
                    onRemove = { model.removePortion(it) },
                    onSetServings = { id, servings -> model.updateServings(id, servings) },
                )
            }
        }
    }

    pickerSlot?.let { slot ->
        FoodPickerSheet(state.catalog, slot, onAdd = { food, servings -> model.addFood(food, servings, slot) }, onDismiss = { pickerSlot = null })
    }
    swapUnderReview?.let { swap ->
        SwapReviewSheet(swap, onApply = { model.apply(swap) }, onDismiss = { swapUnderReview = null })
    }
    portionTarget?.let { target ->
        PortionSwapSheet(
            target.portion,
            target.meal,
            remember(target) { model.alternatives(target.portion, target.meal) },
            onSelect = { model.apply(it, target.meal) },
            onDismiss = { portionTarget = null },
        )
    }
    if (isPickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { isPickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        model.selectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    isPickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { isPickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(pickerState) }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

@Composable
private fun MealCard(
    slot: MealSlot,
    meal: MealItem?,
    swap: MealSwap?,
    targetsCalories: Double?,
    onAdd: () -> Unit,
    onReviewSwap: (MealSwap) -> Unit,
    onFindCheaper: (Portion, MealItem) -> Unit,
    onRemove: (UUID) -> Unit,
    onSetServings: (UUID, Double) -> Unit,
) {
    val prices = LocalCurrency.current
    MacroCard(Modifier.contentWidth().testTag("meal-${slot.rawValue}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(slot.icon, contentDescription = null, tint = Brand.colors.gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(slot.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (meal != null && !meal.isEmpty) {
                // The lines as shown, added up: the header always equals the column.
                Text(prices.formatDisplayAmount(prices.shownCost(meal)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                TierChip(meal.effectiveTier)
            }
        }

        if (meal != null && !meal.isEmpty) {
            meal.portions.forEach { portion ->
                PortionRow(portion, meal, onFindCheaper, onRemove, onSetServings)
            }
            HorizontalDivider()
            Row {
                MacroAxis.entries.forEach { axis ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(axis.formatted(axis.valueIn(meal.nutrition)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = axis.tint)
                        Text(axis.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (swap != null) {
                FilledTonalButton(
                    onClick = { onReviewSwap(swap) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Brand.colors.underBudget.copy(alpha = 0.14f),
                        contentColor = Brand.colors.underBudget,
                    ),
                ) {
                    Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Swap to save ${prices.formatDisplayAmount(swap.shownSaving(prices))}")
                }
            }
        } else {
            Caption(
                if (targetsCalories == null) "Nothing planned yet."
                else "Nothing planned. Aim for about ${DisplayFormat.calories(targetsCalories * slot.defaultCalorieShare)} here.",
            )
        }

        TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("add-${slot.rawValue}")) {
            Icon(Icons.Rounded.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add food")
        }
    }
}

@Composable
private fun PortionRow(
    portion: Portion,
    meal: MealItem,
    onFindCheaper: (Portion, MealItem) -> Unit,
    onRemove: (UUID) -> Unit,
    onSetServings: (UUID, Double) -> Unit,
) {
    val prices = LocalCurrency.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(portion.food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Caption(
                "${portion.quantityDescription} · ${DisplayFormat.calories(portion.nutrition.calories)} · " +
                    "${DisplayFormat.grams(portion.nutrition.protein)} protein",
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(prices.format(portion.cost), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${portion.food.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Find a cheaper option") },
                    leadingIcon = { Icon(Icons.Rounded.Autorenew, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onFindCheaper(portion, meal)
                    },
                )
                HorizontalDivider()
                listOf(0.5, 1.0, 1.5, 2.0).forEach { servings ->
                    DropdownMenuItem(
                        text = { Text("Set to ${DisplayFormat.flexible(servings, 2)} serving${if (servings == 1.0) "" else "s"}") },
                        onClick = {
                            menuOpen = false
                            onSetServings(portion.id, servings)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Remove", color = Brand.colors.overBudget) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Brand.colors.overBudget) },
                    onClick = {
                        menuOpen = false
                        onRemove(portion.id)
                    },
                )
            }
        }
    }
}
