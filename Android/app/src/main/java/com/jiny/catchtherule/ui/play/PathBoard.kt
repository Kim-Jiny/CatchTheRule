package com.jiny.catchtherule.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.modeColor
import kotlin.math.min
import kotlin.math.sqrt

/** 경로 추적 플레이 상태. 퍼즐이 바뀌면 새로 만든다(remember key = puzzle.id). */
class PathUi(puzzle: Puzzle) {
    val size: Int = puzzle.size ?: 0
    val trail: List<Pair<Int, Int>> = puzzle.trail.orEmpty().filter { it.size >= 2 }.map { it[0] to it[1] }
    val total: Int = puzzle.total ?: 0
    /** 선택한 칸(행 우선 0-기반 인덱스), 없으면 -1. */
    var selected by mutableIntStateOf(-1)

    fun tap(i: Int) { selected = if (selected == i) -1 else i }

    /** 제출 값: 선택 칸의 1-기반 행 우선 번호. answer 와 같을 때만 정답이다. */
    fun submitValue(): String = (selected + 1).toString()
}

private val PATH_GRID_MAX = 340.dp

@Composable
fun PathPlayArea(ui: PathUi, solved: Boolean, feedback: AnswerFeedback?) {
    val mode = modeColor("path")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(feedback) {
        if (feedback == AnswerFeedback.Wrong) {
            for (x in listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f)) shake.animateTo(x, tween(40))
        }
    }
    val achieved = solved || feedback == AnswerFeedback.Correct
    val n = ui.size
    val stroke = when {
        feedback == AnswerFeedback.Wrong -> AppColors.Danger
        achieved -> AppColors.Success
        else -> AppColors.Stroke
    }
    val last = ui.trail.size - 1

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 총 이동 횟수 배지(언어 중립: 아이콘 + 숫자)
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(mode.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Route, null, tint = mode, modifier = Modifier.size(16.dp))
            Text("×${ui.total}", color = mode, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val pad = 10.dp
            val gridDp = min((maxWidth - pad * 2).value, PATH_GRID_MAX.value).dp
            val cellDp = gridDp / n
            val start = ui.trail.firstOrNull()
            Box(
                Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                    .clip(RoundedCornerShape(18.dp)).background(AppColors.Card)
                    .border(if (stroke == AppColors.Stroke) 1.dp else 2.dp, stroke, RoundedCornerShape(18.dp))
                    .padding(pad)
                    .semantics {
                        contentDescription = "${n}x$n, ${(start?.first ?: 0) + 1},${(start?.second ?: 0) + 1}, $last, ${ui.total}"
                    },
            ) {
                Box(Modifier.size(gridDp)) {
                    Column {
                        for (r in 0 until n) Row {
                            for (c in 0 until n) {
                                val i = r * n + c
                                val sel = ui.selected == i && !achieved
                                Box(
                                    Modifier.size(cellDp).padding(1.dp)
                                        .then(if (sel) Modifier.shadow(8.dp, RoundedCornerShape(6.dp), ambientColor = mode, spotColor = mode) else Modifier)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) mode.copy(alpha = 0.45f) else AppColors.Stroke.copy(alpha = 0.18f))
                                        .then(if (sel) Modifier.border(2.dp, mode, RoundedCornerShape(6.dp)) else Modifier)
                                        .clickable(enabled = !achieved) { ui.tap(i) },
                                )
                            }
                        }
                    }
                    // 이동 선 + 화살촉(터치는 통과)
                    Canvas(Modifier.matchParentSize()) {
                        val cell = size.width / n
                        val rad = cell * 0.31f
                        val w = 2.dp.toPx()
                        fun center(p: Pair<Int, Int>) = Offset((p.second + 0.5f) * cell, (p.first + 0.5f) * cell)
                        for (k in 0 until last) {
                            val a = center(ui.trail[k]); val b = center(ui.trail[k + 1])
                            val dx = b.x - a.x; val dy = b.y - a.y
                            val len = sqrt(dx * dx + dy * dy)
                            if (len < rad * 2.2f) continue
                            val ux = dx / len; val uy = dy / len
                            val p0 = Offset(a.x + ux * rad, a.y + uy * rad)
                            val p1 = Offset(b.x - ux * rad, b.y - uy * rad)
                            drawLine(mode.copy(alpha = 0.85f), p0, p1, w, StrokeCap.Round)
                            val h = cell * 0.16f
                            val px = -uy; val py = ux
                            drawLine(mode, p1, Offset(p1.x - ux * h + px * h * 0.7f, p1.y - uy * h + py * h * 0.7f), w, StrokeCap.Round)
                            drawLine(mode, p1, Offset(p1.x - ux * h - px * h * 0.7f, p1.y - uy * h - py * h * 0.7f), w, StrokeCap.Round)
                        }
                    }
                    // 자취 점: 같은 칸을 다시 지나면 번호를 묶어서 한 점에 표시.
                    val byCell = ui.trail.withIndex().groupBy({ it.value }, { it.index })
                    val dot = cellDp * 0.62f
                    for ((cellPos, idxs) in byCell) {
                        val isStart = 0 in idxs
                        val isLast = last in idxs
                        Box(
                            Modifier.offset(cellDp * cellPos.second + (cellDp - dot) / 2, cellDp * cellPos.first + (cellDp - dot) / 2)
                                .size(dot).clip(CircleShape)
                                .background(if (isLast) mode else AppColors.Card)
                                .border(if (isStart) 3.dp else 2.dp, if (isStart && !isLast) AppColors.Star else mode, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                idxs.joinToString(","),
                                color = if (isLast) Color.White else AppColors.TextPrimary,
                                fontSize = (dot.value * (if (idxs.size > 1) 0.34f else 0.5f)).sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PathControls(ui: PathUi, solved: Boolean, onCheck: () -> Unit) {
    BoardCheckButton(Modifier.fillMaxWidth(), androidx.compose.ui.res.stringResource(com.jiny.catchtherule.R.string.path_check), ui.selected >= 0 && !solved, onCheck)
}
