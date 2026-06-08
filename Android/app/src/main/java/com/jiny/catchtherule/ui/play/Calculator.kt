package com.jiny.catchtherule.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.ui.theme.AppColors
import kotlin.math.roundToInt

/** 화면 내에서 드래그로 옮길 수 있는 플로팅 계산기(기록 포함). */
@Composable
fun CalculatorPanel(onClose: () -> Unit) {
    var expr by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    // 화면 밖으로 끌려나가 사라지지 않도록 이동 범위 제한.
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val maxX = with(density) { ((cfg.screenWidthDp.dp - 250.dp) / 2 - 8.dp).toPx() }.coerceAtLeast(0f)
    val maxY = with(density) { ((cfg.screenHeightDp.dp - 430.dp) / 2 - 8.dp).toPx() }.coerceAtLeast(0f)

    val rows = listOf(
        listOf("C", "⌫", "÷", "×"),
        listOf("7", "8", "9", "−"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
    )

    fun tap(key: String) {
        when (key) {
            "C" -> expr = ""
            "⌫" -> if (expr.isNotEmpty()) expr = expr.dropLast(1)
            "=" -> {
                val r = CalcEval.evaluate(expr)
                if (r != null) {
                    val res = CalcEval.format(r)
                    history.add("$expr = $res")
                    if (history.size > 20) history.removeAt(0)
                    expr = res
                }
            }
            else -> expr += key
        }
    }

    Box(
        Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(250.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.BgElevated)
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(20.dp)),
    ) {
        Column {
            // 이동 핸들(드래그) + 제목 + 닫기
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(maxX, maxY) {
                        detectDragGestures { _, drag ->
                            offsetX = (offsetX + drag.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + drag.y).coerceIn(-maxY, maxY)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.OpenWith, null, tint = AppColors.TextTertiary, modifier = Modifier.size(15.dp))
                Text(
                    stringResource(R.string.calc_title), color = AppColors.TextSecondary,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp),
                )
                Box(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Close, null, tint = AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp).clickable { onClose() },
                )
            }

            // 계산 기록
            Column(
                Modifier.fillMaxWidth().height(64.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (history.isEmpty()) {
                    Text(
                        stringResource(R.string.calc_no_history), color = AppColors.TextTertiary,
                        fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    history.forEach { line ->
                        Text(line, color = AppColors.TextTertiary, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }

            // 디스플레이
            Text(
                expr.ifEmpty { "0" }, color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif, maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.End,
            )

            // 키패드
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { key -> CalcKey(key, Modifier.weight(1f)) { tap(key) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalcKey("0", Modifier.weight(2f)) { tap("0") }
                    CalcKey(".", Modifier.weight(1f)) { tap(".") }
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalcKey(key: String, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val isEquals = key == "="
    val isOp = key in setOf("÷", "×", "−", "+", "C", "⌫")
    val bg: Brush = if (isEquals) AppColors.AccentGradient else androidx.compose.ui.graphics.SolidColor(AppColors.Card)
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key,
            color = if (isEquals) androidx.compose.ui.graphics.Color.White else if (isOp) AppColors.Accent2 else AppColors.TextPrimary,
            fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 안전한 사칙연산 평가기(× ÷ − + , 소수, 우선순위). */
object CalcEval {
    fun evaluate(input: String): Double? {
        val s = input.replace("×", "*").replace("÷", "/").replace("−", "-")
        val tokens = tokenize(s) ?: return null
        return eval(tokens)
    }

    private sealed class Tok {
        data class Num(val v: Double) : Tok()
        data class Op(val c: Char) : Tok()
    }

    private fun tokenize(s: String): List<Tok>? {
        val out = ArrayList<Tok>()
        var buf = StringBuilder()
        for (c in s) {
            when {
                c.isDigit() || c == '.' -> buf.append(c)
                c in "+-*/" -> {
                    if (c == '-' && buf.isEmpty() && (out.isEmpty() || out.last() is Tok.Op)) {
                        buf.append(c) // 단항 마이너스
                    } else {
                        if (buf.isEmpty()) return null
                        out.add(Tok.Num(buf.toString().toDoubleOrNull() ?: return null)); buf = StringBuilder()
                        out.add(Tok.Op(c))
                    }
                }
                c == ' ' -> {}
                else -> return null
            }
        }
        if (buf.isNotEmpty()) out.add(Tok.Num(buf.toString().toDoubleOrNull() ?: return null))
        return if (out.isEmpty()) null else out
    }

    private fun eval(tokens: List<Tok>): Double? {
        val first = tokens.firstOrNull() as? Tok.Num ?: return null
        val nums = ArrayList<Double>(); nums.add(first.v)
        val ops = ArrayList<Char>()
        var i = 1
        while (i < tokens.size) {
            val op = (tokens[i] as? Tok.Op)?.c ?: return null
            val n = (tokens.getOrNull(i + 1) as? Tok.Num)?.v ?: return null
            if (op == '*' || op == '/') {
                val last = nums.removeAt(nums.size - 1)
                if (op == '/' && n == 0.0) return null
                nums.add(if (op == '*') last * n else last / n)
            } else {
                ops.add(op); nums.add(n)
            }
            i += 2
        }
        var result = nums.firstOrNull() ?: return null
        for (k in ops.indices) {
            val n = nums[k + 1]
            result = if (ops[k] == '+') result + n else result - n
        }
        return result
    }

    fun format(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "0"
        return if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString()
        else d.toString().trimEnd('0').trimEnd('.')
    }
}
