package com.dskion.touch.core.model

/** A single read of the current foreground window's accessibility tree. */
data class ScreenSnapshot(
    val packageName: String,
    val nodes: List<ScreenNode>,
    val timestampMillis: Long,
)
