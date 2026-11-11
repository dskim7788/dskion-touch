package com.dskion.touch.accessibility

/**
 * Session-only tallies (reset when the service restarts) of what actually happened this run -
 * no account/backend, so this is honest about not being a persistent delivery history. Backs the
 * 매니저's "오늘 몇 건" answer and decides when it has something to react to.
 */
object RideStatsHolder {
    var startWorkCount: Int = 0
        private set
    var acceptCount: Int = 0
        private set
    var rejectCount: Int = 0
        private set

    fun recordStartWork() {
        startWorkCount++
    }

    fun recordAccept() {
        acceptCount++
    }

    fun recordReject() {
        rejectCount++
    }

    fun summary(): String = "오늘 수락 ${acceptCount}건, 거절 ${rejectCount}건이에요"
}
