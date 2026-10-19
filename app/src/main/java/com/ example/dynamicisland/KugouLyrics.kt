package com.example.dynamicisland

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object KugouLyrics {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(title: String, artist: String, durationMs: Long): List<LrcLine> =
        withContext(Dispatchers.IO) {
            try {
                val kw = URLEncoder.encode("$title $artist", "UTF-8")
                val searchUrl =
                    "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
                        "&keyword=$kw&duration=$durationMs&hash="
                val searchJson = http(searchUrl)
                val candidates = JSONObject(searchJson).optJSONArray("candidates")
                    ?: return@withContext emptyList()
                if (candidates.length() == 0) return@withContext emptyList()

                val first = candidates.getJSONObject(0)
                val id = first.getString("id")
                val key = first.getString("accesskey")

                val dlUrl =
                    "https://lyrics.kugou.com/download?ver=1&client=pc" +
                        "&id=$id&accesskey=$key&fmt=lrc&charset=utf8"
                val dlJson = http(dlUrl)
                val content = JSONObject(dlJson).optString("content")
                if (content.isBlank()) return@withContext emptyList()

                val lrc = String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
                LrcParser.parse(lrc)
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun http(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        return client.newCall(req).execute().use { it.body?.string() ?: "" }
    }
}

object LrcParser {
    private val RE = Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?](.*)""")

    fun parse(lrc: String): List<LrcLine> {
        val out = mutableListOf<LrcLine>()
        lrc.lineSequence().forEach { line ->
            val m = RE.find(line) ?: return@forEach
            val min = m.groupValues[1].toInt()
            val sec = m.groupValues[2].toInt()
            val msStr = m.groupValues[3].padEnd(3, '0').take(3).ifBlank { "000" }
            val ms = msStr.toInt()
            val text = m.groupValues[4].trim()
            if (text.isNotEmpty()) {
                out += LrcLine(min * 60_000L + sec * 1_000L + ms, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
