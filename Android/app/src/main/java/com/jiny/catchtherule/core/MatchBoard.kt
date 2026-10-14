package com.jiny.catchtherule.core

/**
 * 성냥개비 퍼즐 순수 로직(docs/match-mode-spec.md). UI/Compose 의존 없음.
 *
 * 숫자 셀: 7슬롯 비트 a=0 b=1 c=2 d=3 e=4 f=5 g=6.
 * 연산자 셀: 3슬롯 비트 hu=0 hl=1 v=2.
 */
data class MatchSlot(val cell: Int, val slot: Int)

object MatchRules {
    const val A = 1; const val B = 2; const val C = 4; const val D = 8; const val E = 16; const val F = 32; const val G = 64
    const val HU = 1; const val HL = 2; const val V = 4
    const val DIGIT_SLOTS = 7
    const val OP_SLOTS = 3

    private fun m(s: String): Int = s.fold(0) { acc, c -> acc or (1 shl (c - 'a')) }

    private val digitMasks = mapOf(
        '0' to m("abcdef"), '1' to m("bc"), '2' to m("abdeg"), '3' to m("abcdg"), '4' to m("bcfg"),
        '5' to m("acdfg"), '6' to m("acdefg"), '7' to m("abc"), '8' to m("abcdefg"), '9' to m("abcdfg"),
    )
    private val maskToDigit = digitMasks.entries.associate { it.value to it.key }

    /** 연산자 마스크 → 문자. */
    fun opGlyph(mask: Int): Char? = when (mask) {
        HU or HL -> '='
        HU, HL -> '-'
        HU or V, HL or V -> '+'
        else -> null
    }

    fun digitGlyph(mask: Int): Char? = maskToDigit[mask]

    fun glyph(isDigit: Boolean, mask: Int): Char? = if (isDigit) digitGlyph(mask) else opGlyph(mask)

    /** 초기 글리프 → 마스크. '+' 는 {hu,v}, '-' 는 {hu}, '=' 는 {hu,hl}. */
    fun maskOf(ch: Char): Int = when (ch) {
        '+' -> HU or V
        '-', '−' -> HU
        '=' -> HU or HL
        else -> digitMasks[ch] ?: 0
    }

    fun isDigitCell(ch: Char) = ch in '0'..'9'

    /** 슬롯 집합 문자열("abcdef" / "hu,v" 등) → 마스크. 테스트 벡터용. */
    fun maskFromSlots(isDigit: Boolean, slots: String): Int {
        if (isDigit) return m(slots.filter { it in 'a'..'g' })
        var r = 0
        val s = slots.replace(",", " ").trim()
        if (s.isEmpty()) return 0
        // "hu hl v" 또는 붙여 쓴 "huhlv"
        var i = 0
        val t = s.replace(" ", "")
        while (i < t.length) {
            when {
                t.startsWith("hu", i) -> { r = r or HU; i += 2 }
                t.startsWith("hl", i) -> { r = r or HL; i += 2 }
                t[i] == 'v' -> { r = r or V; i += 1 }
                else -> i += 1
            }
        }
        return r
    }

    /** 식 문자열 판정: 참/거짓, 구조가 잘못되면 null. */
    fun evaluate(text: String): Boolean? {
        if (text.count { it == '=' } != 1) return null
        val parts = text.split('=')
        val l = side(parts[0]) ?: return null
        val r = side(parts[1]) ?: return null
        return l == r
    }

    private fun side(s: String): Long? {
        if (s.isEmpty()) return null
        var total = 0L
        var sign = 1L
        var i = 0
        while (true) {
            val start = i
            while (i < s.length && s[i] in '0'..'9') i++
            if (i == start) return null
            val num = s.substring(start, i)
            if (num.length > 1 && num[0] == '0') return null
            total += sign * num.toLong()
            if (i == s.length) return total
            sign = when (s[i]) { '+' -> 1L; '-' -> -1L; else -> return null }
            i++
        }
    }
}

