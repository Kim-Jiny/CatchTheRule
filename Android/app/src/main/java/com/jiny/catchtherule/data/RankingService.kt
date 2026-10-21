package com.jiny.catchtherule.data

import android.content.Context
import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.RankEntry

/**
 * 랭킹 백엔드 추상화. 실서버는 [MinigameRankingService], 오프라인 개발용은 [MockRankingService].
 */
/** 리더보드 결과. [period]/[resetsAtMs] 는 구버전 서버에서는 null(클라이언트가 KST 로 계산). */
data class Leaderboard(
    val entries: List<RankEntry>,
    val period: String? = null,
    val resetsAtMs: Long? = null,
)

interface RankingService {
    /** 점수 제출 후 내 순위를 반환. */
    suspend fun submit(score: Int, nickname: String, mode: GameMode): Int
    /** 리더보드 조회. */
    suspend fun leaderboard(mode: GameMode): Leaderboard
}

/**
 * 앱 전역 랭킹 서비스. [init] 으로 한 번 실서버 구현을 주입한다(MainActivity 에서 호출).
 * 미초기화 시에는 안전하게 Mock 으로 폴백.
 */
object Ranking {
    @Volatile
    private var injected: RankingService? = null

    val service: RankingService
        get() = injected ?: MockRankingService

    /** 서비스 직접 주입(DEBUG 스크린샷 모드의 가짜 랭킹용). 이후 [init] 은 무시된다. */
    fun inject(service: RankingService) { injected = service }

    fun init(context: Context) {
        if (injected == null) {
            injected = MinigameRankingService(context.applicationContext)
        }
    }
}
