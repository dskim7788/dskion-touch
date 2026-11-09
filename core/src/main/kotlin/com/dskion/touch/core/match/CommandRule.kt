package com.dskion.touch.core.match

/**
 * One command mapped to a target button on a given screen.
 * [isAutoTrigger] rules fire on their own in 배달 모드 (no voice command needed);
 * non-auto rules are matched against recognized speech in 일반 모드.
 */
data class CommandRule(
    val screenProfileId: String,
    val spokenPhrases: Set<String> = emptySet(),
    val targetSignature: NodeSignature,
    val isAutoTrigger: Boolean = false,
) {
    init {
        require(isAutoTrigger || spokenPhrases.isNotEmpty()) {
            "A non-auto-trigger rule needs at least one spoken phrase"
        }
    }
}
