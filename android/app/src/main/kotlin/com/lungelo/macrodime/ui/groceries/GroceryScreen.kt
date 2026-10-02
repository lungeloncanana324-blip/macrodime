/*
 * GroceryScreen.kt
 * MacroDime
 *
 * The consolidated weekly shopping list, grouped by supermarket section in the
 * order the user walks the store. Port of MacroDime/Views/GroceryListView.swift.
 *
 * iOS puts "already have" and delete behind swipes. Here they sit in a visible
 * menu on each row, and the whole row is the tick target: ticking things off
 * happens one-handed, in a shop, usually in a hurry.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lungelo.macrodime.ui.groceries

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lungelo.macrodime.data.GroceryItemEntity
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.StatTile
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.components.icon
import com.lungelo.macrodime.ui.plan.EmptyState
import com.lungelo.macrodime.ui.theme.Brand
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GroceryScreen(model: GroceryViewModel, onGoToPlan: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    val prices = LocalCurrency.current

    LaunchedEffect(Unit) { model.refresh() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.clearMessage()
        }
    }

    // The locale's own short date: "Sep 27" in the US, "27 Sep" in South Africa.
    // Read from the configuration, so a language change redraws the label.
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    val weekLabel = "${state.weekStart.format(format)}-${state.weekStart.plusDays(6).format(format)}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groceries", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { model.shiftWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous week")
                    }
                    TextButton(onClick = model::thisWeek) { Text(weekLabel) }
                    IconButton(onClick = { model.shiftWeek(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next week")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "List options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rebuild from meal plan") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menuOpen = false; model.regenerate() },
                            )
                            DropdownMenuItem(
                                text = { Text("Show checked items") },
                                leadingIcon = { Checkbox(checked = state.showChecked, onCheckedChange = null) },
                                onClick = { model.setShowChecked(!state.showChecked) },
                            )
                            DropdownMenuItem(
                                text = { Text("Uncheck all") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                                onClick = { menuOpen = false; model.uncheckAll() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!state.isLoaded) return@Scaffold
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.ShoppingCart,
                    "No list for this week",
                    "Plan some meals for these seven days and the list builds itself from them.",
                ) { TextButton(onClick = onGoToPlan) { Text("Go to the plan") } }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                // Totals are the lines as shown, added up, so each one equals
                // the sections below it to the cent, in any currency.
                val stillToBuy = prices.shownTotal(state.items.map { it.outstandingCost })
                val fullList = prices.shownTotal(state.items.map { it.estimatedCost })
                val budget = prices.shown(state.weeklyBudget)
                MacroCard(Modifier.contentWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            "Still to buy",
                            prices.formatDisplayAmount(stillToBuy),
                            Modifier.weight(1f),
                            "${state.checkedCount} of ${state.items.size} ticked",
                            Icons.Rounded.ShoppingCart,
                            Brand.colors.underBudget,
                        )
                        StatTile("Full list", prices.formatDisplayAmount(fullList), Modifier.weight(1f), "Before pantry items", Icons.Rounded.Functions)
                        if (state.weeklyBudget > 0) {
                            val within = fullList <= budget
                            StatTile(
                                "Weekly budget",
                                prices.formatDisplayAmount(budget),
                                Modifier.weight(1f),
                                if (within) "Within budget" else "Over by ${prices.formatDisplayAmount(fullList - budget)}",
                                if (within) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                if (within) Brand.colors.underBudget else Brand.colors.overBudget,
                            )
                        }
                    }
                    if (state.items.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { state.checkedCount.toFloat() / state.items.size },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Shopping progress" },
                            color = Brand.colors.underBudget,
                            drawStopIndicator = {},
                        )
                    }
                }
            }

            state.sections.forEach { (section, items) ->
                item(key = section.rawValue) {
                    MacroCard(Modifier.contentWidth(), padding = 0.dp) {
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(section.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            // Struck-through "already have" lines are not in it, by design.
                            Text(prices.formatTotal(items.map { it.outstandingCost }), style = MaterialTheme.typography.labelLarge)
                        }
                        Column {
                            items.forEachIndexed { index, item ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                                GroceryRow(item, model)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroceryRow(item: GroceryItemEntity, model: GroceryViewModel) {
    val prices = LocalCurrency.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClickLabel = if (item.isChecked) "Mark as not bought" else "Mark as bought") {
                model.toggleChecked(item)
            }
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { stateDescription = if (item.isChecked) "Bought" else "Not bought" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (item.isChecked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.isChecked) Brand.colors.underBudget else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Row {
                Caption(item.quantityDescription)
                if (item.isAlreadyOwned) Caption(" · already have", color = Brand.colors.pantry)
            }
        }
        Text(
            prices.format(item.estimatedCost),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textDecoration = if (item.isAlreadyOwned) TextDecoration.LineThrough else null,
            color = if (item.isAlreadyOwned || item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${item.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (item.isAlreadyOwned) "Need it after all" else "Already have it") },
                    leadingIcon = {
                        Icon(
                            if (item.isAlreadyOwned) Icons.Rounded.AddShoppingCart else Icons.Rounded.Home,
                            contentDescription = null,
                            tint = Brand.colors.pantry,
                        )
                    },
                    onClick = { menuOpen = false; model.toggleOwned(item) },
                )
                // A line a planned meal still needs would come straight back on
                // the next rebuild, so only a manually added line can be deleted;
                // for the rest, "Already have it" is the honest action.
                if (item.sourceFoodId.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = Brand.colors.overBudget) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Brand.colors.overBudget) },
                        onClick = { menuOpen = false; model.delete(item) },
                    )
                }
            }
        }
    }
}
