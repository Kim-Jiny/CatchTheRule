package com.jiny.catchtherule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.jiny.catchtherule.data.AnalyticsService
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.data.ProgressStore
import com.jiny.catchtherule.data.Ranking
import com.jiny.catchtherule.ui.RootScreen
import com.jiny.catchtherule.ui.theme.CatchTheRuleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Ranking.init(this)
        AnalyticsService.ping(this)
        val progress = ProgressStore(this)
        setContent {
            CatchTheRuleTheme {
                CompositionLocalProvider(LocalProgress provides progress) {
                    RootScreen()
                }
            }
        }
    }
}
