/*
 * Components.kt
 * MacroDime
 *
 * Shared presentation pieces: progress rings, the budget meter, stat tiles,
 * chips, selectable rows. Port of MacroDime/Views/Components/MacroComponents.swift,
 * kept in one file so the visual language is defined once.
 *
 * Colour is never the only signal anywhere here: every ring carries a label and
 * a value, every budget state says "over" or "left" in words, and every row a
 * screen reader lands on reads as one sentence.
 */
package com.lungelo.macrodime.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.CurrencySettings
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.roundToIntHalfAway
import com.lungelo.macrodime.engine.MacroAxis
import com.lungelo.macrodime.ui.theme.Brand

/**
 * How money is shown, provided once at the root from the profile. The default
 * is the catalogue's own currency, so a screen rendered outside the app shell
 * still shows a true dollar amount rather than a local symbol on a dollar figure.
 */
val LocalCurrency = staticCompositionLocalOf { CurrencySettings.USD }

/** The widest a column of cards grows on a tablet or in landscape, as on iOS. */
val ContentMaxWidth = 620.dp

// Macro palette and labels

val MacroAxis.tint: Color
    @Composable get() = when (this) {
        MacroAxis.Calories -> Brand.colors.gold
        MacroAxis.Protein -> Brand.colors.protein
        MacroAxis.Carbs -> Brand.colors.carbs
        MacroAxis.Fat -> Brand.colors.fat
    }

val MacroAxis.displayName: String
    get() = when (this) {
        MacroAxis.Calories -> "Calories"
        MacroAxis.Protein -> "Protein"
        MacroAxis.Carbs -> "Carbs"
        MacroAxis.Fat -> "Fat"
    }

val MacroAxis.shortName: String
    get() = when (this) {
        MacroAxis.Calories -> "kcal"
        MacroAxis.Protein -> "P"
        MacroAxis.Carbs -> "C"
        MacroAxis.Fat -> "F"
    }

/** A value on this axis with its unit. */
fun MacroAxis.formatted(value: Double): String =
    if (this == MacroAxis.Calories) DisplayFormat.calories(value) else DisplayFormat.grams(value)

// Cards

/** The standard card. One definition, so radius, padding and colour never drift between screens. */
@Composable
fun MacroCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** Secondary text: captions, footnotes, explanations. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

// Progress ring

/**
 * One circular progress indicator. Over target is drawn, not clamped: the ring
 * fills, then a thinner red arc sweeps the overflow, because the over-target
 * state is the one the user most needs to see.
 */
@Composable
fun MacroRing(progress: Double, tint: Color, modifier: Modifier = Modifier, lineWidth: Dp = 9.dp) {
    val animated by animateFloatAsState(progress.toFloat().coerceIn(0f, 10f), label = "ring")
    val over = Brand.colors.overBudget
    Canvas(modifier) {
        val stroke = lineWidth.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(tint.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        val primary = animated.coerceAtMost(1f)
        if (primary > 0f) {
            drawArc(tint, -90f, 360f * primary, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val overflow = (animated - 1f).coerceIn(0f, 1f)
        if (overflow > 0f) {
            drawArc(over, -90f, 360f * overflow, false, topLeft, arcSize, style = Stroke(stroke * 0.55f, cap = StrokeCap.Round))
        }
    }
}

/** A ring plus the label and numbers that make it readable without colour. */
@Composable
fun MacroRingStat(
    axis: MacroAxis,
    consumed: Double,
    target: Double,
    modifier: Modifier = Modifier,
    ringSize: Dp = 74.dp,
    lineWidth: Dp = 9.dp,
) {
    val progress = if (target > 0) consumed / target else 0.0
    val description = "${axis.displayName}: ${axis.formatted(consumed)} of ${axis.formatted(target)}, " +
        DisplayFormat.percent(progress)
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }) {
        // Four rings share one row; on a 360 dp phone each gets less than its
        // ideal size, so the ring shrinks rather than the row overflowing. At
        // large text sizes it grows with the text, so the percentage inside
        // never spills over the stroke.
        val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
        val diameter = min(ringSize * fontScale, maxWidth)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                MacroRing(progress, axis.tint, Modifier.size(diameter), lineWidth)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DisplayFormat.percent(progress.coerceAtMost(9.99)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        axis.shortName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(axis.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${consumed.roundToIntHalfAway()} / ${target.roundToIntHalfAway()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The four rings, sharing the width equally. At large system text sizes four
 * labelled rings no longer fit across a phone, so they become two rows of two
 * rather than clipping "Calories" to "Calori".
 */
@Composable
fun MacroRingRow(
    consumed: NutritionFacts,
    targets: NutritionFacts,
    ringSize: Dp = 74.dp,
    lineWidth: Dp = 9.dp,
) {
    val rows = if (LocalDensity.current.fontScale > 1.3f) MacroAxis.entries.chunked(2) else listOf(MacroAxis.entries)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { axes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                axes.forEach { axis ->
                    MacroRingStat(axis, axis.valueIn(consumed), axis.valueIn(targets), Modifier.weight(1f), ringSize, lineWidth)
                }
            }
        }
    }
}

// Budget meter

/**
 * Spend against allowance. Turns red the moment spend exceeds the allowance,
 * and states the overage in words as well as colour.
 */
@Composable
fun BudgetMeter(spent: Double, allowance: Double, showsCaption: Boolean = true) {
    val prices = LocalCurrency.current
    val progress = if (allowance > 0) spent / allowance else 0.0
    val isOver = spent > allowance && allowance > 0
    val tint = if (isOver) Brand.colors.overBudget else Brand.colors.underBudget
    val animated by animateFloatAsState(progress.toFloat().coerceIn(0f, 1f), label = "budget")
    val status = if (isOver) "${prices.format(spent - allowance)} over" else "${prices.format(allowance - spent)} left"

    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Food budget: ${prices.format(spent)} spent of ${prices.format(allowance)}, $status"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(prices.format(spent), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(
                    "of ${prices.format(allowance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                Icon(
                    if (isOver) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(status, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.Medium)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
        if (showsCaption) Caption("Estimated cost of today's planned meals")
    }
}

// Tiles and chips

/** A small labelled metric, used three to a row. */
@Composable
fun StatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Price-tier chip: `$` or `$$`. Green for the budget tier, gold for the flexible one. */
@Composable
fun TierChip(tier: BudgetTier) {
    val color = if (tier == BudgetTier.Strict) Brand.colors.underBudget else Brand.colors.gold
    Text(
        tier.priceSymbol,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = tier.displayName },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}

/** One choice in a list of options, used throughout onboarding. Behaves as a radio button. */
@Composable
fun SelectableRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val gold = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(2.dp, if (isSelected) gold else Color.Transparent),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) gold else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Centres a column of cards and stops it growing past [ContentMaxWidth]. */
fun Modifier.contentWidth(): Modifier = this.widthIn(max = ContentMaxWidth).fillMaxWidth()
