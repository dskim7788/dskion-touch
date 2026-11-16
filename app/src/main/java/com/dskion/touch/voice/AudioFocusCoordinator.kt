package com.dskion.touch.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Requests audio focus around the manager's/dispatch-result's spoken lines, released again as
 * soon as that one utterance finishes. Without this, nothing ever told the system this app was
 * about to play audio, so a rider's music/YouTube/오디오북 app kept playing at full volume right
 * on top of our TTS/Gemini-voice output with no ducking or pause at all - both streams mixed
 * together at once, which is what sounded like "충돌"/interference. [AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK]
 * asks whatever's currently playing to just lower its volume (if it supports ducking) or briefly
 * pause for the few seconds our line takes, then resume normally - not a long-lived takeover, since
 * we always abandon focus right after each utterance completes.
 */
object AudioFocusCoordinator {

    private var focusRequest: AudioFocusRequest? = null
    private var legacyListener: AudioManager.OnAudioFocusChangeListener? = null

    private fun requestFocus(context: Context) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                val listener = AudioManager.OnAudioFocusChangeListener {}
                legacyListener = listener
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }
    }

    private fun abandonFocus(context: Context) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                legacyListener?.let {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(it)
                }
                legacyListener = null
            }
        }
    }

    /** Wraps one spoken line: requests focus, runs [block] (expected to suspend until that line
     * is fully done playing), then always releases focus - even if [block] throws or is
     * cancelled, so a dropped focus request never lingers and keeps ducking the rider's music. */
    suspend fun <T> withFocus(context: Context, block: suspend () -> T): T {
        requestFocus(context)
        try {
            return block()
        } finally {
            abandonFocus(context)
        }
    }
}
