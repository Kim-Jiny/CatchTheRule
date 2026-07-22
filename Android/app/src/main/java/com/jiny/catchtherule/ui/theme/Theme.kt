package com.jiny.catchtherule.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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

/**
 * 전체화면(캠페인 세션 / 타임어택) 전용 배경.
 *
 * 탭 화면들은 RootScreen 의 Scaffold 가 주는 padding 으로 시스템 바를 피하지만,
 * 이 두 화면은 Scaffold 바깥에서 렌더링되므로 인셋을 직접 적용해야 한다.
 * (미적용 시 상단 닫기·힌트 버튼이 상태바와 겹치고, 하단 배너 광고가
 *  3버튼 내비게이션 바 아래로 깔려 오클릭 위험이 있었다.)
 *
 * 그라데이션 배경은 화면 끝까지 그리고 콘텐츠만 안쪽으로 민다.
 */
@Composable
fun FullScreenBackground(content: @Composable () -> Unit) {
    ScreenBackground {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            content()
        }
    }
}

/** iOS 의 .card() 모디파이어에 대응. */
fun Modifier.card(radius: Dp = 20.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(AppColors.Card)
    .border(1.dp, AppColors.Stroke, RoundedCornerShape(radius))
