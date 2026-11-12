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
 * Thin client for the Anthropic Messages API - same model/provider as 루틴 라이더의 "AI 루틴
 * 관리" 기능, called directly from the device with the user's own key since this app has no
 * backend/account system to hold it server-side instead. Deliberately minimal: one short text
 * completion, no tools, no streaming - the reply goes straight into TTS while the rider is on the
 * bike, so it needs to come back quickly and stay short.
 */
object AiManagerClient {
    private const val LOG_TAG = "AiManagerClient"
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-5-5"
    private const val MAX_TOKENS = 300
    private const val CONNECT_TIMEOUT_MILLIS = 6000
    private const val READ_TIMEOUT_MILLIS = 20000

    suspend fun complete(
        apiKey: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userMessage: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val messages = JSONArray()
            for ((role, content) in history) {
                messages.put(JSONObject().put("role", role).put("content", content))
            }
            messages.put(JSONObject().put("role", "user").put("content", userMessage))

            val requestBody = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", MAX_TOKENS)
                .put("system", systemPrompt)
                .put("messages", messages)
                .toString()

            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }

            connection.outputStream.use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { it.write(requestBody) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            if (status !in 200..299) {
                Log.e(LOG_TAG, "Anthropic API error $status: $responseText")
                error("HTTP $status")
            }

            val content = JSONObject(responseText).optJSONArray("content") ?: JSONArray()
            val reply = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") {
                    if (reply.isNotEmpty()) reply.append(" ")
                    reply.append(block.optString("text"))
                }
            }
            reply.toString().trim().ifEmpty { error("empty reply") }
        }.onFailure { e -> Log.e(LOG_TAG, "AI completion failed", e) }
    }
}
