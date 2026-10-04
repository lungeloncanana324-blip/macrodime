/*
 * OnboardingScreen.kt
 * MacroDime
 *
 * The multi-step wizard. Port of MacroDime/Views/OnboardingView.swift, with
 * the three intro screens in front of it on first run (IntroScreens.kt).
 *
 * The summary step is the point of the whole flow: the user sees their real
 * TDEE, targets and daily cost estimate *before* committing, so the numbers
 * feel earned rather than assigned.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lungelo.macrodime.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import com.lungelo.macrodime.ui.components.CardShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lungelo.macrodime.domain.BiologicalSex
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.EatingSchedule
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.FoodExclusion
import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.MeasurementSystem
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.domain.roundedHalfAway
import com.lungelo.macrodime.engine.FoodCatalog
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.CardTitle
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.MacroRingRow
import com.lungelo.macrodime.ui.components.PlanGapsCard
import com.lungelo.macrodime.ui.components.SelectableRow
import com.lungelo.macrodime.ui.components.StatTile
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.components.icon
import com.lungelo.macrodime.ui.settings.HealthDisclaimer
import com.lungelo.macrodime.ui.theme.Brand

/**
 * @param onClose shown as a close button when editing from Settings; null on first run.
 * @param onSaved called after the profile is stored.
 */
