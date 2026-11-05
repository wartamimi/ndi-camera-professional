package id.lcscam.app.billing

import id.lcscam.app.i18n.tr
import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import id.lcscam.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A purchasable offer as returned by Google Play (prices are Play's localized prices). */
data class PlanOffer(
    val tier: Tier,
    val productId: String,
    val basePlanId: String?,
    val periodLabel: String,
    val formattedPrice: String,
    internal val details: ProductDetails,
    internal val offerToken: String?,
)

sealed interface BillingStatus {
    data object Connecting : BillingStatus
    data object Ready : BillingStatus
    data class Unavailable(val reason: String) : BillingStatus
}

/**
 * Google Play Billing (Play Billing Library 8). Subscriptions: ndicam_basic / ndicam_pro /
 * ndicam_ultra; one-time product: ndicam_max_lifetime. The current tier is the highest owned,
 * purchased (not pending) product. The last known tier is cached so the app starts in the right
 * tier offline; it is re-validated with Google Play on every start and on resume.
 */
class BillingRepository(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("billing", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow<BillingStatus>(BillingStatus.Connecting)
    val status: StateFlow<BillingStatus> = _status.asStateFlow()
    private val _offers = MutableStateFlow<List<PlanOffer>>(emptyList())
    val offers: StateFlow<List<PlanOffer>> = _offers.asStateFlow()
    private val _purchasedTier = MutableStateFlow(cachedTier())
    private val _tier = MutableStateFlow(effectiveTier())
    /** The tier that gates features. */
    val tier: StateFlow<Tier> = _tier.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val subsOwned = mutableSetOf<String>()
    private val inappOwned = mutableSetOf<String>()
    // Publish only after both product types answered, so a half-finished query never downgrades the tier.
    @Volatile private var subsQueried = false
    @Volatile private var inappQueried = false

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init { connect() }

    /** Debug builds only: force a tier to test gating without Play (shown as such in the UI). */
    var debugOverride: Tier?
        get() = if (BuildConfig.DEBUG) prefs.getString(KEY_DEBUG, null)?.let { runCatching { Tier.valueOf(it) }.getOrNull() } else null
        set(value) {
            if (!BuildConfig.DEBUG) return
            prefs.edit().apply { if (value == null) remove(KEY_DEBUG) else putString(KEY_DEBUG, value.name) }.apply()
            _tier.value = effectiveTier()
        }

    private fun effectiveTier(): Tier = debugOverride ?: _purchasedTier.value

    private fun cachedTier(): Tier = prefs.getString(KEY_TIER, null)?.let { runCatching { Tier.valueOf(it) }.getOrNull() } ?: Tier.FREE

    fun connect() {
        if (client.isReady) { refresh(); return }
        _status.value = BillingStatus.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _status.value = BillingStatus.Ready
                    refresh()
                } else {
                    _status.value = BillingStatus.Unavailable(describe(result))
                }
            }
            override fun onBillingServiceDisconnected() {
                _status.value = BillingStatus.Unavailable("Google Play service disconnected")
            }
        })
    }

    /** Re-queries product details and owned purchases ("Restore purchases"). */
    fun refresh() {
        if (!client.isReady) { connect(); return }
        queryProducts()
        queryOwned()
    }

    private fun queryProducts() {
        val subs = PlanCatalog.products.filter { it.subscription }
        val inapp = PlanCatalog.products.filter { !it.subscription }
        val collected = java.util.Collections.synchronizedList(mutableListOf<PlanOffer>())
        var pending = 2
        fun done() {
            pending--
            if (pending == 0) _offers.value = collected.sortedWith(compareBy({ it.tier.ordinal }, { it.periodLabel }))
        }
        fun query(products: List<PlanProduct>, type: String) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(products.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(type).build()
            }).build()
            client.queryProductDetailsAsync(params) { result, detailsResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    detailsResult.productDetailsList.forEach { pd -> collected += toOffers(pd) }
                } else {
                    Log.w(TAG, "queryProductDetails($type): ${describe(result)}")
                }
                synchronized(this) { done() }
            }
        }
        query(subs, BillingClient.ProductType.SUBS)
        query(inapp, BillingClient.ProductType.INAPP)
    }

    private fun toOffers(pd: ProductDetails): List<PlanOffer> {
        val tier = PlanCatalog.tierForProduct(pd.productId) ?: return emptyList()
        val subs = pd.subscriptionOfferDetails
        if (subs != null) {
            // One entry per base plan: prefer the base-plan offer (no offerId) for clarity.
            return subs.groupBy { it.basePlanId }.mapNotNull { (basePlan, offers) ->
                val o = offers.firstOrNull { it.offerId == null } ?: offers.first()
                val phase = o.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                PlanOffer(tier, pd.productId, basePlan, periodLabel(phase.billingPeriod), phase.formattedPrice, pd, o.offerToken)
            }
        }
        @Suppress("DEPRECATION")
        val one = pd.oneTimePurchaseOfferDetails ?: return emptyList()
        return listOf(PlanOffer(tier, pd.productId, null, "lifetime", one.formattedPrice, pd, null))
    }

    private fun periodLabel(iso: String): String = when (iso) {
        "P1M" -> "monthly"; "P1Y" -> "yearly"; "P1W" -> "weekly"; "P3M" -> "3 months"; "P6M" -> "6 months"
        else -> iso
    }

    private fun queryOwned() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                synchronized(subsOwned) { subsOwned.clear(); subsOwned += ownedIds(list) }
                subsQueried = true
                publishTier()
            }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                synchronized(inappOwned) { inappOwned.clear(); inappOwned += ownedIds(list) }
                inappQueried = true
                publishTier()
            }
        }
    }

    private fun ownedIds(list: List<Purchase>): Set<String> {
        val ids = mutableSetOf<String>()
        for (p in list) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            ids += p.products
            if (!p.isAcknowledged) acknowledge(p)
        }
        return ids
    }

    private fun acknowledge(p: Purchase) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "acknowledge failed: ${describe(r)}")
        }
    }

    private fun publishTier() {
        if (!subsQueried || !inappQueried) return
        val owned = synchronized(subsOwned) { subsOwned.toSet() } + synchronized(inappOwned) { inappOwned.toSet() }
        val t = PlanCatalog.tierFromOwnedProducts(owned)
        _purchasedTier.value = t
        prefs.edit().putString(KEY_TIER, t.name).apply()
        _tier.value = effectiveTier()
    }

    fun launchPurchase(activity: Activity, offer: PlanOffer): Boolean {
        if (!client.isReady) { _message.value = tr("Google Play Billing is not available", "Google Play Billing tidak tersedia"); return false }
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
        offer.offerToken?.let { p.setOfferToken(it) }
        val r = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p.build())).build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) _message.value = describe(r)
        return r.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.filter { it.purchaseState == Purchase.PurchaseState.PENDING }?.takeIf { it.isNotEmpty() }?.let {
                    _message.value = tr("Payment pending. Your plan activates when Google Play confirms it.", "Pembayaran tertunda. Paket aktif setelah Google Play mengonfirmasi.")
                }
                queryOwned()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _message.value = null
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned()
            else -> _message.value = describe(result)
        }
    }

    fun clearMessage() { _message.value = null }

    private fun describe(r: BillingResult): String = "Google Play: ${r.debugMessage.ifBlank { "code ${r.responseCode}" }}"

    private companion object {
        const val TAG = "BillingRepository"
        const val KEY_TIER = "tier"
        const val KEY_DEBUG = "debug_override"
    }
}
