package com.jiny.catchtherule.core.model

enum class GameMode(val key: String) {
    TimeAttack("timeAttack")
}

/** 랭킹 보드의 한 줄. */
data class RankEntry(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val country: String? = null,   // ISO 3166-1 alpha-2 (예: "KR")
    val isMe: Boolean = false,
) {
    /** 국가코드 → 국기 이모지 (예: "KR" → 🇰🇷). 없거나 형식이 틀리면 null. */
    val flagEmoji: String? get() = flagFrom(country)
}

/** ISO 국가코드를 국기 이모지로 변환. */
fun flagFrom(code: String?): String? {
    if (code == null || code.length != 2) return null
    val upper = code.uppercase()
    if (!upper.all { it in 'A'..'Z' }) return null
    val first = 0x1F1E6 + (upper[0].code - 'A'.code)
    val second = 0x1F1E6 + (upper[1].code - 'A'.code)
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
