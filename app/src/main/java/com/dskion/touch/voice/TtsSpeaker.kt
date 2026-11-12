package com.dskion.touch.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Thin wrapper over the built-in TextToSpeech engine - spoken feedback for voice commands
 * that act while the user is looking at another app, not our own screen. */
class TtsSpeaker(context: Context) {

    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.KOREAN
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dskion_touch_tts")
    }

    /** Same as [speak], but suspends until the engine reports the utterance finished (or errors) -
     * needed wherever something starts listening again right after speaking, so the mic doesn't
     * pick up the tail end of this device's own voice. */
    suspend fun speakAndAwait(text: String) {
        if (!ready || tts == null) return
        suspendCancellableCoroutine<Unit> { continuation ->
            val utteranceId = "dskion_touch_tts_${System.nanoTime()}"
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                @Deprecated("Deprecated in the base class, still required to override")
                override fun onError(id: String?) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onError(id: String?, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }

    /** Differentiates the 매니저 personas without depending on which ko-KR voices happen to be
     * installed on the device (varies a lot by manufacturer/TTS engine) - a lower/higher pitch on
     * the same voice is a simple, reliably-available way to make the two personas sound distinct. */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
