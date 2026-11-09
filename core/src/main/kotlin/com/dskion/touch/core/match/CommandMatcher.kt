package com.dskion.touch.core.match

import com.dskion.touch.core.model.ScreenNode
import com.dskion.touch.core.model.ScreenSnapshot

/** Pure matching engine: screen snapshot (+ optional spoken text) in, target node out. No Android deps. */
class CommandMatcher(
    private val profiles: List<ScreenProfile>,
    private val rules: List<CommandRule>,
) {
    fun identifyScreen(snapshot: ScreenSnapshot): ScreenProfile? =
        profiles.firstOrNull { it.matches(snapshot) }

    /**
     * [spokenText] null means 배달 모드 (auto trigger) - only [CommandRule.isAutoTrigger] rules
     * are considered. Otherwise matches 일반 모드 - the first non-auto rule whose phrase the
     * spoken text contains.
     */
    fun match(spokenText: String?, snapshot: ScreenSnapshot): ScreenNode? {
        val profile = identifyScreen(snapshot) ?: return null
        val candidates = rules.filter { it.screenProfileId == profile.id }

        val rule = if (spokenText == null) {
            candidates.firstOrNull { it.isAutoTrigger }
        } else {
            val normalized = spokenText.trim()
            candidates.firstOrNull { rule ->
                !rule.isAutoTrigger && rule.spokenPhrases.any(normalized::contains)
            }
        } ?: return null

        return snapshot.nodes.firstOrNull(rule.targetSignature::matches)
    }
}
