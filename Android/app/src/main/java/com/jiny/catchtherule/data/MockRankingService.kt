package com.jiny.catchtherule.data

import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.RankEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 서버 없이 동작하는 임시 랭킹. 실제 Minigame 서버 결합 전 UI/흐름 개발용.
 * 제출한 점수는 프로세스 메모리에만 유지된다.
 */
object MockRankingService : RankingService {

    private val mutex = Mutex()
    private val scores = mutableListOf(
        "규칙왕" to 42,
        "패턴마스터" to 38,
        "수열요정" to 35,
        "두뇌풀가동" to 31,
        "초보탈출" to 24,
        "느긋한고양이" to 19,
        "퍼즐러" to 16,
    )

    override suspend fun submit(score: Int, nickname: String, mode: GameMode): Int {
        delay(400) // 네트워크 흉내
        return mutex.withLock {
            val idx = scores.indexOfFirst { it.first == nickname }
            if (idx >= 0) {
                scores[idx] = nickname to maxOf(scores[idx].second, score)
            } else {
                scores.add(nickname to score)
            }
            scores.sortByDescending { it.second }
            scores.indexOfFirst { it.first == nickname } + 1
        }
    }

    override suspend fun leaderboard(mode: GameMode): List<RankEntry> {
        delay(300)
        return mutex.withLock {
            scores.sortedByDescending { it.second }
                // 샘플 랭킹은 한국 국기로 표시.
                .mapIndexed { i, (nick, sc) -> RankEntry(rank = i + 1, nickname = nick, score = sc, country = "KR") }
        }
    }
}
