/*
 * SettingsScreen.kt
 * MacroDime
 *
 * Profile review, currency, price sources, health and safety, and deleting
 * everything. Port of SettingsView in MacroDime/App/MacroDimeApp.swift.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lungelo.macrodime.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Support
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lungelo.macrodime.BuildConfig
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.activity
import com.lungelo.macrodime.data.budgetTier
import com.lungelo.macrodime.data.currency
import com.lungelo.macrodime.data.dietaryProfile
import com.lungelo.macrodime.data.goal
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.domain.CurrencySettings
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.Money
import com.lungelo.macrodime.domain.PriceBook
import com.lungelo.macrodime.engine.DietaryFilter
import com.lungelo.macrodime.engine.FoodCatalog
import com.lungelo.macrodime.engine.PriceTable
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.onboarding.OnboardingViewModel
import com.lungelo.macrodime.ui.theme.Brand
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    profile: UserProfileEntity,
    repository: MacroDimeRepository,
    onEditProfile: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val prices = LocalCurrency.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isConfirmingDelete by rememberSaveable { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val prescription = profile.prescription
    val diet = profile.dietaryProfile

    fun save(update: UserProfileEntity) {
        scope.launch { runCatching { repository.saveProfile(update) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Group("Your plan") {
                    Labeled("Goal", profile.goal.displayName)
                    Labeled("Activity", profile.activity.displayName)
                    Labeled("Budget tier", profile.budgetTier.displayName)
                    Labeled("Daily allowance", prices.format(profile.dailyFoodBudget))
                }
            }
            item {
                Group("Targets") {
                    Labeled("BMR", DisplayFormat.calories(prescription.bmr))
                    Labeled("TDEE", DisplayFormat.calories(prescription.tdee))
                    Labeled("Calories", DisplayFormat.calories(prescription.targets.calories))
                    Labeled("Protein", DisplayFormat.grams(prescription.targets.protein))
                    Labeled("Carbs", DisplayFormat.grams(prescription.targets.carbs))
                    Labeled("Fat", DisplayFormat.grams(prescription.targets.fat))
                }
            }
            item {
                // What the plan may suggest, in one place, read-only with the
                // editor one tap away, so a missing ingredient has a visible reason.
                val removed = DietaryFilter.rejected(FoodCatalog.all, diet).size
                Group(
                    "Food rules",
                    footer = if (removed == 0) {
                        "Nothing is excluded, so every ingredient in the catalogue is available to your plan."
                    } else {
                        "$removed of ${FoodCatalog.all.size} ingredients are excluded from your plan by these rules."
                    },
                ) {
                    Labeled("Pattern", diet.pattern.displayName)
                    Labeled("Meals a day", diet.schedule.displayName)
                    Labeled("Cooking", diet.prepEffort.displayName)
                    if (diet.exclusions.isNotEmpty()) Labeled("Avoiding", diet.exclusions.map { it.displayName }.sorted().joinToString(", "))
                    if (diet.blockedFoodIds.isNotEmpty()) {
                        val count = diet.blockedFoodIds.size
                        Labeled("Blocked", "$count food${if (count == 1) "" else "s"}")
                    }
                    if (diet.mealsOutPerWeek > 0) Labeled("Eating out", "${diet.mealsOutPerWeek} a week")
                }
            }
            item { CurrencyGroup(profile, ::save) }
            item {
                // The prices are compiled in, so saying so is also the privacy
                // statement: showing a price never goes online.
                val summary = PriceTable.summary
                Group("Prices", footer = summary.explanation) {
                    Labeled("Official averages", "${summary.sourcedCount} of ${summary.totalCount} foods")
                    Labeled("Estimates", "${summary.estimatedCount} foods")
                    Labeled("Latest data", summary.period)
                }
            }
            item {
                Group(
                    null,
                    footer = "Your targets are recalculated from your current weight every time it changes. Changing " +
                        "what you eat re-filters the ingredient catalogue straight away.",
                ) {
                    ActionRow("Edit profile", Icons.Rounded.Edit, onClick = onEditProfile, tag = "edit-profile")
                }
            }
            item {
                Group(null, footer = HealthDisclaimer.SHORT) {
                    ActionRow("Health & Safety", Icons.Rounded.HealthAndSafety, onClick = onOpenHealth, chevron = true)
                    HorizontalDivider()
                    ActionRow("Privacy policy", Icons.Rounded.Policy, onClick = { openLink(context, HealthDisclaimer.PRIVACY_POLICY_URL) }, external = true)
                }
            }
            item {
                Group("Your data", footer = "Everything MacroDime stores stays on this phone. Nothing is uploaded, and there is no account.") {
                    ActionRow(
                        "Delete All My Data",
                        Icons.Rounded.DeleteForever,
                        tint = Brand.colors.overBudget,
                        onClick = { isConfirmingDelete = true },
                        tag = "delete-all",
                    )
                }
            }
            item {
                Caption("MacroDime ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.contentWidth().padding(top = 8.dp))
            }
        }
    }

    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text("Delete everything?") },
            text = { Text(HealthDisclaimer.DELETE_SUMMARY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDelete = false
                        // No navigation needed: the root watches the profile, so
                        // removing it returns the app to onboarding on its own.
                        scope.launch {
                            runCatching { repository.deleteAllUserData() }.onFailure { deleteError = it.message ?: "Unknown error" }
                        }
                    },
                    modifier = Modifier.testTag("confirm-delete"),
                ) { Text("Delete Everything", color = Brand.colors.overBudget) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text("Cancel") } },
        )
    }
    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("Could not delete") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } },
        )
    }
}

/**
 * The fix for a real bug. Catalogue prices are US averages, and the iOS app
 * once printed them with whatever symbol the device locale named. Showing a
 * different currency is a conversion at a rate the user supplies, never a
 * relabelling. The rate is typed, never fetched: there is no exchange-rate feed
 * in this app, and inventing one would put an uncheckable number on screen.
 */
