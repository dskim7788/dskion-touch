package com.dskion.touch.core.format

import com.dskion.touch.core.model.ScreenNode
import com.dskion.touch.core.model.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertTrue

class ScreenSnapshotFormatterTest {

    private fun node(text: String?, resourceId: String?, contentDescription: String? = null) = ScreenNode(
        text = text,
        resourceId = resourceId,
        className = "android.widget.Button",
        boundsLeft = 10,
        boundsTop = 20,
        boundsRight = 110,
        boundsBottom = 70,
        isClickable = true,
        isEnabled = true,
        contentDescription = contentDescription,
    )

    @Test
    fun `format includes package, node count, and each node's fields`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.rider",
            nodes = listOf(
                node(text = "수락하기", resourceId = "com.example.rider:id/accept_button"),
                node(text = null, resourceId = null, contentDescription = "수락 버튼"),
            ),
            timestampMillis = 1_000L,
        )

        val output = ScreenSnapshotFormatter.format(snapshot)

        assertTrue(output.contains("package: com.example.rider"))
        assertTrue(output.contains("nodes (2):"))
        assertTrue(output.contains("text=\"수락하기\""))
        assertTrue(output.contains("resId=\"com.example.rider:id/accept_button\""))
        assertTrue(output.contains("bounds=(10,20,110,70)"))
        assertTrue(output.contains("text=null"))
        assertTrue(output.contains("resId=null"))
        assertTrue(output.contains("desc=\"수락 버튼\""))
    }
}
