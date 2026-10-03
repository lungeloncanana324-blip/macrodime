/*
 * Paywall.kt
 * MacroDime
 *
 * MacroDime Pro: the paywall, the screens that stand in for a locked feature,
 * and the two cards Today shows around it. Every word and figure comes from
 * Paywall and EntitlementPolicy in :core, where it is tested; these
 * composables only lay it out.
 *
 * What moves conversion here, and why it is honest:
 *  - It opens right after onboarding, with the targets and budget the person
 *    just set, which is when they most want the plan.
 *  - The yearly plan is chosen by default and carries the 14-day trial; its
 *    monthly cost and its saving are worked out from the store's own prices.
 *  - The trial is explained step by step, including when to cancel, and the
 *    full terms sit under the button: a surprise charge becomes a refund.
 *  - The button stays on screen while the page scrolls.
 */
package com.lungelo.macrodime.ui.paywall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lungelo.macrodime.billing.PlayBillingStore
import com.lungelo.macrodime.billing.StoreState
import com.lungelo.macrodime.billing.SubscriptionStore
import com.lungelo.macrodime.billing.findActivity
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.EntitlementPolicy
import com.lungelo.macrodime.domain.Paywall
import com.lungelo.macrodime.domain.PaywallReason
import com.lungelo.macrodime.domain.ProOffer
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.domain.Store
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.CardTitle
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.components.openExternal
import com.lungelo.macrodime.ui.settings.HealthDisclaimer
import com.lungelo.macrodime.ui.theme.Brand

/** The person's own numbers, as the paywall quotes them. */
private data class PersonalNumbers(val calories: String, val protein: String, val allowance: String)

@Composable
private fun UserProfileEntity.numbers(): PersonalNumbers {
    val targets = prescription.targets
    return PersonalNumbers(
        calories = DisplayFormat.calories(targets.calories),
        protein = DisplayFormat.grams(targets.protein),
        allowance = LocalCurrency.current.format(dailyFoodBudget),
    )
}

/**
 * The paywall, full screen. Closes itself once the store confirms Pro, and on
 * Back or "Not now", which is always there: a paywall nobody can leave is
 * against both stores' rules, and it costs trust.
 */
@Composable
fun PaywallScreen(
    subscriptions: SubscriptionStore,
    profile: UserProfileEntity,
    reason: PaywallReason,
    savedSoFarUsd: Double,
    onClose: () -> Unit,
) {
    val state by subscriptions.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prices = LocalCurrency.current
    var chosen by rememberSaveable { mutableStateOf(ProPlan.Annual) }
    // Pro already, on opening, is not a reason to close: only a purchase made here is.
    val startedPro = remember { state.isPro }
    LaunchedEffect(state.isPro) { if (state.isPro && !startedPro) onClose() }
    BackHandler(onBack = onClose)

    PaywallContent(
        state = state,
        store = subscriptions.store,
        profile = profile,
        reason = reason,
        savedSoFar = if (!state.isPro && savedSoFarUsd >= 0.01) prices.format(savedSoFarUsd) else null,
        // The yearly plan unless the store does not sell it, so the button is never dead.
        selected = if (state.offers.containsKey(chosen)) chosen else state.leadOffer?.plan ?: chosen,
        onSelect = { chosen = it },
        onPurchase = { plan -> context.findActivity()?.let { subscriptions.purchase(it, plan) } },
        onRestore = subscriptions::restore,
        onRetry = subscriptions::refresh,
        onDismissMessage = subscriptions::clearMessage,
        onOpenPrivacy = { openExternal(context, HealthDisclaimer.PRIVACY_POLICY_URL) },
        onClose = onClose,
    )
}

/** The paywall's body, apart from the store, so tests can render it in any state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallContent(
    state: StoreState,
    store: Store,
    profile: UserProfileEntity,
    reason: PaywallReason,
    savedSoFar: String?,
    selected: ProPlan,
    onSelect: (ProPlan) -> Unit,
    onPurchase: (ProPlan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onClose: () -> Unit,
) {
    val numbers = profile.numbers()
    val offer = state.offers[selected]

    Scaffold(
        modifier = Modifier.testTag("paywall"),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("paywall-close")) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (state.availability == StoreState.Availability.Ready) {
                        TextButton(onClick = onRestore, modifier = Modifier.testTag("paywall-restore")) { Text("Restore") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { Footer(state, offer, onPurchase, onDismissMessage, onClose) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProBadge()
                    Text(
                        Paywall.headline(reason, savedSoFar, numbers.allowance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("paywall-headline"),
                    )
                    Text(
                        Paywall.subheadline(numbers.calories, numbers.protein, numbers.allowance),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                MacroCard(Modifier.contentWidth()) {
                    Paywall.benefits.forEach { BenefitRow(it) }
                }
            }
            item { Plans(state, selected, onSelect, onRetry) }
            if (offer?.freeTrial != null) {
                item { Timeline(offer, store) }
            }
            item {
                Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (offer != null) Caption(Paywall.disclosure(offer, store), Modifier.testTag("paywall-terms"))
                    TextButton(onClick = onOpenPrivacy, contentPadding = PaddingValues(0.dp)) { Text("Privacy policy") }
                }
            }
        }
    }
}

@Composable
private fun Footer(
    state: StoreState,
    offer: ProOffer?,
    onPurchase: (ProPlan) -> Unit,
    onDismissMessage: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.message?.let { MessageLine(it, onDismissMessage) }
            val canBuy = offer != null && state.availability == StoreState.Availability.Ready && !state.isPurchasing
            if (offer != null || state.availability == StoreState.Availability.Connecting) {
                Button(
                    onClick = { offer?.let { onPurchase(it.plan) } },
                    enabled = canBuy,
                    modifier = Modifier.contentWidth().heightIn(min = 56.dp).testTag("paywall-cta"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isPurchasing) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            offer?.let(Paywall::callToAction) ?: "Loading plans",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.testTag("paywall-not-now")) { Text("Not now") }
        }
    }
}

@Composable
private fun Plans(state: StoreState, selected: ProPlan, onSelect: (ProPlan) -> Unit, onRetry: () -> Unit) {
    val offers = ProPlan.entries.mapNotNull { state.offers[it] }
    Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            offers.isNotEmpty() -> offers.forEach { offer ->
                PlanOption(Paywall.card(offer, state.offers[ProPlan.Monthly]), offer.plan == selected) { onSelect(offer.plan) }
            }
            state.availability == StoreState.Availability.Connecting -> MacroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading prices from Google Play", style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> MacroCard(Modifier.testTag("paywall-unavailable")) {
                Text(
                    if (state.availability == StoreState.Availability.Unavailable) UNAVAILABLE else PlayBillingStore.NO_PLANS,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

/** One plan as a radio choice. The yearly one names its trial and its saving; the monthly one claims neither. */
@Composable
private fun PlanOption(card: Paywall.PlanCard, isSelected: Boolean, onClick: () -> Unit) {
    val gold = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .testTag("plan-${card.plan.name}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(2.dp, if (isSelected) gold else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) gold else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    card.badge?.let {
                        Spacer(Modifier.width(8.dp))
                        Pill(it)
                    }
                }
                card.trial?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = gold, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(card.price, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                card.perMonth?.let { Caption(it) }
            }
        }
    }
}

