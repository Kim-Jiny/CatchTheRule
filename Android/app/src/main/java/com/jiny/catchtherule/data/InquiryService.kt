package com.jiny.catchtherule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** CatchTheRule 문의(로그인 없음, deviceId 기반). */
@Serializable
data class Inquiry(
    val id: Int,
    val content: String,
    val status: String,
    val reply: String? = null,
    @SerialName("replied_at") val repliedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isReplied: Boolean get() = status == "replied"
}

/** 랭킹·문의가 공유하는 기기 식별자(영속 UUID). 개인정보 비식별. */
object CtrDevice {
    // 최초 실행 시 analytics·ranking·IAP 검증이 동시에 id() 를 호출하면 서로 다른 UUID 를
    // 생성·저장(last-writer-wins)해 기기 행이 중복될 수 있다. 생성+저장을 원자화한다.
    @Synchronized
    fun id(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("ctr_device", Context.MODE_PRIVATE)
        prefs.getString("id", null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString("id", fresh).apply()
        return fresh
    }
}

class InquiryService(context: Context) {
    private val appContext = context.applicationContext
    private val baseUrl = "https://duo.jiny.shop"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun submit(content: String, nickname: String?) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SubmitBody(CtrDevice.id(appContext), nickname, content))
        request("POST", "/api/catchtherule/inquiries", body)
        Unit
    }

    suspend fun myInquiries(): List<Inquiry> = withContext(Dispatchers.IO) {
        val deviceId = CtrDevice.id(appContext)
        val text = request("GET", "/api/catchtherule/inquiries?deviceId=$deviceId", null)
        json.decodeFromString<ListResponse>(text).inquiries
    }

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
    private data class SubmitBody(val deviceId: String, val nickname: String?, val content: String)

    @Serializable
    private data class ListResponse(val inquiries: List<Inquiry>)
}
