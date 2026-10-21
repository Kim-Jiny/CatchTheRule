package com.jiny.catchtherule.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.card
import com.jiny.catchtherule.ui.theme.modeColor
import kotlin.math.roundToInt

private fun isWeight(token: String) = token.isNotEmpty() && token.all { it.isDigit() }

/** 토큰 하나: 숫자면 회색 원형 추 배지, 아니면 이모지. */
@Composable
private fun ScaleToken(token: String, emojiSize: Int = 28) {
    if (isWeight(token)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF8A8FA3))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(token, color = Color.White, fontSize = if (token.length > 2) 10.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    } else {
        Text(token, fontSize = emojiSize.sp, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pan(tokens: List<String>, tint: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape).background(tint.copy(alpha = 0.10f)).border(1.dp, tint.copy(alpha = 0.45f), shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { tokens.forEach { ScaleToken(it) } }
    }
}

/** 평형 저울 한 개: 위에 두 접시, 아래에 수평 빔과 가운데 받침대. */
@Composable
private fun BalanceScale(left: List<String>, right: List<String>, tint: Color) {
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = "${left.joinToString(" ")} = ${right.joinToString(" ")}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pan(left, tint, Modifier.weight(1f).fillMaxHeight())
            Pan(right, tint, Modifier.weight(1f).fillMaxHeight())
        }
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val beamH = 4.dp.toPx()
            val cx = size.width / 2f
            // 받침대(삼각형) + 빔 + 중심축
            val stand = Path().apply {
                moveTo(cx, beamH)
                lineTo(cx - 12.dp.toPx(), size.height)
                lineTo(cx + 12.dp.toPx(), size.height)
                close()
            }
            drawPath(stand, tint.copy(alpha = 0.5f))
            drawRoundRect(tint.copy(alpha = 0.25f), Offset(0f, 0f), Size(size.width, beamH + 4.dp.toPx()), CornerRadius(beamH))
            drawRoundRect(tint, Offset(0f, 2.dp.toPx()), Size(size.width, beamH), CornerRadius(beamH))
            drawCircle(Color.White, 4.dp.toPx(), Offset(cx, 2.dp.toPx() + beamH / 2f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * 저울 추리 디스플레이: 평형 저울 목록 + 마지막 query 카드 `[토큰들] = [입력값 or ?]`.
 */
@Composable
fun ScaleDisplay(
    puzzle: Puzzle,
    modifier: Modifier = Modifier,
    typed: String = "",
    feedback: AnswerFeedback? = null,
    reveal: Boolean = false,
) {
    val scales = puzzle.scales ?: return
    val query = puzzle.query ?: return
    val color = modeColor("scale")
    Column(
        modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val shake = remember { Animatable(0f) }
        LaunchedEffect(feedback) {
            if (feedback == AnswerFeedback.Wrong) {
                for (x in listOf(-12f, 12f, -9f, 9f, -5f, 5f, 0f)) shake.animateTo(x, tween(40))
            }
        }
        val tint = answerSlotColor(feedback, color)
        val shape = RoundedCornerShape(16.dp)
        FlowRow(
            Modifier.padding(top = 4.dp).fillMaxWidth()
                .offset { IntOffset(shake.value.roundToInt(), 0) }
                .shadow(10.dp, shape, ambientColor = tint, spotColor = tint)
                .clip(shape).background(AppColors.Card).border(1.5.dp, tint.copy(alpha = 0.85f), shape)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics { contentDescription = "${query.joinToString(" ")} = ?" },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            query.forEach { ScaleToken(it, 30) }
            Text("=", color = tint, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
        // 질문을 맨 위에 두고 저울은 그 아래 — 저울이 많아 카드가 길어도 질문이 키패드에 가리지 않는다.
        scales.forEach { s ->
            BalanceScale(s.getOrNull(0).orEmpty(), s.getOrNull(1).orEmpty(), color)
        }
    }
}
