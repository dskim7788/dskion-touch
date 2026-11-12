package com.dskion.touch.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Plays headerless 16-bit mono PCM (as returned by [com.dskion.touch.ai.GeminiTtsClient]) and
 * suspends until playback should be finished. Awaiting matters here, not just nice-to-have: the
 * hands-free chat loop starts listening again right after speaking, and without waiting for
 * playback to actually finish, the mic would pick up the tail of the manager's own voice as if
 * the rider had just spoken.
 */
object PcmPlayer {
    private const val LOG_TAG = "PcmPlayer"
    private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM
    private const val PLAYBACK_TAIL_BUFFER_MILLIS = 150L

    suspend fun play(pcm: ByteArray, sampleRateHz: Int) {
        if (pcm.isEmpty()) return
        runCatching {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                maxOf(minBufferSize, pcm.size),
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            try {
                audioTrack.write(pcm, 0, pcm.size)
                audioTrack.play()
                val durationMillis = (pcm.size.toLong() * 1000L) / (sampleRateHz.toLong() * BYTES_PER_SAMPLE)
                delay(durationMillis + PLAYBACK_TAIL_BUFFER_MILLIS)
            } finally {
                audioTrack.stop()
                audioTrack.release()
            }
        }.onFailure { e -> Log.e(LOG_TAG, "PCM playback failed", e) }
    }
}
