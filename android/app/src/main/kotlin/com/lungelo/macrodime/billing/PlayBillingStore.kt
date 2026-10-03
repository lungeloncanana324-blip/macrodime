/*
 * PlayBillingStore.kt
 * MacroDime
 *
 * MacroDime Pro through Google Play Billing. Everything goes through the Play
 * Store app on the phone: there is no server of ours, the app still holds no
 * internet permission, and a subscription belongs to the person's Google
 * account, so it follows them to a new phone.
 *
 * The rules (which offer to sell, what a purchase means, how long Pro lasts
 * offline) are pure and tested in :core (Subscription.kt). This class only
 * moves Play's answers into them.
 *
 * Known limit, by design: purchases are not verified by a server, so a
 * determined person on a rooted phone could fake one. Verifying would need a
 * backend, which this app deliberately does not have.
 */
package com.lungelo.macrodime.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.EntitlementPolicy
import com.lungelo.macrodime.domain.PlayOffers
import com.lungelo.macrodime.domain.ProOffer
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.domain.PurchaseRecord
import com.lungelo.macrodime.domain.Store
import com.lungelo.macrodime.domain.StorePurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PlayBillingStore(
    context: Context,
    private val preferences: ProPreferences,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : SubscriptionStore {

    override val store = Store.GooglePlay

    override val manageSubscriptionUrl =
        "https://play.google.com/store/account/subscriptions?sku=${PlayOffers.PRODUCT_ID}&package=${context.packageName}"

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(
        StoreState(entitlement = EntitlementPolicy.resolve(null, preferences.entitlement, clock())),
    )
    override val state: StateFlow<StoreState> = _state.asStateFlow()

    /** One refresh at a time: they share the connection and write the same state. */
    private val lock = Mutex()
    private var productDetails: ProductDetails? = null
    private var selected: Map<ProPlan, PlayOffers.Selected> = emptyMap()

    /** The offer whose purchase sheet is open, recorded against its token when Play confirms it. */
    private var launched: ProOffer? = null

    /** Built on first use, so a process that never shows Pro never binds to the Play Store. */
    private val client: BillingClient by lazy {
        BillingClient.newBuilder(appContext)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    override fun refresh() {
        scope.launch { lock.withLock { load() } }
    }

    override fun restore() {
        scope.launch {
            lock.withLock {
                load()
                val now = _state.value
                if (now.availability == StoreState.Availability.Ready && !now.isPro) {
                    _state.update { it.copy(message = NOTHING_TO_RESTORE) }
                }
            }
        }
    }

    override fun clearMessage() = _state.update { it.copy(message = null) }

    override fun forget() {
        preferences.clear()
        _state.update { it.copy(entitlement = Entitlement.FREE) }
        refresh()
    }

    override fun purchase(activity: Activity, plan: ProPlan) {
        val details = productDetails
        val choice = selected[plan]
        if (details == null || choice == null || !client.isReady) {
            _state.update { it.copy(message = NO_PLANS) }
            refresh()
            return
        }
        launched = choice.offer
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(choice.offerToken)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        when (result.responseCode) {
            BillingResponseCode.OK -> _state.update { it.copy(isPurchasing = true, message = null) }
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> _state.update { it.copy(message = PURCHASE_FAILED) }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> scope.launch {
                lock.withLock {
                    record(purchases.orEmpty())
                    load()
                }
            }
            BillingResponseCode.USER_CANCELED -> _state.update { it.copy(isPurchasing = false) }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(isPurchasing = false) }
                refresh()
            }
            else -> _state.update { it.copy(isPurchasing = false, message = PURCHASE_FAILED) }
        }
    }

    /** Notes the plan and the trial's end against the purchase this phone just made. */
    private fun record(purchases: List<Purchase>) {
        val offer = launched ?: return
        val bought = purchases.firstOrNull { PlayOffers.PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            ?: return
        preferences.record = PurchaseRecord(bought.purchaseToken, offer.plan, offer.freeTrial?.endAfter(bought.purchaseTime))
        launched = null
    }

    private suspend fun load() {
        if (!connect()) {
            _state.update {
                it.copy(
                    availability = StoreState.Availability.Unavailable,
                    entitlement = EntitlementPolicy.resolve(null, preferences.entitlement, clock()),
                    isPurchasing = false,
                )
            }
            return
        }

        val products = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PlayOffers.PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build(),
                    ),
                )
                .build(),
        )
        if (products.billingResult.responseCode == BillingResponseCode.OK) {
            val details = products.productDetailsList.orEmpty().firstOrNull { it.productId == PlayOffers.PRODUCT_ID }
            productDetails = details
            selected = PlayOffers.select(details?.subscriptionOfferDetails.orEmpty().map { it.toInput() })
        }

        val owned = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        val live = if (owned.billingResult.responseCode == BillingResponseCode.OK) {
            val purchases = owned.purchasesList.orEmpty()
            acknowledge(purchases)
            EntitlementPolicy.fromPurchases(purchases.map { it.toStorePurchase() }, PlayOffers.PRODUCT_ID, preferences.record, clock())
        } else {
            null
        }
        if (live != null) preferences.entitlement = live
        val pending = live?.isPro != true &&
            owned.purchasesList.orEmpty().any { PlayOffers.PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }

        _state.update {
            it.copy(
                availability = StoreState.Availability.Ready,
                offers = selected.mapValues { (_, choice) -> choice.offer },
                entitlement = EntitlementPolicy.resolve(live, preferences.entitlement, clock()),
                isPurchasing = false,
                isPending = pending,
                message = when {
                    pending -> PENDING
                    // A failure to load the plans, now loaded, is no longer news.
                    it.message == NO_PLANS && selected.isNotEmpty() -> null
                    else -> it.message
                },
            )
        }
    }

    /** Play refunds a purchase not acknowledged within three days. */
    private suspend fun acknowledge(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (EntitlementPolicy.needsAcknowledging(purchase.toStorePurchase())) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
            }
        }
    }

    /** True once connected. False when Play cannot be reached: not installed, signed out, or no answer in 10 s. */
    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        val result = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(result)
                    }

                    // Automatic reconnection handles a later drop; the next call reconnects.
                    override fun onBillingServiceDisconnected() = Unit
                })
            }
        }
        return result?.responseCode == BillingResponseCode.OK
    }

    private fun ProductDetails.SubscriptionOfferDetails.toInput() = PlayOffers.Input(
        basePlanId = basePlanId,
        offerId = offerId,
        offerToken = offerToken,
        phases = pricingPhases.pricingPhaseList.map {
            PlayOffers.Phase(it.priceAmountMicros, it.priceCurrencyCode, it.billingPeriod, it.billingCycleCount, it.recurrenceMode)
        },
    )

    private fun Purchase.toStorePurchase() = StorePurchase(
        productIds = products,
        token = purchaseToken,
        purchaseTimeMillis = purchaseTime,
        isPurchased = purchaseState == Purchase.PurchaseState.PURCHASED,
        isPending = purchaseState == Purchase.PurchaseState.PENDING,
        isAcknowledged = isAcknowledged,
        isAutoRenewing = isAutoRenewing,
        isSuspended = isSuspended,
    )

    companion object {
        const val NO_PLANS = "Plans couldn't be loaded from Google Play. Check your connection, then try again."
        const val PURCHASE_FAILED = "The purchase didn't go through. Try again in a moment, or check Google Play for the result."
        const val PENDING = "Your payment is pending. Pro unlocks as soon as Google Play confirms it."
        const val NOTHING_TO_RESTORE = "No MacroDime Pro subscription was found for the Google account on this phone."
    }
}
