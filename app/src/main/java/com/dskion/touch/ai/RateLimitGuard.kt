package com.dskion.touch.ai

import android.os.SystemClock

/**
 * After a 429 from a given Gemini endpoint, skip the network call entirely for a short cooldown
 * instead of immediately retrying something that just told us to back off. This matters most
 * during one active chat session: without it, every turn would re-attempt (and re-fail) the same
 * rate-limited call, adding latency for nothing and risking further throttling, instead of
 * falling back to the scripted reply/local TTS right away.
 */
object RateLimitGuard {
    private const val COOLDOWN_MILLIS = 60_000L
    private val cooldownUntil = mutableMapOf<String, Long>()

    fun isRateLimited(key: String): Boolean {
        val until = cooldownUntil[key] ?: return false
        return SystemClock.elapsedRealtime() < until
    }

    fun markRateLimited(key: String) {
        cooldownUntil[key] = SystemClock.elapsedRealtime() + COOLDOWN_MILLIS
    }
}
