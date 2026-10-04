/*
 * Subscription.kt
 * MacroDime
 *
 * MacroDime Pro, in store-neutral terms: what the stores sell, what the paywall
 * says about it, and what the app remembers about who has it. Billing itself
 * (Google Play Billing, StoreKit) lives in the apps; everything here is pure so
 * the copy and the rules are tested on the JVM and held in step with the iOS
 * twin, MacroDime/Domain/Subscription.swift.
 *
 * Three rules shape it:
 *  1. Every figure on the paywall comes from the store's own price for this
 *     user. Nothing is hard-coded, so a price change in Play Console or App
 *     Store Connect cannot leave the paywall quoting an old one.
 *  2. A free trial is offered only when the store says this user may take one.
 *     Play leaves out offers a user is not eligible for, and StoreKit answers
 *     isEligibleForIntroOffer; the copy follows, so nobody is promised a trial
 *     they will be charged for.
 *  3. The terms are stated before the button, in full: that the app needs a
 *     subscription, the trial length, the price after it, that it renews, and
 *     where to cancel. Both stores require it, and a charge nobody expected
 *     becomes a refund and a one-star review.
 */
package com.lungelo.macrodime.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

/** The two ways to pay for MacroDime Pro. Only the yearly plan carries a free trial. */
enum class ProPlan(val title: String, val per: String, val every: String) {
    Annual("Yearly", "a year", "every year"),
    Monthly("Monthly", "a month", "every month"),
}

/** A period as both stores state it, in ISO 8601: P14D, P2W, P1M, P1Y. */
data class StorePeriod(val count: Int, val unit: Span) {

    enum class Span(val singular: String, val chrono: ChronoUnit) {
        Day("day", ChronoUnit.DAYS),
        Week("week", ChronoUnit.WEEKS),
        Month("month", ChronoUnit.MONTHS),
        Year("year", ChronoUnit.YEARS),
    }

    /**
     * The form that goes before "free trial": "14-day", "1-month". Weeks are
     * said in days, because a 14-day trial is how people compare them.
     */
    val adjective: String
        get() = if (unit == Span.Week) "${count * 7}-day" else "$count-${unit.singular}"

    /** The form that stands alone: "14 days", "1 month". Weeks again in days. */
    val phrase: String
        get() {
            val (n, word) = if (unit == Span.Week) count * 7 to "day" else count to unit.singular
            return "$n ${if (n == 1) word else word + "s"}"
        }

    /** When a period starting at [startMillis] ends, on the calendar (UTC). */
    fun endAfter(startMillis: Long): Long =
        Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).plus(count.toLong(), unit.chrono).toInstant().toEpochMilli()

    companion object {
        private val ISO = Regex("""^P(\d{1,3})([DWMY])$""")

        /** Null for anything the stores do not use for these products, rather than a guess. */
        fun parse(iso: String): StorePeriod? {
            val match = ISO.matchEntire(iso.trim().uppercase()) ?: return null
            val count = match.groupValues[1].toInt()
            if (count <= 0) return null
            val unit = when (match.groupValues[2]) {
                "D" -> Span.Day
                "W" -> Span.Week
                "M" -> Span.Month
                else -> Span.Year
            }
            return StorePeriod(count, unit)
        }
    }
}

/**
 * One way to buy Pro, as the store priced it for this user. [freeTrial] is set
 * only when the store says this user may take a trial now.
 */
data class ProOffer(
    val plan: ProPlan,
    /** The recurring price, in [currencyCode]. */
    val price: Double,
    val currencyCode: String,
    val freeTrial: StorePeriod? = null,
)

/** Where a subscription is managed and cancelled. The rules differ, so the copy does. */
enum class Store(val displayName: String, val cancelBeforeTrialEnds: String, val cancelAnyTime: String) {
    GooglePlay(
        "Google Play",
        "Cancel in Google Play before the trial ends and you won't be charged.",
        "Cancel any time in Google Play.",
    ),
    AppStore(
        "the App Store",
        "Cancel at least 24 hours before the trial ends and you won't be charged.",
        "Cancel any time in Settings, under your Apple Account, at least 24 hours before it renews.",
    ),
}

/**
 * Why the paywall is showing, which decides its headline. Since 2026-10-04
 * there is no free tier: the paywall is the way into the app, straight after
 * onboarding, and again for anyone whose trial or subscription has ended.
 */
