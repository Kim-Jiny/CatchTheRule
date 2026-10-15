package com.jiny.catchtherule.ui.theme

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 시스템 "애니메이션 없음"(ANIMATOR_DURATION_SCALE == 0) 여부. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** 화면이 보이는 상태(STARTED 이상)인지. 백그라운드/가려지면 false → 무한 애니메이션 정지. */
@Composable
fun rememberScreenActive(): Boolean {
    val owner = LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

/** 장식용 무한 애니메이션을 돌려도 되는지(보이는 중 + Reduce Motion 아님). */
@Composable
fun rememberMotionEnabled(): Boolean = rememberScreenActive() && !rememberReduceMotion()

private class Glyph(
    val ch: String, val size: Float, val x: Float, val speed: Float, val phase: Float,
    val alpha: Float, val spin: Float, val sway: Float,
)

private val GLYPH_CHARS = listOf("+", "−", "×", "÷", "=", "%", "7", "9", "▲", "●", "■", "∑", "π", "?", "√", "∞", "3", "△")

private val BLOBS = listOf(
    // 색, 기준 위치(x,y 비율), 이동 반경(비율), 주기(초), 위상, 반지름(비율)
    Triple(Color(0xFF7C5CFF), floatArrayOf(0.15f, 0.20f, 0.12f, 26f, 0.0f), 0.85f),
    Triple(Color(0xFF18C8FF), floatArrayOf(0.90f, 0.50f, 0.10f, 22f, 2.1f), 0.75f),
    Triple(Color(0xFFFF5C8A), floatArrayOf(0.30f, 0.92f, 0.14f, 30f, 4.2f), 0.80f),
)

/**
 * 살아 있는 배경: 오로라 블롭 3개 + 떠다니는 수학·도형 글리프.
 * 전부 Canvas 한 장에 그린다. 시간은 단일 프레임 루프로만 갱신(그리기 단계만 무효화).
 * 움직임이 꺼지면(Reduce Motion/백그라운드) 마지막 프레임 그대로 정지한다.
 */
@Composable
fun NeonBackdrop() {
    val motion = rememberMotionEnabled()
    val measurer: TextMeasurer = rememberTextMeasurer()
    val glyphs = remember {
        val r = Random(7)
        List(16) { i ->
            Glyph(
                ch = GLYPH_CHARS[i % GLYPH_CHARS.size],
                size = 18f + r.nextFloat() * 26f,
                x = r.nextFloat(),
                speed = 1f / (28f + r.nextFloat() * 30f),
                phase = r.nextFloat(),
                alpha = 0.04f + r.nextFloat() * 0.06f,
                spin = (if (r.nextBoolean()) 1f else -1f) * (6f + r.nextFloat() * 14f),
                sway = 6f + r.nextFloat() * 14f,
            )
        }
    }
    val layouts = remember(glyphs) {
        glyphs.map {
            measurer.measure(it.ch, TextStyle(color = Color.White, fontSize = it.size.sp, fontWeight = FontWeight.Bold))
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) time += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = time
        BLOBS.forEach { (color, p, rad) ->
            val ang = (2 * PI * (t / p[3]) + p[4]).toFloat()
            val cx = w * (p[0] + p[2] * cos(ang))
            val cy = h * (p[1] + p[2] * sin(ang * 0.8f))
            val r = w * rad
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(cx, cy), radius = r,
                ),
                radius = r, center = Offset(cx, cy),
            )
        }
        glyphs.forEachIndexed { i, g ->
            val layout = layouts[i]
            val prog = ((t * g.speed + g.phase) % 1f)
            val margin = 60f
            val y = h + margin - prog * (h + margin * 2)
            val x = w * g.x + sin(prog * 2 * PI.toFloat() * 2f) * g.sway
            val center = Offset(x, y)
            withTransform({ rotate(prog * g.spin * 6f, center) }) {
                drawText(
                    layout, color = Color.White.copy(alpha = g.alpha),
                    topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
                )
            }
        }
    }
}
