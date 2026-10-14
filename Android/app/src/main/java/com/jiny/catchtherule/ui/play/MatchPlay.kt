package com.jiny.catchtherule.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.DropResult
import com.jiny.catchtherule.core.MatchBoard
import com.jiny.catchtherule.core.MatchGame
import com.jiny.catchtherule.core.MatchRules
import com.jiny.catchtherule.core.MatchSlot
import com.jiny.catchtherule.core.MatchStatus
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.ui.theme.AppColors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class MatchMessage { Invalid, Limit }

/** 성냥개비 퍼즐 플레이 상태. 퍼즐이 바뀌면 새로 만든다(remember key = puzzle.id). */
class MatchUi(puzzle: Puzzle) {
    val game = MatchGame(MatchBoard(puzzle.equation.orEmpty(), puzzle.moves ?: 1))
    /** game 상태가 바뀔 때마다 증가 — 컴포저블이 읽어 재구성/재그리기를 유발. */
    var version by mutableIntStateOf(0)
        private set
    var heldPos by mutableStateOf(Offset.Zero)
    var message by mutableStateOf<MatchMessage?>(null)
        private set
    var messageTick by mutableIntStateOf(0)
        private set

    fun pick(slot: MatchSlot, pos: Offset): Boolean {
        val ok = game.pick(slot)
        if (ok) { heldPos = pos; version++ }
        return ok
    }

    fun drop(target: MatchSlot?): DropResult {
        val r = game.drop(target)
        if (r == DropResult.Rejected) flash(MatchMessage.Limit)
        version++
        return r
    }

    fun undo() { game.undo(); version++ }
    fun reset() { game.reset(); version++ }
    fun flash(m: MatchMessage) { message = m; messageTick++ }
    fun clearMessage() { message = null }
}

// ───────────────────────── 기하(슬롯 → 막대 좌표) ─────────────────────────

/** 캔버스 좌표계의 막대(중심, 길이, 세로 여부). */
private class Bar(val cx: Float, val cy: Float, val len: Float, val vertical: Boolean) {
    val x1 get() = if (vertical) cx else cx - len / 2
    val y1 get() = if (vertical) cy - len / 2 else cy
    val x2 get() = if (vertical) cx else cx + len / 2
    val y2 get() = if (vertical) cy + len / 2 else cy
}

private class MatchGeometry(val board: MatchBoard, availW: Float, density: Float) {
    val wd: Float
    val wo: Float
    val gap: Float
    val h: Float
    val t: Float
    val padV: Float = 30f * density
    val startX: Float
    private val cellX = FloatArray(board.cellCount)

    init {
        val nd = board.isDigit.count { it }
        val no = board.cellCount - nd
        val units = nd + 0.7f * no + 0.22f * (board.cellCount - 1)
        wd = min(availW / units, 64f * density)
        wo = wd * 0.7f
        gap = wd * 0.22f
        h = wd * 1.9f
        t = wd * 0.17f
        val total = wd * units
        startX = (availW - total) / 2f
        var x = startX
        for (c in 0 until board.cellCount) {
            cellX[c] = x
            x += cellWidth(c) + gap
        }
    }

    val canvasHeight get() = h + 2 * padV
    fun cellWidth(c: Int) = if (board.isDigit[c]) wd else wo
    fun cellLeft(c: Int) = cellX[c]

    fun bar(c: Int, slot: Int, masks: List<Int>): Bar {
        val x0 = cellX[c]
        val y0 = padV
        if (board.isDigit[c]) {
            val xl = x0 + t * 0.75f
            val xr = x0 + wd - t * 0.75f
            val yT = y0 + t * 0.75f
            val yM = y0 + h / 2
            val yB = y0 + h - t * 0.75f
            val hl = (xr - xl) - 1.2f * t
            val vl = (yM - yT) - 1.2f * t
            val cxm = (xl + xr) / 2
            return when (slot) {
                0 -> Bar(cxm, yT, hl, false)                  // a
                1 -> Bar(xr, (yT + yM) / 2, vl, true)         // b
                2 -> Bar(xr, (yM + yB) / 2, vl, true)         // c
                3 -> Bar(cxm, yB, hl, false)                  // d
                4 -> Bar(xl, (yM + yB) / 2, vl, true)         // e
                5 -> Bar(xl, (yT + yM) / 2, vl, true)         // f
                else -> Bar(cxm, yM, hl, false)               // g
            }
        }
        // 연산자 셀: 그려지는 위치는 현재 글리프에 따라 달라진다(논리 슬롯은 불변).
        // 십자(+)/마이너스(-)는 가로 막대를 세로 중앙에, 이퀄(=)과 고스트만 ±δ.
        val cxm = x0 + wo / 2
        val yM = y0 + h / 2
        val hLen = wd * 0.54f
        val vLen = wd * 0.62f
        val m = masks[c]
        val occupied = m and (1 shl slot) != 0
        val bothH = (m and MatchRules.HU != 0) && (m and MatchRules.HL != 0)
        val delta = t * 0.85f
        val dy = when {
            slot == 2 -> 0f
            !occupied -> if (slot == 0) -delta else delta
            bothH && m and MatchRules.V == 0 -> if (slot == 0) -delta else delta
            else -> 0f
        }
        return if (slot == 2) Bar(cxm, yM, vLen, true) else Bar(cxm, yM + dy, hLen, false)
    }

