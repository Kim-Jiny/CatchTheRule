package com.jiny.catchtherule.core

/** "숫자 만들기" 연산자. */
enum class TargetOp(val symbol: String) {
    Add("+"), Sub("−"), Mul("×"), Div("÷")
}

/** 식의 토큰. Num 은 재료 타일 하나(원자적)를 가리킨다. */
sealed class TargetToken {
    data class Num(val tile: Int, val value: Int) : TargetToken()
    data class Op(val op: TargetOp) : TargetToken()
    data object Open : TargetToken()
    data object Close : TargetToken()

    val text: String
        get() = when (this) {
            is Num -> value.toString()
            is Op -> op.symbol
            Open -> "("
            Close -> ")"
        }
}

/** 정확한 유리수(약분됨, 분모 > 0). 절댓값이 [LIMIT] 을 넘으면 무효(null). */
data class Frac(val n: Long, val d: Long) {
    val isInteger: Boolean get() = d == 1L
    override fun toString() = if (isInteger) n.toString() else "$n/$d"

    companion object {
        const val LIMIT = 1_000_000_000L
        fun of(n: Long, d: Long): Frac? {
            if (d == 0L) return null
            var nn = n; var dd = d
            if (dd < 0) { nn = -nn; dd = -dd }
            val g = gcd(Math.abs(nn), dd)
            if (g > 1) { nn /= g; dd /= g }
            return if (Math.abs(nn) > LIMIT || dd > LIMIT) null else Frac(nn, dd)
        }
        private fun gcd(a: Long, b: Long): Long { var x = a; var y = b; while (y != 0L) { val t = x % y; x = y; y = t }; return if (x == 0L) 1 else x }
    }

    // 피연산자가 LIMIT(1e9) 이하이므로 곱/합이 Long 범위 안.
    fun plus(o: Frac) = of(n * o.d + o.n * d, d * o.d)
    fun minus(o: Frac) = of(n * o.d - o.n * d, d * o.d)
    fun times(o: Frac) = of(n * o.n, d * o.d)
    fun div(o: Frac) = if (o.n == 0L) null else of(n * o.d, d * o.n)
}

/**
 * 수식 입력 상태(불변). 문법 가드·평가·사용된 타일 집계를 UI 와 분리한 순수 로직.
 * [numbers] 는 재료 숫자들, 각 타일(인덱스)은 한 번만 쓸 수 있다.
 */
data class TargetExpression(
    val numbers: List<Int>,
    val tokens: List<TargetToken> = emptyList(),
) {
    val usedTiles: Set<Int> get() = tokens.filterIsInstance<TargetToken.Num>().map { it.tile }.toSet()
    private val last: TargetToken? get() = tokens.lastOrNull()
    private val openCount: Int get() = tokens.count { it == TargetToken.Open } - tokens.count { it == TargetToken.Close }

    // 직전이 "값을 끝낸" 토큰(숫자 또는 ')')인가
    private val afterValue: Boolean get() = last is TargetToken.Num || last == TargetToken.Close

    fun canAddNumber(tile: Int) = tile in numbers.indices && tile !in usedTiles && !afterValue
    fun canAddOp() = afterValue
    fun canAddOpen() = !afterValue
    fun canAddClose() = openCount > 0 && afterValue

    fun addNumber(tile: Int) = if (canAddNumber(tile)) copy(tokens = tokens + TargetToken.Num(tile, numbers[tile])) else null
    fun addOp(op: TargetOp) = if (canAddOp()) copy(tokens = tokens + TargetToken.Op(op)) else null
    fun addOpen() = if (canAddOpen()) copy(tokens = tokens + TargetToken.Open) else null
    fun addClose() = if (canAddClose()) copy(tokens = tokens + TargetToken.Close) else null

    /** 마지막 토큰 하나 삭제(숫자면 타일 복구). */
    fun deleteLast() = if (tokens.isEmpty()) this else copy(tokens = tokens.dropLast(1))
    fun clear() = copy(tokens = emptyList())

    val displayText: String get() = tokens.joinToString(" ") { it.text }

    /** 비어있지 않고, 마지막이 값이며, 괄호가 모두 닫힘. (0 나누기 등은 [evaluate] 가 null) */
    val isComplete: Boolean get() = tokens.isNotEmpty() && afterValue && openCount == 0

    /** 완성된 식의 값. 미완성/0 나누기/오버플로면 null. */
    fun evaluate(): Frac? {
        if (!isComplete) return null
        val p = Parser(tokens)
        val v = p.expr() ?: return null
        return if (p.pos == tokens.size) v else null
    }

    private class Parser(val t: List<TargetToken>) {
        var pos = 0
        fun expr(): Frac? {
            var v = term() ?: return null
            while (pos < t.size) {
                val tk = t[pos] as? TargetToken.Op ?: break
                if (tk.op != TargetOp.Add && tk.op != TargetOp.Sub) break
                pos++
                val r = term() ?: return null
                v = (if (tk.op == TargetOp.Add) v.plus(r) else v.minus(r)) ?: return null
            }
            return v
        }
        fun term(): Frac? {
            var v = factor() ?: return null
            while (pos < t.size) {
                val tk = t[pos] as? TargetToken.Op ?: break
                if (tk.op != TargetOp.Mul && tk.op != TargetOp.Div) break
                pos++
                val r = factor() ?: return null
                v = (if (tk.op == TargetOp.Mul) v.times(r) else v.div(r)) ?: return null
            }
            return v
        }
        fun factor(): Frac? {
            val tk = t.getOrNull(pos) ?: return null
            return when (tk) {
                is TargetToken.Num -> { pos++; Frac.of(tk.value.toLong(), 1) }
                TargetToken.Open -> {
                    pos++
                    val v = expr() ?: return null
                    if (t.getOrNull(pos) != TargetToken.Close) return null
                    pos++
                    v
                }
                else -> null
            }
        }
    }
}
