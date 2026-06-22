package com.jiny.catchtherule.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.card

enum class AnswerFeedback { Correct, Wrong }

/** 수열/격자를 카드 셀로 표시. 빈칸은 그라데이션 테두리로 강조. */
@Composable
fun SequenceDisplay(
    puzzle: Puzzle,
    typed: String = "",
    reveal: Boolean = false,
    feedback: AnswerFeedback? = null,
    modifier: Modifier = Modifier,
) {
    val blankText = when {
        reveal -> puzzle.answer
        typed.isEmpty() -> "?"
        else -> typed
    }
    // 정답 시 정답 칸 팝(scale 오버슈트) 애니메이션.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(feedback) {
        if (feedback == AnswerFeedback.Correct) {
            pop.snapTo(1f)
            pop.animateTo(1.22f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
            pop.animateTo(1f, spring(dampingRatio = 0.6f))
        }
    }
    val popScale = pop.value
    val grid = puzzle.grid
    if (puzzle.isPrompt) {
        // 논리형: 질문 문단을 카드로(줄바꿈 보존, 왼쪽 정렬).
        Box(
            modifier.fillMaxWidth().card().padding(20.dp),
        ) {
            Text(
                puzzle.localizedPrompt,
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
            )
        }
    } else if (puzzle.isFigure && puzzle.figures != null) {
        FigureNumberRow(figures = puzzle.figures, blankText = blankText, feedback = feedback)
    } else if (puzzle.isFigureSequence) {
        FigureRow(puzzle.figureTokens ?: emptyList(), puzzle, reveal, feedback, popScale)
    } else if (puzzle.type == "equation" && !grid.isNullOrEmpty()) {
        // 수식형: "[2] + [3] = [13]" — 숫자는 박스, 연산자는 사이 텍스트, 빈칸은 강조 박스
        val cols = (grid.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        // 칸 수가 많아도 가로로 넘치지 않게 단계적 축소.
        val fontSize = when { cols >= 9 -> 14.sp; cols >= 8 -> 16.sp; cols >= 7 -> 18.sp; cols >= 6 -> 21.sp; cols >= 5 -> 24.sp; else -> 28.sp }
        val side = (fontSize.value * 1.8f).dp
        androidx.compose.foundation.layout.Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            grid.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { value ->
                        when {
                            value == null -> EqBox(blankText, fontSize, side, true, feedback, popScale)
                            isEqOperator(value) -> Text(
                                value, color = AppColors.TextTertiary, fontSize = fontSize,
                                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif, maxLines = 1, softWrap = false,
                            )
                            else -> EqBox(value, fontSize, side, false, feedback, popScale)
                        }
                    }
                }
            }
        }
    } else if (!grid.isNullOrEmpty()) {
        // 격자형(두 줄/매트릭스/수식형)
        val cols = (grid.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        val spacing = if (cols >= 5) 8.dp else 10.dp
        val fontSize = when { cols >= 5 -> 22.sp; cols >= 4 -> 25.sp; else -> 28.sp }
        val cellHeight = if (grid.size >= 3) 52.dp else 60.dp
        androidx.compose.foundation.layout.Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            grid.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { value -> SeqCell(value, blankText, fontSize, cellHeight, feedback, popScale) }
                }
            }
        }
    } else {
        // 단일 행 (칸 수에 따라 폰트/간격/높이 축소 → 스크롤 없이 화면 폭에 맞춤)
        val tokens = puzzle.tokens ?: emptyList()
        val count = tokens.size.coerceAtLeast(1)
        val spacing = when { count >= 7 -> 6.dp; count >= 6 -> 8.dp; count >= 5 -> 10.dp; else -> 12.dp }
        val fontSize = when { count >= 7 -> 20.sp; count >= 6 -> 23.sp; count >= 5 -> 26.sp; else -> 30.sp }
        val cellHeight = if (count >= 6) 66.dp else 76.dp
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tokens.forEach { value -> SeqCell(value, blankText, fontSize, cellHeight, feedback, popScale) }
        }
    }
}

