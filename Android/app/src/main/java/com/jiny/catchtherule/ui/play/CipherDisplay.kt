package com.jiny.catchtherule.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.card
import com.jiny.catchtherule.ui.theme.modeColor
import kotlin.math.roundToInt

/** 정답/오답 시 입력 슬롯에 쓰는 색. */
internal fun answerSlotColor(feedback: AnswerFeedback?, base: Color): Color = when (feedback) {
    AnswerFeedback.Correct -> AppColors.Success
    AnswerFeedback.Wrong -> AppColors.Danger
    null -> base
}

/**
 * 암호 해독 디스플레이: 예시 행 `[이모지들] → [숫자]` 목록 + 마지막 query 행 `[이모지들] → [입력값 or ?]`.
 */
@Composable
fun CipherDisplay(
    puzzle: Puzzle,
    modifier: Modifier = Modifier,
    typed: String = "",
    feedback: AnswerFeedback? = null,
    reveal: Boolean = false,
) {
    val pairs = puzzle.pairs ?: return
    val query = puzzle.query ?: return
    val color = modeColor("cipher")
    val summary = pairs.joinToString("; ") { "${it.getOrNull(0).orEmpty().joinToString(" ")} → ${it.getOrNull(1).orEmpty().joinToString("")}" }
    Column(
        modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 16.dp)
            .semantics { contentDescription = "$summary; ${query.joinToString(" ")} → ?" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        pairs.forEach { pair ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    pair.getOrNull(0).orEmpty().joinToString(""), fontSize = (if ((pair.getOrNull(0)?.size ?: 0) > 3) 22 else 26).sp, maxLines = 1, softWrap = false,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1.4f),
                )
                Text("→", color = AppColors.TextTertiary, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Text(
                    pair.getOrNull(1).orEmpty().joinToString(""), color = AppColors.TextSecondary, fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                    textAlign = TextAlign.Start, modifier = Modifier.weight(1f),
                )
            }
        }
        // query 행: 모드색 테두리 + 글로우
        val shake = remember { Animatable(0f) }
        LaunchedEffect(feedback) {
            if (feedback == AnswerFeedback.Wrong) {
                for (x in listOf(-12f, 12f, -9f, 9f, -5f, 5f, 0f)) shake.animateTo(x, tween(40))
            }
        }
        val tint = answerSlotColor(feedback, color)
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier.padding(top = 6.dp).fillMaxWidth()
                .offset { IntOffset(shake.value.roundToInt(), 0) }
                .shadow(10.dp, shape, ambientColor = tint, spotColor = tint)
                .clip(shape).background(AppColors.Card).border(1.5.dp, tint.copy(alpha = 0.85f), shape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                query.joinToString(""), fontSize = (if (query.size > 3) 24 else 30).sp, maxLines = 1, softWrap = false,
                textAlign = TextAlign.End, modifier = Modifier.weight(1.4f),
            )
            Text("→", color = tint, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val text = when {
                    reveal -> puzzle.answer
                    typed.isEmpty() -> "?"
                    else -> typed
                }
                Text(
                    text, color = if (text == "?") tint.copy(alpha = 0.7f) else tint, fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    modifier = Modifier.widthIn(min = 24.dp),
                )
            }
        }
    }
}
