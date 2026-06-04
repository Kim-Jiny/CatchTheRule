package com.jiny.catchtherule.data

import android.content.Context
import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.RankEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minigame 서버(https://duo.jiny.shop)의 CatchTheRule 랭킹 API 결합.
 *
 *   POST /api/catchtherule/scores       { nickname, score, mode, deviceId } -> { rank }
 *   GET  /api/catchtherule/leaderboard?mode=timeAttack&limit=100 -> { entries: [...] }
 *
 * 로그인이 없으므로 인증 없이 호출하고, 기기당 1행 유지를 위해
 * 로컬에 영속화한 deviceId(UUID)를 함께 보낸다.
 */
class MinigameRankingService(context: Context) : RankingService {

    private val baseUrl = "https://duo.jiny.shop"
    private val deviceId: String = CtrDevice.id(context)   // 문의 서비스와 동일한 값 공유
    private val country: String? = java.util.Locale.getDefault().country.takeIf { it.length == 2 }
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun submit(score: Int, nickname: String, mode: GameMode): Int =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(SubmitBody(nickname, score, mode.key, deviceId, country))
            val text = request("POST", "/api/catchtherule/scores", body)
            json.decodeFromString<SubmitResponse>(text).rank
        }

    override suspend fun leaderboard(mode: GameMode): List<RankEntry> =
        withContext(Dispatchers.IO) {
            val text = request("GET", "/api/catchtherule/leaderboard?mode=${mode.key}&limit=100", null)
            json.decodeFromString<LeaderboardResponse>(text).entries.map {
                RankEntry(rank = it.rank, nickname = it.nickname, score = it.score, country = it.country)
            }
        }

    // MARK: - HTTP

    private fun request(method: String, path: String, body: String?): String {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw RuntimeException("HTTP $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    @Serializable
    private data class SubmitBody(
        val nickname: String,
        val score: Int,
        val mode: String,
        val deviceId: String,
        val country: String?,
    )

    @Serializable
    private data class SubmitResponse(val rank: Int)

    @Serializable
    private data class LeaderboardResponse(val entries: List<Entry>) {
        @Serializable
        data class Entry(val rank: Int, val nickname: String, val score: Int, val country: String? = null)
    }
}
