package com.jiny.catchtherule.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Google Play 인앱결제 — 현재는 "광고 제거" 비소모성 1종.
 * 구매 여부를 SharedPreferences 에 미러링해 오프라인에서도 즉시 게이팅 가능.
 * iOS 의 StoreManager 와 동등.
 */
class BillingManager(context: Context) {

    private val appContext = context.applicationContext
    private fun prefs() = appContext.getSharedPreferences("ctr_billing", Context.MODE_PRIVATE)

    var removeAdsPurchased by mutableStateOf(prefs().getBoolean(K_REMOVE_ADS, false))
        private set
    var removeAdsPrice by mutableStateOf("")
        private set

    private var productDetails: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private val client = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** 앱 시작 시 연결 + 제품/기존 구매 조회. */
    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = list.firstOrNull()
                removeAdsPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
            }
        }
    }

    /** 기존 구매 조회(앱 시작·구매 복원 공용). onResult = 광고제거 소유 여부. */
    fun queryPurchases(onResult: ((Boolean) -> Unit)? = null) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any {
                    it.products.contains(REMOVE_ADS_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                purchases.forEach {
                    if (it.purchaseState == Purchase.PurchaseState.PURCHASED) acknowledge(it)
                }
                setPurchased(owned)
                onResult?.invoke(owned)
            } else {
                onResult?.invoke(removeAdsPurchased)
            }
        }
    }

    /** 구매 플로우 시작. */
    fun purchase(activity: Activity) {
        val pd = productDetails ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build()
                )
            ).build()
        client.launchBillingFlow(activity, params)
    }

    private fun handlePurchase(p: Purchase) {
        if (p.products.contains(REMOVE_ADS_ID) &&
            p.purchaseState == Purchase.PurchaseState.PURCHASED
        ) {
            setPurchased(true)
            acknowledge(p)
        }
    }

    private fun acknowledge(p: Purchase) {
        if (!p.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            ) {}
        }
    }

    private fun setPurchased(value: Boolean) {
        removeAdsPurchased = value
        prefs().edit().putBoolean(K_REMOVE_ADS, value).apply()
    }

    companion object {
        const val REMOVE_ADS_ID = "remove_ads"
        private const val K_REMOVE_ADS = "remove_ads"
    }
}

val LocalBilling = staticCompositionLocalOf<BillingManager> {
    error("BillingManager not provided")
}
