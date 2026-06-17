package com.jiny.catchtherule.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.core.model.Puzzle
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 진행도/설정의 단일 소스. SharedPreferences 에 영속화하고
 * Compose snapshot state 로 노출한다. (로그인/서버 불필요)
 * iOS 의 ProgressStore 와 동등.
 */
class ProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ctr_progress", Context.MODE_PRIVATE)
    private val store = PuzzleStore.get(context)

    /** 트랙별 "다음에 풀 인덱스"(해당 트랙 퍼즐 배열 기준). 키=트랙. */
    var indices by mutableStateOf(loadIndices())
        private set
    var hintsRemaining by mutableIntStateOf(prefs.getInt(K_HINTS, INITIAL_HINTS))
        private set
    /** 도형규칙 해금 알림을 본 적 있는지(1회성). */
    var shapesUnlockSeen by mutableStateOf(prefs.getBoolean(K_UNLOCK_SEEN, false))
        private set
    var bestTimeAttack by mutableIntStateOf(prefs.getInt(K_BEST, 0))
        private set
    var nickname by mutableStateOf(prefs.getString(K_NICK, "") ?: "")
        private set
    var soundOn by mutableStateOf(prefs.getBoolean(K_SOUND, true))
        private set
    var hapticsOn by mutableStateOf(prefs.getBoolean(K_HAPTICS, true))
        private set
    var stars by mutableStateOf(loadStars())
        private set

    // MARK: Derived

    val totalStars: Int get() = stars.values.sum()

    fun currentIndex(track: String = PuzzleStore.DEFAULT_TRACK): Int = indices[track] ?: 0
    fun maxStars(track: String = PuzzleStore.DEFAULT_TRACK): Int = store.totalCount(track) * 3
    fun earnedStars(track: String = PuzzleStore.DEFAULT_TRACK): Int =
        store.puzzles(track).sumOf { stars[it.id] ?: 0 }
    fun isCampaignFinished(track: String = PuzzleStore.DEFAULT_TRACK): Boolean =
        currentIndex(track) >= store.totalCount(track)
    fun progressFraction(track: String = PuzzleStore.DEFAULT_TRACK): Float {
        val total = store.totalCount(track)
        return if (total > 0) (currentIndex(track).toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    fun starCount(puzzleId: String): Int = stars[puzzleId] ?: 0

    /** 도형규칙 해금 여부 = 숫자규칙 챕터 1~SHAPES_UNLOCK_CHAPTERS 전부 클리어. */
    val isShapesUnlocked: Boolean
        get() {
            val needed = store.puzzles("numbers").count { it.chapter <= SHAPES_UNLOCK_CHAPTERS }
            return needed > 0 && currentIndex("numbers") >= needed
        }

    fun markShapesUnlockSeen() {
        if (shapesUnlockSeen) return
        shapesUnlockSeen = true
        prefs.edit().putBoolean(K_UNLOCK_SEEN, true).apply()
    }

    // MARK: Mutations

    fun recordCampaignClear(puzzle: Puzzle, atIndex: Int, earnedStars: Int) {
        val best = maxOf(stars[puzzle.id] ?: 0, earnedStars)
        stars = stars.toMutableMap().apply { put(puzzle.id, best) }
        prefs.edit().putString(K_STARS, Json.encodeToString(stars)).apply()
        val track = puzzle.trackKey
        if (atIndex >= currentIndex(track)) {
            indices = indices.toMutableMap().apply { put(track, atIndex + 1) }
            prefs.edit().putString(K_INDICES, Json.encodeToString(indices)).apply()
        }
    }

    fun addHints(count: Int) {
        if (count <= 0) return
        hintsRemaining += count
        prefs.edit().putInt(K_HINTS, hintsRemaining).apply()
    }

    fun spendHint(): Boolean {
        if (hintsRemaining <= 0) return false
        hintsRemaining -= 1
        prefs.edit().putInt(K_HINTS, hintsRemaining).apply()
        return true
    }

    fun updateNickname(value: String) {
        nickname = value
        prefs.edit().putString(K_NICK, value).apply()
    }

    fun setSound(value: Boolean) {
        soundOn = value
        prefs.edit().putBoolean(K_SOUND, value).apply()
    }

    fun setHaptics(value: Boolean) {
        hapticsOn = value
        prefs.edit().putBoolean(K_HAPTICS, value).apply()
    }

    fun updateBestTimeAttack(score: Int) {
        if (score > bestTimeAttack) {
            bestTimeAttack = score
            prefs.edit().putInt(K_BEST, score).apply()
        }
    }

    fun resetProgress() {
        indices = emptyMap()
        stars = emptyMap()
        hintsRemaining = INITIAL_HINTS
        bestTimeAttack = 0
        shapesUnlockSeen = false
        prefs.edit()
            .remove(K_INDICES)
            .remove(K_INDEX)            // 구버전 키도 정리
            .remove(K_STARS)
            .remove(K_UNLOCK_SEEN)
            .putInt(K_HINTS, INITIAL_HINTS)
            .putInt(K_BEST, 0)
            .apply()
    }

    private fun loadStars(): Map<String, Int> {
        val raw = prefs.getString(K_STARS, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    /** 트랙별 인덱스 로드. 없으면 구버전 단일 currentIndex 를 numbers 로 1회 마이그레이션. */
    private fun loadIndices(): Map<String, Int> {
        prefs.getString(K_INDICES, null)?.let { raw ->
            return runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
        }
        val legacy = prefs.getInt(K_INDEX, 0)
        return if (legacy > 0) mapOf(PuzzleStore.DEFAULT_TRACK to legacy) else emptyMap()
    }

    companion object {
        const val INITIAL_HINTS = 3
        const val SHAPES_UNLOCK_CHAPTERS = 5
        private const val K_INDEX = "currentIndex"      // 구버전(마이그레이션 소스)
        private const val K_INDICES = "trackIndices"
        private const val K_UNLOCK_SEEN = "shapesUnlockSeen"
        private const val K_STARS = "stars"
        private const val K_HINTS = "hintsRemaining"
        private const val K_NICK = "nickname"
        private const val K_SOUND = "soundOn"
        private const val K_HAPTICS = "hapticsOn"
        private const val K_BEST = "bestTimeAttack"
    }
}

val LocalProgress = staticCompositionLocalOf<ProgressStore> {
    error("ProgressStore not provided")
}
