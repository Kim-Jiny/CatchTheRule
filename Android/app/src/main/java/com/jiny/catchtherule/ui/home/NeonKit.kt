package com.jiny.catchtherule.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.modeColor
import com.jiny.catchtherule.ui.theme.modeGlow
import com.jiny.catchtherule.ui.theme.modeGradient
import com.jiny.catchtherule.ui.theme.rememberMotionEnabled
import com.jiny.catchtherule.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/** 모드별 아이콘. */
fun modeIcon(track: String): ImageVector = when (track) {
    "shapes" -> Icons.Filled.ChangeHistory
    "logic" -> Icons.Filled.Lightbulb
    "contradiction" -> Icons.Filled.Warning
    "target" -> Icons.Filled.Calculate
    "match" -> Icons.Filled.LocalFireDepartment
    else -> Icons.Filled.Numbers
}

/** 눌림 scale 0.96 스프링 + 가벼운 햅틱(설정 준수) 클릭. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    val progress = LocalProgress.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (progress.hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** 스태거 등장: delay → translateY 24→0 + alpha 0→1 (스프링). Reduce Motion 이면 즉시. */
fun Modifier.staggerIn(index: Int, stepMs: Int = 60): Modifier = composed {
    val reduce = rememberReduceMotion()
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(index.toLong() * stepMs)
        p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 24.dp.toPx()
    }
}

/** 0→target 카운트업(ease-out). Reduce Motion 이면 즉시 target. */
@Composable
fun rememberCountUp(target: Float, durationMs: Int = 900, delayMs: Int = 0): State<Float> {
    val reduce = rememberReduceMotion()
    val anim = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target) {
        if (reduce) anim.snapTo(target)
        else anim.animateTo(target, tween(durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    return anim.asState()
}

/** 카드 배경: 카드색 위에 모드색 틴트 대각 그라데이션 + 모드색 외곽선 + 소프트 글로우. */
fun Modifier.neonCard(
    track: String,
    radius: Dp = 22.dp,
    strong: Boolean = false,
    locked: Boolean = false,
): Modifier {
    val c = if (locked) Color(0xFF6B7280) else modeColor(track)
    val shape = RoundedCornerShape(radius)
    val base = AppColors.Card
    val a = c.copy(alpha = if (locked) 0.06f else 0.16f).compositeOver(base)
    val b = c.copy(alpha = 0.02f).compositeOver(base)
    return this
        .drawBehind {
            if (!locked) {
                val pad = if (strong) 18.dp.toPx() else 12.dp.toPx()
                val alpha = if (strong) 0.34f else 0.22f
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(c.copy(alpha = alpha), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = maxOf(size.width, size.height) / 2 + pad,
                    ),
                    topLeft = Offset(-pad, -pad),
                    size = Size(size.width + pad * 2, size.height + pad * 2),
                    cornerRadius = CornerRadius(radius.toPx() + pad),
                )
            }
        }
        .clip(shape)
        .background(Brush.linearGradient(listOf(a, b)))
        .border(1.dp, c.copy(alpha = if (strong) 0.9f else if (locked) 0.18f else 0.35f), shape)
}

/** 글로우 아이콘 타일(모드색 그라데이션 라운드 사각) + 모드별 고유 idle 애니메이션. */
@Composable
fun GlowIconTile(track: String, size: Dp, locked: Boolean = false, modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled() && !locked
    val tileBrush = if (locked) Brush.linearGradient(listOf(Color(0xFF4B5563), Color(0xFF374151))) else modeGradient(track)
    val glow = if (locked) Color.Transparent else modeGlow(track)
    Box(
        modifier
            .size(size)
            .drawBehind {
                val pad = 10.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(glow.copy(alpha = glow.alpha * 0.8f), Color.Transparent),
                        center = Offset(this.size.width / 2, this.size.height / 2),
                        radius = this.size.width / 2 + pad,
                    ),
                    topLeft = Offset(-pad, -pad),
                    size = Size(this.size.width + pad * 2, this.size.height + pad * 2),
                    cornerRadius = CornerRadius(size.toPx() / 3 + pad),
                )
            }
            .clip(RoundedCornerShape(size / 3.4f))
            .background(tileBrush),
        contentAlignment = Alignment.Center,
    ) {
        val iconModifier = Modifier.size(size * 0.5f)
        if (motion) IdleIcon(track, iconModifier)
        else Icon(modeIcon(track), null, tint = Color.White, modifier = iconModifier)
    }
}

/** 모드마다 주기·동작이 다른 idle 애니메이션 아이콘. */
@Composable
private fun IdleIcon(track: String, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "idle-$track")
    val p: Float by when (track) {
        "shapes" -> t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), "p")
        "logic" -> t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), "p")
        "contradiction" -> t.animateFloat(
            0f, 0f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 3400
                    0f at 0; 0f at 1800; 10f at 1950; -10f at 2150; 8f at 2350; -6f at 2550; 0f at 2750; 0f at 3400
                },
            ),
            "p",
        )
        "target" -> t.animateFloat(1f, 1.14f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), "p")
        "match" -> t.animateFloat(0.94f, 1.12f, infiniteRepeatable(tween(850), RepeatMode.Reverse), "p")
        else -> t.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(2300), RepeatMode.Reverse), "p")
    }
    Icon(
        modeIcon(track), null, tint = Color.White,
        modifier = modifier.graphicsLayer {
            when (track) {
                "shapes" -> rotationZ = p
                "logic" -> { alpha = p; scaleX = 0.9f + 0.1f * p; scaleY = scaleX }
                "contradiction" -> rotationZ = p
                "target", "match" -> { scaleX = p; scaleY = p }
                else -> translationY = p.dp.toPx()
            }
        },
    )
}

/** 얇은 진행 바(모드색, 0→값). */
@Composable
fun NeonBar(track: String, fraction: Float, modifier: Modifier = Modifier, height: Dp = 5.dp, delayMs: Int = 0) {
    val f by rememberCountUp(fraction.coerceIn(0f, 1f), 800, delayMs)
    Box(
        modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier.fillMaxWidth(f.coerceIn(0.001f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(50)).background(modeGradient(track)),
        )
    }
}