@Composable
private fun Timeline(offer: ProOffer, store: Store) {
    val icons = listOf(Icons.Rounded.LockOpen, Icons.Rounded.Event, Icons.Rounded.Autorenew)
    MacroCard(Modifier.contentWidth().testTag("paywall-timeline")) {
        CardTitle("How the trial works")
        Paywall.timeline(offer, store).forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(icons.getOrElse(index) { Icons.Rounded.Event }, contentDescription = null, tint = Brand.colors.gold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(step.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Caption(step.detail)
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(benefit: Paywall.Benefit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Brand.colors.gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(benefit.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Caption(benefit.detail)
        }
    }
}

@Composable
private fun ProBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = Brand.colors.gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(Paywall.PRO_NAME, style = MaterialTheme.typography.labelLarge, color = Brand.colors.gold, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = Brand.colors.underBudget.copy(alpha = 0.16f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Brand.colors.underBudget,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MessageLine(text: String, onDismiss: () -> Unit) {
    Row(Modifier.contentWidth().testTag("paywall-message"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Brand.colors.caution, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp)) }
    }
}

private const val UNAVAILABLE =
    "Google Play couldn't be reached. Check that the Play Store app is installed and signed in, then try again."

// Around the paywall

/**
 * What a locked tab shows: the feature described in the person's own numbers,
 * and a way in. Never a blank screen or a bare lock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProLockedTab(title: String, reason: PaywallReason, profile: UserProfileEntity, state: StoreState, store: Store, onUnlock: () -> Unit) {
    val numbers = profile.numbers()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                MacroCard(Modifier.contentWidth().testTag("locked-${reason.name}")) {
                    ProBadge()
                    Text(Paywall.headline(reason, null, numbers.allowance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        Paywall.lockedDetail(reason, numbers.calories, numbers.protein),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Paywall.benefits.forEach { BenefitRow(it) }
                    UnlockButton(state, reason, onUnlock)
                    Caption(Paywall.unlockCaption(store))
                }
            }
        }
    }
}

/** Today, for a free account: the plan the targets above it would get. */
@Composable
fun UpgradeCard(profile: UserProfileEntity, state: StoreState, onUpgrade: () -> Unit) {
    MacroCard(Modifier.contentWidth().testTag("upgrade-card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Brand.colors.gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            CardTitle(Paywall.upgradeTitle(profile.numbers().allowance))
        }
        Caption(Paywall.UPGRADE_DETAIL)
        UnlockButton(state, PaywallReason.Planner, onUpgrade)
    }
}

/** The last days of a trial: what happens next, said plainly, with the way to cancel one tap away. */
@Composable
fun TrialReminderCard(state: StoreState, store: Store, nowMillis: Long, onManage: () -> Unit) {
    val days = EntitlementPolicy.trialDaysLeft(state.entitlement, nowMillis) ?: return
    val offer = state.offers[state.entitlement.plan ?: ProPlan.Annual]
    MacroCard(Modifier.contentWidth().testTag("trial-reminder")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Event, contentDescription = null, tint = Brand.colors.caution, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            CardTitle(EntitlementPolicy.reminderTitle(days))
        }
        Caption(EntitlementPolicy.reminderDetail(state.entitlement, offer, store))
        OutlinedButton(onClick = onManage) { Text("Manage in ${store.displayName}") }
    }
}

@Composable
private fun UnlockButton(state: StoreState, reason: PaywallReason, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("unlock-${reason.name}"),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(state.leadOffer?.let(Paywall::callToAction) ?: "See Pro plans", fontWeight = FontWeight.SemiBold)
    }
}

/** Icons the screens around the paywall share, kept with it. */
val ProIcon: ImageVector get() = Icons.Rounded.WorkspacePremium
