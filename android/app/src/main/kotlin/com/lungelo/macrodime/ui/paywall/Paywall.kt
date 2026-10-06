/*
 * Paywall.kt
 * MacroDime
 *
 * MacroDime Pro: the paywall, which since 2026-10-04 is the way into the app
 * (a 14-day free trial that renews into the yearly plan; there is no free
 * tier), and the card Today shows in a trial's last two days. Every word and
 * figure comes from Paywall and EntitlementPolicy in :core, where it is
 * tested; these composables only lay it out.
 *
 * What moves conversion here, and why each is honest:
 *  - It opens on the week the app has just planned, with today's real meals
 *    and their real cost. The plan exists; the trial unlocks it.
 *  - The fear that stops a trial start is "I'll forget and be charged". The
 *    timeline answers it (today, a reminder on day 12, the charge on day 14),
 *    the reminder is real (TrialReminder), and "No payment today" sits under
 *    the button.
 *  - The yearly price is the biggest price on the screen; its monthly
 *    equivalent is small print, because Play's policy forbids leading a yearly
 *    plan with a monthly figure.
 *  - It cannot be closed, which both stores allow when the terms are stated in
 *    full, starting with the fact that the app needs a subscription (said
 *    above the plans and again in the terms). What must stay reachable does:
 *    restore, the privacy policy, the health statement and Delete All My Data,
 *    from the menu.
 *  - A subscription Google Play has put on hold (a failed payment, or a pause)
 *    is not sold again: the paywall says what happened and opens Google Play,
 *    where it is fixed.
 */
package com.lungelo.macrodime.ui.paywall

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lungelo.macrodime.R
import com.lungelo.macrodime.billing.PlayBillingStore
import com.lungelo.macrodime.billing.StoreState
import com.lungelo.macrodime.billing.SubscriptionStore
import com.lungelo.macrodime.billing.TrialReminder
import com.lungelo.macrodime.billing.findActivity
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.prescription
import com.lungelo.macrodime.domain.DisplayFormat
import com.lungelo.macrodime.domain.EntitlementPolicy
import com.lungelo.macrodime.domain.MealItem
import com.lungelo.macrodime.domain.Paywall
import com.lungelo.macrodime.domain.PaywallReason
import com.lungelo.macrodime.domain.ProOffer
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.domain.Store
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.components.CardTitle
import com.lungelo.macrodime.ui.components.FoodPhoto
import com.lungelo.macrodime.ui.components.LocalCurrency
import com.lungelo.macrodime.ui.components.MacroCard
import com.lungelo.macrodime.ui.components.OptionShape
import com.lungelo.macrodime.ui.components.StatusBarOverPhoto
import com.lungelo.macrodime.ui.components.contentWidth
import com.lungelo.macrodime.ui.components.openExternal
import com.lungelo.macrodime.ui.components.photo
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
 * The paywall, full screen, as the way into the app. The root replaces it with
 * the app the moment the store confirms Pro, so it never closes itself.
 *
 * @param preview today's planned meals, quoted on the paywall.
 * @param onOpenHealth the full health and safety statement.
 * @param onOpenSources where the prices on the paywall come from, linked, as Play
 *   requires of government figures; reachable without paying.
 * @param onDeleteAll Delete All My Data, reachable without paying.
 */