@Composable
fun OnboardingScreen(model: OnboardingViewModel, onClose: (() -> Unit)?, onSaved: () -> Unit) {
    // Back walks the wizard backwards; from the first step it leaves (closing
    // the editor, or, on first run, letting the system take the user out).
    BackHandler(enabled = !model.isFirstStep || onClose != null) {
        if (!model.goBack()) onClose?.invoke()
    }

    when (model.step) {
        OnboardingViewModel.Step.Hook -> return HookScreen(onStart = model::advance)
        OnboardingViewModel.Step.Pains -> return PainsScreen(model.pains, model::togglePain, onBack = { model.goBack() }, onContinue = model::advance)
        OnboardingViewModel.Step.Fixes -> return FixesScreen(model.pains, onBack = { model.goBack() }, onContinue = model::advance)
        else -> Unit
    }

    // The wizard runs before a profile exists, so it provides the draft's
    // currency rather than reading one from the store.
    CompositionLocalProvider(LocalCurrency provides model.currency) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                Column {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            when {
                                !model.isFirstStep -> IconButton(onClick = { model.goBack() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                                onClose != null -> IconButton(onClick = onClose) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close without saving")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    LinearProgressIndicator(
                        progress = { model.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .semantics { contentDescription = "Setup progress" },
                        color = Brand.colors.accent,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }
            },
            bottomBar = { Footer(model, onSaved) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            AnimatedContent(
                targetState = model.step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "step",
                modifier = Modifier.padding(padding),
            ) { step ->
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(step.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(step.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (step) {
                            // Drawn by IntroScreens before this scaffold is reached.
                            OnboardingViewModel.Step.Hook, OnboardingViewModel.Step.Pains, OnboardingViewModel.Step.Fixes -> Unit
                            OnboardingViewModel.Step.Welcome -> WelcomeStep(model)
                            OnboardingViewModel.Step.BodyMetrics -> BodyMetricsStep(model)
                            OnboardingViewModel.Step.Goal -> GoalStep(model)
                            OnboardingViewModel.Step.Activity -> ActivityStep(model)
                            OnboardingViewModel.Step.Diet -> DietStep(model)
                            OnboardingViewModel.Step.Routine -> RoutineStep(model)
                            OnboardingViewModel.Step.Budget -> BudgetStep(model)
                            OnboardingViewModel.Step.Summary -> SummaryStep(model)
                        }
                    }
                }
            }
        }

        model.saveError?.let { message ->
            AlertDialog(
                onDismissRequest = model::dismissSaveError,
                confirmButton = { TextButton(onClick = model::dismissSaveError) { Text("OK") } },
                title = { Text("Could not save") },
                text = { Text(message) },
            )
        }
    }
}

@Composable
private fun Footer(model: OnboardingViewModel, onSaved: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The acknowledgement sits beside the button it unlocks. On iOS it
            // once sat below four cards, and the first person to run the app
            // found a disabled button and no visible reason for it.
            if (model.step == OnboardingViewModel.Step.Summary) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) { model.hasAcknowledgedDisclaimer = !model.hasAcknowledgedDisclaimer }
                        .testTag("acknowledge"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "I understand these are estimates, not medical advice.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = model.hasAcknowledgedDisclaimer, onCheckedChange = null)
                }
            }

            val error = model.validationError
            if (error != null && (model.step == OnboardingViewModel.Step.BodyMetrics || model.step == OnboardingViewModel.Step.Summary)) {
                WarningLine(error.message)
            }

            Button(
                onClick = { if (model.isLastStep) model.save(onSaved) else model.advance() },
                enabled = model.canAdvance,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("continue"),
            ) {
                // Planning a week takes a moment on a phone: say what is happening.
                if (model.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    when {
                        !model.isLastStep -> "Continue"
                        model.isEditing -> if (model.isSaving) "Saving" else "Save changes"
                        model.isSaving -> "Building your week"
                        else -> "Build my week"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// Steps

/** The six questions ahead, so "about a minute" can be checked at a glance. */
private val questionsAhead = listOf(
    "About you" to "Height, weight and age",
    "Your goal" to "Lose fat or build muscle",
    "How active you are" to "How much you move in a week",
    "What you eat" to "Your diet, and anything to avoid",
    "How you eat" to "Meals a day and time to cook",
    "Your budget" to "What you can spend on food a day",
)

@Composable
private fun WelcomeStep(model: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = model.displayName,
            onValueChange = { model.displayName = it },
            label = { Text("What should we call you?") },
            placeholder = { Text("Optional") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("name"),
        )
        MacroCard {
            questionsAhead.forEachIndexed { index, (title, detail) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Caption(detail)
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyMetricsStep(model: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MacroCard {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MeasurementSystem.entries.forEachIndexed { index, system ->
                    SegmentedButton(
                        selected = model.measurementSystem == system,
                        onClick = { model.updateMeasurementSystem(system) },
                        shape = SegmentedButtonDefaults.itemShape(index, MeasurementSystem.entries.size),
                    ) { Text(system.displayName, maxLines = 1) }
                }
            }

            // Asked on Welcome the first time; the editor starts here, so it asks again.
            if (model.isEditing) {
                OutlinedTextField(
                    value = model.displayName,
                    onValueChange = { model.displayName = it },
                    label = { Text("Name") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (model.measurementSystem == MeasurementSystem.Metric) {
                NumberField("Height", model.heightText, "cm", model::updateHeightText, decimal = false, tag = "height")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberMenu("Feet", model.heightFeet, 3..7, "ft", Modifier.weight(1f)) { model.updateHeight(it, model.heightInches) }
                    NumberMenu("Inches", model.heightInches, 0..11, "in", Modifier.weight(1f)) { model.updateHeight(model.heightFeet, it) }
                }
            }

            NumberField(
                "Weight",
                model.weightText,
                if (model.measurementSystem == MeasurementSystem.Metric) "kg" else "lb",
                model::updateWeightText,
                decimal = true,
                tag = "weight",
            )

            Stepper(
                label = "Age",
                value = "${model.age}",
                onDecrement = { model.updateAge(model.age - 1) },
                onIncrement = { model.updateAge(model.age + 1) },
                canDecrement = model.age > 18,
                canIncrement = model.age < 100,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Biological sex", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BiologicalSex.entries.forEachIndexed { index, sex ->
                        SegmentedButton(
                            selected = model.sex == sex,
                            onClick = { model.sex = sex },
                            shape = SegmentedButtonDefaults.itemShape(index, BiologicalSex.entries.size),
                        ) { Text(sex.displayName) }
                    }
                }
                Caption("Biological sex is used only as a term in the BMR equation.")
            }
        }
        LivePreviewStrip(model)
    }
}

@Composable
private fun GoalStep(model: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FitnessGoal.entries.forEach { goal ->
            SelectableRow(goal.displayName, goal.subtitle, goal.icon, model.goal == goal) { model.goal = goal }
        }
        LivePreviewStrip(model)
    }
}

@Composable
private fun ActivityStep(model: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityLevel.entries.forEach { level ->
            SelectableRow(
                level.displayName,
                "${level.subtitle} · ×${DisplayFormat.number(level.multiplier, 3)}",
                level.icon,
                model.activity == level,
            ) { model.activity = level }
        }
        LivePreviewStrip(model)
    }
}

/**
 * What the user eats, and what they refuse. Every answer removes food from the
 * catalogue before the engine plans anything, which is why the count updates
 * as they tap.
 */
@Composable
private fun DietStep(model: OnboardingViewModel) {
    var isPickingBlocked by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MacroCard {
            CardTitle("Your pattern")
            DietaryPattern.entries.forEach { pattern ->
                SelectableRow(pattern.displayName, pattern.subtitle, pattern.icon, model.dietaryPattern == pattern) {
                    model.dietaryPattern = pattern
                }
            }
        }

        MacroCard {
            CardTitle("Anything to avoid?")
            Caption(
                "Allergies, intolerances, faith rules or plain dislike. Tap to exclude, tap again to allow. " +
                    "This removes ingredients from every suggestion the app makes.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FoodExclusion.entries.forEach { exclusion ->
                    FilterChip(
                        selected = exclusion in model.foodExclusions,
                        onClick = { model.toggle(exclusion) },
                        label = { Text(exclusion.displayName) },
                    )
                }
            }
            val removed = model.excludedFoodCount
            Caption(
                if (removed == 0) "Nothing is excluded at the moment." else "Removes $removed of ${FoodCatalog.all.size} ingredients from every suggestion.",
                color = if (removed == 0) MaterialTheme.colorScheme.onSurfaceVariant else Brand.colors.accent,
            )
            // The escape hatch for "I know it fits, I still will not eat it",
            // offered once something else is excluded, so the step stays short
            // for the majority with no restrictions.
            if (removed > 0 || model.blockedFoodIds.isNotEmpty()) {
                TextButton(onClick = { isPickingBlocked = true }) {
                    Icon(Icons.Rounded.PanTool, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    val count = model.blockedFoodIds.size
                    Text(if (count == 0) "Never suggest a specific food" else "$count food${if (count == 1) "" else "s"} blocked")
                }
            }
        }
    }

    if (isPickingBlocked) {
        AlertDialog(
            onDismissRequest = { isPickingBlocked = false },
            confirmButton = { TextButton(onClick = { isPickingBlocked = false }) { Text("Done") } },
            title = { Text("Never suggest") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(FoodCatalog.all.sortedBy { it.name }, key = { it.id }) { food ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { model.toggleBlocked(food.id) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = food.id in model.blockedFoodIds, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(food.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
        )
    }
}

/** How the day is divided and how much cooking is realistic: the answer people most often get wrong. */
@Composable
private fun RoutineStep(model: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MacroCard {
            CardTitle("Meals a day")
            EatingSchedule.entries.forEach { schedule ->
                SelectableRow(schedule.displayName, schedule.subtitle, Icons.Rounded.Schedule, model.eatingSchedule == schedule) {
                    model.eatingSchedule = schedule
                }
            }
        }
        MacroCard {
            CardTitle("How much cooking?")
            PrepEffort.entries.forEach { effort ->
                SelectableRow(effort.displayName, effort.subtitle, effort.icon, model.prepEffort == effort) {
                    model.prepEffort = effort
                }
            }
        }
        MacroCard {
            Stepper(
                label = "Meals eaten out",
                value = "${model.mealsOutPerWeek} a week",
                onDecrement = { model.updateMealsOut(model.mealsOutPerWeek - 1) },
                onIncrement = { model.updateMealsOut(model.mealsOutPerWeek + 1) },
                canDecrement = model.mealsOutPerWeek > 0,
                canIncrement = model.mealsOutPerWeek < 21,
            )
            Caption(
                "Meals away from home are not planned, counted or budgeted. The plan says so rather than pretending " +
                    "they do not exist.",
            )
        }
    }
}

@Composable
private fun BudgetStep(model: OnboardingViewModel) {
    val prices = LocalCurrency.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BudgetTier.entries.forEach { tier ->
            SelectableRow(
                "${tier.displayName} (${tier.priceSymbol})",
                tier.subtitle,
                if (tier == BudgetTier.Strict) Icons.Rounded.Payments else Icons.Rounded.ShoppingBasket,
                model.budgetTier == tier,
            ) { model.selectBudgetTier(tier) }
        }

        MacroCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily food allowance", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(prices.format(model.dailyFoodBudget), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            // The slider works in the currency on screen, stepping half a unit,
            // and stores USD.
            val low = prices.convert(3.0).toFloat()
            val high = prices.convert(60.0).toFloat()
            Slider(
                value = prices.convert(model.dailyFoodBudget).toFloat().coerceIn(low, high),
                onValueChange = { shown ->
                    val stepped = (shown * 2.0).roundedHalfAway() / 2.0
                    model.budgetWasEdited(prices.toStorage(stepped))
                },
                valueRange = low..high,
                modifier = Modifier.semantics { stateDescription = prices.format(model.dailyFoodBudget) }.testTag("allowance"),
            )
            Caption("About ${prices.format(model.weeklyBudget)} a week.")
            Caption(
                "Ingredient prices are US supermarket averages and every meal cost is built from them. If you entered " +
                    "a currency and rate, amounts are converted at your rate and are approximate.",
            )
            model.budgetWarning?.let { WarningLine(it) }
        }
    }
}

@Composable
private fun SummaryStep(model: OnboardingViewModel) {
    val prescription = model.prescription
    val prices = LocalCurrency.current
    var isDisclaimerExpanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TargetsHero(
            calories = DisplayFormat.calories(prescription.targets.calories),
            protein = DisplayFormat.grams(prescription.targets.protein),
            allowance = prices.format(model.dailyFoodBudget),
        )

        MacroCard {
            CardTitle("How we got there")
            CalculationRow("BMI", DisplayFormat.number(prescription.bmi, 1), prescription.bmiCategory.displayName)
            CalculationRow("BMR (Mifflin-St Jeor)", DisplayFormat.calories(prescription.bmr), "At complete rest")
            CalculationRow("TDEE", DisplayFormat.calories(prescription.tdee), "BMR × ${DisplayFormat.flexible(model.activity.multiplier, 3)}")
            CalculationRow(
                if (prescription.isDeficit) "Deficit" else "Surplus",
                DisplayFormat.calories(kotlin.math.abs(prescription.calorieDelta)),
                "${DisplayFormat.percent(kotlin.math.abs(1 - model.goal.calorieMultiplier))} " +
                    "${if (prescription.isDeficit) "below" else "above"} maintenance",
            )
            CalculationRow(
                "Protein",
                DisplayFormat.grams(prescription.targets.protein),
                "${DisplayFormat.number(prescription.proteinGramsPerKilogram(model.weightKg), 1)} g per kg body weight",
            )
            if (prescription.adjustments.isNotEmpty()) {
                HorizontalDivider()
                prescription.adjustments.forEach { adjustment ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = Brand.colors.caution, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Caption(adjustment.message, color = Brand.colors.caution)
                    }
                }
            }
        }

        MacroCard {
            CardTitle("Your budget")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Per day", prices.format(model.dailyFoodBudget), Modifier.weight(1f), model.budgetTier.displayName, Icons.Rounded.CalendarMonth, Brand.colors.underBudget)
                StatTile("Per week", prices.format(model.weeklyBudget), Modifier.weight(1f), "7 days", Icons.Rounded.ShoppingCart, Brand.colors.underBudget)
                StatTile("Per 1,000 kcal", prices.format(model.costPer1000Calories), Modifier.weight(1f), "Energy cost", Icons.Rounded.LocalFireDepartment, Brand.colors.underBudget)
            }
            model.budgetWarning?.let { WarningLine(it) }
        }

        // Gaps in this combination, shown while the choices behind them can
        // still be changed.
        PlanGapsCard(model.feasibility, title = "Before you commit")

        MacroCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = Brand.colors.overBudget, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Brand.colors.overBudget)
            }
            Caption(if (isDisclaimerExpanded) HealthDisclaimer.BODY else HealthDisclaimer.BODY.take(180).trimEnd() + "…")
            TextButton(onClick = { isDisclaimerExpanded = !isDisclaimerExpanded }) {
                Text(if (isDisclaimerExpanded) "Show less" else "Read the full statement")
            }
        }

        Caption(HealthDisclaimer.BMI_CAVEAT)
    }
}

