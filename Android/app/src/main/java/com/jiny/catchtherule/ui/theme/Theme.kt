package com.jiny.catchtherule.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 앱 전역 디자인 토큰. 다크 베이스 + 바이올렛→시안 포인트 그라데이션. */
object AppColors {
    val Bg = Color(0xFF0E1116)
    val BgElevated = Color(0xFF151A22)
    val Card = Color(0xFF1B212B)
    val Stroke = Color(0x12FFFFFF)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0x9EFFFFFF)
    val TextTertiary = Color(0x57FFFFFF)

    val Accent = Color(0xFF7C5CFF)
    val Accent2 = Color(0xFF39E5C8)
    val Success = Color(0xFF3DDC97)
    val Danger = Color(0xFFFF6B6B)
    val Star = Color(0xFFFFD166)

    val AccentGradient = Brush.linearGradient(listOf(Accent, Accent2))
}

@Composable
fun CatchTheRuleTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = AppColors.Accent,
        secondary = AppColors.Accent2,
        background = AppColors.Bg,
        surface = AppColors.Card,
        onPrimary = Color.White,
        onBackground = AppColors.TextPrimary,
        onSurface = AppColors.TextPrimary,
    )
    MaterialTheme(colorScheme = colors, content = content)
}

/** 화면 공통 배경. */
@Composable
fun ScreenBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppColors.Bg, AppColors.BgElevated)))
    ) {
        content()
    }
}

/** iOS 의 .card() 모디파이어에 대응. */
fun Modifier.card(radius: Dp = 20.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(AppColors.Card)
    .border(1.dp, AppColors.Stroke, RoundedCornerShape(radius))
