package com.dskion.touch.ai

/** The prebuilt voices [GeminiTtsClient] can use (Gemini's TTS "voice options" list) - exposed so
 * Settings can let the user pick one per 매니저 persona instead of a hardcoded choice. [tone] is a
 * short Korean description of how Google's docs characterize each voice, just to help pick one. */
data class GeminiVoiceOption(val voiceName: String, val tone: String)

object GeminiVoices {
    const val DEFAULT_MALE_VOICE = "Charon"
    const val DEFAULT_FEMALE_VOICE = "Kore"

    val options: List<GeminiVoiceOption> = listOf(
        GeminiVoiceOption("Zephyr", "밝고 경쾌함"),
        GeminiVoiceOption("Puck", "활기참"),
        GeminiVoiceOption("Charon", "차분하고 설명적"),
        GeminiVoiceOption("Kore", "또렷하고 단단함"),
        GeminiVoiceOption("Fenrir", "역동적이고 흥분됨"),
        GeminiVoiceOption("Leda", "젊고 발랄함"),
        GeminiVoiceOption("Orus", "단단함"),
        GeminiVoiceOption("Aoede", "산뜻함"),
        GeminiVoiceOption("Callirrhoe", "느긋함"),
        GeminiVoiceOption("Autonoe", "밝음"),
        GeminiVoiceOption("Enceladus", "숨소리 섞인 톤"),
        GeminiVoiceOption("Iapetus", "또렷함"),
        GeminiVoiceOption("Umbriel", "느긋함"),
        GeminiVoiceOption("Algieba", "부드러움"),
        GeminiVoiceOption("Despina", "부드러움"),
        GeminiVoiceOption("Erinome", "또렷함"),
        GeminiVoiceOption("Algenib", "허스키함"),
        GeminiVoiceOption("Rasalgethi", "설명적"),
        GeminiVoiceOption("Laomedeia", "활기참"),
        GeminiVoiceOption("Achernar", "부드러움"),
        GeminiVoiceOption("Alnilam", "단단함"),
        GeminiVoiceOption("Schedar", "고르고 안정적"),
        GeminiVoiceOption("Gacrux", "성숙함"),
        GeminiVoiceOption("Pulcherrima", "당당함"),
        GeminiVoiceOption("Achird", "다정함"),
        GeminiVoiceOption("Zubenelgenubi", "캐주얼함"),
        GeminiVoiceOption("Vindemiatrix", "온화함"),
        GeminiVoiceOption("Sadachbia", "활발함"),
        GeminiVoiceOption("Sadaltager", "박식함"),
        GeminiVoiceOption("Sulafat", "따뜻함"),
    )
}