// Pieces

/** The plan's three numbers, large, on one dark card: what every day will be built to. */
@Composable
private fun TargetsHero(calories: String, protein: String, allowance: String) {
    Surface(Modifier.fillMaxWidth().testTag("targets-hero"), shape = CardShape, color = MaterialTheme.colorScheme.primary) {
        Row(Modifier.padding(20.dp)) {
            listOf("Calories" to calories, "Protein" to protein, "Food budget" to allowance).forEach { (label, value) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                    Text("a day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** Always-visible preview, so the user watches the numbers respond as they change an input. */
@Composable
private fun LivePreviewStrip(model: OnboardingViewModel) {
    val prescription = model.prescription
    MacroCard(Modifier.alpha(if (model.validationError == null) 1f else 0.4f), padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("TDEE", DisplayFormat.calories(prescription.tdee), Modifier.weight(1f), "Maintenance", Icons.Rounded.Bolt, MaterialTheme.colorScheme.onSurfaceVariant)
            StatTile("Target", DisplayFormat.calories(prescription.targets.calories), Modifier.weight(1f), model.goal.displayName, model.goal.icon, Brand.colors.accent)
            StatTile("Protein", DisplayFormat.grams(prescription.targets.protein), Modifier.weight(1f), "Per day", Icons.Rounded.Restaurant, Brand.colors.protein)
        }
    }
}

@Composable
private fun CalculationRow(title: String, value: String, caption: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Caption(caption)
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Brand.colors.caution, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Brand.colors.caution)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    unit: String,
    onChange: (String) -> Unit,
    decimal: Boolean,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> if (text.length <= 7 && text.all { it.isDigit() || it == '.' || it == ',' }) onChange(text) },
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun NumberMenu(label: String, value: Int, range: IntRange, unit: String, modifier: Modifier, onSelect: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = "$value $unit",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            range.forEach { option ->
                DropdownMenuItem(text = { Text("$option $unit") }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

/** A labelled value with minus and plus buttons, the Android stand-in for a SwiftUI Stepper. */
@Composable
internal fun Stepper(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        FilledTonalIconButton(onClick = onDecrement, enabled = canDecrement) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label")
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onIncrement, enabled = canIncrement) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $label")
        }
    }
}