    private fun segDist(p: Offset, b: Bar): Float {
        val dx = b.x2 - b.x1
        val dy = b.y2 - b.y1
        val l2 = dx * dx + dy * dy
        val u = if (l2 == 0f) 0f else (((p.x - b.x1) * dx + (p.y - b.y1) * dy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (b.x1 + u * dx), p.y - (b.y1 + u * dy))
    }

    /** 조건을 만족하는 슬롯 중 p 에 가장 가까운(반경 이내) 것. 선분 거리 우선, 중심 거리 보조. */
    fun nearest(p: Offset, radius: Float, masks: List<Int>, accept: (MatchSlot) -> Boolean): MatchSlot? {
        var best: MatchSlot? = null
        var bestScore = Float.MAX_VALUE
        for (c in 0 until board.cellCount) {
            for (s in 0 until board.slotCount(c)) {
                val slot = MatchSlot(c, s)
                if (!accept(slot)) continue
                val b = bar(c, s, masks)
                val d = segDist(p, b)
                if (d > radius) continue
                val score = d + 0.25f * hypot(p.x - b.cx, p.y - b.cy)
                if (score < bestScore) { bestScore = score; best = slot }
            }
        }
        return best
    }
}

private val StickBody = Color(0xFFEBD9B4)
private val StickHead = Color(0xFFE5484D)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPill(b: Bar, thick: Float, color: Color, alpha: Float = 1f) {
    val w = if (b.vertical) thick else b.len
    val hh = if (b.vertical) b.len else thick
    drawRoundRect(
        color = color, alpha = alpha,
        topLeft = Offset(b.cx - w / 2, b.cy - hh / 2), size = Size(w, hh),
        cornerRadius = CornerRadius(thick / 2, thick / 2),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStick(b: Bar, thick: Float, body: Color, head: Color) {
    drawPill(b, thick, body)
    val r = thick * 0.62f
    val c = if (b.vertical) Offset(b.cx, b.y1 + thick * 0.35f) else Offset(b.x1 + thick * 0.35f, b.cy)
    drawCircle(head, radius = r, center = c)
}

private const val PICK_RADIUS_DP = 24f
private const val DROP_RADIUS_DP = 44f

@Composable
fun MatchPlayArea(ui: MatchUi, solved: Boolean, feedback: AnswerFeedback?, onReject: () -> Unit) {
    val shake = remember { Animatable(0f) }
    suspend fun doShake() {
        for (x in listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f)) shake.animateTo(x, tween(40))
    }
    LaunchedEffect(ui.messageTick) {
        if (ui.messageTick == 0) return@LaunchedEffect
        doShake()
        kotlinx.coroutines.delay(1100)
        ui.clearMessage()
    }
    LaunchedEffect(feedback) { if (feedback == AnswerFeedback.Wrong) doShake() }

    val achieved = solved || feedback == AnswerFeedback.Correct
    val v = ui.version   // 상태 변화 구독(그리기 함수에 전달)
    val game = ui.game
    val stroke = when {
        feedback == AnswerFeedback.Wrong -> AppColors.Danger
        achieved -> AppColors.Success
        else -> AppColors.Stroke
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.match_moves, game.displacement.also { v }, game.moves),
            color = if (game.displacement >= game.moves) AppColors.Accent2 else AppColors.TextSecondary,
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
        BoxWithConstraints(
            Modifier.fillMaxWidth().offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                .clip(RoundedCornerShape(18.dp)).background(AppColors.Card)
                .border(if (stroke == AppColors.Stroke) 1.dp else 2.dp, stroke, RoundedCornerShape(18.dp)),
        ) {
            val density = LocalDensity.current
            val availW = with(density) { maxWidth.toPx() } - 2 * with(density) { 12.dp.toPx() }
            val geo = remember(game.board, availW, density.density) { MatchGeometry(game.board, availW, density.density) }
            val pickR = PICK_RADIUS_DP * density.density
            val dropR = DROP_RADIUS_DP * density.density
            val canvasH = with(density) { geo.canvasHeight.toDp() }
            val offX = with(density) { 12.dp.toPx() }
            val solvedState = androidx.compose.runtime.rememberUpdatedState(achieved)

            Canvas(
                Modifier.fillMaxWidth().height(canvasH)
                    .pointerInput(geo) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (solvedState.value) return@awaitEachGesture
                            val p0 = down.position - Offset(offX, 0f)
                            // 막대 근처가 아니면 소비하지 않고 반환 → 부모의 세로 스크롤이 그대로 동작.
                            val slot = geo.nearest(p0, pickR, ui.game.masks) { ui.game.isOccupied(it) } ?: return@awaitEachGesture
                            down.consume()
                            if (!ui.pick(slot, p0)) return@awaitEachGesture
                            var pos = p0
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                pos = ch.position - Offset(offX, 0f)
                                ch.consume()
                                ui.heldPos = pos
                                if (ch.changedToUp() || !ch.pressed) break
                            }
                            val target = geo.nearest(pos, dropR, ui.game.masks) { s -> !ui.game.isOccupied(s) }
                            val r = ui.drop(target)
                            if (r == DropResult.Rejected) onReject()
                        }
                    },
            ) {
                translate(offX, 0f) {
                    drawMatch(geo, game, ui.heldPos, achieved, v)
                }
            }
        }
        Box(Modifier.defaultMinSize(minHeight = 22.dp), contentAlignment = Alignment.Center) {
            val msg = ui.message
            if (msg != null) {
                Text(
                    stringResource(if (msg == MatchMessage.Invalid) R.string.match_invalid else R.string.match_limit),
                    color = AppColors.Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMatch(geo: MatchGeometry, game: MatchGame, heldPos: Offset, achieved: Boolean, @Suppress("UNUSED_PARAMETER") version: Int) {
    val board = geo.board
    val held = game.held
    val masks = game.masks
    val t = geo.t
    val body = if (achieved) AppColors.Success else StickBody
    val head = if (achieved) AppColors.Success.copy(alpha = 0.7f) else StickHead

    // 무효 글리프 셀: 은은한 붉은 톤(들고 있는 동안은 일시적이므로 표시하지 않음).
    if (held == null) {
        for (c in 0 until board.cellCount) {
            if (board.glyph(masks, c) == null) {
                drawRoundRect(
                    AppColors.Danger, alpha = 0.13f,
                    topLeft = Offset(geo.cellLeft(c) - t * 0.4f, geo.padV - t * 0.4f),
                    size = Size(geo.cellWidth(c) + t * 0.8f, geo.h + t * 0.8f),
                    cornerRadius = CornerRadius(t, t),
                )
            }
        }
    }

    // 고스트(빈 슬롯) + 가장 가까운 슬롯 하이라이트
    val hl = if (held != null) geo.nearest(heldPos, DROP_RADIUS_DP * density, masks, { s -> masks[s.cell] and (1 shl s.slot) == 0 }) else null
    if (held != null) {
        for (c in 0 until board.cellCount) for (s in 0 until board.slotCount(c)) {
            if (masks[c] and (1 shl s) != 0) continue
            val slot = MatchSlot(c, s)
            val b = geo.bar(c, s, masks)
            if (slot == hl) drawPill(b, t * 1.1f, AppColors.Accent2, 0.75f)
            else drawPill(b, t, Color.White, 0.13f)
        }
    }

    // 놓인 막대
    for (c in 0 until board.cellCount) for (s in 0 until board.slotCount(c)) {
        if (masks[c] and (1 shl s) == 0) continue
        drawStick(geo.bar(c, s, masks), t, body, head)
    }

    // 들고 있는 막대(손가락을 따라감)
    if (held != null) {
        val ob = geo.bar(held.cell, held.slot, game.board.initialMasks)
        val hb = Bar(heldPos.x, heldPos.y, ob.len, ob.vertical)
        drawCircle(Color.Black, alpha = 0.25f, radius = t * 2.2f, center = heldPos + Offset(0f, t * 0.6f))
        drawStick(hb, t * 1.15f, StickBody, StickHead)
    }
}

/** 하단 컨트롤: 되돌리기/처음부터 + 확인(가장 눈에 띄게). */
@Composable
fun MatchControls(ui: MatchUi, solved: Boolean, onCheck: () -> Unit) {
    val g = ui.game
    val live = ui.version >= 0   // 상태 변화 구독(항상 true)
    val canCheck = live && g.canCheck && !solved
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MatchSecondary(Modifier.weight(1f), stringResource(R.string.match_undo), Icons.AutoMirrored.Filled.Undo, live && g.canUndo && !solved) { ui.undo() }
            MatchSecondary(Modifier.weight(1f), stringResource(R.string.match_reset), Icons.Filled.Refresh, live && g.canReset && !solved) { ui.reset() }
        }
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                .background(if (canCheck) AppColors.Accent else AppColors.Card)
                .border(1.dp, AppColors.Stroke, RoundedCornerShape(16.dp))
                .clickable(enabled = canCheck) { onCheck() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.match_check),
                color = if (canCheck) Color.White else AppColors.TextTertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MatchSecondary(modifier: Modifier, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) AppColors.Card else AppColors.Card.copy(alpha = 0.4f))
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 제출 값: 참이면 answer, 거짓이면 answer 와 절대 같지 않은 "x"+식, 무효면 null. */
fun matchSubmitValue(puzzle: Puzzle, ui: MatchUi): String? = when (ui.game.status()) {
    MatchStatus.Invalid -> null
    MatchStatus.True -> puzzle.answer
    MatchStatus.False -> "x" + (ui.game.text ?: "")
}
