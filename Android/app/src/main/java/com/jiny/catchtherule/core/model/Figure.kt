package com.jiny.catchtherule.core.model

import kotlinx.serialization.Serializable

/**
 * "도형에서 규칙찾기" 트랙에서 직접 그려 표현하는 도형 1개의 명세.
 * iOS 의 Figure 구조체와 동일한 스키마(JSON 공유).
 *
 * - 숫자형(keypad): [slots] 에 꼭짓점/세그먼트/중앙 숫자를 담고, null 인 칸이 빈칸(정답).
 *   레이아웃은 shape + slots.size 로 결정(triangle 3=세 꼭짓점, 4=+중앙 / square 4, 5 / circle N).
 * - 시각형(choices): [rotation]/[filled]/[count] 로 순수 시각 규칙을 표현하고,
 *   보기 식별은 [code](== 퍼즐 answer) 로 채점.
 */
@Serializable
data class Figure(
    val shape: String,                 // "triangle" | "square" | "circle" | "arrow" | "dot"
    val slots: List<String?>? = null,  // 숫자형: 슬롯 값(시계방향). null = 빈칸
    val rotation: Int? = null,         // 시각형: 회전(도)
    val filled: Boolean? = null,       // 시각형: 채움(true)/외곽선(false)
    val count: Int? = null,            // 시각형: 같은 도형 N개 반복
    val code: String? = null,          // 시각형 보기 식별자
) {
    val rotationDegrees: Float get() = (rotation ?: 0).toFloat()
    val isFilled: Boolean get() = filled ?: true
    val repeatCount: Int get() = maxOf(1, count ?: 1)
    val hasSlots: Boolean get() = slots?.isNotEmpty() == true
}
