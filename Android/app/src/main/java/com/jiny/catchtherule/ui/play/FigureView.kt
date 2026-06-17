package com.jiny.catchtherule.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.core.model.Figure
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.card
import kotlin.math.cos
import kotlin.math.sin

// 도형 규칙 렌더러 — iOS FigureView.swift 와 동등.

// MARK: 숫자형 — 도형 + 숫자 슬롯

private const val FIG_SIDE = 150f   // dp

/** 숫자형 퍼즐: 완성된 예시 도형 + 빈칸 도형을 나란히 보여줘 규칙을 추론하게 한다. */
@Composable
fun FigureNumberRow(figures: List<Figure>, blankText: String = "?", feedback: AnswerFeedback? = null) {
    val n = figures.size.coerceAtLeast(1)
    val side = (if (n >= 3) 100f else if (n == 2) 138f else 150f).dp
    val spacing = if (n >= 3) 6.dp else 12.dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        figures.forEach { fig -> FigureNumberView(fig, blankText, feedback, side) }
    }
}

/** 숫자형 도형 1개(삼각형/사각형/원). 빈칸 슬롯은 blankText 로 표시. */
@Composable
fun FigureNumberView(figure: Figure, blankText: String = "?", feedback: AnswerFeedback? = null, side: Dp = FIG_SIDE.dp) {
    val slots = figure.slots ?: emptyList()
    val labelSide = maxOf(28.dp, side * 0.27f)
    val labelFont = maxOf(14f, side.value * 0.135f).sp
    Box(Modifier.size(side)) {
        // 윤곽선 + 원 세그먼트 구분선
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.16f
            val strokePx = 2.dp.toPx()
            when (figure.shape) {
                "square" -> drawRoundRect(
                    color = AppColors.Stroke,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = Stroke(width = strokePx),
                )
                "circle" -> {
                    val r = size.minDimension / 2 - inset
                    val c = Offset(size.width / 2, size.height / 2)
                    drawCircle(AppColors.Stroke, radius = r, center = c, style = Stroke(width = strokePx))
                    // 세그먼트 경계 구분선
                    val n = slots.size.coerceAtLeast(1)
                    for (i in 0 until n) {
                        val deg = -90.0 + (i + 0.5) * 360.0 / n
                        val a = Math.toRadians(deg)
                        drawLine(
                            AppColors.Stroke.copy(alpha = 0.6f),
                            start = c,
                            end = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                else -> { // triangle
                    val p = Path().apply {
                        moveTo(size.width / 2, inset)
                        lineTo(size.width - inset, size.height - inset)
                        lineTo(inset, size.height - inset)
                        close()
                    }
                    drawPath(p, AppColors.Stroke, style = Stroke(width = strokePx))
                }
            }
        }
        // 슬롯 숫자 라벨
        val anchors = anchorFractions(figure.shape, slots.size)
        slots.forEachIndexed { i, value ->
            val (fx, fy) = anchors[i]
            SlotLabel(
                value = value,
                blankText = blankText,
                feedback = feedback,
                labelSide = labelSide,
                fontSize = labelFont,
                modifier = Modifier.offset(x = side * fx - labelSide / 2, y = side * fy - labelSide / 2),
            )
        }
    }
}

@Composable
private fun SlotLabel(
    value: String?,
    blankText: String,
    feedback: AnswerFeedback?,
    labelSide: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
) {
    val isBlank = value == null
    val strokeBrush: Brush = when {
        !isBlank -> SolidColor(AppColors.Stroke)
        feedback == AnswerFeedback.Correct -> SolidColor(AppColors.Success)
        feedback == AnswerFeedback.Wrong -> SolidColor(AppColors.Danger)
        else -> AppColors.AccentGradient
    }
    Box(
        modifier
            .size(labelSide)
            .clip(CircleShape)
            .background(if (isBlank) Color.White.copy(alpha = 0.05f) else AppColors.Card)
            .border(if (isBlank) 2.dp else 1.dp, strokeBrush, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isBlank) blankText else value.orEmpty(),
            color = if (isBlank) AppColors.TextPrimary else AppColors.TextSecondary,
            fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

/** 슬롯 앵커(0..1 비율, 시계방향, 위에서 시작). 마지막 1칸이 남으면 중앙(triangle/square). */
private fun anchorFractions(shape: String, n: Int): List<Pair<Float, Float>> {
    val center = 0.5f to 0.5f
    return when (shape) {
        "square" -> {
            val corners = listOf(0.16f to 0.16f, 0.84f to 0.16f, 0.84f to 0.84f, 0.16f to 0.84f)
            if (n > 4) corners + center else corners.take(n)
        }
        "circle" -> (0 until n).map { i ->
            val a = Math.toRadians(-90.0 + i * 360.0 / n.coerceAtLeast(1))
            (0.5f + (cos(a) * 0.2).toFloat()) to (0.5f + (sin(a) * 0.2).toFloat())
        }
        else -> { // triangle
            val verts = listOf(0.5f to 0.18f, 0.80f to 0.80f, 0.20f to 0.80f)
            // 삼각형 무게중심은 박스 정중앙(0.5)보다 아래 → 중앙 슬롯을 꼭짓점 평균으로.
            val triCenter = 0.5f to (verts.sumOf { it.second.toDouble() } / verts.size).toFloat()
            if (n > 3) verts + triCenter else verts.take(n)
        }
    }
}

// MARK: 시각형 — 순수 모양

/** 도형이 칸별로 변하는 시퀀스. null 셀이 빈칸(보기에서 고를 도형 자리). */
@Composable
fun FigureRow(figs: List<Figure?>, puzzle: Puzzle, reveal: Boolean, feedback: AnswerFeedback?, popScale: Float) {
    val count = figs.size.coerceAtLeast(1)
    val spacing = if (count >= 6) 6.dp else 10.dp
    val glyph = if (count >= 6) 34.dp else 42.dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        figs.forEach { fig ->
            if (fig != null) {
                Box(Modifier.weight(1f).height(76.dp).card(16.dp), contentAlignment = Alignment.Center) {
                    FigureGlyph(figure = fig, size = glyph)
                }
            } else {
                FigureBlankCell(puzzle, reveal, feedback, glyph)
            }
        }
    }
}

@Composable
private fun RowScope.FigureBlankCell(puzzle: Puzzle, reveal: Boolean, feedback: AnswerFeedback?, glyph: Dp) {
    val answerFig = if (reveal) puzzle.figureChoices?.firstOrNull { it.code == puzzle.answer } else null
    val strokeBrush: Brush = when (feedback) {
        AnswerFeedback.Correct -> SolidColor(AppColors.Success)
        AnswerFeedback.Wrong -> SolidColor(AppColors.Danger)
        else -> AppColors.AccentGradient
    }
    Box(
        Modifier
            .weight(1f)
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(2.dp, strokeBrush, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (answerFig != null) {
            FigureGlyph(figure = answerFig, size = glyph)
        } else {
            Text("?", color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 시각 규칙용 도형(회전/채움/개수). 시퀀스 셀·보기 공용. */
@Composable
fun FigureGlyph(figure: Figure, size: Dp) {
    val s = if (figure.repeatCount > 1) size * 0.62f else size
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(figure.repeatCount) {
            Canvas(Modifier.size(s)) { drawGlyph(figure) }
        }
    }
}

private fun DrawScope.drawGlyph(figure: Figure) {
    val color = AppColors.Accent
    val filled = figure.isFilled
    val stroke = Stroke(width = 2.5.dp.toPx())
    val w = size.width
    val h = size.height
    rotate(figure.rotationDegrees) {
        when (figure.shape) {
            "square" -> if (filled)
                drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            else drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = stroke)
            "circle", "dot" -> if (filled)
                drawCircle(color, radius = w / 2)
            else drawCircle(color, radius = w / 2 - stroke.width / 2, style = stroke)
            "arrow" -> drawPath(arrowPath(w, h), color)
            else -> { // triangle
                val p = Path().apply {
                    moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close()
                }
                if (filled) drawPath(p, color) else drawPath(p, color, style = stroke)
            }
        }
    }
}

private fun arrowPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w / 2, 0f)
    lineTo(0f, h * 0.5f)
    lineTo(w * 0.28f, h * 0.5f)
    lineTo(w * 0.28f, h)
    lineTo(w * 0.72f, h)
    lineTo(w * 0.72f, h * 0.5f)
    lineTo(w, h * 0.5f)
    close()
}
