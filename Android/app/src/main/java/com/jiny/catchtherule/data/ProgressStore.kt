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
    private val total = PuzzleStore.get(context).totalCount

    var currentIndex by mutableIntStateOf(prefs.getInt(K_INDEX, 0))
        private set
    var hintsRemaining by mutableIntStateOf(prefs.getInt(K_HINTS, DAILY_HINTS))
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
    val maxStars: Int get() = total * 3
    val isCampaignFinished: Boolean get() = currentIndex >= total
    val progressFraction: Float
        get() = if (total > 0) (currentIndex.toFloat() / total).coerceIn(0f, 1f) else 0f

    fun starCount(puzzleId: String): Int = stars[puzzleId] ?: 0

    // MARK: Mutations

    fun recordCampaignClear(puzzle: Puzzle, atGlobalIndex: Int, earnedStars: Int) {
        val best = maxOf(stars[puzzle.id] ?: 0, earnedStars)
        stars = stars.toMutableMap().apply { put(puzzle.id, best) }
        prefs.edit().putString(K_STARS, Json.encodeToString(stars)).apply()
        if (atGlobalIndex >= currentIndex) {
            currentIndex = atGlobalIndex + 1
            prefs.edit().putInt(K_INDEX, currentIndex).apply()
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
        currentIndex = 0
        stars = emptyMap()
        hintsRemaining = DAILY_HINTS
        bestTimeAttack = 0
        prefs.edit()
            .putInt(K_INDEX, 0)
            .remove(K_STARS)
            .putInt(K_HINTS, DAILY_HINTS)
            .putInt(K_BEST, 0)
            .apply()
    }

    private fun loadStars(): Map<String, Int> {
        val raw = prefs.getString(K_STARS, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    companion object {
        const val DAILY_HINTS = 5
        private const val K_INDEX = "currentIndex"
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
