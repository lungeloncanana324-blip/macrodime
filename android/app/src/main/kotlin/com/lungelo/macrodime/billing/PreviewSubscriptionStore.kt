/*
 * PreviewSubscriptionStore.kt
 * MacroDime
 *
 * A stand-in for Google Play, for the unit tests and for debug screenshots,
 * where there is no Play Store to ask. Its prices are the ones Play Console is
 * set up with (docs/play-store-listing.md), and a purchase completes at once.
 * Never used by a release build: only tests and debug-only intents create it.
 */
package com.lungelo.macrodime.billing

import android.app.Activity
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.ProOffer
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.domain.Store
import com.lungelo.macrodime.domain.StorePeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PreviewSubscriptionStore(
    entitlement: Entitlement = Entitlement.FREE,
    availability: StoreState.Availability = StoreState.Availability.Ready,
    offers: Map<ProPlan, ProOffer> = DEFAULT_OFFERS,
    onHold: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
) : SubscriptionStore {

    override val store = Store.GooglePlay
    override val manageSubscriptionUrl = "https://play.google.com/store/account/subscriptions"

    private val _state = MutableStateFlow(
        StoreState(
            availability = availability,
            offers = if (availability == StoreState.Availability.Ready) offers else emptyMap(),
            entitlement = entitlement,
            isOnHold = onHold,
        ),
    )
    override val state: StateFlow<StoreState> = _state.asStateFlow()

    /** What happened, for tests to check. */
    val purchased = mutableListOf<ProPlan>()
    var forgotten = 0
        private set

    override fun refresh() = Unit

    override fun purchase(activity: Activity, plan: ProPlan) {
        val offer = _state.value.offers[plan] ?: return
        val now = clock()
        purchased += plan
        _state.update {
            it.copy(entitlement = Entitlement(isPro = true, plan = plan, verifiedAtMillis = now, trialEndsAtMillis = offer.freeTrial?.endAfter(now)))
        }
    }

    override fun restore() {
        if (!_state.value.isPro && !_state.value.isOnHold) _state.update { it.copy(message = PlayBillingStore.NOTHING_TO_RESTORE) }
    }

    override fun clearMessage() = _state.update { it.copy(message = null) }

    override fun forget() {
        forgotten += 1
        _state.update { it.copy(entitlement = Entitlement.FREE) }
    }

    companion object {
        /** What Play Console sells: the yearly plan with its 14-day trial, and nothing else. */
        val DEFAULT_OFFERS = mapOf(
            ProPlan.Annual to ProOffer(ProPlan.Annual, 29.99, "USD", StorePeriod.parse("P2W")),
        )

        /** For the case where a monthly base plan is switched on as well: the app shows it as a second choice. */
        val WITH_MONTHLY = DEFAULT_OFFERS + (ProPlan.Monthly to ProOffer(ProPlan.Monthly, 5.99, "USD"))

        /** A subscriber, for screenshots of the full app. */
        fun subscriber() = PreviewSubscriptionStore(Entitlement(isPro = true, plan = ProPlan.Annual, verifiedAtMillis = System.currentTimeMillis()))
    }
}