@Composable
fun PaywallScreen(
    subscriptions: SubscriptionStore,
    profile: UserProfileEntity,
    reason: PaywallReason,
    savedSoFarUsd: Double,
    preview: List<MealItem>,
    onOpenHealth: () -> Unit,
    onOpenSources: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val state by subscriptions.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prices = LocalCurrency.current
    var chosen by rememberSaveable { mutableStateOf(ProPlan.Annual) }
    var remind by rememberSaveable { mutableStateOf(TrialReminder.isEnabled(context)) }
    val selected = if (state.offers.containsKey(chosen)) chosen else state.leadOffer?.plan ?: chosen

    fun buy(plan: ProPlan) {
        context.findActivity()?.let { subscriptions.purchase(it, plan) }
    }
    // Asked for at the moment it is wanted, just before the store's sheet,
    // and the purchase goes ahead whatever the answer: a refusal only means
    // the reminder comes as a card on Today instead.
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { buy(selected) }

    PaywallContent(
        state = state,
        store = subscriptions.store,
        profile = profile,
        reason = reason,
        savedSoFar = if (savedSoFarUsd >= 0.01) prices.format(savedSoFarUsd) else null,
        preview = preview,
        selected = selected,
        remind = remind,
        onSelect = { chosen = it },
        onRemindChange = {
            remind = it
            TrialReminder.setEnabled(context, it)
        },
        onPurchase = { plan ->
            TrialReminder.setEnabled(context, remind)
            val wantsPermission = remind && state.offers[plan]?.freeTrial != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !TrialReminder.canNotify(context)
            if (wantsPermission) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS) else buy(plan)
        },
        onRestore = subscriptions::restore,
        onRetry = subscriptions::refresh,
        onDismissMessage = subscriptions::clearMessage,
        onOpenPrivacy = { openExternal(context, HealthDisclaimer.PRIVACY_POLICY_URL) },
        onOpenHealth = onOpenHealth,
        onOpenSources = onOpenSources,
        onDeleteAll = onDeleteAll,
        onManage = { openExternal(context, subscriptions.manageSubscriptionUrl) },
    )
}

/** The paywall's body, apart from the store, so tests can render it in any state. */
@Composable
fun PaywallContent(
    state: StoreState,
    store: Store,
    profile: UserProfileEntity,
    reason: PaywallReason,
    savedSoFar: String?,
    preview: List<MealItem>,
    selected: ProPlan,
    remind: Boolean,
    onSelect: (ProPlan) -> Unit,
    onRemindChange: (Boolean) -> Unit,
    onPurchase: (ProPlan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSources: () -> Unit,
    onDeleteAll: () -> Unit,
    onManage: () -> Unit,
) {
    val numbers = profile.numbers()
    val onHold = state.isOnHold
    // Nothing is sold to someone on hold: the subscription they have is fixed in the store.
    val offer = if (onHold) null else state.offers[selected]
    var isConfirmingDelete by rememberSaveable { mutableStateOf(false) }
    StatusBarOverPhoto()

    Scaffold(
        modifier = Modifier.testTag("paywall"),
        bottomBar = { Footer(state, offer, store, onPurchase, onRetry, onDismissMessage, onManage) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // The list stops at the pinned button rather than running on under it,
        // so nothing that can be tapped is ever hidden behind the footer.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Header(
                    showsRestore = state.availability == StoreState.Availability.Ready,
                    onRestore = onRestore,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenHealth = onOpenHealth,
                    onOpenSources = onOpenSources,
                    onDeleteAll = { isConfirmingDelete = true },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp).contentWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        Paywall.headline(reason, savedSoFar, onHold),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.testTag("paywall-headline"),
                    )
                    Text(
                        if (onHold) Paywall.onHoldDetail(store) else Paywall.subheadline(numbers.calories, numbers.protein, numbers.allowance),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (preview.any { !it.isEmpty }) {
                item { TodayPreview(preview.filter { !it.isEmpty }, Modifier.padding(horizontal = 20.dp)) }
            }
            if (!onHold) {
                item {
                    MacroCard(Modifier.padding(horizontal = 20.dp).contentWidth()) {
                        Paywall.benefits.forEach { BenefitRow(it) }
                    }
                }
                item { Plans(state, selected, onSelect, onRetry, Modifier.padding(horizontal = 20.dp)) }
            }
            if (offer?.freeTrial != null) {
                item { Timeline(offer, store, Modifier.padding(horizontal = 20.dp)) }
                item { ReminderRow(remind, onRemindChange, Modifier.padding(horizontal = 20.dp)) }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp).contentWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (offer != null) Caption(Paywall.disclosure(offer, store), Modifier.testTag("paywall-terms"))
                    Row {
                        if (state.availability == StoreState.Availability.Ready) {
                            TextButton(onClick = onRestore, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("paywall-restore")) {
                                Text("Restore purchases")
                            }
                            Spacer(Modifier.width(20.dp))
                        }
                        TextButton(onClick = onOpenPrivacy, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Privacy policy") }
                    }
                }
            }
        }
    }

    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text("Delete everything?") },
            text = { Text("Your profile, plans, measurements and photos are removed from this phone. A subscription belongs to your Google account and is not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDelete = false
                        onDeleteAll()
                    },
                    modifier = Modifier.testTag("paywall-confirm-delete"),
                ) { Text("Delete everything", color = Brand.colors.overBudget) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The photograph, with the menu and Restore over it. */
@Composable
private fun Header(
    showsRestore: Boolean,
    onRestore: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSources: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(250.dp)) {
        FoodPhoto(R.drawable.food_spread, Modifier.fillMaxSize())
        // Dark at the top for the status bar and the menu, fading into the page at the bottom.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.35f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.35f), modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    Paywall.PRO_NAME,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("paywall-more")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (showsRestore) {
                        DropdownMenuItem(text = { Text("Restore purchases") }, onClick = { menuOpen = false; onRestore() })
                    }
                    DropdownMenuItem(text = { Text("Health and safety") }, onClick = { menuOpen = false; onOpenHealth() })
                    DropdownMenuItem(
                        text = { Text("Sources") },
                        onClick = { menuOpen = false; onOpenSources() },
                        modifier = Modifier.testTag("paywall-sources"),
                    )
                    DropdownMenuItem(text = { Text("Privacy policy") }, onClick = { menuOpen = false; onOpenPrivacy() })
                    DropdownMenuItem(
                        text = { Text("Delete all my data", color = Brand.colors.overBudget) },
                        onClick = { menuOpen = false; onDeleteAll() },
                        modifier = Modifier.testTag("paywall-delete"),
                    )
                }
            }
        }
    }
}

