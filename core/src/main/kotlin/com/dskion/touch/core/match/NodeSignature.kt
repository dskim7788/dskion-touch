package com.dskion.touch.core.match

import com.dskion.touch.core.model.ScreenNode

/**
 * Matches a node by resource-id (preferred - far more stable release to release than
 * button text) and/or text. At least one field must be set.
 */
data class NodeSignature(
    val resourceId: String? = null,
    val textEquals: String? = null,
    val textContains: String? = null,
) {
    init {
        require(resourceId != null || textEquals != null || textContains != null) {
            "NodeSignature needs at least one of resourceId/textEquals/textContains"
        }
    }

    fun matches(node: ScreenNode): Boolean {
        if (resourceId != null && node.resourceId != resourceId) return false
        if (textEquals != null && node.text != textEquals) return false
        if (textContains != null && node.text?.contains(textContains) != true) return false
        return true
    }
}
