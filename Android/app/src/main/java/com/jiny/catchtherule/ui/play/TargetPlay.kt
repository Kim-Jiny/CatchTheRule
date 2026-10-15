package com.jiny.catchtherule.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.TargetExpression
import com.jiny.catchtherule.core.TargetOp
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors

/** 숫자 만들기 플레이 상태(수식 + 미완성 피드백 틱). 퍼즐이 바뀌면 새로 만든다. */
class TargetUi(puzzle: Puzzle) {
    var expr by mutableStateOf(TargetExpression(puzzle.numbers.orEmpty()))
        private set
    var incompleteTick by mutableIntStateOf(0)   // 증가할 때마다 흔들림 + 안내
        private set

    private fun update(next: TargetExpression?): Boolean {
        if (next == null) return false
        expr = next
        return true
    }

    fun tapTile(i: Int) = update(expr.addNumber(i))
    fun tapOp(o: TargetOp) = update(expr.addOp(o))
    fun tapOpen() = update(expr.addOpen())
    fun tapClose() = update(expr.addClose())
    fun delete() { expr = expr.deleteLast() }
    fun reset() { expr = expr.clear() }
    fun markIncomplete() { incompleteTick++ }
}

private fun tileFontSize(value: Int, count: Int): TextUnit {
    val base = when (value.toString().length) { in 0..3 -> 26; 4 -> 23; 5 -> 20; 6 -> 18; else -> 16 }
    return (if (count >= 6) minOf(base, 22) else base).sp
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetPlayArea(puzzle: Puzzle, ui: TargetUi, solved: Boolean, feedback: AnswerFeedback?, onTile: (Int) -> Unit) {
    val expr = ui.expr
    val shake = remember { Animatable(0f) }
    var showIncomplete by remember { mutableStateOf(false) }
    suspend fun doShake() {
        for (x in listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(40))
    }
    LaunchedEffect(ui.incompleteTick) {
        if (ui.incompleteTick == 0) return@LaunchedEffect
        showIncomplete = true
        doShake()
        kotlinx.coroutines.delay(900)
        showIncomplete = false
    }
    LaunchedEffect(feedback) { if (feedback == AnswerFeedback.Wrong) doShake() }

    val value = expr.evaluate()
    val hit = value != null && value.isInteger && value.n == (puzzle.target ?: 0).toLong()
    val achieved = solved || feedback == AnswerFeedback.Correct
    val count = expr.numbers.size
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 목표 카드
        val goalColor = if (achieved) AppColors.Success else AppColors.Accent2
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(if (achieved) AppColors.Success.copy(alpha = 0.12f) else AppColors.Card)
                .border(if (achieved) 2.dp else 1.dp, if (achieved) AppColors.Success else AppColors.Stroke, RoundedCornerShape(20.dp))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.target_label), color = AppColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                (puzzle.target ?: 0).toString(), color = goalColor, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif, maxLines = 1, softWrap = false,
            )
        }

        // 수식 표시줄 + 실시간 결과 미리보기
        val exprStroke = when {
            feedback == AnswerFeedback.Wrong -> AppColors.Danger
            achieved -> AppColors.Success
            else -> AppColors.Stroke
        }
        Column(
            Modifier.fillMaxWidth().offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                .clip(RoundedCornerShape(18.dp)).background(AppColors.Card)
                .border(if (exprStroke == AppColors.Stroke) 1.dp else 2.dp, exprStroke, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (expr.tokens.isEmpty()) {
                Text(stringResource(R.string.target_expression_hint), color = AppColors.TextTertiary, fontSize = 15.sp, modifier = Modifier.defaultMinSize(minHeight = 34.dp))
            } else {
                Text(
                    expr.displayText, color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif, lineHeight = 34.sp,
                )
            }
            Text(
                if (value != null) "= $value" else if (expr.tokens.isEmpty()) "" else "= ?",
                color = if (hit) AppColors.Success else AppColors.TextSecondary,
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif,
                modifier = Modifier.defaultMinSize(minHeight = 26.dp),
            )
            if (showIncomplete) {
                Text(stringResource(R.string.target_incomplete), color = AppColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 재료 타일(사용된 타일은 흐리게, 탭 불가)
        val used = expr.usedTiles
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            expr.numbers.forEachIndexed { i, v ->
                val isUsed = i in used
                Box(
                    Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isUsed) AppColors.Card.copy(alpha = 0.4f) else AppColors.Card)
                        .border(1.dp, if (isUsed) AppColors.Stroke else AppColors.Accent2, RoundedCornerShape(14.dp))
                        // 문법상 못 넣는 자리여도 탭은 받아 거부 햅틱을 줄 수 있게 isUsed 만 비활성
                        .clickable(enabled = !achieved && !isUsed) { onTile(i) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        v.toString(), color = if (isUsed) AppColors.TextTertiary else AppColors.TextPrimary,
                        fontSize = tileFontSize(v, count), fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 하단 입력 패드: 연산자 / 괄호·지우기·처음부터 / 확인. 문법상 불가한 키는 흐리게 + onReject(햅틱). */
@Composable
fun TargetControls(ui: TargetUi, target: Int, solved: Boolean, onReject: () -> Unit, onCheck: () -> Unit) {
    val e = ui.expr
    val value = e.evaluate()
    val ready = value != null && !solved
    val hit = value != null && value.isInteger && value.n == target.toLong()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetOp.entries.forEach { o ->
                PadKey(Modifier.weight(1f), o.symbol, null, e.canAddOp() && !solved, onReject) { ui.tapOp(o) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadKey(Modifier.weight(1f), "(", null, e.canAddOpen() && !solved, onReject) { ui.tapOpen() }
            PadKey(Modifier.weight(1f), ")", null, e.canAddClose() && !solved, onReject) { ui.tapClose() }
            PadKey(Modifier.weight(1f), null, Icons.AutoMirrored.Filled.Backspace, e.tokens.isNotEmpty() && !solved, {},
                desc = stringResource(R.string.target_delete)) { ui.delete() }
            PadKey(Modifier.weight(1f), null, Icons.Filled.Refresh, e.tokens.isNotEmpty() && !solved, {},
                desc = stringResource(R.string.target_reset)) { ui.reset() }
        }
        val bg = when { !ready -> AppColors.Card; hit -> AppColors.Success; else -> AppColors.Accent }
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                .border(1.dp, AppColors.Stroke, RoundedCornerShape(16.dp))
                .clickable(enabled = !solved) { onCheck() },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.target_check), color = if (ready) Color.White else AppColors.TextTertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PadKey(
    modifier: Modifier, label: String?, icon: ImageVector?, enabled: Boolean, onReject: () -> Unit,
    desc: String? = null, onClick: () -> Unit,
) {
    val tint = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) AppColors.Card else AppColors.Card.copy(alpha = 0.4f))
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(14.dp))
            .clickable { if (enabled) onClick() else onReject() },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
        else Text(label ?: "", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
