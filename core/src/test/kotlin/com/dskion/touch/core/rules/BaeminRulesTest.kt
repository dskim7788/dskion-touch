package com.dskion.touch.core.rules

import com.dskion.touch.core.match.CommandMatcher
import com.dskion.touch.core.model.ScreenNode
import com.dskion.touch.core.model.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaeminRulesTest {

    // Trimmed down from a real node dump captured off the device (2026-11-10).
    private fun readyToStartSnapshot() = ScreenSnapshot(
        packageName = BaeminRules.PACKAGE_NAME,
        timestampMillis = 1_794_314_164_051L,
        nodes = listOf(
            ScreenNode(
                text = null,
                resourceId = "myPageButton",
                className = "android.view.ViewGroup",
                boundsLeft = 49, boundsTop = 170, boundsRight = 518, boundsBottom = 380,
                isClickable = true, isEnabled = true,
            ),
            ScreenNode(
                text = null,
                resourceId = "start-work-button",
                className = "android.view.ViewGroup",
                boundsLeft = 60, boundsTop = 2560, boundsRight = 1380, boundsBottom = 2789,
                isClickable = true, isEnabled = true,
            ),
            ScreenNode(
                text = "운행시작",
                resourceId = "bros-textview",
                className = "android.widget.TextView",
                boundsLeft = 568, boundsTop = 2616, boundsRight = 872, boundsBottom = 2732,
                isClickable = false, isEnabled = true,
            ),
        ),
    )

    private val matcher = CommandMatcher(BaeminRules.profiles, BaeminRules.rules)

    @Test
    fun `identifies the ready-to-start screen`() {
        assertEquals(BaeminRules.readyToStartScreen, matcher.identifyScreen(readyToStartSnapshot()))
    }

    @Test
    fun `운행 시작 matches the clickable start-work-button node, not the label`() {
        val target = matcher.match("운행 시작 눌러줘", readyToStartSnapshot())
        assertEquals("start-work-button", target?.resourceId)
        assertEquals(true, target?.isClickable)
    }

    @Test
    fun `unrelated speech does not match`() {
        assertNull(matcher.match("오늘 날씨 어때", readyToStartSnapshot()))
    }

    @Test
    fun `auto mode does not fire for this manual-only rule`() {
        assertNull(matcher.match(null, readyToStartSnapshot()))
    }
}
