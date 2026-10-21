package com.jiny.catchtherule

import android.app.Activity
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.RankEntry
import com.jiny.catchtherule.data.KstPeriod
import com.jiny.catchtherule.data.Leaderboard
import com.jiny.catchtherule.data.Ranking
import com.jiny.catchtherule.data.RankingService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Google Play 스크린샷 촬영용 DEBUG 전용 훅(src/debug 소스셋 — 릴리스에는 stub 만 들어간다).
 * 사용: `adb shell am start -n com.jiny.catchtherule/.MainActivity --es ctr_scene home | challenge | play:<퍼즐 id>`
 * 켜지면 광고/UMP/결제/애널리틱스/서버 갱신을 끄고, 진행도를 SharedPreferences 에 시드하며, 가짜 랭킹을 쓴다.
 * iOS 의 ScreenshotMode.swift 와 같은 값을 쓴다.
 */
object ScreenshotMode {
    var active: Boolean = false
        private set
    /** 0=홈, 1=도전. */
    var startTab: Int = 0
        private set
    /** `play:<id>` 이면 (트랙, 시작 인덱스). */
    var play: Pair<String, Int>? = null
        private set

    /** 실제 진행도(ctr_progress)를 건드리지 않도록 별도 파일에 시드한다. */
    const val prefsName = "ctr_progress_shot"

    private const val FAKE_NICK = "Player"
    private const val FAKE_BEST = 14

    private val fractions = mapOf(
        "numbers" to 1.0, "shapes" to 0.42, "logic" to 0.28,
        "contradiction" to 0.55, "target" to 0.35, "match" to 0.18,
    )
    private const val HERO_TRACK = "target"

    /** ProgressStore 생성 전에 호출. */
    fun setup(activity: Activity) {
        val scene = activity.intent?.getStringExtra("ctr_scene") ?: return
        active = true
        startTab = if (scene == "challenge") 1 else 0
        // 시스템 바(상태바/내비게이션) 숨김 — 합성 단계에서 깨끗한 상태바를 그려 넣는다.
        androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val store = PuzzleStore.get(activity)
        if (scene.startsWith("play:")) {
            val id = scene.removePrefix("play:")
            store.tracks.forEach { t ->
                val i = store.puzzles(t).indexOfFirst { it.id == id }
                if (i >= 0) play = t to i
            }
        }

        val indices = mutableMapOf<String, Int>()
        val stars = mutableMapOf<String, Int>()
        for (track in store.tracks) {
            val list = store.puzzles(track)
            val n = Math.round(list.size * (fractions[track] ?: 0.3)).toInt()
            indices[track] = n
            list.take(n).forEachIndexed { i, p -> stars[p.id] = if (i % 5 == 0) 2 else 3 }
        }
        activity.applicationContext.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE).edit()
            .putString("trackIndices", Json.encodeToString(indices))
            .putString("stars", Json.encodeToString(stars))
            .putInt("hintsRemaining", 30)
            .putBoolean("shapesUnlockSeen", true)
            .putString("nickname", FAKE_NICK)
            .putBoolean("soundOn", false)
            .putBoolean("hapticsOn", false)
            .putString("lastTrack", HERO_TRACK)
            .putInt("bestTimeAttack", FAKE_BEST)
            .putString("bestTimeAttackPeriod", KstPeriod.current())
            .commit()
        Ranking.inject(FakeRanking)
    }

    /** 실서버/실닉네임이 나오지 않도록 하는 고정 데이터. 동점 한 쌍(31) 포함, "Player"(14) = 11위. */
    private object FakeRanking : RankingService {
        private val rows = listOf(
            Triple("NeonFox", 42, "US"), Triple("QuietOwl", 38, "KR"), Triple("PixelPanda", 35, "JP"),
            Triple("RuleHunter", 31, "DE"), Triple("MintCat", 31, "FR"), Triple("Zenith", 27, "ES"),
            Triple("BluePine", 24, "BR"), Triple("Orbit", 21, "CN"), Triple("Tango", 19, "KR"),
            Triple("Lumi", 17, "US"), Triple("Player", 14, "KR"), Triple("Quokka", 12, "JP"),
            Triple("Pebble", 9, "DE"), Triple("Nova", 7, "FR"),
        )

        override suspend fun submit(score: Int, nickname: String, mode: GameMode): Int = 11

        override suspend fun leaderboard(mode: GameMode): Leaderboard {
            val entries = mutableListOf<RankEntry>()
            rows.forEachIndexed { i, r ->
                val rank = if (i > 0 && rows[i - 1].second == r.second) entries[i - 1].rank else i + 1
                entries.add(RankEntry(rank = rank, nickname = r.first, score = r.second, country = r.third))
            }
            return Leaderboard(entries, KstPeriod.current(), KstPeriod.resetsAt())
        }
    }
}