enum class PaywallReason { AfterOnboarding, Returning }

/** Everything the paywall says, from the store's offers and the user's own numbers. Copy lives here, tested, not in the screens. */
object Paywall {

    const val PRO_NAME = "MacroDime Pro"

    data class Benefit(val title: String, val detail: String)

    val benefits = listOf(
        Benefit("A week of meals, planned", "Built for your calories, protein and diet the moment you finish setup."),
        Benefit("Priced to your budget", "Every meal costed against your daily allowance before you shop."),
        Benefit("Cheaper swaps", "Same macros within 10%, cheaper ingredients, one tap to apply."),
        Benefit("A shopping list that builds itself", "From the week you planned, in store-walk order."),
    )

    /** Beside the button whenever the store offers a trial: the line that answers the first worry. */
    const val NO_PAYMENT_TODAY = "No payment today"

    /**
     * Play's policy asks every offer to say whether a subscription is needed to
     * use the app at all. Here it is, and the paywall has no close button, so
     * it is said above the plans and again in the terms.
     */
    const val SUBSCRIPTION_REQUIRED = "A subscription is required to use MacroDime."

    /**
     * For a subscription the store holds but has suspended: a payment that
     * failed (Play's account hold) or a pause. Nothing new is sold to them;
     * the store is where it is fixed.
     */
    const val ON_HOLD_HEADLINE = "Your subscription is on hold"

    fun onHoldDetail(store: Store): String =
        "${store.displayName.replaceFirstChar { it.uppercase() }} couldn't take your last payment, or the subscription is paused. " +
            "Sort it out in ${store.displayName} and your plan opens again, just as you left it."

    fun onHoldAction(store: Store): String = "Fix it in ${store.displayName}"

    /** One plan, as its card on the paywall reads. */
    data class PlanCard(
        val plan: ProPlan,
        val title: String,
        /** "$29.99 a year". */
        val price: String,
        /** "$2.50 a month", on the yearly card only. */
        val perMonth: String?,
        /** "Save 58%", on the yearly card, only when the yearly plan saves. */
        val badge: String?,
        /** "14 days free", only when this user may take the trial. */
        val trial: String?,
    )

    /** One step of the trial timeline: when, and what happens. */
    data class TimelineStep(val title: String, val detail: String)

    fun money(amount: Double, currencyCode: String): String =
        DisplayFormat.currency(CurrencyDigits.round(amount, CurrencyDigits.minorUnits(currencyCode)), currencyCode)

    /** "$29.99 a year". */
    fun priceLine(offer: ProOffer): String = "${money(offer.price, offer.currencyCode)} ${offer.plan.per}"

    /**
     * Whole percent the yearly plan saves against twelve monthly payments,
     * rounded down so the badge never overstates it. Null when the two are in
     * different currencies, or the yearly plan does not save at least 1%.
     */
    fun annualSavingsPercent(annual: ProOffer, monthly: ProOffer): Int? {
        if (annual.plan != ProPlan.Annual || monthly.plan != ProPlan.Monthly) return null
        if (annual.currencyCode != monthly.currencyCode || monthly.price <= 0 || annual.price <= 0) return null
        val percent = floor((1 - annual.price / (monthly.price * 12)) * 100 + 1e-9).toInt()
        return percent.takeIf { it >= 1 }
    }

    fun card(offer: ProOffer, monthly: ProOffer?): PlanCard = PlanCard(
        plan = offer.plan,
        title = offer.plan.title,
        price = priceLine(offer),
        perMonth = if (offer.plan == ProPlan.Annual) "${money(offer.price / 12, offer.currencyCode)} a month" else null,
        badge = if (offer.plan == ProPlan.Annual && monthly != null) annualSavingsPercent(offer, monthly)?.let { "Save $it%" } else null,
        trial = offer.freeTrial?.let { "${it.phrase} free" },
    )

    /** The button. "Start my 14-day free trial" only when the store offers this user one. */
    fun callToAction(offer: ProOffer): String =
        offer.freeTrial?.let { "Start my ${it.adjective} free trial" } ?: "Subscribe for ${priceLine(offer)}"

