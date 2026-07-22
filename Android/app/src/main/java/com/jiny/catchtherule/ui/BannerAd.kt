package com.jiny.catchtherule.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp

    // AdView 를 remember 로 보관해 재구성마다 새로 만들지 않는다. 단위/폭이 바뀌면 새로 생성.
    val adView = remember(unitId, widthDp) {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = unitId
            loadAd(AdRequest.Builder().build())
        }
    }

    // 라이프사이클에 맞춰 pause/resume, 화면에서 사라지면 destroy() 로 누수(내부 WebView 포함) 방지.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { adView },
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
