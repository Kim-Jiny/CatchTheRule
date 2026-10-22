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
    val numbers: List<Int>? = null,            // 숫자 만들기 재료 숫자들
    val target: Int? = null,                   // 숫자 만들기 목표 숫자
    val equation: List<String>? = null,        // 성냥개비 퍼즐 초기 식(셀 배열)
    val moves: Int? = null,                    // 성냥개비 퍼즐 허용 이동 수(순변위)
    val frames: List<List<String>>? = null,    // 조각 숫자: frames[장][자리] = 켜지는 획 문자열(a~g)
    val combine: String? = null,               // 조각 숫자 합성 규칙 "or" | "xor"
    val frameMs: Int? = null,                  // 조각 숫자: 한 장 표시 시간(ms)
    val pairs: List<List<List<String>>>? = null,   // 암호 해독: pairs[i] = [입력 토큰들, 출력 토큰들]
    val scales: List<List<List<String>>>? = null,  // 저울 추리: scales[i] = [왼쪽 접시 토큰들, 오른쪽 접시 토큰들]
    val query: List<String>? = null,               // 암호/저울 질문 토큰들
    val board: List<List<Int>>? = null,            // 격자 채우기: 0 = 빈칸, 나머지는 고정 칸
    val rules: List<String>? = null,               // 격자 채우기 규칙 집합("row","col","box","diag")
    val lt: List<List<Int>>? = null,               // 격자 채우기 부등호 [r1,c1,r2,c2] = 앞 칸 < 뒤 칸
    val cages: List<List<Int>>? = null,            // 격자 채우기 합 제약 [합, r1,c1, r2,c2, …]
    val size: Int? = null,                         // 경로 추적: 격자 한 변 칸 수
    val start: List<Int>? = null,                  // 경로 추적: 출발 [행,열]
    val trail: List<List<Int>>? = null,            // 경로 추적: 보이는 자취 점들 (trail[0] = 출발)
    val total: Int? = null,                        // 경로 추적: 총 이동 횟수
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

    /** 숫자 만들기(재료+목표) 퍼즐 여부. */
    val isTarget: Boolean get() = numbers?.isNotEmpty() == true

    /** 성냥개비 퍼즐 여부. */
    val isMatch: Boolean get() = equation?.isNotEmpty() == true

    /** 암호 해독 퍼즐 여부. */
    val isCipher: Boolean get() = pairs?.isNotEmpty() == true

    /** 저울 추리 퍼즐 여부. */
    val isScale: Boolean get() = scales?.isNotEmpty() == true

    /** 격자 채우기 퍼즐 여부. */
    val isFill: Boolean get() = board?.isNotEmpty() == true

    /** 경로 추적 퍼즐 여부. */
    val isPath: Boolean get() = trail?.isNotEmpty() == true

    /** 조각 숫자 퍼즐 여부. */
    val isFragment: Boolean get() = frames?.isNotEmpty() == true

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
