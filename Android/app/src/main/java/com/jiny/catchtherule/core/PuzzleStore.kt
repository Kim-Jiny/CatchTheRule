package com.jiny.catchtherule.core

import android.content.Context
import com.jiny.catchtherule.core.model.Puzzle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 퍼즐 제공자.
 * - 기본 11챕터: 번들 assets/puzzles.json
 * - 추가 스테이지: 서버에서 받아 캐시(filesDir). 오프라인이면 캐시/번들 폴백.
 * 서버 갱신은 캐시에 저장되어 **다음 실행**에 반영된다.
 */
class PuzzleStore private constructor(private val allPuzzles: List<Puzzle>) {

    /** 트랙별 퍼즐(이미 (챕터,순서) 정렬됨). 캠페인 인덱스는 이 배열 기준. */
    fun puzzles(track: String = DEFAULT_TRACK): List<Puzzle> = allPuzzles.filter { it.trackKey == track }

    /** 존재하는 트랙 목록(고정 순서 우선, 그 외는 등장 순). */
    val tracks: List<String>
        get() = allPuzzles.map { it.trackKey }.distinct().sortedWith(
            compareBy({ TRACK_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it })
        )

    fun totalCount(track: String = DEFAULT_TRACK): Int = puzzles(track).size
    fun chapters(track: String = DEFAULT_TRACK): List<Int> =
        puzzles(track).map { it.chapter }.distinct().sorted()
    fun puzzlesIn(chapter: Int, track: String = DEFAULT_TRACK): List<Puzzle> =
        puzzles(track).filter { it.chapter == chapter }
    fun puzzleAt(index: Int, track: String = DEFAULT_TRACK): Puzzle? = puzzles(track).getOrNull(index)

    fun position(index: Int, track: String = DEFAULT_TRACK): Pair<Int, Int>? {
        val p = puzzleAt(index, track) ?: return null
        val stage = puzzlesIn(p.chapter, track).indexOfFirst { it.id == p.id } + 1
        return p.chapter to stage
    }

    @Serializable
    private data class ServerPuzzles(val version: Long = 0, val puzzles: List<Puzzle> = emptyList())

    companion object {
        const val DEFAULT_TRACK = "numbers"
        /** 모드 표시 순서(고정). 목록에 없는 트랙은 뒤에 등장 순으로 붙는다. */
        private val TRACK_ORDER = listOf("numbers", "shapes", "logic")
        @Volatile private var instance: PuzzleStore? = null
        private val json = Json { ignoreUnknownKeys = true }
        private const val CACHE = "ctr_server_puzzles.json"
        private const val URL_STR = "https://duo.jiny.shop/api/catchtherule/puzzles"

        fun get(context: Context): PuzzleStore =
            instance ?: synchronized(this) {
                instance ?: load(context.applicationContext).also { instance = it }
            }

        private fun load(context: Context): PuzzleStore {
            val bundled = runCatching {
                json.decodeFromString<List<Puzzle>>(
                    context.assets.open("puzzles.json").bufferedReader().use { it.readText() }
                )
            }.getOrDefault(emptyList())
            return PuzzleStore(merge(bundled, readCache(context)))
        }

        private fun readCache(context: Context): List<Puzzle> {
            val f = File(context.filesDir, CACHE)
            if (!f.exists()) return emptyList()
            return runCatching { json.decodeFromString<List<Puzzle>>(f.readText()) }.getOrDefault(emptyList())
        }

        /** 번들 우선, id 중복 제외, (챕터, 순서) 정렬. */
        private fun merge(base: List<Puzzle>, extra: List<Puzzle>): List<Puzzle> {
            val seen = base.map { it.id }.toMutableSet()
            val all = base.toMutableList()
            for (p in extra) if (seen.add(p.id)) all.add(p)
            return all.sortedWith(compareBy({ it.chapter }, { it.order }))
        }

        /** 백그라운드에서 서버 추가분을 받아 캐시에 저장(다음 실행 반영). */
        fun refreshFromServer(context: Context) {
            val app = context.applicationContext
            Thread {
                runCatching {
                    val conn = (URL(URL_STR).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000; readTimeout = 10000
                    }
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val parsed = json.decodeFromString<ServerPuzzles>(text)   // 디코드=검증
                        File(app.filesDir, CACHE).writeText(json.encodeToString(parsed.puzzles))
                    }
                    conn.disconnect()
                }
            }.start()
        }
    }
}
