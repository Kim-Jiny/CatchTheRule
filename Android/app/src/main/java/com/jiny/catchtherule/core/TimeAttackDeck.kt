package com.jiny.catchtherule.core

import com.jiny.catchtherule.core.model.Puzzle
import kotlin.random.Random

/**
 * 타임어택 출제 덱. UI 와 분리된 순수 로직.
 * - 풀: [tracks] 모드, 각 모드의 앞쪽 절반 챕터(chapter <= ceil(최대챕터/2))만.
 * - 뽑기: 모드를 균등 무작위로 고르되 같은 모드 3연속은 막고, 모드별 셔플 리스트를 순서대로 소진(소진 시 재셔플).
 */
class TimeAttackDeck(
    allPuzzles: (String) -> List<Puzzle>,
    tracks: List<String>,
    private val random: Random = Random.Default,
) {
    private class Lane(val puzzles: List<Puzzle>) {
        var order: List<Puzzle> = emptyList()
        var index = 0
    }

    private val lanes: Map<String, Lane> = tracks.mapNotNull { t ->
        val all = allPuzzles(t)
        val maxChapter = all.maxOfOrNull { it.chapter } ?: return@mapNotNull null
        val limit = (maxChapter + 1) / 2
        val pool = all.filter { it.chapter <= limit }
        if (pool.isEmpty()) null else t to Lane(pool)
    }.toMap()

    private var last: String? = null
    private var streak = 0

    val isEmpty: Boolean get() = lanes.isEmpty()

    /** 다음 문제. 풀이 비어 있으면 null. */
    fun next(): Puzzle? {
        if (lanes.isEmpty()) return null
        var candidates = lanes.keys.toList()
        if (streak >= 2 && candidates.size > 1) candidates = candidates.filter { it != last }
        val track = candidates[random.nextInt(candidates.size)]
        streak = if (track == last) streak + 1 else 1
        last = track
        val lane = lanes.getValue(track)
        if (lane.index >= lane.order.size) {
            val prev = lane.order.lastOrNull()
            var shuffled = lane.puzzles.shuffled(random)
            // 재셔플 직후 직전 문제가 맨 앞에 오면 연속 중복 → 뒤로 보냄
            if (prev != null && shuffled.size > 1 && shuffled.first().id == prev.id) {
                shuffled = shuffled.drop(1) + shuffled.first()
            }
            lane.order = shuffled
            lane.index = 0
        }
        return lane.order[lane.index++]
    }
}
