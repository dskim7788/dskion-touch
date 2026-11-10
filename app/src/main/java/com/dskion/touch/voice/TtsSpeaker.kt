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

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
