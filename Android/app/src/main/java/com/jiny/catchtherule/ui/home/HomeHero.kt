package com.jiny.catchtherule.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.modeColor
import com.jiny.catchtherule.ui.theme.modeGradient
import com.jiny.catchtherule.ui.theme.rememberMotionEnabled
import kotlin.math.roundToInt

private val TNUM = TextStyle(fontFeatureSettings = "tnum")

/** 모드색 그라데이션 진행률 링(0→값 카운트업) + 가운데 % 카운트업. */
@Composable
fun NeonRing(track: String, fraction: Float, size: Dp, stroke: Dp = 8.dp, delayMs: Int = 0) {
    val f by rememberCountUp(fraction.coerceIn(0f, 1f), 900, delayMs)
    val color = modeColor(track)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(
                color = Color.White.copy(alpha = 0.08f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = w),
            )
            if (f > 0f) {
                drawArc(
                    brush = modeGradient(track), startAngle = -90f, sweepAngle = 360f * f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            "${(f * 100).roundToInt()}%",
            style = TNUM.copy(color = color.copy(alpha = 1f), fontSize = (size.value * 0.23f).sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/** 모드색 그라데이션 pill. 은은한 펄스 글로우(2초) + 샤인 스윕(3초마다 좌→우). */
@Composable
fun ShineButton(track: String, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val motion = rememberMotionEnabled()
    val color = modeColor(track)
    val pulse: Float
    val sweep: Float
    if (motion) {
        val t = rememberInfiniteTransition(label = "shine")
        val pulseState by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), "pulse")
        val sweepState by t.animateFloat(
            -0.4f, -0.4f,
            infiniteRepeatable(keyframes { durationMillis = 3000; -0.4f at 0; 1.4f at 900 using LinearEasing; 1.4f at 3000 }),
            "sweep",
        )
        pulse = pulseState; sweep = sweepState
    } else {
        pulse = 0.5f; sweep = -1f
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                val pad = (8.dp.toPx() * (0.6f + 0.6f * pulse))
                drawRoundRect(
                    color = color.copy(alpha = 0.18f + 0.22f * pulse),
                    topLeft = Offset(-pad, -pad / 2),
                    size = Size(size.width + pad * 2, size.height + pad),
                    cornerRadius = CornerRadius(16.dp.toPx() + pad),
                )
            }
            .clip(shape)
            .background(modeGradient(track))
            .pressable(onClick = onClick)
            .drawBehind {
                if (sweep > -0.3f && sweep < 1.3f) {
                    val bandW = size.width * 0.28f
                    val x = size.width * sweep
                    clipRect {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                                startX = x - bandW, endX = x + bandW,
                            ),
                        )
                    }
                }
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * 이어하기 히어로 카드(홈) / 챕터 목록 상단 이어하기 카드 공용.
 * [onOpen] 이 null 이면 카드 본문은 탭 불가(챕터 목록 안에서 쓸 때).
 */
@Composable
fun HeroCard(
    track: String,
    caption: String,
    title: String,
    subtitle: String,
    fraction: Float,
    finished: Boolean,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    onContinue: () -> Unit,
) {
    val bodyModifier = if (onOpen != null) Modifier.pressable(onClick = onOpen) else Modifier
    Column(
        modifier.fillMaxWidth().then(bodyModifier).neonCard(track, strong = false).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlowIconTile(track, 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(caption, color = modeColor(track), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, color = AppColors.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 25.sp)
                Text(subtitle, color = AppColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NeonRing(track, fraction, 74.dp)
        }
        if (finished) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.home_wait_update), color = AppColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            ShineButton(track, stringResource(R.string.home_continue), onClick = onContinue)
        }
    }
}
