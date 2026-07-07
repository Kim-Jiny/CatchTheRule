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
    val track: String? = null,                // "numbers"(기본/null) | "shapes". 모드 구분.
    val chapter: Int,
    val order: Int,
    val tokens: List<String?>? = null,        // 단일 행. null 인 항이 빈칸. (grid 사용 시 생략)
    val grid: List<List<String?>>? = null,    // 다중 행/매트릭스/수식형. 한 칸이 null(빈칸).
    val figures: List<Figure>? = null,        // 숫자형 도형(예시 여러 개를 한 줄에, 마지막에 빈칸 슬롯)
    val figureTokens: List<Figure?>? = null,  // 시각형 시퀀스. null = 빈칸 셀.
    val figureChoices: List<Figure>? = null,  // 시각형 보기(도형 보기)
    val prompt: Map<String, String>? = null,   // 논리형 질문 문단(로케일코드 → 문장). 있으면 수열 대신 질문 표시.
    val statements: Map<String, List<String>>? = null,  // 모순찾기 문장 목록(로케일코드 → 문장 배열). answer=모순 문장 번호(1-based).
    val answer: String,
    val inputType: String,      // "keypad" | "choices"
    val choices: List<String>? = null,
    val hints: Map<String, List<String>>,      // 로케일코드 → 힌트 3개
    val explanation: Map<String, String>,      // 로케일코드 → 해설
) {
    /** 소속 트랙(없으면 기본 캠페인 "numbers"). */
    val trackKey: String get() = track ?: "numbers"

    val isGrid: Boolean get() = grid?.isNotEmpty() == true

    /** 숫자형 도형(여러 예시) 퍼즐 여부. */
    val isFigure: Boolean get() = figures?.isNotEmpty() == true

    /** 도형 시퀀스(시각형) 퍼즐 여부. */
    val isFigureSequence: Boolean get() = figureTokens?.isNotEmpty() == true

    /** 논리형(질문 문단) 퍼즐 여부. */
    val isPrompt: Boolean get() = prompt?.isNotEmpty() == true

    /** 모순찾기(문장 목록) 퍼즐 여부. */
    val isContradiction: Boolean get() = statements?.isNotEmpty() == true

    /** 현재 언어의 문장 목록(없으면 영어 → 임의 폴백). */
    val localizedStatements: List<String>
        get() {
            val code = java.util.Locale.getDefault().language
            return statements?.get(code) ?: statements?.get("en") ?: statements?.values?.firstOrNull() ?: emptyList()
        }

    /** 현재 언어의 질문 문단(없으면 영어 → 임의 폴백). */
    val localizedPrompt: String
        get() {
            val code = java.util.Locale.getDefault().language
            return prompt?.get(code) ?: prompt?.get("en") ?: prompt?.values?.firstOrNull() ?: ""
        }

    /** 현재 언어의 힌트(없으면 영어 → 임의 폴백). */
    val localizedHints: List<String>
        get() {
            val code = java.util.Locale.getDefault().language
            return hints[code] ?: hints["en"] ?: hints.values.firstOrNull() ?: emptyList()
        }

    /** 현재 언어의 해설. */
    val localizedExplanation: String
        get() {
            val code = java.util.Locale.getDefault().language
            return explanation[code] ?: explanation["en"] ?: ""
        }

    fun isCorrect(value: String): Boolean = value.trim() == answer
}

enum class InputType(val key: String) {
    Keypad("keypad"),
    Choices("choices");

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: Keypad
    }
}