@Composable
private fun RowScope.SeqCell(
    value: String?,
    blankText: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    height: androidx.compose.ui.unit.Dp,
    feedback: AnswerFeedback?,
    popScale: Float = 1f,
) {
    val isBlank = value == null
    val correct = isBlank && feedback == AnswerFeedback.Correct
    val strokeBrush: Brush = when {
        !isBlank -> SolidColor(AppColors.Stroke)
        feedback == AnswerFeedback.Correct -> SolidColor(AppColors.Success)
        feedback == AnswerFeedback.Wrong -> SolidColor(AppColors.Danger)
        else -> AppColors.AccentGradient
    }
    Box(
        Modifier
            .weight(1f)
            .graphicsLayer {
                if (isBlank) { scaleX = popScale; scaleY = popScale }
            }
            .height(height)
            .then(
                if (correct) Modifier.shadow(16.dp, RoundedCornerShape(16.dp),
                    spotColor = AppColors.Success, ambientColor = AppColors.Success)
                else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isBlank) Color.White.copy(alpha = 0.03f) else AppColors.Card)
            .border(if (isBlank) 2.dp else 1.dp, strokeBrush, RoundedCornerShape(16.dp))
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isBlank) blankText else (value ?: ""),
            color = if (isBlank) AppColors.TextPrimary else AppColors.TextSecondary,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

/** 수식형 숫자/빈칸 박스. */
@Composable
private fun EqBox(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    side: androidx.compose.ui.unit.Dp,
    isBlank: Boolean,
    feedback: AnswerFeedback?,
    popScale: Float,
) {
    val strokeBrush: Brush = when {
        !isBlank -> SolidColor(AppColors.Stroke)
        feedback == AnswerFeedback.Correct -> SolidColor(AppColors.Success)
        feedback == AnswerFeedback.Wrong -> SolidColor(AppColors.Danger)
        else -> AppColors.AccentGradient
    }
    Box(
        Modifier
            .graphicsLayer { if (isBlank) { scaleX = popScale; scaleY = popScale } }
            .defaultMinSize(minWidth = side, minHeight = side)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isBlank) Color.White.copy(alpha = 0.03f) else AppColors.Card)
            .border(if (isBlank) 2.dp else 1.dp, strokeBrush, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (isBlank) AppColors.TextPrimary else AppColors.TextSecondary,
            fontSize = fontSize, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif, maxLines = 1, softWrap = false,
        )
    }
}

private fun isEqOperator(s: String): Boolean =
    s in setOf("+", "-", "−", "×", "x", "*", "÷", "/", "=", "·", ">", "<", "→")

/** 정답 시 잠깐 나타나는 "정답!" 배지. */
@Composable
fun CorrectBadge() {
    Row(
        Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(AppColors.Success)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.correct), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun Keypad(
    canSubmit: Boolean,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
) {
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
    )
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { n -> DigitKey(n, Modifier.weight(1f), onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyBox(Modifier.weight(1f), onClick = onDelete) {
                Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            }
            DigitKey(0, Modifier.weight(1f), onDigit)
            Box(
                Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (canSubmit) AppColors.AccentGradient else SolidColor(Color.White.copy(alpha = 0.08f)))
                    .clickable(enabled = canSubmit) { onSubmit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun DigitKey(n: Int, modifier: Modifier, onDigit: (Int) -> Unit) {
    KeyBox(modifier, onClick = { onDigit(n) }) {
        Text(n.toString(), color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KeyBox(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .height(60.dp)
            .card(16.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun ChoicesGrid(choices: List<String>, enabled: Boolean = true, onPick: (String) -> Unit) {
    // 긴 텍스트(논리형 보기)는 폰트를 줄여 한 칸에 들어가게.
    val maxLen = choices.maxOfOrNull { it.length } ?: 1
    val fontSize = when { maxLen >= 12 -> 16.sp; maxLen >= 7 -> 20.sp; else -> 24.sp }
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .card(16.dp)
                            .clickable(enabled = enabled) { onPick(c) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            c, color = AppColors.TextPrimary, fontSize = fontSize,
                            fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 도형 보기 4개(2x2). 탭하면 그 도형의 code 를 제출한다. */
@Composable
fun FigureChoicesGrid(
    choices: List<com.jiny.catchtherule.core.model.Figure>,
    enabled: Boolean = true,
    onPick: (String) -> Unit,
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { fig ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                            .card(16.dp)
                            .clickable(enabled = enabled) { onPick(fig.code ?: "") },
                        contentAlignment = Alignment.Center,
                    ) {
                        FigureGlyph(figure = fig, size = 46.dp)
                    }
                }
            }
        }
    }
}