/** Today's real meals, so the paywall shows the plan it unlocks rather than describing one. */
@Composable
private fun TodayPreview(meals: List<MealItem>, modifier: Modifier = Modifier) {
    val prices = LocalCurrency.current
    MacroCard(modifier.contentWidth().testTag("paywall-preview")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Today on your plan", Modifier.weight(1f))
            Text(
                prices.formatDisplayAmount(meals.sumOf { prices.shownCost(it) }),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        meals.forEach { meal ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodPhoto(meal.slot.photo, Modifier.size(52.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        meal.slot.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(meal.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Caption("${DisplayFormat.calories(meal.nutrition.calories)} · ${DisplayFormat.grams(meal.nutrition.protein)} protein")
                }
                Spacer(Modifier.width(8.dp))
                Text(prices.formatDisplayAmount(prices.shownCost(meal)), style = MaterialTheme.typography.labelLarge)
            }
        }
        Caption("Plus the rest of your week, and a shopping list to match.")
    }
}

@Composable
private fun Footer(
    state: StoreState,
    offer: ProOffer?,
    store: Store,
    onPurchase: (ProPlan) -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    onManage: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.message?.let { MessageLine(it, onDismissMessage) }
            if (state.isOnHold) {
                Button(onClick = onManage, modifier = Modifier.contentWidth().heightIn(min = 56.dp).testTag("paywall-fix-payment"), shape = CircleShape) {
                    Text(Paywall.onHoldAction(store), style = MaterialTheme.typography.titleMedium)
                }
                return@Column
            }
            val canBuy = offer != null && state.availability == StoreState.Availability.Ready && !state.isPurchasing
            if (offer != null || state.availability == StoreState.Availability.Connecting) {
                Button(
                    onClick = { offer?.let { onPurchase(it.plan) } },
                    enabled = canBuy,
                    modifier = Modifier.contentWidth().heightIn(min = 56.dp).testTag("paywall-cta"),
                    shape = CircleShape,
                ) {
                    if (state.isPurchasing) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(offer?.let(Paywall::callToAction) ?: "Loading plans", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (offer != null) {
                Text(
                    if (offer.freeTrial != null) "${Paywall.NO_PAYMENT_TODAY}. ${store.cancelBeforeTrialEnds}" else store.cancelAnyTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (state.availability != StoreState.Availability.Connecting) {
                // Nothing to sell: say why where the button would be, with the
                // one thing that can help, rather than leave a gap.
                Text(
                    if (state.availability == StoreState.Availability.Unavailable) UNAVAILABLE else PlayBillingStore.NO_PLANS,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.contentWidth(),
                )
                Button(onClick = onRetry, modifier = Modifier.contentWidth().heightIn(min = 52.dp).testTag("paywall-retry"), shape = CircleShape) {
                    Text("Try again", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Plans(state: StoreState, selected: ProPlan, onSelect: (ProPlan) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val offers = ProPlan.entries.mapNotNull { state.offers[it] }
    Column(modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            offers.isNotEmpty() -> {
                Caption(Paywall.SUBSCRIPTION_REQUIRED, Modifier.testTag("paywall-required"))
                offers.forEach { offer ->
                    PlanOption(Paywall.card(offer, state.offers[ProPlan.Monthly]), offer.plan == selected, isOnlyChoice = offers.size == 1) { onSelect(offer.plan) }
                }
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

/**
 * One plan. With a single plan on sale it is simply the plan, selected, with
 * no radio to tap; with two it is a choice. The yearly price leads; its
 * monthly equivalent is small print under it.
 */
@Composable
private fun PlanOption(card: Paywall.PlanCard, isSelected: Boolean, isOnlyChoice: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .testTag("plan-${card.plan.name}"),
        shape = OptionShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) BorderStroke(2.dp, ink) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isOnlyChoice) {
                Icon(
                    if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) ink else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                    card.badge?.let {
                        Spacer(Modifier.width(8.dp))
                        Pill(it)
                    }
                }
                card.trial?.let { Pill(it) }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(card.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                card.perMonth?.let { Caption(it) }
            }
        }
    }
}

/** The trial, step by step, on a line: what happens today, when the reminder comes, when the charge does. */
@Composable
private fun Timeline(offer: ProOffer, store: Store, modifier: Modifier = Modifier) {
    val steps = Paywall.timeline(offer, store)
    val icons = if (steps.size == 3) {
        listOf(Icons.Rounded.LockOpen, Icons.Rounded.NotificationsActive, Icons.Rounded.CreditCard)
    } else {
        listOf(Icons.Rounded.LockOpen, Icons.Rounded.CreditCard)
    }
    MacroCard(modifier.contentWidth().testTag("paywall-timeline")) {
        CardTitle("How your free trial works")
        steps.forEachIndexed { index, step ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(36.dp).background(
                            if (index == 0) Brand.colors.accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icons.getOrElse(index) { Icons.Rounded.Event },
                            contentDescription = null,
                            tint = if (index == 0) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (index < steps.lastIndex) {
                        Box(Modifier.width(2.dp).height(26.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(top = 6.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleSmall)
                    Caption(step.detail)
                }
            }
        }
    }
}

/** The reminder the timeline promises, with the switch to turn it off. On by default. */
@Composable
private fun ReminderRow(remind: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.contentWidth().clickable(role = Role.Switch) { onChange(!remind) }.testTag("paywall-remind"),
        shape = OptionShape,
        color = Brand.colors.accentSoft,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Brand.colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Remind me before it ends", style = MaterialTheme.typography.titleSmall)
                Caption("One notification, ${EntitlementPolicy.REMINDER_DAYS} days before the trial ends.")
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = remind, onCheckedChange = null)
        }
    }
}

@Composable
private fun BenefitRow(benefit: Paywall.Benefit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Brand.colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(benefit.title, style = MaterialTheme.typography.titleSmall)
            Caption(benefit.detail)
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = Brand.colors.accentSoft) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Brand.colors.accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
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

/** The last days of a trial: what happens next, said plainly, with the way to cancel one tap away. */
@Composable
fun TrialReminderCard(state: StoreState, store: Store, nowMillis: Long, onManage: () -> Unit) {
    val days = EntitlementPolicy.trialDaysLeft(state.entitlement, nowMillis) ?: return
    val offer = state.offers[state.entitlement.plan ?: ProPlan.Annual]
    MacroCard(Modifier.contentWidth().testTag("trial-reminder")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Brand.colors.caution, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            CardTitle(EntitlementPolicy.reminderTitle(days))
        }
        Caption(EntitlementPolicy.reminderDetail(state.entitlement, offer, store))
        OutlinedButton(onClick = onManage, shape = CircleShape) { Text("Manage in ${store.displayName}") }
    }
}
