package com.jiny.catchtherule.core

import android.content.Context
import com.jiny.catchtherule.core.model.Puzzle
import kotlinx.serialization.json.Json

/** 번들된 assets/puzzles.json 을 로드해 제공한다. 서버 불필요. */
class PuzzleStore private constructor(val puzzles: List<Puzzle>) {

    val totalCount: Int get() = puzzles.size

    val chapters: List<Int> get() = puzzles.map { it.chapter }.distinct().sorted()

    fun puzzlesIn(chapter: Int): List<Puzzle> = puzzles.filter { it.chapter == chapter }

    fun puzzleAt(globalIndex: Int): Puzzle? = puzzles.getOrNull(globalIndex)

    /** 전역 인덱스 → (챕터, 챕터 내 순번) 표시용. */
    fun position(globalIndex: Int): Pair<Int, Int>? {
        val p = puzzleAt(globalIndex) ?: return null
        val stage = puzzlesIn(p.chapter).indexOfFirst { it.id == p.id } + 1
        return p.chapter to stage
    }

    companion object {
        @Volatile private var instance: PuzzleStore? = null

        fun get(context: Context): PuzzleStore =
            instance ?: synchronized(this) {
                instance ?: load(context.applicationContext).also { instance = it }
            }

        private val json = Json { ignoreUnknownKeys = true }

        private fun load(context: Context): PuzzleStore {
            val text = context.assets.open("puzzles.json")
                .bufferedReader().use { it.readText() }
            val list = json.decodeFromString<List<Puzzle>>(text)
                .sortedWith(compareBy({ it.chapter }, { it.order }))
            return PuzzleStore(list)
        }
    }
}
