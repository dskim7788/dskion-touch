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

    // Trimmed down from a real node dump captured off the device after "운행시작" was tapped.
    private fun waitingForDispatchSnapshot() = ScreenSnapshot(
        packageName = BaeminRules.PACKAGE_NAME,
        timestampMillis = 1_794_315_032_320L,
        nodes = listOf(
            ScreenNode(
                text = null,
                resourceId = "신규배차_켜기버튼",
                className = "android.view.ViewGroup",
                boundsLeft = 60, boundsTop = 185, boundsRight = 491, boundsBottom = 365,
                isClickable = true, isEnabled = true,
            ),
            ScreenNode(
                text = "신규배차",
                resourceId = "bros-textview",
                className = "android.widget.TextView",
                boundsLeft = 121, boundsTop = 241, boundsRight = 317, boundsBottom = 310,
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
    fun `identifies the waiting-for-dispatch screen and matches 신규배차 켜줘`() {
        val snapshot = waitingForDispatchSnapshot()
        assertEquals(BaeminRules.waitingForDispatchScreen, matcher.identifyScreen(snapshot))
        val target = matcher.match("신규배차 켜줘", snapshot)
        assertEquals("신규배차_켜기버튼", target?.resourceId)
    }

    @Test
    fun `ready-to-start phrase does not fire on the waiting-for-dispatch screen`() {
        assertNull(matcher.match("운행 시작", waitingForDispatchSnapshot()))
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
