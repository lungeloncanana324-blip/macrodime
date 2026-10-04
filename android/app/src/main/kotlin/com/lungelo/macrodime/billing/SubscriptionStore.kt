/*
 * SubscriptionStore.kt
 * MacroDime
 *
 * The app's single view of MacroDime Pro: what can be bought, whether this
 * person has it, and whether the store can be reached. Google Play Billing
 * implements it (PlayBillingStore); PreviewSubscriptionStore stands in for it
 * in tests and in debug screenshots, where there is no Play Store to ask.
 */
package com.lungelo.macrodime.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.ProOffer
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.domain.Store
import kotlinx.coroutines.flow.StateFlow

data class StoreState(
    val availability: Availability = Availability.Connecting,
    /** What can be bought now, priced by the store for this person. Empty until loaded. */
    val offers: Map<ProPlan, ProOffer> = emptyMap(),
    val entitlement: Entitlement = Entitlement.FREE,
    /** The store's purchase sheet is open. */
    val isPurchasing: Boolean = false,
    /** A payment the store has not confirmed yet (cash, some bank transfers). */
    val isPending: Boolean = false,
    /**
     * The store holds a subscription it has suspended: a failed payment (Play's
     * account hold) or a pause. It is fixed in the store, not bought again.
     */
    val isOnHold: Boolean = false,
    /** The last thing worth telling the person, cleared by [SubscriptionStore.clearMessage]. */
    val message: String? = null,
) {
    enum class Availability { Connecting, Ready, Unavailable }

    val isPro: Boolean get() = entitlement.isPro

    /** The plan the paywall leads with and the locked screens quote: yearly, when the store has it. */
    val leadOffer: ProOffer? get() = offers[ProPlan.Annual] ?: offers[ProPlan.Monthly]
}

interface SubscriptionStore {

    val state: StateFlow<StoreState>

    /** Where Pro is managed and cancelled, which decides the paywall's wording. */
    val store: Store

    /** The store's own page for managing this subscription. */
    val manageSubscriptionUrl: String

    /** Reads the offers and the entitlement again. Called whenever the app comes to the front. */
    fun refresh()

    /** Opens the store's purchase sheet for [plan]. */
    fun purchase(activity: Activity, plan: ProPlan)

    /** Asks the store what this account owns, and says so if it owns nothing. */
    fun restore()

    /**
     * Lets the store show its own message about a payment that failed, with
     * the way to fix it. Called whenever the app comes to the front; the store
     * shows nothing when there is nothing wrong.
     */
    fun showPaymentMessages(activity: Activity) = Unit

    fun clearMessage()

    /**
     * Forgets everything remembered about Pro on this phone, for Delete All My
     * Data. A subscription itself belongs to the store account and survives;
     * the next refresh finds it again.
     */
    fun forget()
}

/** The activity behind a Compose context, for the store's purchase sheet. */
fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