/** 한 퍼즐의 불변 정의(초기 식 + 허용 이동). */
class MatchBoard(equation: List<String>, val moves: Int) {
    val cellCount = equation.size
    val isDigit: List<Boolean> = equation.map { MatchRules.isDigitCell(it.first()) }
    val initialMasks: List<Int> = equation.map { MatchRules.maskOf(it.first()) }

    fun slotCount(cell: Int) = if (isDigit[cell]) MatchRules.DIGIT_SLOTS else MatchRules.OP_SLOTS

    fun glyph(masks: List<Int>, cell: Int): Char? = MatchRules.glyph(isDigit[cell], masks[cell])

    /** 모든 셀이 유효하면 식 문자열, 아니면 null. */
    fun text(masks: List<Int>): String? {
        val sb = StringBuilder()
        for (c in 0 until cellCount) sb.append(glyph(masks, c) ?: return null)
        return sb.toString()
    }

    /** 현재 막대가 놓인 슬롯 중 초기에 없던 슬롯 수. */
    fun displacement(masks: List<Int>): Int =
        masks.indices.sumOf { Integer.bitCount(masks[it] and initialMasks[it].inv()) }
}

enum class MatchStatus { Invalid, True, False }

enum class DropResult { Placed, Returned, Rejected }

/** 플레이 세션 상태(들고 있는 막대, 되돌리기 스택). 순수 Kotlin. */
class MatchGame(val board: MatchBoard) {
    var masks: List<Int> = board.initialMasks
        private set
    /** 들고 있는 막대의 원래 슬롯(없으면 null). */
    var held: MatchSlot? = null
        private set
    private var beforePick: List<Int> = masks
    private val undoStack = ArrayList<List<Int>>()

    val moves get() = board.moves
    val displacement get() = board.displacement(masks)
    val canUndo get() = undoStack.isNotEmpty() && held == null
    val canReset get() = (masks != board.initialMasks || undoStack.isNotEmpty()) && held == null
    val canCheck get() = displacement >= 1 && held == null

    fun isOccupied(s: MatchSlot) = masks[s.cell] and (1 shl s.slot) != 0

    fun pick(s: MatchSlot): Boolean {
        if (held != null || !isOccupied(s)) return false
        beforePick = masks
        masks = masks.toMutableList().also { it[s.cell] = it[s.cell] and (1 shl s.slot).inv() }
        held = s
        return true
    }

    /** 이동 한도를 넘지 않고 target 에 놓을 수 있는지(빈 슬롯이어야 함). */
    fun canDrop(target: MatchSlot): Boolean {
        if (held == null || isOccupied(target)) return false
        return board.displacement(withStick(target)) <= board.moves
    }

    private fun withStick(t: MatchSlot): List<Int> =
        masks.toMutableList().also { it[t.cell] = it[t.cell] or (1 shl t.slot) }

    fun drop(target: MatchSlot?): DropResult {
        val origin = held ?: return DropResult.Returned
        held = null
        if (target == null || target == origin || isOccupied(target)) {
            masks = beforePick
            return DropResult.Returned
        }
        val next = withStick(target)
        if (board.displacement(next) > board.moves) {
            masks = beforePick
            return DropResult.Rejected
        }
        undoStack.add(beforePick)
        masks = next
        return DropResult.Placed
    }

    fun undo() {
        if (held != null || undoStack.isEmpty()) return
        masks = undoStack.removeAt(undoStack.size - 1)
    }

    fun reset() {
        held = null
        masks = board.initialMasks
        undoStack.clear()
    }

    val text: String? get() = board.text(masks)

    fun status(): MatchStatus {
        val t = text ?: return MatchStatus.Invalid
        return when (MatchRules.evaluate(t)) {
            null -> MatchStatus.Invalid
            true -> MatchStatus.True
            false -> MatchStatus.False
        }
    }
}
