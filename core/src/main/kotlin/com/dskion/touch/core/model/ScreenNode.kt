package com.dskion.touch.core.model

/** One interactive/text node read off an accessibility tree, stripped to what matching needs. */
data class ScreenNode(
    val text: String?,
    val resourceId: String?,
    val className: String?,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val isClickable: Boolean,
    val isEnabled: Boolean,
) {
    val centerX: Int get() = (boundsLeft + boundsRight) / 2
    val centerY: Int get() = (boundsTop + boundsBottom) / 2
}
