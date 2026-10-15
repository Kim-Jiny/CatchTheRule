package com.jiny.catchtherule.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.theme.rememberReduceMotion
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.jiny.catchtherule.R
import com.jiny.catchtherule.ui.challenge.ChallengeScreen
import com.jiny.catchtherule.ui.challenge.TimeAttackScreen
import com.jiny.catchtherule.ui.home.HomeScreen
import com.jiny.catchtherule.ui.play.CampaignSessionScreen
import com.jiny.catchtherule.ui.settings.SettingsScreen
import com.jiny.catchtherule.ui.theme.AppColors

private sealed interface FullScreen {
    data class Campaign(val startIndex: Int, val track: String) : FullScreen
    data object TimeAttack : FullScreen
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun RootScreen() {
    var fullScreen by remember {
        mutableStateOf<FullScreen?>(com.jiny.catchtherule.ScreenshotMode.play?.let { FullScreen.Campaign(it.second, it.first) })
    }
    var selectedTab by rememberSaveable { mutableStateOf(com.jiny.catchtherule.ScreenshotMode.startTab) }
    var homeMode by rememberSaveable { mutableStateOf<String?>(null) }   // 선택된 모드 상세(허브 백스택)

    when (val fs = fullScreen) {
        is FullScreen.Campaign -> {
            CampaignSessionScreen(startIndex = fs.startIndex, track = fs.track, onClose = { fullScreen = null })
            return
        }
        FullScreen.TimeAttack -> {
            TimeAttackScreen(onClose = { fullScreen = null })
            return
        }
        null -> Unit
    }

    val tabs = listOf(
        Tab(stringResource(R.string.tab_home), Icons.Filled.Home),
        Tab(stringResource(R.string.tab_challenge), Icons.Filled.EmojiEvents),
        Tab(stringResource(R.string.tab_settings), Icons.Filled.Settings),
    )
    val reduce = rememberReduceMotion()
    val stateHolder = rememberSaveableStateHolder()

    // 하단 플로팅 탭바 영역(탭바 높이 + 여백 + 시스템 내비 인셋)이 Scaffold padding 으로 전달되어
    // 각 탭 콘텐츠(배너 포함)가 탭바 위에서 끝난다.
    Scaffold(
        containerColor = AppColors.NeonBgBottom,
        bottomBar = {
            NeonTabBar(tabs = tabs, selected = selectedTab, reduceMotion = reduce, onSelect = { selectedTab = it })
        },
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (reduce) ContentTransform(EnterTransition.None, ExitTransition.None)
                else (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 40 }) togetherWith fadeOut(tween(120))
            },
            label = "tab-content",
        ) { tab ->
            stateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> HomeScreen(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        selectedMode = homeMode,
                        onSelectMode = { homeMode = it },
                        onBackToHub = { homeMode = null },
                        onPlay = { startIndex, track -> fullScreen = FullScreen.Campaign(startIndex, track) },
                    )
                    1 -> ChallengeScreen(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        onStart = { fullScreen = FullScreen.TimeAttack },
                    )
                    else -> SettingsScreen(
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }
    }
}

private val BarShape = RoundedCornerShape(50)

@Composable
private fun NeonTabBar(tabs: List<Tab>, selected: Int, reduceMotion: Boolean, onSelect: (Int) -> Unit) {
    val progress = LocalProgress.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // 선택 항목은 아이콘+라벨이라 더 넓다. 가중치를 스프링으로 애니메이션 → 알약 위치/폭도 함께 슬라이드.
    val weights = tabs.indices.map { i ->
        animateFloatAsState(
            if (i == selected) 1.75f else 1f,
            if (reduceMotion) tween(0) else spring(dampingRatio = 0.75f, stiffness = 380f),
            label = "tab-weight-$i",
        ).value
    }
    Box(
        Modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 6.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(14.dp, BarShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(BarShape)
                .background(Color(0xFF10141C).copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), BarShape)
                .padding(6.dp),
        ) {
            val total = weights.sum()
            val unit = maxWidth / total
            val pillX = unit * weights.take(selected).sum()
            val pillW = unit * weights[selected]
            // 슬라이드하는 알약(액센트 그라데이션 + 글로우)
            Box(
                Modifier
                    .offset(x = pillX)
                    .width(pillW)
                    .fillMaxHeight()
                    .drawBehind {
                        val pad = 8.dp.toPx()
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                listOf(AppColors.Accent.copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(size.width / 2, size.height / 2),
                                radius = size.width / 2 + pad,
                            ),
                            topLeft = Offset(-pad, -pad),
                            size = Size(size.width + pad * 2, size.height + pad * 2),
                            cornerRadius = CornerRadius(size.height / 2 + pad),
                        )
                    }
                    .clip(BarShape)
                    .background(AppColors.AccentGradient),
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Start) {
                tabs.forEachIndexed { i, tab ->
                    val isSel = i == selected
                    val bounce = remember { Animatable(1f) }
                    LaunchedEffect(isSel) {
                        if (isSel && !reduceMotion && bounce.value == 1f) {
                            bounce.animateTo(1.18f, tween(110))
                            bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                        }
                    }
                    val labelAlpha by animateFloatAsState(if (isSel) 1f else 0f, tween(if (reduceMotion) 0 else 200), label = "tab-label-$i")
                    Row(
                        Modifier
                            .weight(weights[i])
                            .fillMaxHeight()
                            .clip(BarShape)
                            .semantics(mergeDescendants = true) { contentDescription = tab.label }
                            .selectable(
                                selected = isSel,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) {
                                if (!isSel) {
                                    if (progress.hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(i)
                                }
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            tab.icon, null,
                            tint = if (isSel) Color.White else Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
                        )
                        if (isSel) {
                            Text(
                                tab.label,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .graphicsLayer { alpha = labelAlpha; translationX = (1f - labelAlpha) * 8.dp.toPx() },
                            )
                        }
                    }
                }
            }
        }
    }
}
