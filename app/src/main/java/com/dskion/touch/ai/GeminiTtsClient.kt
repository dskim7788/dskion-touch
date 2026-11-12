package com.dskion.touch.ai

import android.util.Base64
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
 * Generates natural-sounding speech from text via Gemini's native TTS model, instead of the
 * device's built-in (often robotic-sounding) TextToSpeech engine - same Gemini key as
 * [AiManagerClient] covers both the conversation and the voice. The endpoint returns headerless
 * 16-bit PCM audio, not a playable file format, so [com.dskion.touch.voice.PcmPlayer] plays it via
 * AudioTrack rather than MediaPlayer.
 */
object GeminiTtsClient {
    private const val LOG_TAG = "GeminiTtsClient"
    private const val MODEL = "gemini-2.5-flash-preview-tts"
    private const val CONNECT_TIMEOUT_MILLIS = 6000
    private const val READ_TIMEOUT_MILLIS = 20000
    private const val DEFAULT_SAMPLE_RATE_HZ = 24000

    data class PcmAudio(val pcm: ByteArray, val sampleRateHz: Int)

    suspend fun synthesize(apiKey: String, text: String, voiceName: String): Result<PcmAudio> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestBody = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))),
                        ),
                    )
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("responseModalities", JSONArray().put("AUDIO"))
                            .put(
                                "speechConfig",
                                JSONObject().put(
                                    "voiceConfig",
                                    JSONObject().put(
                                        "prebuiltVoiceConfig",
                                        JSONObject().put("voiceName", voiceName),
                                    ),
                                ),
                            ),
                    )
                    .toString()

                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey",
                )
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
                    Log.e(LOG_TAG, "Gemini TTS error $status: $responseText")
                    error("HTTP $status")
                }

                val inlineData = JSONObject(responseText)
                    .optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optJSONObject("inlineData")
                    ?: error("no audio in response")

                val base64Data = inlineData.optString("data").ifBlank { error("empty audio data") }
                val mimeType = inlineData.optString("mimeType")
                val sampleRate = Regex("rate=(\\d+)").find(mimeType)?.groupValues?.get(1)?.toIntOrNull()
                    ?: DEFAULT_SAMPLE_RATE_HZ
                val pcm = Base64.decode(base64Data, Base64.NO_WRAP)
                PcmAudio(pcm, sampleRate)
            }.onFailure { e -> Log.e(LOG_TAG, "TTS synthesis failed", e) }
        }
}