    /** The terms, stated in full under the button, starting with the fact that the app needs them. */
    fun disclosure(offer: ProOffer, store: Store): String {
        val price = priceLine(offer)
        val renews = "$PRO_NAME renews ${offer.plan.every} until you cancel."
        val terms = offer.freeTrial?.let { trial ->
            "${trial.phrase.replaceFirstChar { it.uppercase() }} free, then $price. $renews ${store.cancelBeforeTrialEnds}"
        } ?: "$price. $renews ${store.cancelAnyTime}"
        return "$SUBSCRIPTION_REQUIRED $terms"
    }

    /**
     * What happens when, for an offer with a trial: the plan unlocks today, a
     * reminder comes [EntitlementPolicy.REMINDER_DAYS] days before the end
     * (the app keeps that promise with a notification and a card on Today),
     * and the charge comes when the trial ends unless it was cancelled. Empty
     * when there is no trial to explain.
     */
    fun timeline(offer: ProOffer, store: Store): List<TimelineStep> {
        val trial = offer.freeTrial ?: return emptyList()
        val days = if (trial.unit == StorePeriod.Span.Day || trial.unit == StorePeriod.Span.Week) trial.phrase.substringBefore(' ').toInt() else null
        val reminderDay = days?.minus(EntitlementPolicy.REMINDER_DAYS)
        return buildList {
            add(TimelineStep("Today", "Your full plan unlocks. $NO_PAYMENT_TODAY."))
            if (days == null) {
                add(TimelineStep("Before it ends", "A reminder that your trial is ending."))
            } else if (reminderDay != null && reminderDay >= 1) {
                add(TimelineStep("Day $reminderDay", "A reminder that your trial ends in ${EntitlementPolicy.REMINDER_DAYS} days."))
            }
            add(
                TimelineStep(
                    if (days == null) "After ${trial.phrase}" else "Day $days",
                    "${priceLine(offer)} starts, renewing ${offer.plan.every}. ${store.cancelBeforeTrialEnds}",
                ),
            )
        }
    }

    /**
     * The headline, in the user's own numbers where there are some. Someone
     * whose swaps have already saved money (which means they had Pro, in a
     * trial) is shown that figure first: their own result, not a promise.
     */
    fun headline(reason: PaywallReason, savedSoFar: String?, isOnHold: Boolean = false): String = when {
        isOnHold -> ON_HOLD_HEADLINE
        savedSoFar != null -> "Your swaps have saved you $savedSoFar"
        reason == PaywallReason.AfterOnboarding -> "Your week is ready"
        else -> "Welcome back"
    }

    /** Below the headline: what the plan does with the targets they just set. */
    fun subheadline(calories: String, protein: String, dailyAllowance: String): String =
        "$calories and $protein of protein a day, planned within $dailyAllowance, with a shopping list to match."
}

/**
 * Whether this person has Pro, as a store last confirmed it, and what the app
 * remembers about it. The store is the source of truth. This record is what
 * keeps a subscriber's plan open on a plane, and what dates the trial reminder.
 */
data class Entitlement(
    val isPro: Boolean,
    val plan: ProPlan? = null,
    /** When a store last confirmed [isPro]; 0 if never. */
    val verifiedAtMillis: Long = 0,
    /** When a free trial started on this phone ends; null outside a recorded trial. */
    val trialEndsAtMillis: Long? = null,
    /** False once the person has cancelled: Pro then runs to the end of what they paid for, or of the trial, and stops. */
    val willRenew: Boolean = true,
) {
    companion object {
        val FREE = Entitlement(isPro = false)
    }
}

object EntitlementPolicy {

    /** How long a confirmed subscriber keeps Pro without the store being reachable. */
    const val OFFLINE_GRACE_DAYS = 7

    /** How many days before a trial ends the in-app reminder appears. */
    const val REMINDER_DAYS = 2

    private const val DAY_MILLIS = 86_400_000L

    /** A clock may drift; a confirmation dated a little in the future is still believed, a lot is not. */
    private const val CLOCK_TOLERANCE_MILLIS = 10 * 60_000L

