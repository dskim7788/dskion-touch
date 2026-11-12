package com.dskion.touch.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Both [AiManagerClient] and [GeminiTtsClient] fail silently by design (the caller falls back to
 * scripted replies/local TTS so a ride never goes quiet over a bad request) - which also means
 * there was no way to tell *why* the AI features stopped giving real answers (expired/invalid
 * key, a renamed/retired model id, a network block, ...) without device logcat access. This holds
 * the last failure from either client so Settings can show it.
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
}
