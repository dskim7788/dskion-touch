package com.dskion.touch.core.match

import com.dskion.touch.core.model.ScreenSnapshot

/** Identifies one known screen within a target app by node signatures that must all be present. */
data class ScreenProfile(
    val id: String,
    val packageName: String,
    val requiredSignatures: List<NodeSignature>,
) {
    fun matches(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.packageName != packageName) return false
        return requiredSignatures.all { signature -> snapshot.nodes.any(signature::matches) }
    }
}