@Composable
private fun CurrencyGroup(profile: UserProfileEntity, save: (UserProfileEntity) -> Unit) {
    val currency = profile.currency
    var expanded by remember { mutableStateOf(false) }
    var rateText by rememberSaveable(profile.currencyCodeRaw) {
        mutableStateOf(DisplayFormat.flexible(profile.currencyUnitsPerUSD, 4, Locale.ROOT).replace(",", ""))
    }
    val parsedRate = OnboardingViewModel.parseDecimal(rateText)
    val rateIsUsable = parsedRate != null && Money.isUsable(parsedRate)

    Group("Currency", footer = PriceBook.RATE_DISCLAIMER) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = currency.displayCode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Show prices in") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CurrencySettings.pickerOptions(currency.displayCode).forEach { code ->
                    DropdownMenuItem(text = { Text(code) }, onClick = {
                        expanded = false
                        save(profile.copy(currencyCodeRaw = code))
                    })
                }
            }
        }
        OutlinedTextField(
            value = rateText,
            onValueChange = { text ->
                if (text.length > 10 || !text.all { it.isDigit() || it == '.' || it == ',' }) return@OutlinedTextField
                rateText = text
                val rate = OnboardingViewModel.parseDecimal(text)
                if (rate != null && Money.isUsable(rate)) save(profile.copy(currencyUnitsPerUSD = rate))
            },
            label = { Text("Rate") },
            prefix = { Text("1 USD = ") },
            suffix = { Text(currency.displayCode) },
            isError = !rateIsUsable,
            supportingText = if (rateIsUsable) null else ({ Text("Enter a rate between 0.01 and 10,000.") }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("rate"),
        )
        if (currency.isConverting) {
            Labeled("Allowance shows as", currency.format(profile.dailyFoodBudget))
        } else if (currency.displayCode != PriceBook.CURRENCY_CODE) {
            // A code with no rate yet is not a conversion: amounts stay honest
            // dollars until the user says what a dollar is worth.
            Caption(
                "Type today's rate to show prices in ${currency.displayCode}. Until then, amounts stay in USD.",
                color = Brand.colors.caution,
            )
        }
    }
}

/** Readable at any time, so the disclaimer is not a one-time dialog the user tapped past. */
@Composable
fun HealthAndSafetyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health & Safety", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Group("Health & safety") { Text(HealthDisclaimer.BODY, style = MaterialTheme.typography.bodyMedium) } }
            item { Group("On BMI") { Text(HealthDisclaimer.BMI_CAVEAT, style = MaterialTheme.typography.bodyMedium) } }
            item {
                Group(null, footer = "If tracking food is making your relationship with eating worse, stop and talk to someone.") {
                    ActionRow(HealthDisclaimer.SUPPORT_NAME, Icons.Rounded.Support, onClick = { openLink(context, HealthDisclaimer.SUPPORT_URL) }, external = true)
                }
            }
        }
    }
}

// Pieces

@Composable
private fun Group(title: String?, footer: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )
        }
        MacroCard { content() }
        if (footer != null) Caption(footer, Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    chevron: Boolean = false,
    external: Boolean = false,
    tag: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp)
            .let { if (tag != null) it.testTag(tag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == Brand.colors.overBudget) tint else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        when {
            chevron -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            external -> Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Opens in your browser", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Hands a web address to the browser. MacroDime itself never connects to
 * anything: it has no internet permission, so the page loads in the browser,
 * not in the app.
 */
private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser installed. Nothing useful to do; the URL is also in the store listing.
    }
}
