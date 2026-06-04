package com.jiny.catchtherule.core.model

import kotlinx.serialization.Serializable

/**
 * 하나의 규칙찾기 퍼즐. 번들된 assets/puzzles.json 으로부터 디코딩된다.
 * iOS 의 Puzzle 구조체와 동일한 스키마(JSON 공유).
 *
 * 토큰/정답을 문자열로 다뤄 숫자·문자(A,B,C)·모양(이모지) 퍼즐을
 * 하나의 스키마로 통합한다. (type 은 표시용 분류로만 사용)
 */
@Serializable
data class Puzzle(
    val id: String,
    val type: String,
    val chapter: Int,
    val order: Int,
    val tokens: List<String?>? = null,        // 단일 행. null 인 항이 빈칸. (grid 사용 시 생략)
    val grid: List<List<String?>>? = null,    // 다중 행/매트릭스/수식형. 한 칸이 null(빈칸).
    val answer: String,
    val inputType: String,      // "keypad" | "choices"
    val choices: List<String>? = null,
    val hints: List<String>,
    val explanation: String,
) {
    val isGrid: Boolean get() = grid?.isNotEmpty() == true

    fun isCorrect(value: String): Boolean = value.trim() == answer
}

enum class InputType(val key: String) {
    Keypad("keypad"),
    Choices("choices");

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: Keypad
    }
}
