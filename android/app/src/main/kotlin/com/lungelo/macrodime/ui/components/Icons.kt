/*
 * Icons.kt
 * MacroDime
 *
 * The Material Symbols standing in for the SF Symbols each enum names on iOS.
 * Kept out of :core, which knows nothing about how a goal is drawn.
 */
package com.lungelo.macrodime.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import com.lungelo.macrodime.domain.ActivityLevel
import com.lungelo.macrodime.domain.DietaryPattern
import com.lungelo.macrodime.domain.FitnessGoal
import com.lungelo.macrodime.domain.GrocerySection
import com.lungelo.macrodime.domain.MealSlot
import com.lungelo.macrodime.domain.PrepEffort
import com.lungelo.macrodime.engine.PlanGap

val FitnessGoal.icon: ImageVector
    get() = when (this) {
        FitnessGoal.FatLoss -> Icons.AutoMirrored.Rounded.TrendingDown
        FitnessGoal.MuscleGain -> Icons.AutoMirrored.Rounded.TrendingUp
    }

val ActivityLevel.icon: ImageVector
    get() = when (this) {
        ActivityLevel.Sedentary -> Icons.Rounded.Chair
        ActivityLevel.LightlyActive -> Icons.AutoMirrored.Rounded.DirectionsWalk
        ActivityLevel.ModeratelyActive -> Icons.AutoMirrored.Rounded.DirectionsRun
        ActivityLevel.VeryActive -> Icons.Rounded.FitnessCenter
    }

val MealSlot.icon: ImageVector
    get() = when (this) {
        MealSlot.Breakfast -> Icons.Rounded.WbTwilight
        MealSlot.Lunch -> Icons.Rounded.WbSunny
        MealSlot.Dinner -> Icons.Rounded.Bedtime
        MealSlot.Snack -> Icons.Rounded.Spa
    }

val GrocerySection.icon: ImageVector
    get() = when (this) {
        GrocerySection.Produce -> Icons.Rounded.Eco
        GrocerySection.MeatAndSeafood -> Icons.Rounded.SetMeal
        GrocerySection.Pantry -> Icons.Rounded.Inventory2
        GrocerySection.Frozen -> Icons.Rounded.AcUnit
        GrocerySection.Dairy -> Icons.Rounded.Egg
    }

val DietaryPattern.icon: ImageVector
    get() = when (this) {
        DietaryPattern.Omnivore -> Icons.Rounded.Restaurant
        DietaryPattern.Pescatarian -> Icons.Rounded.SetMeal
        DietaryPattern.Vegetarian -> Icons.Rounded.Eco
        DietaryPattern.Vegan -> Icons.Rounded.Grass
    }

val PrepEffort.icon: ImageVector
    get() = when (this) {
        PrepEffort.NoCook -> Icons.Rounded.Bolt
        PrepEffort.Quick -> Icons.Rounded.Timer
        PrepEffort.Standard -> Icons.Rounded.SoupKitchen
        PrepEffort.Unlimited -> Icons.Rounded.LocalFireDepartment
    }

val PlanGap.Severity.icon: ImageVector
    get() = when (this) {
        PlanGap.Severity.Info -> Icons.Rounded.Info
        PlanGap.Severity.Caution -> Icons.Rounded.Warning
        PlanGap.Severity.Blocking -> Icons.Rounded.Report
    }
