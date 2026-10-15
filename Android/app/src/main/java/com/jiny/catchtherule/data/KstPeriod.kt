package com.jiny.catchtherule.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 월간 랭킹 기간 계산. 기준은 KST(Asia/Seoul, DST 없음 = 고정 UTC+9) 달력 월.
 * minSdk 24 라 java.time 대신 Calendar 를 쓴다.
 */
object KstPeriod {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    private fun kstCalendar(nowMs: Long): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("GMT+09:00"), Locale.US).apply { timeInMillis = nowMs }

    /** 현재 KST 월 "YYYY-MM". */
    fun current(nowMs: Long = System.currentTimeMillis()): String {
        val c = kstCalendar(nowMs)
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    /** 다음 달 1일 00:00 KST 의 epoch ms. */
    fun resetsAt(nowMs: Long = System.currentTimeMillis()): Long {
        val c = kstCalendar(nowMs)
        c.clear()
        val now = kstCalendar(nowMs)
        c.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 1, 0, 0, 0)
        c.add(Calendar.MONTH, 1)
        return c.timeInMillis
    }

    /** 초기화까지 남은 일수(올림, 최소 0). */
    fun daysUntil(resetsAtMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val diff = resetsAtMs - nowMs
        if (diff <= 0) return 0
        return ((diff + DAY_MS - 1) / DAY_MS).toInt()
    }

    /** 서버의 ISO8601(UTC) 문자열 파싱. 실패 시 null. */
    fun parseIso(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            val f = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val d = runCatching { f.parse(text) }.getOrNull()
            if (d != null) return d.time
        }
        return null
    }
}
