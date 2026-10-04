/*
 * PlanGapsCard.kt
 * MacroDime
 *
 * The gap list, shared by the onboarding summary and the Today screen. Port of
 * MacroDime/Views/Components/PlanGapsCard.swift.
 *
 * One component for both, deliberately: the same rule set produces these
 * messages, and a plan that reads "nothing to flag" in one place while another
 * disagrees would destroy trust in both. Every row carries what the problem
 * is, the evidence, and what to do about it.
 */
package com.lungelo.macrodime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lungelo.macrodime.engine.PlanAudit
import com.lungelo.macrodime.engine.PlanGap
import com.lungelo.macrodime.ui.theme.Brand

@Composable
fun PlanGapsCard(report: PlanAudit.Report, title: String = "Plan gaps", collapsedLimit: Int = 3) {
    var showsEverything by rememberSaveable { mutableStateOf(false) }
    val visible = if (showsEverything) report.gaps else report.gaps.take(collapsedLimit)
    val hiddenCount = (report.gaps.size - collapsedLimit).coerceAtLeast(0)

    MacroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(title, Modifier.weight(1f))
            val worst = report.worstSeverity
            if (worst != null) {
                Icon(worst.icon, contentDescription = null, tint = tint(worst), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(report.headline, style = MaterialTheme.typography.labelMedium, color = tint(worst), fontWeight = FontWeight.Medium)
            } else {
                Text(report.headline, style = MaterialTheme.typography.labelMedium, color = Brand.colors.underBudget)
            }
        }

        if (report.isEmpty) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Verified, contentDescription = null, tint = Brand.colors.underBudget, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nothing to flag in this plan.", style = MaterialTheme.typography.bodyMedium, color = Brand.colors.underBudget)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visible.forEach { GapRow(it) }
            }
            if (hiddenCount > 0) {
                TextButton(onClick = { showsEverything = !showsEverything }) {
                    Text(if (showsEverything) "Show fewer" else "Show $hiddenCount more")
                }
            }
        }
    }
}

@Composable
private fun GapRow(gap: PlanGap) {
    val color = tint(gap.severity)
    Row(
        Modifier.clearAndSetSemantics {
            contentDescription = "${gap.severity.displayName}. ${gap.title}. ${gap.detail} ${gap.remedy}"
        },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(gap.severity.icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(gap.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Caption(gap.detail)
            Caption(gap.remedy, color = if (gap.severity == PlanGap.Severity.Info) MaterialTheme.colorScheme.onSurfaceVariant else color)
        }
    }
}

/** Colour is never the only signal: each row also has an icon and the remedy in words. */
@Composable
private fun tint(severity: PlanGap.Severity): Color = when (severity) {
    PlanGap.Severity.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    PlanGap.Severity.Caution -> Brand.colors.caution
    PlanGap.Severity.Blocking -> Brand.colors.overBudget
}
