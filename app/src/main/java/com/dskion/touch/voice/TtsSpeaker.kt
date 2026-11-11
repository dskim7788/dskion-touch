package com.dskion.touch.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

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
