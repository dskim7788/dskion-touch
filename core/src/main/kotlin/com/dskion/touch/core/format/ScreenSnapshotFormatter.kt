package com.dskion.touch.core.format

import com.dskion.touch.core.model.ScreenSnapshot

/** Plain-text dump of a [ScreenSnapshot], meant to be copied out of the Console screen and
 * pasted back so real 배민/쿠팡이츠 node data can be turned into [com.dskion.touch.core.match.ScreenProfile]s. */
object ScreenSnapshotFormatter {
    fun format(snapshot: ScreenSnapshot): String = buildString {
        appendLine("package: ${snapshot.packageName}")
        appendLine("timestampMillis: ${snapshot.timestampMillis}")
        appendLine("nodes (${snapshot.nodes.size}):")
        snapshot.nodes.forEachIndexed { index, node ->
            appendLine(
                "[$index] text=${node.text.quoteOrNull()} resId=${node.resourceId.quoteOrNull()} " +
                    "class=${node.className.quoteOrNull()} " +
                    "bounds=(${node.boundsLeft},${node.boundsTop},${node.boundsRight},${node.boundsBottom}) " +
                    "clickable=${node.isClickable} enabled=${node.isEnabled}",
            )
        }
    }

    private fun String?.quoteOrNull(): String = if (this == null) "null" else "\"$this\""
}
