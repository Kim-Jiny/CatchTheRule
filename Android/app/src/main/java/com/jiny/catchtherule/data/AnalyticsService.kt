package com.jiny.catchtherule.data

import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 익명 디바이스 핑(유저 카운팅/통계용). 로그인 없이 공유 deviceId 로 집계.
 * 앱 실행 시 1회 fire-and-forget. 실패해도 앱 동작에 영향 없음.
 */
object AnalyticsService {
    private const val BASE_URL = "https://duo.jiny.shop"
    private val json = Json { ignoreUnknownKeys = true }

    fun ping(context: Context) {
        val appContext = context.applicationContext
        Thread {
            runCatching {
                val version = runCatching {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                }.getOrNull()
                val country = Locale.getDefault().country.takeIf { it.length == 2 }
                val body = json.encodeToString(
                    PingBody(CtrDevice.id(appContext), "android", version, Build.VERSION.RELEASE, country)
                )
                val conn = (URL("$BASE_URL/api/catchtherule/devices/ping").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    // 응답 본문을 끝까지 읽어 소켓 keep-alive 재사용을 돕는다(성공/에러 모두).
                    (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }
        }.start()
    }

    @Serializable
    private data class PingBody(
        val deviceId: String,
        val platform: String,
        val appVersion: String?,
        val osVersion: String?,
        val country: String?,
    )
}
