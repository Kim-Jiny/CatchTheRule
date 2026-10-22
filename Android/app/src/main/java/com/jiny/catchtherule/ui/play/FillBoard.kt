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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.modeColor
import kotlin.math.min
import kotlin.math.sqrt

/** 격자 채우기 플레이 상태(docs/fill-path-mode-spec.md). 퍼즐이 바뀌면 새로 만든다(remember key = puzzle.id). */
class FillUi(puzzle: Puzzle) {
    val n: Int = puzzle.board?.size ?: 0
    /** 고정 칸 값(0 = 빈칸), 행 우선. */
    val fixed: List<Int> = puzzle.board.orEmpty().flatten()
    /** 현재 값(고정 칸 포함), 행 우선. */
    val values = mutableStateListOf<Int>().also { it.addAll(fixed) }
    var selected by mutableIntStateOf(-1)
    var invalidTick by mutableIntStateOf(0)
        private set
    var invalidShown by mutableStateOf(false)
        private set

    private val rules = puzzle.rules.orEmpty().toSet()
    private val lt = puzzle.lt.orEmpty().filter { it.size == 4 }
    private val cages = puzzle.cages.orEmpty().filter { it.size >= 3 }

    val blankCount: Int = fixed.count { it == 0 }
    val isComplete: Boolean get() = fixed.indices.all { values[it] != 0 }

    /** 상자 크기(높이, 너비): N=4 → 2×2, N=6 → 2×3. 그 외는 상자 없음. */
    private val box: Pair<Int, Int>? = when (n) { 4 -> 2 to 2; 6 -> 2 to 3; else -> null }
    val hasBox: Boolean = "box" in rules && box != null
    val hasDiag: Boolean = "diag" in rules

    fun isDiag(i: Int): Boolean = hasDiag && (i / n == i % n || i / n + i % n == n - 1)

    /** 케이지별 칸 인덱스(행 우선) 목록과 목표 합. */
    val cageList: List<Pair<Int, List<Int>>> = cages.map { c ->
        c[0] to (1 until c.size step 2).filter { it + 1 < c.size }.map { c[it] * n + c[it + 1] }
    }

    /** lt 항목: (작은 칸, 큰 칸) 인덱스. */
    val ltList: List<Pair<Int, Int>> = lt.map { (it[0] * n + it[1]) to (it[2] * n + it[3]) }

    fun tap(i: Int) { if (fixed[i] == 0) selected = i }
    fun input(d: Int) { if (selected >= 0 && fixed[selected] == 0) values[selected] = d }
    fun clear() { if (selected >= 0 && fixed[selected] == 0) values[selected] = 0 }
    fun flashInvalid() { invalidShown = true; invalidTick++ }
    fun clearInvalid() { invalidShown = false }

    /** 규칙을 어기는 칸 집합(채워진 값끼리 충돌한 경우만 — 정답을 직접 알려 주지는 않는다). */
    fun conflicts(): Set<Int> {
        val bad = HashSet<Int>()
        fun checkGroup(g: List<Int>) {
            val seen = HashMap<Int, Int>()
            for (i in g) {
                val v = values[i]
                if (v == 0) continue
                seen.getOrPut(v) { 0 }
                seen[v] = seen[v]!! + 1
            }
            for (i in g) if (values[i] != 0 && (seen[values[i]] ?: 0) > 1) bad.add(i)
        }
        for (r in 0 until n) checkGroup((0 until n).map { r * n + it })
        for (c in 0 until n) checkGroup((0 until n).map { it * n + c })
        if (hasBox) {
            val (bh, bw) = box!!
            for (br in 0 until n / bh) for (bc in 0 until n / bw) {
                checkGroup((0 until bh).flatMap { dr -> (0 until bw).map { dc -> (br * bh + dr) * n + bc * bw + dc } })
            }
        }
        if (hasDiag) {
            checkGroup((0 until n).map { it * n + it })
            checkGroup((0 until n).map { it * n + (n - 1 - it) })
        }
        for ((a, b) in ltList) {
            if (values[a] != 0 && values[b] != 0 && values[a] >= values[b]) { bad.add(a); bad.add(b) }
        }
        for ((sum, cells) in cageList) {
            val vs = cells.map { values[it] }
            val filled = vs.filter { it != 0 }
            val total = filled.sum()
            val dup = filled.size != filled.toSet().size
            if (dup || total > sum || (filled.size == cells.size && total != sum)) bad.addAll(cells)
        }
        return bad
    }

