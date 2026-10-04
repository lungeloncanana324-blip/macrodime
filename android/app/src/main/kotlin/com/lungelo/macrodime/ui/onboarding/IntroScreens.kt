/*
 * IntroScreens.kt
 * MacroDime
 *
 * The three screens before the first question, kept short on purpose:
 *  1. What the app is, over a photograph of the food it plans.
 *  2. What has been getting in the way, in the person's own words, picked
 *     from five. This is the screen that connects: it asks before it tells.
 *  3. The answer to each thing they picked, each one something the app
 *     actually does today.
 * Every word comes from Intro and PainPoint in :core, where it is tested.
 */
package com.lungelo.macrodime.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lungelo.macrodime.R
import com.lungelo.macrodime.domain.Intro
import com.lungelo.macrodime.domain.PainPoint
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.FoodPhoto
import com.lungelo.macrodime.ui.components.OptionShape
import com.lungelo.macrodime.ui.components.StatusBarOverPhoto
import com.lungelo.macrodime.ui.theme.Brand

private val PainPoint.icon: ImageVector
    get() = when (this) {
        PainPoint.HealthyFoodCostsTooMuch -> Icons.Rounded.Payments
        PainPoint.ProteinIsHard -> Icons.Rounded.Egg
        PainPoint.DontKnowWhatToCook -> Icons.Rounded.SoupKitchen
        PainPoint.FoodGoesToWaste -> Icons.Rounded.ShoppingBasket
        PainPoint.PlansIgnoreCost -> Icons.Rounded.Receipt
    }

/** The first screen: the food, the promise, one button. */
@Composable
fun HookScreen(onStart: () -> Unit) {
    StatusBarOverPhoto()
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("intro-hook")) {
        FoodPhoto(R.drawable.food_hero, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.25f),
                    0.35f to Color.Transparent,
                    0.55f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.92f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("MACRODIME", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Text(Intro.HEADLINE, style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(Intro.SUBHEADLINE, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.88f))
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("intro-start"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF161614)),
            ) { Text(Intro.GET_STARTED, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** The second screen: what has been getting in the way. Any, all or none. */
@Composable
fun PainsScreen(picked: Set<PainPoint>, onToggle: (PainPoint) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    IntroPage(
        photo = R.drawable.food_groceries,
        title = Intro.PAINS_TITLE,
        subtitle = Intro.PAINS_SUBTITLE,
        button = "Continue",
        onBack = onBack,
        onButton = onContinue,
        tag = "intro-pains",
    ) {
        PainPoint.entries.forEach { pain ->
            val isPicked = pain in picked
            val ink = MaterialTheme.colorScheme.primary
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = isPicked, role = Role.Checkbox, onValueChange = { onToggle(pain) })
                    .testTag("pain-${pain.name}"),
                shape = OptionShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (isPicked) BorderStroke(2.dp, ink) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(if (isPicked) ink else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            pain.icon,
                            contentDescription = null,
                            tint = if (isPicked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(pain.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(
                        if (isPicked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isPicked) ink else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/** The third screen: the answer to each thing picked, or what the app does when nothing was. */
@Composable
fun FixesScreen(picked: Set<PainPoint>, onBack: () -> Unit, onContinue: () -> Unit) {
    IntroPage(
        photo = R.drawable.food_mealprep,
        title = Intro.fixesTitle(picked),
        subtitle = Intro.FIXES_SUBTITLE,
        button = Intro.BUILD_MY_PLAN,
        onBack = onBack,
        onButton = onContinue,
        tag = "intro-fixes",
    ) {
        Intro.fixes(picked).forEach { pain ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.size(32.dp).background(Brand.colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Brand.colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(pain.fixTitle, style = MaterialTheme.typography.titleMedium)
                    Caption(pain.fixDetail)
                }
            }
        }
    }
}

/** The shape both question-free pages share: a photograph, a title, the content, one button. */
@Composable
private fun IntroPage(
    photo: Int,
    title: String,
    subtitle: String,
    button: String,
    onBack: () -> Unit,
    onButton: () -> Unit,
    tag: String,
    content: @Composable () -> Unit,
) {
    StatusBarOverPhoto()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(tag)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                FoodPhoto(photo, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.4f),
                            0.4f to Color.Transparent,
                            0.75f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                content()
                // Room for the pinned button, so the last option is never hidden under it.
                Spacer(Modifier.height(110.dp))
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onButton,
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 56.dp).testTag("intro-continue"),
                    shape = CircleShape,
                ) { Text(button, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
