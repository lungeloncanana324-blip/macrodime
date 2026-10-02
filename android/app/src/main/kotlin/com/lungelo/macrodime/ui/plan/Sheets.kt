/*
 * Sheets.kt
 * MacroDime
 *
 * The three sheets that act on a day: reviewing a whole-meal swap, choosing a
 * cheaper option for one ingredient, and adding a food. Ports of
 * SwapReviewSheet, PortionSwapSheet and FoodPickerSheet on iOS.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lungelo.macrodime.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.ArrowCircleUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.Portion
import com.lungelo.macrodime.engine.MacroAxis
import com.lungelo.macrodime.engine.MealSwap
import com.lungelo.macrodime.engine.PortionSwap
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.CardTitle
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.TierChip
import com.lungelo.macrodime.ui.components.displayName
import com.lungelo.macrodime.ui.components.formatted
import com.lungelo.macrodime.ui.onboarding.Stepper
import com.lungelo.macrodime.ui.theme.Brand
import java.util.Locale

/** A sheet header: title on the left, a cancel and an optional confirm on the right. */
@Composable
private fun SheetHeader(title: String, onCancel: () -> Unit, cancelLabel: String = "Cancel", confirm: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(cancelLabel) }
        confirm?.invoke()
    }
}

/**
 * Confirmation for a proposed swap. Shows the macro and cost effect side by
 * side, because "cheaper" is only acceptable if the macros hold.
 */
@Composable
fun SwapReviewSheet(swap: MealSwap, onApply: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SwapReviewContent(swap, onApply, onDismiss)
    }
}

