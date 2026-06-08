package com.jiny.catchtherule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.jiny.catchtherule.data.AnalyticsService
import com.jiny.catchtherule.data.BillingManager
import com.jiny.catchtherule.data.LocalBilling
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.data.ProgressStore
import com.jiny.catchtherule.data.Ranking
import com.jiny.catchtherule.ui.RootScreen
import com.jiny.catchtherule.ui.theme.CatchTheRuleTheme

class MainActivity : ComponentActivity() {
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Ranking.init(this)
        AnalyticsService.ping(this)
        com.jiny.catchtherule.core.PuzzleStore.refreshFromServer(this)  // 추가 스테이지 캐시 갱신
        val progress = ProgressStore(this)
        billing = BillingManager(this, progress).also { it.start() }
        setContent {
            CatchTheRuleTheme {
                CompositionLocalProvider(
                    LocalProgress provides progress,
                    LocalBilling provides billing,
                ) {
                    RootScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 외부(스토어/다른 기기)에서의 구매·환불 반영
        if (::billing.isInitialized) billing.queryPurchases()
    }
}