    /**
     * What to act on. [live] is the store's answer now, or null when it could
     * not be asked (no connection to Google Play, say). A subscriber the store
     * confirmed within [OFFLINE_GRACE_DAYS] keeps Pro while offline; an older
     * confirmation, or one dated in the future (a clock set back), does not.
     */
    fun resolve(live: Entitlement?, cached: Entitlement, nowMillis: Long): Entitlement {
        if (live != null) return live
        if (!cached.isPro) return Entitlement.FREE
        val age = nowMillis - cached.verifiedAtMillis
        val trusted = age >= -CLOCK_TOLERANCE_MILLIS && age <= OFFLINE_GRACE_DAYS * DAY_MILLIS
        return if (trusted) cached else Entitlement.FREE
    }

    /** Whole days left in a recorded trial, counting part of a day as a day; null outside one. */
    fun trialDaysLeft(entitlement: Entitlement, nowMillis: Long): Int? {
        val endsAt = entitlement.trialEndsAtMillis ?: return null
        if (!entitlement.isPro || endsAt <= nowMillis) return null
        return ceil((endsAt - nowMillis).toDouble() / DAY_MILLIS).toInt()
    }

    /**
     * When the trial-end notification should fire: [REMINDER_DAYS] days before
     * the trial ends, if that is still ahead. Null for anyone not in a trial
     * that will turn into a charge: a cancelled trial ends on its own and owes
     * nobody a warning.
     */
    fun reminderAt(entitlement: Entitlement, nowMillis: Long): Long? {
        val endsAt = entitlement.trialEndsAtMillis ?: return null
        if (!entitlement.isPro || !entitlement.willRenew) return null
        return (endsAt - REMINDER_DAYS * DAY_MILLIS).takeIf { it > nowMillis }
    }

    /** True in the last [REMINDER_DAYS] days of a trial: the moment to say plainly what happens next. */
    fun showsTrialReminder(entitlement: Entitlement, nowMillis: Long): Boolean =
        trialDaysLeft(entitlement, nowMillis)?.let { it <= REMINDER_DAYS } ?: false

    /** "Your free trial ends tomorrow", "...in 2 days", "...today". */
    fun reminderTitle(daysLeft: Int): String = when {
        daysLeft <= 0 -> "Your free trial ends today"
        daysLeft == 1 -> "Your free trial ends tomorrow"
        else -> "Your free trial ends in $daysLeft days"
    }

    /**
     * What happens next, honestly. Someone who has already cancelled is told
     * they will not be charged, rather than warned that they will be.
     */
    fun reminderDetail(entitlement: Entitlement, offer: ProOffer?, store: Store): String = when {
        !entitlement.willRenew -> "You cancelled, so you won't be charged. Pro ends when the trial does."
        offer != null -> "Then ${Paywall.priceLine(offer)}, renewing ${offer.plan.every}. ${store.cancelBeforeTrialEnds}"
        else -> "Then it renews at the price you agreed to. ${store.cancelBeforeTrialEnds}"
    }

    /**
     * The notification's words. An alarm posts it with no fresh answer from the
     * store, so it cannot know whether the trial was cancelled in the store
     * since the app was last opened: it says what is true either way.
     */
    fun reminderNotice(store: Store): String =
        "If you haven't cancelled, ${Paywall.PRO_NAME} then renews at the price you agreed to. ${store.cancelBeforeTrialEnds}"

    /**
     * The entitlement a store's list of purchases gives, confirmed at [nowMillis].
     * A purchase counts while it is paid for and not suspended (Play's account
     * hold); a payment still pending gives nothing yet. The plan and the trial's
     * end are known only from [record], written when this phone started the
     * purchase, and only for that same purchase.
     */
    fun fromPurchases(purchases: List<StorePurchase>, productId: String, record: PurchaseRecord?, nowMillis: Long): Entitlement {
        val active = purchases
            .filter { productId in it.productIds && it.isPurchased && !it.isSuspended }
            .maxByOrNull { it.purchaseTimeMillis }
            ?: return Entitlement.FREE.copy(verifiedAtMillis = nowMillis)
        val known = record?.takeIf { it.token == active.token }
        return Entitlement(
            isPro = true,
            plan = known?.plan,
            verifiedAtMillis = nowMillis,
            trialEndsAtMillis = known?.trialEndsAtMillis,
            willRenew = active.isAutoRenewing,
        )
    }

