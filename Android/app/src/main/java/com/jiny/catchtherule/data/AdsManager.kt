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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.jiny.catchtherule.BuildConfig
import kotlin.random.Random

/**
 * 광고 매니저.
 *  - 리워드("광고 보고 힌트"): 시청 완료 시 힌트 1개 지급(콜백은 호출부 처리)
 *  - 전면(스테이지 클리어): 챕터 2+ / 10% 확률 / 3분 쿨다운일 때만 노출
 * 둘 다 미리 로드(prefetch)해두고, 닫히면 다음 것을 재로드한다.
 */
class AdsManager(context: Context) {

    private val appContext = context.applicationContext

    /** 표시 가능한 리워드 광고가 준비됐는지(버튼 활성/로딩 표시용). */
    var isReady by mutableStateOf(false)
        private set

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var lastInterstitialAt = 0L

    fun start() {
        MobileAds.initialize(appContext) {
            load()
            loadInterstitial()
        }
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

    // MARK: - 전면(Interstitial)

    /** 전면광고가 없으면 미리 로드해 둔다. */
    fun loadInterstitial() {
        if (interstitialLoading || interstitial != null) return
        interstitialLoading = true
        InterstitialAd.load(
            appContext,
            INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    interstitialLoading = false
                }
            },
        )
    }

    /**
     * 조건(챕터 >= 2 && 10% 당첨 && 마지막 노출 후 3분 경과 && 준비된 광고 존재)을
     * 모두 만족할 때만 전면광고를 노출한다. 노출했으면 true.
     * (광고 제거 구매 여부는 호출부에서 먼저 거른다.)
     */
    fun maybeShowInterstitial(activity: Activity, chapter: Int): Boolean {
        if (chapter < INTERSTITIAL_MIN_CHAPTER) return false
        val now = System.currentTimeMillis()
        if (now - lastInterstitialAt < INTERSTITIAL_COOLDOWN_MS) return false
        // 챕터가 높을수록 확률 상승: 챕터2=10%, 챕터당 +5%p (최대 100%).
        val prob = (INTERSTITIAL_BASE_PROB + INTERSTITIAL_STEP_PROB * (chapter - INTERSTITIAL_MIN_CHAPTER)).coerceIn(0f, 1f)
        if (Random.nextFloat() >= prob) {
            loadInterstitial()   // 이번엔 미당첨 — 다음을 위해 준비
            return false
        }
        val ad = interstitial ?: run { loadInterstitial(); return false }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial()   // 다음 전면 미리 로드
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                loadInterstitial()
            }
        }
        lastInterstitialAt = now
        interstitial = null
        ad.show(activity)
        return true
    }

    companion object {
        // 전면광고 노출 규칙(상수로 조정 가능)
        private const val INTERSTITIAL_MIN_CHAPTER = 2          // 챕터 2부터
        private const val INTERSTITIAL_BASE_PROB = 0.10f        // 챕터 2 기준 10%
        private const val INTERSTITIAL_STEP_PROB = 0.05f        // 챕터 1 증가마다 +5%p
        private const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L  // 3분 쿨다운

        // DEBUG 빌드는 구글 테스트 광고 단위, 릴리스는 실제 단위.
        private val REWARDED_HINT_ID: String
            get() = if (BuildConfig.DEBUG)
                "ca-app-pub-3940256099942544/5224354917"   // Google 테스트 리워드 단위
            else
                "ca-app-pub-2707874353926722/4624765585"   // 실제 힌트 리워드 단위

        private val INTERSTITIAL_ID: String
            get() = if (BuildConfig.DEBUG)
                "ca-app-pub-3940256099942544/1033173712"   // Google 테스트 전면 단위
            else
                "ca-app-pub-2707874353926722/4777351392"   // 실제 게임클리어 전면 단위
    }
}

val LocalAds = staticCompositionLocalOf<AdsManager> {
    error("AdsManager not provided")
}
