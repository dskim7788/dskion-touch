package com.dskion.touch.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Thin client for Google's Gemini API (generativelanguage.googleapis.com) - picked specifically
 * because it has a genuinely free tier (a key from aistudio.google.com, no card required),
 * unlike Anthropic's pay-as-you-go API. Called directly from the device with the user's own key
 * since this app has no backend/account system to hold it server-side. Deliberately minimal: one
 * short text completion, no tools/streaming - the reply goes straight into TTS while the rider is
 * on the bike, so it needs to come back quickly and stay short.
 */
object AiManagerClient {
    private const val LOG_TAG = "AiManagerClient"
    private const val MODEL = "gemini-2.0-flash"
    private const val MAX_OUTPUT_TOKENS = 300
    private const val CONNECT_TIMEOUT_MILLIS = 6000
    private const val READ_TIMEOUT_MILLIS = 20000

    suspend fun complete(
        apiKey: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Gemini는 "assistant" 대신 "model" 역할을 쓴다 - 나머지 코드는 전부 user/assistant로
            // 다루므로 여기서만 변환한다.
            val contents = JSONArray()
            for ((role, text) in history) {
                val geminiRole = if (role == "assistant") "model" else "user"
                contents.put(
                    JSONObject()
                        .put("role", geminiRole)
                        .put("parts", JSONArray().put(JSONObject().put("text", text))),
                )
            }
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", userMessage))),
            )

            val requestBody = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
                )
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("maxOutputTokens", MAX_OUTPUT_TOKENS))
                .toString()

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("content-type", "application/json")
            }

            connection.outputStream.use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { it.write(requestBody) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            if (status !in 200..299) {
                Log.e(LOG_TAG, "Gemini API error $status: $responseText")
                error("HTTP $status")
            }

            val candidates = JSONObject(responseText).optJSONArray("candidates") ?: JSONArray()
            val firstCandidate = candidates.optJSONObject(0) ?: error("empty reply")
            val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val reply = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val text = part.optString("text")
                if (text.isNotEmpty()) {
                    if (reply.isNotEmpty()) reply.append(" ")
                    reply.append(text)
                }
            }
            reply.toString().trim().ifEmpty { error("empty reply") }
        }.onFailure { e -> Log.e(LOG_TAG, "AI completion failed", e) }
    }
}