    /**
     * True when the store holds a suspended subscription (Play's account hold
     * after a failed payment, or a pause) and nothing active. Such a person has
     * no Pro, but is sent to the store to fix it, never sold a second
     * subscription. Play reports suspended subscriptions only when asked to.
     */
    fun isOnHold(purchases: List<StorePurchase>, productId: String): Boolean =
        purchases.none { productId in it.productIds && it.isPurchased && !it.isSuspended } &&
            purchases.any { productId in it.productIds && it.isSuspended }

    /** Play refunds a purchase not acknowledged within three days, so every paid one is acknowledged. */
    fun needsAcknowledging(purchase: StorePurchase): Boolean = purchase.isPurchased && !purchase.isAcknowledged
}

/** One purchase as a store reports it, without the store's own types, so the rules above are testable. */
data class StorePurchase(
    val productIds: List<String>,
    val token: String,
    val purchaseTimeMillis: Long,
    val isPurchased: Boolean,
    val isPending: Boolean = false,
    val isAcknowledged: Boolean = true,
    val isAutoRenewing: Boolean = true,
    val isSuspended: Boolean = false,
)

/** What this phone noted when it started a purchase: which plan, and when its trial ends. */
data class PurchaseRecord(val token: String, val plan: ProPlan, val trialEndsAtMillis: Long?)

/**
 * Google Play's offers for the Pro subscription, reduced to the two the
 * paywall sells. In Play Console the product `pro` has two base plans,
 * `annual` (P1Y) and `monthly` (P1M), and the annual base plan carries one
 * offer: a free trial phase, then the base price. Play returns only the offers
 * this user is eligible for, so the trial disappears by itself for someone who
 * has had one.
 */
object PlayOffers {

    const val PRODUCT_ID = "pro"
    const val ANNUAL = "annual"
    const val MONTHLY = "monthly"

    /** Play's ProductDetails.RecurrenceMode values. */
    const val INFINITE_RECURRING = 1
    const val FINITE_RECURRING = 2

    data class Phase(
        val priceMicros: Long,
        val currencyCode: String,
        val billingPeriod: String,
        val billingCycleCount: Int,
        val recurrenceMode: Int,
    )

    /** One SubscriptionOfferDetails: a base plan, optionally an offer on it, and its pricing phases in order. */
    data class Input(val basePlanId: String, val offerId: String?, val offerToken: String, val phases: List<Phase>)

    data class Selected(val offer: ProOffer, val offerToken: String)

    /**
     * What to sell for each plan. An offer the paywall could not describe
     * honestly is skipped rather than sold: a discounted first period, two
     * free phases, or a "yearly" base plan that does not renew yearly.
     */
    fun select(inputs: List<Input>): Map<ProPlan, Selected> = buildMap {
        candidates(inputs, ANNUAL, "P1Y", ProPlan.Annual)
            .maxByOrNull { it.offer.freeTrial?.let { trial -> trial.endAfter(0) } ?: -1L }
            ?.let { put(ProPlan.Annual, it) }
        // The monthly plan is sold at its base price only: the trial belongs to the yearly plan.
        candidates(inputs, MONTHLY, "P1M", ProPlan.Monthly)
            .firstOrNull { it.offer.freeTrial == null }
            ?.let { put(ProPlan.Monthly, it) }
    }

    private fun candidates(inputs: List<Input>, basePlanId: String, period: String, plan: ProPlan): List<Selected> =
        inputs.filter { it.basePlanId == basePlanId }.mapNotNull { input ->
            val recurring = input.phases.lastOrNull()
                ?.takeIf { it.recurrenceMode == INFINITE_RECURRING && it.priceMicros > 0 && it.billingPeriod.equals(period, ignoreCase = true) }
                ?: return@mapNotNull null
            val trial = when (input.phases.size) {
                1 -> null
                2 -> {
                    val first = input.phases.first()
                    if (first.priceMicros != 0L || first.recurrenceMode != FINITE_RECURRING || first.billingCycleCount < 1) return@mapNotNull null
                    val unit = StorePeriod.parse(first.billingPeriod) ?: return@mapNotNull null
                    unit.copy(count = unit.count * first.billingCycleCount)
                }
                else -> return@mapNotNull null
            }
            Selected(ProOffer(plan, recurring.priceMicros / 1_000_000.0, recurring.currencyCode, trial), input.offerToken)
        }
}
