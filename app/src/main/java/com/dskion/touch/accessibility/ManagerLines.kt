package com.dskion.touch.accessibility

/** Short Korean praise/encouragement lines the 매니저 persona speaks after a real action (a
 * dispatch accepted/rejected, 신규배차 toggled, 운행 시작) - kept local/scripted, not an
 * open-ended chatbot, so there's no network call or API key involved. */
object ManagerLines {
    private val startWorkLines = listOf(
        "오늘도 화이팅! 안전 운행하세요.",
        "좋아요, 오늘 하루도 힘내봐요!",
        "출발이 좋네요. 조심히 다녀오세요!",
    )

    private val acceptLines = listOf(
        "좋아요, 잘하고 있어요!",
        "수락 완료! 안전하게 다녀오세요.",
        "굿 초이스! 화이팅이에요.",
    )

    private val rejectLines = listOf(
        "알겠어요, 다음 배차 기대해봐요.",
        "괜찮아요, 좋은 배차가 또 올 거예요.",
    )

    private val toggleLines = listOf(
        "설정 바꿔드렸어요.",
        "네, 반영했어요!",
    )

    private val greetingLines = listOf(
        "네, 말씀하세요!",
        "저 여기 있어요, 무엇을 도와드릴까요?",
        "네! 안전 운행하고 계시죠?",
    )

    fun forStartWork(): String = startWorkLines.random()
    fun forAccept(): String = acceptLines.random()
    fun forReject(): String = rejectLines.random()
    fun forToggle(): String = toggleLines.random()
    fun forGreeting(): String = greetingLines.random()
}
