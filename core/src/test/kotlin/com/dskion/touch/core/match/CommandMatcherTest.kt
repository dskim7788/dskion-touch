package com.dskion.touch.core.match

import com.dskion.touch.core.model.ScreenNode
import com.dskion.touch.core.model.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandMatcherTest {

    private fun node(
        text: String? = null,
        resourceId: String? = null,
        clickable: Boolean = true,
    ) = ScreenNode(
        text = text,
        resourceId = resourceId,
        className = "android.widget.Button",
        boundsLeft = 0,
        boundsTop = 0,
        boundsRight = 100,
        boundsBottom = 50,
        isClickable = clickable,
        isEnabled = true,
    )

    // A fabricated fixture standing in for a real 배민/쿠팡이츠 dump until one is supplied.
    private val acceptScreenProfile = ScreenProfile(
        id = "order_offer",
        packageName = "com.example.rider",
        requiredSignatures = listOf(NodeSignature(resourceId = "com.example.rider:id/order_offer_title")),
    )

    private val acceptRule = CommandRule(
        screenProfileId = "order_offer",
        spokenPhrases = setOf("수락", "콜 받아"),
        targetSignature = NodeSignature(resourceId = "com.example.rider:id/accept_button"),
    )

    private val autoAcceptRule = CommandRule(
        screenProfileId = "order_offer",
        targetSignature = NodeSignature(resourceId = "com.example.rider:id/accept_button"),
        isAutoTrigger = true,
    )

    private fun snapshotWithOfferScreen() = ScreenSnapshot(
        packageName = "com.example.rider",
        nodes = listOf(
            node(text = "새 배차", resourceId = "com.example.rider:id/order_offer_title"),
            node(text = "수락하기", resourceId = "com.example.rider:id/accept_button"),
            node(text = "거절", resourceId = "com.example.rider:id/reject_button"),
        ),
        timestampMillis = 0L,
    )

    @Test
    fun `identifies the screen from its required signature`() {
        val matcher = CommandMatcher(listOf(acceptScreenProfile), listOf(acceptRule))
        val profile = matcher.identifyScreen(snapshotWithOfferScreen())
        assertEquals("order_offer", profile?.id)
    }

    @Test
    fun `unknown screen does not match any profile`() {
        val matcher = CommandMatcher(listOf(acceptScreenProfile), listOf(acceptRule))
        val unrelated = ScreenSnapshot("com.other.app", emptyList(), 0L)
        assertNull(matcher.identifyScreen(unrelated))
    }

    @Test
    fun `spoken phrase matches the right command and resolves its target node`() {
        val matcher = CommandMatcher(listOf(acceptScreenProfile), listOf(acceptRule))
        val target = matcher.match("수락", snapshotWithOfferScreen())
        assertEquals("com.example.rider:id/accept_button", target?.resourceId)
    }

    @Test
    fun `spoken phrase can be a substring of a longer recognized utterance`() {
        val matcher = CommandMatcher(listOf(acceptScreenProfile), listOf(acceptRule))
        val target = matcher.match("콜 받아 줘", snapshotWithOfferScreen())
        assertEquals("com.example.rider:id/accept_button", target?.resourceId)
    }

    @Test
    fun `unmatched phrase returns null`() {
        val matcher = CommandMatcher(listOf(acceptScreenProfile), listOf(acceptRule))
        assertNull(matcher.match("아무말", snapshotWithOfferScreen()))
    }

    @Test
    fun `null spoken text only considers auto-trigger rules`() {
        val matcher = CommandMatcher(listOf(acceptScreenProfile), listOf(acceptRule, autoAcceptRule))
        val target = matcher.match(null, snapshotWithOfferScreen())
        assertEquals("com.example.rider:id/accept_button", target?.resourceId)
    }

    @Test
    fun `auto mode does not fire when only a manual rule exists`() {
        val matcher = CommandMatcher(listOf(acceptScreenProfile), listOf(acceptRule))
        assertNull(matcher.match(null, snapshotWithOfferScreen()))
    }
}
