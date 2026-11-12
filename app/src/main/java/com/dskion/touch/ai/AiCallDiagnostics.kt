package com.dskion.touch.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Both [AiManagerClient] and [GeminiTtsClient] fail silently by design (the caller falls back to
 * scripted replies/local TTS so a ride never goes quiet over a bad request) - which also means
 * there was no way to tell *why* the AI features stopped giving real answers (expired/invalid
 * key, a renamed/retired model id, a network block, a rate limit, ...) without device logcat
 * access. This holds the last failure from either client so Settings can show it.
 */
object AiCallDiagnostics {
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    fun record(source: String, detail: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        _lastError.value = "[$timestamp] $source: $detail"
    }

    fun clear() {
        _lastError.value = null
    }

    /** Turns a raw HTTP failure into something a non-developer can actually read - the server's
     * own JSON error body (what [record] used to be given verbatim) is accurate but looks exactly
     * like a crash dump. Pulls out just `error.message` when the body parses, and labels the most
     * common Gemini status codes so a 429 free-tier quota hit doesn't look like the same kind of
     * problem as a bad key or a missing model. */
    fun describeHttpError(status: Int, responseBody: String): String {
        val serverMessage = runCatching {
            JSONObject(responseBody).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val label = when (status) {
            429 -> "사용량 한도 초과(429) - 무료 티어 한도를 다 써서 그래요. 잠시 후(보통 1분~하루 이내) 다시 시도해보세요"
            401, 403 -> "인증 실패($status) - API 키가 잘못됐거나 만료됐을 수 있어요"
            404 -> "모델 또는 경로 없음(404) - 앱이 쓰는 모델 이름이 더 이상 없을 수 있어요"
            400 -> "잘못된 요청(400)"
            in 500..599 -> "Google 서버 오류($status) - 일시적인 문제일 가능성이 커요"
            else -> "HTTP $status"
        }
        return if (serverMessage != null) "$label - ${serverMessage.take(200)}" else label
    }
}
