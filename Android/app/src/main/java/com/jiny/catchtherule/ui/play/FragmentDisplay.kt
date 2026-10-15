package com.jiny.catchtherule.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.card
import com.jiny.catchtherule.ui.theme.rememberReduceMotion
import com.jiny.catchtherule.ui.theme.rememberScreenActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/** 장 색(고정 순서): 시안 / 핑크 / 앰버 / 그린. */
private val FRAME_COLORS = listOf(Color(0xFF18C8FF), Color(0xFFFF5C8A), Color(0xFFFFB020), Color(0xFF3DDC97))
private const val FADE_MS = 120

fun fragmentFrameColor(frame: Int): Color = FRAME_COLORS[frame % FRAME_COLORS.size]

/** 상단 프롬프트 + (xor 일 때) 규칙 캡션. 세션/타임어택 공용. */
@Composable
fun FragmentPrompt(puzzle: Puzzle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.fragment_prompt), color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (puzzle.combine == "xor") {
            Text(stringResource(R.string.fragment_rule_xor), color = AppColors.TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * 조각 숫자 디스플레이: 7세그먼트 숫자를 장(frame)별로 돌아가며 보여 준다(무한 순환).
 * 퍼즐이 바뀌면 처음 장부터 재시작, 화면이 비활성이면 정지.
 */
@Composable
fun FragmentDisplay(puzzle: Puzzle, modifier: Modifier = Modifier) {
    val frames = puzzle.frames ?: return
    if (frames.isEmpty()) return
    key(puzzle.id) {
        val count = frames.size
        val frameMs = (puzzle.frameMs ?: 1500).coerceAtLeast(600)
        val active = rememberScreenActive()
        val reduce = rememberReduceMotion()
        var current by remember { mutableIntStateOf(0) }
        var previous by remember { mutableIntStateOf(-1) }
        val fade = remember { Animatable(1f) }
        val bar = remember { Animatable(0f) }

        LaunchedEffect(active, count, frameMs, reduce) {
            if (!active) return@LaunchedEffect
            while (true) {
                // 진행 바는 장식(동작 시간은 delay 로 결정 → 시스템 애니메이션 배율 0 이어도 순환 유지).
                bar.snapTo(0f)
                coroutineScope {
                    if (!reduce) launch { bar.animateTo(1f, tween(frameMs, easing = LinearEasing)) } else bar.snapTo(1f)
                    delay(frameMs.toLong())
                }
                previous = current
                current = (current + 1) % count
                if (reduce || count == 1) {
                    fade.snapTo(1f)
                } else {
                    fade.snapTo(0f)
                    launch { fade.animateTo(1f, tween(FADE_MS)) }
                }
            }
        }

        val digits = frames.maxOf { it.size }.coerceAtLeast(1)
        Column(
            modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val gap = 12.dp
                val cellW: Dp = ((maxWidth - gap * 3) / 4).coerceAtMost(76.dp)
                val cellH = cellW * 1.8f
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (d in 0 until digits) {
                        val curSegs = frames[current].getOrNull(d).orEmpty()
                        val prevSegs = frames.getOrNull(previous)?.getOrNull(d).orEmpty()
                        val curColor = fragmentFrameColor(current)
                        val prevColor = fragmentFrameColor(previous.coerceAtLeast(0))
                        val f = fade.value
                        Canvas(Modifier.size(cellW, cellH)) {
                            drawDigit(curSegs, prevSegs, curColor, prevColor, f, previous >= 0)
                        }
                    }
                }
            }
            // 장 점 + 진행 바
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until count) {
                    val on = i == current
                    val c = fragmentFrameColor(i)
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (on) c else Color.Transparent)
                            .border(1.5.dp, if (on) c else c.copy(alpha = 0.4f), CircleShape)
                            .semantics { contentDescription = "${i + 1}/$count" },
                    )
                }
            }
            Box(Modifier.width(140.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bar.value).clip(CircleShape).background(fragmentFrameColor(current)))
            }
        }
    }
}

// 세그먼트 기하: 셀 w×h, 두께 t. (a 위, b 우상, c 우하, d 아래, e 좌하, f 좌상, g 가운데)
private fun DrawScope.drawDigit(cur: String, prev: String, curColor: Color, prevColor: Color, fade: Float, hasPrev: Boolean) {
    val w = size.width
    val h = size.height
    val t = w * 0.17f
    val gap = t * 0.18f
    val r = CornerRadius(t / 2f)
    val vh = h / 2f - t / 2f - 2 * gap
    val ghost = Color.White.copy(alpha = 0.07f)
    fun rect(seg: Char): Pair<Offset, Size> = when (seg) {
        'a' -> Offset(t + gap, 0f) to Size(w - 2 * t - 2 * gap, t)
        'g' -> Offset(t + gap, (h - t) / 2f) to Size(w - 2 * t - 2 * gap, t)
        'd' -> Offset(t + gap, h - t) to Size(w - 2 * t - 2 * gap, t)
        'f' -> Offset(0f, t / 2f + gap) to Size(t, vh)
        'b' -> Offset(w - t, t / 2f + gap) to Size(t, vh)
        'e' -> Offset(0f, h / 2f + gap) to Size(t, vh)
        else -> Offset(w - t, h / 2f + gap) to Size(t, vh) // 'c'
    }
    for (seg in "abcdefg") {
        val (o, s) = rect(seg)
        drawRoundRect(ghost, o, s, r)
        fun lit(color: Color, alpha: Float) {
            if (alpha <= 0.01f) return
            // 글로우: 넓은 반투명 라운드 사각 2겹 + 본체
            val g1 = t * 0.55f
            drawRoundRect(color.copy(alpha = 0.12f * alpha), Offset(o.x - g1, o.y - g1), Size(s.width + 2 * g1, s.height + 2 * g1), CornerRadius(t / 2f + g1))
            val g2 = t * 0.25f
            drawRoundRect(color.copy(alpha = 0.22f * alpha), Offset(o.x - g2, o.y - g2), Size(s.width + 2 * g2, s.height + 2 * g2), CornerRadius(t / 2f + g2))
            drawRoundRect(color.copy(alpha = alpha), o, s, r)
        }
        val inCur = seg in cur
        val inPrev = hasPrev && seg in prev
        if (inPrev && fade < 1f) lit(prevColor, 1f - fade)
        if (inCur) lit(curColor, fade)
    }
}
