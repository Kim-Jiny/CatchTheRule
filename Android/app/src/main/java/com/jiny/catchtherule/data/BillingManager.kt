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
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Play 인앱결제.
 *  - 광고 제거(remove_ads): 비소모성
 *  - 힌트 묶음(hints_20): 소모성(+20 힌트)
 * 구매는 Play 로 처리 후 서버(/iap/verify)에 영수증을 보내 2차 검증·기록한다.
 */
class BillingManager(context: Context, private val progress: ProgressStore) {

    private val appContext = context.applicationContext
    private fun prefs() = appContext.getSharedPreferences("ctr_billing", Context.MODE_PRIVATE)

    var removeAdsPurchased by mutableStateOf(prefs().getBoolean(K_REMOVE_ADS, false))
        private set
    var removeAdsPrice by mutableStateOf("")
        private set
    var hintsPrices by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    private val productDetails = mutableMapOf<String, ProductDetails>()

    /** 결제 플로우가 떠 있는 동안 true. 연타로 결제창이 여러 번 뜨는 것을 막는다. */
    private var purchaseInProgress = false

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        purchaseInProgress = false   // 결제창이 닫혔다(성공/취소/오류) → 잠금 해제
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

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProducts() {
        val ids = listOf(REMOVE_ADS_ID) + HINT_TIERS.map { hintsId(it) }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            ).build()
        // PBL 8.0.0+: 콜백 2번째 인자가 List<ProductDetails> → QueryProductDetailsResult 로 변경됨.
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryResult.productDetailsList.forEach { productDetails[it.productId] = it }
                removeAdsPrice = productDetails[REMOVE_ADS_ID]?.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                hintsPrices = HINT_TIERS.associateWith { n ->
                    productDetails[hintsId(n)]?.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                }
            }
        }
    }

    /** 기존 구매 조회(앱 시작·복원 공용). onResult = 광고제거 소유 여부. */
    fun queryPurchases(onResult: ((Boolean) -> Unit)? = null) {
        purchaseInProgress = false   // 포그라운드 복귀(onResume) 등 → 결제창 종료로 간주, 잠금 해제(안전망)
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                var owned = false
                purchases.forEach {
                    if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (it.products.contains(REMOVE_ADS_ID)) owned = true
                        handlePurchase(it)
                    }
                }
                setPurchased(owned)
                onResult?.invoke(owned)
            } else {
                onResult?.invoke(removeAdsPurchased)
            }
        }
    }

    /** 구매 플로우 시작. productId = REMOVE_ADS_ID | HINTS_ID */
    fun purchase(activity: Activity, productId: String) {
        if (purchaseInProgress) return   // 이미 결제 플로우 진행 중 → 중복 실행 방지
        val pd = productDetails[productId] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build()
                )
            ).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchaseInProgress = true
        }
    }

    private fun handlePurchase(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        verifyOnServer(p)
        val hintGrant = p.products.firstNotNullOfOrNull { HINT_GRANTS[it] }
        when {
            p.products.contains(REMOVE_ADS_ID) -> {
                setPurchased(true)
                acknowledge(p)
            }
            hintGrant != null -> {
                // 소모성: 소비 후 힌트 지급(소비되면 재조회에서 다시 잡히지 않음 → 1회 지급)
                client.consumeAsync(
                    ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                ) { result, _ ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        runOnMain { progress.addHints(hintGrant) }   // 콜백은 백그라운드 → 메인에서 상태 갱신
                    }
                }
            }
        }
    }

    private fun acknowledge(p: Purchase) {
        if (!p.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            ) {}
        }
    }

    /** 서버 영수증 검증(기록/관리자 확인용). */
    private fun verifyOnServer(p: Purchase) {
        val deviceId = CtrDevice.id(appContext)
        val productId = p.products.firstOrNull() ?: ""
        val txn = p.orderId ?: p.purchaseToken
        Thread {
            runCatching {
                val body = JSONObject()
                    .put("platform", "android")
                    .put("deviceId", deviceId)
                    .put("productId", productId)
                    .put("transactionId", txn)
                    .put("payload", p.originalJson)
                    .put("signature", p.signature)
                    .toString()
                val conn = (URL("$BASE_URL/api/catchtherule/iap/verify").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                try {
                    OutputStreamWriter(conn.outputStream).use { it.write(body) }
                    val code = conn.responseCode
                    (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }
        }.start()
    }

    private fun setPurchased(value: Boolean) = runOnMain {
        removeAdsPurchased = value
        prefs().edit().putBoolean(K_REMOVE_ADS, value).apply()
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun runOnMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    companion object {
        // 상품 ID는 iOS(App Store)와 동일하게 맞춘다.
        const val REMOVE_ADS_ID = "com.jiny.catchtherule.remove_ads"
        val HINT_TIERS = listOf(5, 10, 20, 50)
        fun hintsId(n: Int) = "com.jiny.catchtherule.hints_$n"
        private val HINT_GRANTS = HINT_TIERS.associateBy { hintsId(it) }   // "com.jiny.catchtherule.hints_5" -> 5
        private const val K_REMOVE_ADS = "remove_ads"   // 로컬 SharedPreferences 키(상품 ID 아님)
        private const val BASE_URL = "https://duo.jiny.shop"
    }
}

val LocalBilling = staticCompositionLocalOf<BillingManager> {
    error("BillingManager not provided")
}
