package com.jiny.catchtherule.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.card

enum class AnswerFeedback { Correct, Wrong }

/** 수열을 카드 셀로 표시. 빈칸은 그라데이션 테두리로 강조. */
@Composable
fun SequenceDisplay(
    puzzle: Puzzle,
    typed: String = "",
    reveal: Boolean = false,
    feedback: AnswerFeedback? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        puzzle.tokens.forEachIndexed { _, value ->
            val isBlank = value == null
            val blankText = when {
                reveal -> puzzle.answer
                typed.isEmpty() -> "?"
                else -> typed
            }
            val strokeBrush: Brush = when {
                !isBlank -> SolidColor(AppColors.Stroke)
                feedback == AnswerFeedback.Correct -> SolidColor(AppColors.Success)
                feedback == AnswerFeedback.Wrong -> SolidColor(AppColors.Danger)
                else -> AppColors.AccentGradient
            }
            Box(
                Modifier
                    .defaultMinSize(minWidth = 64.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isBlank) Color.White.copy(alpha = 0.03f) else AppColors.Card)
                    .border(if (isBlank) 2.dp else 1.dp, strokeBrush, RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isBlank) blankText else (value ?: ""),
                    color = if (isBlank) AppColors.TextPrimary else AppColors.TextSecondary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
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
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .card(16.dp)
                            .clickable(enabled = enabled) { onPick(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c, color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