    /** 제출 값: 맞으면 answer, 틀리면 answer 와 절대 같지 않은 "x"+현재 상태. */
    fun submitValue(puzzle: Puzzle): String {
        val s = fixed.indices.filter { fixed[it] == 0 }.joinToString("") { values[it].toString() }
        return if (s == puzzle.answer) puzzle.answer else "x$s"
    }
}

private val GRID_MAX = 340.dp

@Composable
fun FillPlayArea(ui: FillUi, solved: Boolean, feedback: AnswerFeedback?) {
    val mode = modeColor("fill")
    val shake = remember { Animatable(0f) }
    suspend fun doShake() {
        for (x in listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f)) shake.animateTo(x, tween(40))
    }
    LaunchedEffect(ui.invalidTick) {
        if (ui.invalidTick == 0) return@LaunchedEffect
        doShake()
        kotlinx.coroutines.delay(1400)
        ui.clearInvalid()
    }
    LaunchedEffect(feedback) { if (feedback == AnswerFeedback.Wrong) doShake() }

    val achieved = solved || feedback == AnswerFeedback.Correct
    val bad = ui.conflicts()
    val n = ui.n
    val stroke = when {
        feedback == AnswerFeedback.Wrong -> AppColors.Danger
        achieved -> AppColors.Success
        else -> AppColors.Stroke
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 규칙 위반 안내(자리를 항상 차지해 레이아웃이 튀지 않게 투명 처리)
        Text(
            stringResource(R.string.fill_invalid),
            color = AppColors.Danger.copy(alpha = if (ui.invalidShown) 1f else 0f),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val pad = 10.dp
            val gridDp = min((maxWidth - pad * 2).value, GRID_MAX.value).dp
            val cellDp = gridDp / n
            Box(
                Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                    .clip(RoundedCornerShape(18.dp)).background(AppColors.Card)
                    .border(if (stroke == AppColors.Stroke) 1.dp else 2.dp, stroke, RoundedCornerShape(18.dp))
                    .padding(pad)
                    .semantics { contentDescription = "${n}x$n, ${ui.blankCount}" },
            ) {
                Box(Modifier.size(gridDp)) {
                    Column {
                        for (r in 0 until n) Row {
                            for (c in 0 until n) {
                                val i = r * n + c
                                val isFixed = ui.fixed[i] != 0
                                val sel = ui.selected == i && !achieved
                                val isBad = i in bad
                                val bg = when {
                                    isBad -> AppColors.Danger.copy(alpha = 0.22f)
                                    sel -> mode.copy(alpha = 0.22f)
                                    isFixed -> Color.White.copy(alpha = 0.07f)
                                    ui.isDiag(i) -> mode.copy(alpha = 0.10f)
                                    else -> Color.Transparent
                                }
                                Box(
                                    Modifier.size(cellDp)
                                        .background(bg)
                                        .then(if (sel) Modifier.border(2.dp, mode, RoundedCornerShape(4.dp)) else Modifier)
                                        .clickable(enabled = !isFixed && !achieved) { ui.tap(i) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val v = ui.values[i]
                                    if (v != 0) {
                                        Text(
                                            v.toString(),
                                            color = when { isBad -> AppColors.Danger; isFixed -> AppColors.TextPrimary; else -> mode },
                                            fontSize = (cellDp.value * 0.5f).sp,
                                            fontWeight = if (isFixed) FontWeight.Bold else FontWeight.ExtraBold,
                                        )
                                    }
                                    // 케이지 합: 케이지의 첫 칸(행 우선으로 가장 앞) 좌상단.
                                    val cage = ui.cageList.firstOrNull { it.second.minOrNull() == i }
                                    if (cage != null) {
                                        Text(
                                            cage.first.toString(),
                                            color = AppColors.TextSecondary, fontSize = (cellDp.value * 0.25f).sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 선·케이지 윤곽·부등호는 위에 얹는다(터치는 통과).
                    val lineThin = AppColors.Stroke
                    val lineThick = AppColors.TextTertiary
                    val chip = AppColors.Card
                    val chev = AppColors.TextPrimary.copy(alpha = 0.85f)
                    val cageColor = mode.copy(alpha = 0.9f)
                    Canvas(Modifier.matchParentSize()) {
                        val cell = size.width / n
                        val thin = 1.dp.toPx()
                        val thick = 2.5.dp.toPx()
                        for (k in 0..n) {
                            val p = k * cell
                            // 6×6(2행×3열 상자): 가로선은 2칸마다, 세로선은 3칸마다 굵게.
                            val hThick = k == 0 || k == n || (ui.hasBox && when (n) { 4 -> k == 2; 6 -> k % 2 == 0; else -> false })
                            val vThick = k == 0 || k == n || (ui.hasBox && when (n) { 4 -> k == 2; 6 -> k == 3; else -> false })
                            drawLine(if (hThick) lineThick else lineThin, Offset(0f, p), Offset(size.width, p), if (hThick) thick else thin)
                            drawLine(if (vThick) lineThick else lineThin, Offset(p, 0f), Offset(p, size.height), if (vThick) thick else thin)
                        }
                        // 케이지 점선 윤곽
                        val inset = 4.dp.toPx()
                        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                        for ((_, cells) in ui.cageList) {
                            val set = cells.toSet()
                            for (i in cells) {
                                val r = i / n; val c = i % n
                                val x0 = c * cell; val y0 = r * cell
                                if (r == 0 || (i - n) !in set) drawLine(cageColor, Offset(x0 + inset, y0 + inset), Offset(x0 + cell - inset, y0 + inset), 1.5.dp.toPx(), pathEffect = dash)
                                if (r == n - 1 || (i + n) !in set) drawLine(cageColor, Offset(x0 + inset, y0 + cell - inset), Offset(x0 + cell - inset, y0 + cell - inset), 1.5.dp.toPx(), pathEffect = dash)
                                if (c == 0 || (i - 1) !in set) drawLine(cageColor, Offset(x0 + inset, y0 + inset), Offset(x0 + inset, y0 + cell - inset), 1.5.dp.toPx(), pathEffect = dash)
                                if (c == n - 1 || (i + 1) !in set) drawLine(cageColor, Offset(x0 + cell - inset, y0 + inset), Offset(x0 + cell - inset, y0 + cell - inset), 1.5.dp.toPx(), pathEffect = dash)
                            }
                        }
                        // 부등호: 두 칸 경계에 작은 쪽을 가리키는 화살꺾쇠.
                        val s = cell * 0.09f
                        for ((a, b) in ui.ltList) {
                            val ca = Offset((a % n + 0.5f) * cell, (a / n + 0.5f) * cell)
                            val cb = Offset((b % n + 0.5f) * cell, (b / n + 0.5f) * cell)
                            val mid = Offset((ca.x + cb.x) / 2, (ca.y + cb.y) / 2)
                            val dx = ca.x - cb.x; val dy = ca.y - cb.y
                            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            val d = Offset(dx / len, dy / len)       // 작은 칸 쪽 방향
                            val perp = Offset(-d.y, d.x)
                            drawCircle(chip, radius = s * 2.1f, center = mid)
                            val tip = Offset(mid.x + d.x * s, mid.y + d.y * s)
                            val back = Offset(mid.x - d.x * s, mid.y - d.y * s)
                            val w = 2.dp.toPx()
                            drawLine(chev, tip, Offset(back.x + perp.x * s * 1.3f, back.y + perp.y * s * 1.3f), w, StrokeCap.Round)
                            drawLine(chev, tip, Offset(back.x - perp.x * s * 1.3f, back.y - perp.y * s * 1.3f), w, StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}

/** 숫자 패드(1..N) + 지우기 + 확인. */
@Composable
fun FillControls(ui: FillUi, solved: Boolean, onCheck: () -> Unit) {
    val mode = modeColor("fill")
    val canInput = ui.selected >= 0 && !solved
    val canCheck = ui.isComplete && !solved
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 1..ui.n) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (canInput) mode.copy(alpha = 0.16f) else AppColors.Card.copy(alpha = 0.4f))
                        .border(1.dp, if (canInput) mode.copy(alpha = 0.5f) else AppColors.Stroke, RoundedCornerShape(12.dp))
                        .clickable(enabled = canInput) { ui.input(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d.toString(), color = if (canInput) AppColors.TextPrimary else AppColors.TextTertiary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (canInput) AppColors.Card else AppColors.Card.copy(alpha = 0.4f))
                    .border(1.dp, AppColors.Stroke, RoundedCornerShape(16.dp))
                    .clickable(enabled = canInput) { ui.clear() },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.fill_clear), color = if (canInput) AppColors.TextPrimary else AppColors.TextTertiary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            BoardCheckButton(Modifier.weight(2f), stringResource(R.string.fill_check), canCheck, onCheck)
        }
    }
}

/** 확인 버튼(격자 채우기·경로 추적 공용): 활성이면 강조색, 아니면 카드색. */
@Composable
fun BoardCheckButton(modifier: Modifier, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) AppColors.Accent else AppColors.Card)
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else AppColors.TextTertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
