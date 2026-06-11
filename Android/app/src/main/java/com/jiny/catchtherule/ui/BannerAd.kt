package com.jiny.catchtherule.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.jiny.catchtherule.BuildConfig
import com.jiny.catchtherule.data.LocalBilling

/** AdMob 적응형(anchored adaptive) 배너. 광고 제거 구매 시 아무것도 표시하지 않는다. */
@Composable
fun BannerAd(unitId: String, modifier: Modifier = Modifier) {
    val billing = LocalBilling.current
    if (billing.removeAdsPurchased) return

    val widthDp = LocalConfiguration.current.screenWidthDp

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = unitId
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

/** 배너 광고 단위 ID. DEBUG 빌드는 구글 테스트 단위, 릴리스는 실제 단위. */
object BannerUnits {
    private const val TEST = "ca-app-pub-3940256099942544/6300978111"   // Google 테스트 배너(Android)
    val home: String get() = if (BuildConfig.DEBUG) TEST else "ca-app-pub-2707874353926722/3961631589"
    val challenge: String get() = if (BuildConfig.DEBUG) TEST else "ca-app-pub-2707874353926722/1335468248"
    val settings: String get() = if (BuildConfig.DEBUG) TEST else "ca-app-pub-2707874353926722/9170560384"
    val stage: String get() = if (BuildConfig.DEBUG) TEST else "ca-app-pub-2707874353926722/6663816544"
}
