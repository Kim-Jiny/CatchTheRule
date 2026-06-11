package com.jiny.catchtherule.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.jiny.catchtherule.BuildConfig

/**
 * 리워드 광고("광고 보고 힌트 받기").
 * 광고 1회 시청 완료 시 힌트 1개를 지급한다(콜백은 호출부에서 처리).
 * 미리 한 개를 로드해두고(prefetch), 시청 후 다음 광고를 다시 로드한다.
 */
class AdsManager(context: Context) {

    private val appContext = context.applicationContext

    /** 표시 가능한 광고가 준비됐는지(버튼 활성/로딩 표시용). */
    var isReady by mutableStateOf(false)
        private set

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    fun start() {
        MobileAds.initialize(appContext) { load() }
    }

    /** 광고가 없으면 미리 로드해 둔다. */
    fun load() {
        if (loading || rewardedAd != null) return
        loading = true
        RewardedAd.load(
            appContext,
            REWARDED_HINT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                    isReady = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                    isReady = false
                }
            },
        )
    }

    /**
     * 광고를 표시한다. 시청을 완료하면 onReward 가 한 번 호출된다.
     * 광고가 아직 준비되지 않았으면 onReward 없이 즉시 false 를 반환하고 로드를 시도한다.
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit): Boolean {
        val ad = rewardedAd ?: run { load(); return false }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                isReady = false
                load()   // 다음 광고 미리 로드
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                isReady = false
                load()
            }
        }
        rewardedAd = null
        isReady = false
        ad.show(activity) { onReward() }
        return true
    }

    companion object {
        // DEBUG 빌드는 구글 테스트 광고 단위, 릴리스는 실제 단위.
        private val REWARDED_HINT_ID: String
            get() = if (BuildConfig.DEBUG)
                "ca-app-pub-3940256099942544/5224354917"   // Google 테스트 리워드 단위
            else
                "ca-app-pub-2707874353926722/4624765585"   // 실제 힌트 리워드 단위
    }
}

val LocalAds = staticCompositionLocalOf<AdsManager> {
    error("AdsManager not provided")
}