/** The swap review's body. Apart from its sheet so tests can render and look at it. */
@Composable
fun SwapReviewContent(swap: MealSwap, onApply: () -> Unit, onDismiss: () -> Unit) {
    val prices = LocalCurrency.current
    Column {
        SheetHeader("Save ${prices.formatDisplayAmount(swap.shownSaving(prices))}", onDismiss) {
            Button(onClick = { onApply(); onDismiss() }, modifier = Modifier.testTag("apply-swap")) { Text("Apply") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MacroCard {
                CardTitle("Substitutions")
                // Each step saves the drop in the meal's shown cost from the
                // step before, so the steps add up to the saving in the title.
                val steps = swap.shownStepSavings(prices)
                swap.portionSwaps.forEachIndexed { index, portionSwap ->
                    Column {
                        Text(portionSwap.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${portionSwap.replacement.quantityDescription} · saves ${prices.formatDisplayAmount(steps[index])}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (swap.allAdjustments.isNotEmpty()) {
                MacroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        CardTitle("Portion adjustments")
                    }
                    Caption("Quantities changed to keep the macros where they were.")
                    swap.allAdjustments.forEach { adjustment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (adjustment.isIncrease) Icons.Rounded.ArrowCircleUp else Icons.Rounded.ArrowCircleDown,
                                contentDescription = if (adjustment.isIncrease) "More" else "Less",
                                tint = if (adjustment.isIncrease) Brand.colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(adjustment.headline, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Caption(prices.format(adjustment.costDelta))
                        }
                    }
                }
            }

            MacroCard {
                CardTitle("Effect")
                ComparisonRow(
                    "Cost",
                    prices.formatDisplayAmount(prices.shownCost(swap.original)),
                    prices.formatDisplayAmount(prices.shownCost(swap.swapped)),
                    isGood = true,
                )
                HorizontalDivider()
                MacroAxis.entries.forEach { axis ->
                    ComparisonRow(
                        axis.displayName,
                        axis.formatted(axis.valueIn(swap.original.nutrition)),
                        axis.formatted(axis.valueIn(swap.swapped.nutrition)),
                        isGood = false,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                Icon(Icons.Rounded.Verified, contentDescription = null, tint = Brand.colors.underBudget, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Caption(
                    "Worst-case macro change: ${DisplayFormat.percent(swap.worstDrift)}, inside the 10% tolerance.",
                    color = Brand.colors.underBudget,
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(title: String, before: String, after: String, isGood: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            before,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (isGood) TextDecoration.LineThrough else null,
        )
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "becomes",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp).size(14.dp),
        )
        Text(
            after,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isGood) Brand.colors.underBudget else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Ranked alternatives for one ingredient, so the user can choose rather than accept the top pick. */
@Composable
fun PortionSwapSheet(portion: Portion, meal: MealItem, options: List<PortionSwap>, onSelect: (PortionSwap) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PortionSwapContent(portion, meal, options, onSelect, onDismiss)
    }
}

/** The per-ingredient swap list's body, apart from its sheet so tests can render it. */
@Composable
fun PortionSwapContent(portion: Portion, meal: MealItem, options: List<PortionSwap>, onSelect: (PortionSwap) -> Unit, onDismiss: () -> Unit) {
    val prices = LocalCurrency.current
    Column {
        SheetHeader("Low-Cost Swap", onDismiss)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                MacroCard {
                    Caption("Replacing")
                    Text(portion.food.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Caption("${portion.quantityDescription} · ${prices.format(portion.cost)}")
                }
            }
            if (options.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.SearchOff,
                        "No cheaper match",
                        "Nothing in the catalogue is cheaper while keeping this meal's macros within 10%.",
                    )
                }
            } else {
                item { Text("Cheaper alternatives", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
                items(options, key = { it.replacement.food.id }) { option ->
                    MacroCard(
                        Modifier.clickable(role = Role.Button, onClickLabel = "Use ${option.replacement.food.name}") {
                            onSelect(option)
                            onDismiss()
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(option.replacement.food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(6.dp))
                                    TierChip(option.replacement.food.costTier)
                                }
                                Caption("${option.replacement.quantityDescription} · macros within ${DisplayFormat.percent(option.resultingDrift.worst)}")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Saves ${prices.formatDisplayAmount(prices.shownSaving(meal, option.resultingMeal))}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Brand.colors.underBudget,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Searchable catalogue with tier and category filters. */
@Composable
fun FoodPickerSheet(catalog: List<FoodSnapshot>, slot: MealSlot, onAdd: (FoodSnapshot, Double) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        FoodPickerContent(catalog, slot, onAdd, onDismiss)
    }
}

/** The food picker's body, apart from its sheet so tests can render it. */
@Composable
fun FoodPickerContent(catalog: List<FoodSnapshot>, slot: MealSlot, onAdd: (FoodSnapshot, Double) -> Unit, onDismiss: () -> Unit) {
    val prices = LocalCurrency.current
    var query by rememberSaveable { mutableStateOf("") }
    var servings by rememberSaveable { mutableDoubleStateOf(1.0) }
    var strictOnly by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<FoodCategory?>(null) }

    val filtered = remember(catalog, query, strictOnly, category) {
        val needle = query.trim().lowercase(Locale.getDefault())
        catalog.filter { food ->
            (!strictOnly || food.costTier == BudgetTier.Strict) &&
                (category == null || food.category == category) &&
                (needle.isEmpty() || needle in food.name.lowercase(Locale.getDefault()))
        }.sortedBy { it.name }
    }

    Column {
        SheetHeader("Add to ${slot.displayName}", onDismiss, cancelLabel = "Done")
        Column(Modifier.fillMaxHeight(0.92f)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search ingredients") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("food-search"),
                )
                Stepper(
                    label = "Servings",
                    value = DisplayFormat.flexible(servings, 2),
                    onDecrement = { servings = (servings - 0.25).coerceAtLeast(0.25) },
                    onIncrement = { servings = (servings + 0.25).coerceAtMost(6.0) },
                    canDecrement = servings > 0.25,
                    canIncrement = servings < 6.0,
                )
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Switch) { strictOnly = !strictOnly },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Budget staples only", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = strictOnly, onCheckedChange = null)
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") }) }
                items(FoodCategory.entries) { option ->
                    FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.displayName) })
                }
            }
            HorizontalDivider()
            if (filtered.isEmpty()) {
                EmptyState(Icons.Rounded.SearchOff, "No results", "Nothing in your catalogue matches. Your dietary settings may have removed it.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(filtered, key = { it.id }) { food ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClickLabel = "Add ${food.name}") {
                                    onAdd(food, servings)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(6.dp))
                                    TierChip(food.costTier)
                                }
                                Caption("${food.servingDescription} · ${DisplayFormat.calories(food.nutrition.calories)}")
                                // Letter first: "P 6 g" reads as protein; "6 gP" reads as a typo.
                                Caption(
                                    "P ${DisplayFormat.grams(food.nutrition.protein)} · C ${DisplayFormat.grams(food.nutrition.carbs)} · " +
                                        "F ${DisplayFormat.grams(food.nutrition.fat)}",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(prices.format(food.costPerServing), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${DisplayFormat.number(food.proteinPerCurrencyUnit, 1)} g/${prices.format(1.0)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Brand.colors.protein,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

/** A centred icon, title and explanation, for lists with nothing in them. */
@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            action?.invoke()
        }
    }
}
