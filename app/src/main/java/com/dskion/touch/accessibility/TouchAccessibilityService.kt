package com.dskion.touch.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dskion.touch.R
import com.dskion.touch.accessibility.command.AcceptDispatchCommand
import com.dskion.touch.accessibility.command.NewDispatchCommand
import com.dskion.touch.accessibility.command.RespondDispatchCommand
import com.dskion.touch.ai.AiManagerClient
import com.dskion.touch.ai.GeminiTtsClient
import com.dskion.touch.ai.GeminiVoices
import com.dskion.touch.core.match.CommandMatcher
import com.dskion.touch.core.model.ScreenSnapshot
import com.dskion.touch.core.rules.BaeminRules
import com.dskion.touch.data.AiChatRepository
import com.dskion.touch.data.CommandPhraseRepository
import com.dskion.touch.data.EditableCommand
import com.dskion.touch.data.ManagerPersona
import com.dskion.touch.data.ManagerRepository
import com.dskion.touch.data.ModeRepository
import com.dskion.touch.data.MonitoredAppsRepository
import com.dskion.touch.data.OperatingMode
import com.dskion.touch.overlay.VoiceOverlayController
import com.dskion.touch.voice.PcmPlayer
import com.dskion.touch.voice.TtsSpeaker
import com.dskion.touch.voice.VoiceRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads the foreground window's node tree and publishes it to [ScreenStateHolder] for the
 * Console screen to display, and can act on a recognized voice command by finding and clicking
 * the matching button on the current screen.
 *
 * Runs as a foreground service (microphone type) so [startVoiceCommand] keeps working after the
 * user switches away from our own app to the target app - a regular backgrounded Activity can't
 * use the microphone, but an accessibility service promoted to foreground can.
 */
class TouchAccessibilityService : AccessibilityService() {

    private var lastProcessedAtMillis = 0L
    private val phraseRepository = CommandPhraseRepository(this)
    private val modeRepository = ModeRepository(this)
    private val monitoredAppsRepository = MonitoredAppsRepository(this)
    private val managerRepository = ManagerRepository(this)
    private val aiChatRepository = AiChatRepository(this)
    private var managerVoiceChatEnabled = true
    private var currentPersona: ManagerPersona = ManagerPersona.FEMALE
    private var maleVoiceName: String = GeminiVoices.DEFAULT_MALE_VOICE
    private var femaleVoiceName: String = GeminiVoices.DEFAULT_FEMALE_VOICE

    /** Blank = AI 미설정, [managerReplyFor]가 기존 스크립트 대사로만 답한다. 설정에서 키를
     * 넣으면 실제 Gemini API(무료 티어)로 자유 대화가 된다 - 이 앱은 백엔드가 없어서 사용자
     * 본인 키를 기기에서 바로 쓴다. */
    private var managerApiKey: String = ""

    /** 매니저와의 대화 맥락 - [startManagerChatLoop]를 켤 때마다 비워서 새 대화로 시작한다.
     * 세션 메모리일 뿐 저장하지 않는다(계정/백엔드가 없음). */
    private val managerChatHistory = mutableListOf<Pair<String, String>>()
    private val newDispatchCommand = NewDispatchCommand(this)
    private val acceptDispatchCommand = AcceptDispatchCommand(this)
    private val respondDispatchCommand = RespondDispatchCommand(this)

    /** Without this, an uncaught exception from ANY one of the several independent
     * serviceScope.launch { repo.collect { ... } } blocks below (DataStore read hiccup, a bad
     * framework call, anything) propagates past SupervisorJob to the thread's default handler -
     * which on Android kills the whole app process, not just this one collector. Log and keep
     * going instead; a background service outliving a single bad event matters a lot more here
     * than surfacing that failure loudly. */
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(LOG_TAG, "Unhandled exception in a background collector", throwable)
    }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + serviceExceptionHandler)
    private lateinit var ttsSpeaker: TtsSpeaker
    private val overlayController by lazy {
        VoiceOverlayController(this, serviceScope, onTap = ::startVoiceCommand)
    }

    /** 배달 모드일 때 손을 떼지 않고도 쓸 수 있도록, 배민/쿠팡이츠가 화면에 떠 있는 동안
     * 계속 듣고 바로바로 명령을 처리하는 루프 - [currentMode]/[currentForegroundPackage]가
     * 바뀔 때마다 [updateContinuousListening]이 켜고 끈다. */
    private var currentMode: OperatingMode = OperatingMode.NORMAL
    private var currentForegroundPackage: String? = null
    private var continuousListeningJob: Job? = null

    /** Home 탭 "매니저와 대화하기" 버튼의 on/off 상태 - 켜면 [startManagerChatLoop]이 끌 때까지
     * 계속 듣고/답한다. 배달 모드의 [continuousListeningJob]과 마이크를 동시에 쓸 수 없어서
     * 서로 상대를 멈추고 시작한다 ([startContinuousListening]/[startManagerChatLoop] 참고). */
    private var managerChatJob: Job? = null

    /** 플로팅 버튼은 일반 모드의 수동 트리거일 뿐 - 배달 모드는 손 안 대고 계속 듣기 때문에
     * 버튼이 필요 없고, 다른 앱/홈 화면 위에 항상 떠 있는 게 오히려 거슬린다. 모드가 바뀌거나
     * 사용자가 홈 화면에서 직접 끄면 [updateOverlayVisibility]가 보이고/숨긴다. */
    private var overlayPrefEnabled = true

    /** The device's home/launcher package — switching apps via home or the recents overview
     * surfaces this package's window, which should never overwrite a captured target-app snapshot. */
    private val homePackageName: String? by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ttsSpeaker = TtsSpeaker(this)
        startAsForegroundService()
        ScreenStateHolder.setServiceRunning(true)
        serviceScope.launch {
            modeRepository.mode.collect { mode ->
                currentMode = mode
                updateContinuousListening()
                updateOverlayVisibility()
            }
        }
        serviceScope.launch {
            modeRepository.overlayEnabled.collect { enabled ->
                overlayPrefEnabled = enabled
                updateOverlayVisibility()
            }
        }
        serviceScope.launch {
            monitoredAppsRepository.monitoredPackages.collect { packages -> applyPackageFilter(packages) }
        }
        serviceScope.launch {
            managerRepository.persona.collect { persona ->
                currentPersona = persona
                ttsSpeaker.setPitch(if (persona == ManagerPersona.MALE) MALE_PITCH else FEMALE_PITCH)
            }
        }
        serviceScope.launch {
            managerRepository.voiceChatEnabled.collect { enabled ->
                managerVoiceChatEnabled = enabled
                if (!enabled) stopManagerChatLoop()
            }
        }
        serviceScope.launch {
            aiChatRepository.apiKey.collect { key -> managerApiKey = key }
        }
        serviceScope.launch {
            managerRepository.maleVoiceName.collect { name -> maleVoiceName = name }
        }
        serviceScope.launch {
            managerRepository.femaleVoiceName.collect { name -> femaleVoiceName = name }
        }
    }

    /** 일반 모드 + 사용자가 끄지 않았을 때만 플로팅 버튼을 띄운다. 배달 모드는 손 안 대고 계속
     * 듣기 때문에 버튼이 무의미하고, 다른 앱/홈 화면 위에 계속 떠 있으면 방해만 된다. */
    private fun updateOverlayVisibility() {
        runCatching {
            if (overlayPrefEnabled && currentMode == OperatingMode.NORMAL) {
                overlayController.show()
            } else {
                overlayController.hide()
            }
        }.onFailure { e -> Log.e(LOG_TAG, "Failed to update overlay visibility", e) }
    }

    /**
     * Restricts which apps this service even receives accessibility events for, at the OS level -
     * an app not in [packages] never generates an event for this service, so its on-screen text
     * (a banking app's balance, a password field, an OTP, ...) never reaches this process, not even
     * transiently. Always sets a concrete array, never null: null means "no filter" (every app),
     * which would silently undo this the moment the user removes the last entry.
     */
    private fun applyPackageFilter(packages: Set<String>) {
        runCatching {
            val info = serviceInfo ?: return@runCatching
            info.packageNames = packages.toTypedArray()
            serviceInfo = info
        }.onFailure { e -> Log.e(LOG_TAG, "Failed to apply package filter", e) }
    }

    /**
     * The manifest declares this service's foregroundServiceType as "microphone" - on API 34+,
     * starting a foreground service with that type while RECORD_AUDIO isn't granted throws a
     * SecurityException immediately, and this runs unconditionally at the very top of
     * [onServiceConnected], which the OS calls every time the (already-enabled from a previous
     * install) accessibility service reconnects - with no mic permission, that's a crash loop on
     * every single process start, before anything else (including the app's own UI) gets a
     * chance to run. runCatching here is the actual fix, not just defense in depth: without mic
     * permission the service simply stays a regular (non-foreground) accessibility service
     * instead of taking the whole process down.
     */
    private fun startAsForegroundService() {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return@runCatching
            manager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "dskion touch 서비스", NotificationManager.IMPORTANCE_LOW),
            )
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.service_status_title))
                .setContentText(getString(R.string.service_status_on))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()
            val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && micGranted) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { e -> Log.e(LOG_TAG, "Failed to start foreground service", e) }
    }

    /**
     * Counts down briefly (just long enough that the recognizer doesn't pick up the tap/vibration
     * that triggered it - the floating overlay button means the user is typically already in the
     * target app when this starts, unlike the Console tab's test button which needed more time to
     * switch apps), listens for one spoken command, and tries to act on it. Publishes progress to
     * [ScreenStateHolder.voiceStatus] and [ScreenStateHolder.isListening] (the latter drives the
     * overlay button's color).
     */
    fun startVoiceCommand() {
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_mic_required))
            return
        }
        if (ScreenStateHolder.isListening.value) return

        ScreenStateHolder.setListening(true)
        vibrate(LISTEN_START_VIBRATION_MILLIS)
        serviceScope.launch {
            for (remaining in VOICE_COMMAND_COUNTDOWN_SECONDS downTo 1) {
                ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_countdown, remaining))
                delay(1000)
            }
            ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_listening))
            VoiceRecognizer(this@TouchAccessibilityService).startListening(
                onResult = { text ->
                    serviceScope.launch {
                        try {
                            handleRecognizedText(text)
                        } finally {
                            ScreenStateHolder.setListening(false)
                        }
                    }
                },
                onError = { message ->
                    ScreenStateHolder.setVoiceStatus(message)
                    ScreenStateHolder.setListening(false)
                    vibrate()
                },
            )
        }
    }

    /**
     * The "매니저와 대화하기" button on the Home tab - an on/off toggle rather than a
     * press-per-utterance trigger. Turning it on starts a hands-free listen→reply→listen loop
     * that keeps running until turned off again, so the rider doesn't have to tap the button
     * before every single thing they say to the manager.
     */
    fun toggleManagerChat() {
        if (managerChatJob?.isActive == true) {
            stopManagerChatLoop()
            return
        }
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_mic_required))
            return
        }
        if (!managerVoiceChatEnabled) {
            ScreenStateHolder.setVoiceStatus(getString(R.string.home_manager_chat_disabled))
            return
        }
        startManagerChatLoop()
    }

    /** Same mic, so this and the 배달 모드 hands-free loop can't run at once - starting one
     * always stops the other first. */
    private fun startManagerChatLoop() {
        if (managerChatJob?.isActive == true) return
        stopContinuousListening()
        managerChatHistory.clear()
        vibrate(LISTEN_START_VIBRATION_MILLIS)
        ScreenStateHolder.setManagerChatActive(true)
        managerChatJob = serviceScope.launch {
            // 켜자마자 매니저가 먼저 반갑게 인사부터 - 사용자가 뭔가 말해야 반응하는 게 아니라
            // 대화가 시작됐다는 걸 바로 알 수 있게.
            val greeting = managerReplyFor(null)
            ScreenStateHolder.setVoiceStatus(greeting)
            speakManagerReply(greeting)

            while (isActive) {
                try {
                    val micGranted = ContextCompat.checkSelfPermission(this@TouchAccessibilityService, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (!micGranted) {
                        ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_mic_required))
                        delay(MIC_PERMISSION_RECHECK_MILLIS)
                        continue
                    }

                    ScreenStateHolder.setListening(true)
                    val text = awaitOneRecognitionResult()
                    ScreenStateHolder.setListening(false)
                    // 침묵/미인식은 그냥 넘어간다 - 1초마다 "못 들었어요"를 말하면 짜증나니까,
                    // 실제로 뭔가 알아들었을 때만 답한다.
                    if (text != null) {
                        val reply = managerReplyFor(text)
                        ScreenStateHolder.setVoiceStatus(reply)
                        speakManagerReply(reply)
                        vibrate()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ScreenStateHolder.setListening(false)
                }
                delay(CONTINUOUS_LISTEN_GAP_MILLIS)
            }
        }
    }

    private fun stopManagerChatLoop() {
        managerChatJob?.cancel()
        managerChatJob = null
        ScreenStateHolder.setManagerChatActive(false)
        if (ScreenStateHolder.isListening.value) ScreenStateHolder.setListening(false)
    }

    /** [text] null = 대화를 막 시작해서 매니저가 먼저 인사를 건네야 하는 경우
     * ([startManagerChatLoop] 참고). AI 키가 설정돼 있으면 실제 Gemini API로 자유롭게
     * 대화하고(인사도 AI가 자연스럽게 지어서 하도록), 실패하거나(네트워크 오류, 타임아웃 등)
     * 키가 없으면 기존 스크립트 대사로 자연스럽게 떨어진다 - 라이딩 중에는 "AI 응답 실패"보다
     * 뭐라도 짧게 답해주는 게 낫다. */
    private suspend fun managerReplyFor(text: String?): String {
        val apiKey = managerApiKey
        if (apiKey.isBlank()) return managerReplyForScripted(text)

        val userMessage = text ?: GREETING_PROMPT
        val result = AiManagerClient.complete(
            apiKey = apiKey,
            systemPrompt = buildManagerSystemPrompt(),
            history = managerChatHistory.toList(),
            userMessage = userMessage,
        )
        return result.fold(
            onSuccess = { reply ->
                managerChatHistory.add("user" to userMessage)
                managerChatHistory.add("assistant" to reply)
                while (managerChatHistory.size > MAX_MANAGER_HISTORY_ENTRIES) managerChatHistory.removeAt(0)
                reply
            },
            onFailure = { managerReplyForScripted(text) },
        )
    }

    /** Speaks a 매니저 line with Gemini's natural TTS voice when an AI key is configured (a
     * different prebuilt voice per persona), falling back to the device's own TextToSpeech if
     * that fails or no key is set. Always suspends until playback is actually done - the chat
     * loop starts listening again right after this returns, so the mic must not still be hearing
     * the manager's own voice. */
    private suspend fun speakManagerReply(text: String) {
        val apiKey = managerApiKey
        if (apiKey.isNotBlank()) {
            val voiceName = if (currentPersona == ManagerPersona.MALE) maleVoiceName else femaleVoiceName
            val result = GeminiTtsClient.synthesize(apiKey, text, voiceName)
            if (result.isSuccess) {
                val audio = result.getOrThrow()
                PcmPlayer.play(audio.pcm, audio.sampleRateHz)
                return
            }
        }
        ttsSpeaker.speakAndAwait(text)
    }

    private fun managerReplyForScripted(text: String?): String {
        if (text == null) return ManagerLines.forGreeting()
        val normalized = text.replace(" ", "")
        val asksForStats = listOf("몇건", "오늘", "수락", "거절", "확인", "리뷰").any(normalized::contains)
        return if (asksForStats) RideStatsHolder.summary() else ManagerLines.forGreeting()
    }

    /** AI가 짧고 자연스럽게, 라이더의 현재 상황(핸들에서 손을 못 떼는 중, 화면을 못 보는 중)을
     * 감안해서 답하도록 하는 시스템 프롬프트. */
    private fun buildManagerSystemPrompt(): String = """
        당신은 배달 라이더용 음성 보조 앱 "dskion touch"의 AI 매니저 "${currentPersona.displayName}"입니다.
        지금 사용자는 전기자전거를 타고 두 손을 핸들에서 떼지 않은 채 음성만으로 당신과 대화하고 있고,
        화면을 보거나 타이핑할 수 없습니다.
        - 답변은 항상 1~2문장으로 짧고 자연스럽게 말하세요. 길게 설명하지 마세요.
        - 배달 라이더의 하루를 챙기는 다정한 비서처럼 칭찬, 격려, 가벼운 수다, 안전 운행 당부를 해주세요.
        - 의학적/법률적 조언이나 확실하지 않은 사실은 지어내지 말고 모른다고 말하세요.
        - 오늘 이 앱 세션에서 집계된 기록(기기 재시작 시 초기화되는 임시 집계): ${RideStatsHolder.summary()}
    """.trimIndent()

    /** Starts/stops the hands-free loop based on the two things that decide whether it should be
     * running right now: 배달 모드 selected, and 배민/쿠팡이츠 actually in the foreground. Call
     * whenever either changes - it's a no-op if the desired state already matches. */
    private fun updateContinuousListening() {
        val shouldListen = currentMode == OperatingMode.DELIVERY && currentForegroundPackage in TARGET_PACKAGES
        if (shouldListen) startContinuousListening() else stopContinuousListening()
    }

    /**
     * The actual hands-free loop: listen, act on whatever was said (if anything), briefly pause,
     * repeat - for as long as [updateContinuousListening] keeps it alive. No countdown and no
     * vibration/status noise for silence or unmatched speech (that would be constant and
     * exhausting over a whole delivery) - only a real command execution gives feedback, same as
     * [handleRecognizedText]'s existing vibrate/TTS calls for matches and dispatch actions.
     */
    private fun startContinuousListening() {
        if (continuousListeningJob?.isActive == true) return
        stopManagerChatLoop()
        continuousListeningJob = serviceScope.launch {
            while (isActive) {
                try {
                    val micGranted = ContextCompat.checkSelfPermission(this@TouchAccessibilityService, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (!micGranted) {
                        ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_mic_required))
                        delay(MIC_PERMISSION_RECHECK_MILLIS)
                        continue
                    }

                    ScreenStateHolder.setListening(true)
                    val text = awaitOneRecognitionResult()
                    ScreenStateHolder.setListening(false)
                    handleRecognizedText(text, silent = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the shift-long loop alive through an unexpected failure (a malformed
                    // node tree, a recognizer hiccup, ...) instead of silently going dead until the
                    // next app-switch/mode-change happens to restart it.
                    ScreenStateHolder.setListening(false)
                }
                delay(CONTINUOUS_LISTEN_GAP_MILLIS)
            }
        }
    }

    private fun stopContinuousListening() {
        continuousListeningJob?.cancel()
        continuousListeningJob = null
        if (ScreenStateHolder.isListening.value) ScreenStateHolder.setListening(false)
    }

    private suspend fun awaitOneRecognitionResult(): String? = suspendCancellableCoroutine { continuation ->
        VoiceRecognizer(this).startListening(
            onResult = { text -> if (continuation.isActive) continuation.resume(text) },
            onError = { if (continuation.isActive) continuation.resume(null) },
        )
    }

    /** [silent] suppresses the vibrate+status update for "didn't understand"/"no speech" - used by
     * the continuous hands-free loop, where that would otherwise buzz constantly over a whole
     * delivery. A real command (matched here, or one of the dispatch actions above) always gives
     * feedback either way, silent or not. */
    private suspend fun handleRecognizedText(text: String?, silent: Boolean = false) {
        if (text != null) {
            val normalized = text.replace(" ", "")

            // "매니저" 호출 - 비서 역할. 설정에서 AI 키를 넣었으면 managerReplyFor가 실제
            // Gemini API로 자유 대화를 하고, 안 넣었으면 세션 집계 읽어주기/인사 같은 정해진
            // 대사로 답한다 (managerReplyForScripted).
            if (normalized.contains("매니저") && managerVoiceChatEnabled) {
                val reply = managerReplyFor(text)
                ScreenStateHolder.setVoiceStatus(reply)
                speakManagerReply(reply)
                vibrate()
                return
            }

            // User-added synonym phrases (명령어 탭에서 편집) always win first, on top of each
            // command's own hardcoded keyword logic below.
            val customHandlers = listOf(
                EditableCommand.NEW_DISPATCH_ON to {
                    newDispatchCommand.execute(true) { m -> reportDispatchResult(text, m, ManagerLines.forToggle()) }
                },
                EditableCommand.NEW_DISPATCH_OFF to {
                    newDispatchCommand.execute(false) { m -> reportDispatchResult(text, m, ManagerLines.forToggle()) }
                },
                EditableCommand.ACCEPT_DISPATCH to {
                    acceptDispatchCommand.execute { m -> reportDispatchResult(text, m) }
                },
                EditableCommand.RESPOND_ACCEPT to { respondAndReport(accept = true, text = text) },
                EditableCommand.RESPOND_REJECT to { respondAndReport(accept = false, text = text) },
            )
            for ((command, action) in customHandlers) {
                val extra = phraseRepository.extraPhrasesSnapshot(command)
                if (extra.any { normalized.contains(it.replace(" ", "")) }) {
                    action()
                    return
                }
            }

            val dispatchIntent = newDispatchCommand.parseIntent(text)
            if (dispatchIntent != null) {
                newDispatchCommand.execute(dispatchIntent) { resultMessage ->
                    reportDispatchResult(text, resultMessage, ManagerLines.forToggle())
                }
                return
            }
            if (acceptDispatchCommand.parseIntent(text)) {
                acceptDispatchCommand.execute { resultMessage -> reportDispatchResult(text, resultMessage) }
                return
            }
            val respondIntent = respondDispatchCommand.parseIntent(text)
            if (respondIntent != null) {
                respondAndReport(accept = respondIntent, text = text)
                return
            }
        }

        // The match attempt needs the target app in the foreground at the exact moment it runs -
        // a visible/audible "done, safe to switch back" cue matters more than any screen text,
        // since the user has no overlay to watch yet.
        if (text != null && tryPerformCommand(text)) {
            ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_matched, text))
            if (text.replace(" ", "").contains("시작")) {
                RideStatsHolder.recordStartWork()
                if (managerVoiceChatEnabled) ttsSpeaker.speak(ManagerLines.forStartWork())
            }
            vibrate()
        } else if (!silent) {
            ScreenStateHolder.setVoiceStatus(
                if (text == null) {
                    getString(R.string.console_voice_no_speech)
                } else {
                    getString(R.string.console_voice_not_matched, text)
                },
            )
            vibrate()
        }
    }

    /** 수락/거절은 실제로 "일" 개수를 세는 두 경로뿐이라, 여기서 [RideStatsHolder]에 기록하고
     * 매니저가 켜져 있으면 칭찬/위로 한마디를 결과 뒤에 붙인다. */
    private fun respondAndReport(accept: Boolean, text: String) {
        if (accept) RideStatsHolder.recordAccept() else RideStatsHolder.recordReject()
        val managerLine = if (managerVoiceChatEnabled) {
            if (accept) ManagerLines.forAccept() else ManagerLines.forReject()
        } else {
            null
        }
        respondDispatchCommand.execute(accept) { resultMessage -> reportDispatchResult(text, resultMessage, managerLine) }
    }

    private fun reportDispatchResult(spokenText: String, resultMessage: String, managerLine: String? = null) {
        val fullMessage = if (managerLine != null) "$resultMessage $managerLine" else resultMessage
        ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_dispatch_result, spokenText, fullMessage))
        ttsSpeaker.speak(fullMessage)
        vibrate()
    }

    private fun vibrate(durationMillis: Long = 150L) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Re-reads the current screen, matches [spokenText] (null = 배달 모드 auto-trigger) against
     * the known rules (plus any 명령어 탭 custom phrases), and clicks the target node if one is
     * found. Returns whether a click was attempted — not a guarantee the target app acted on it.
     */
    private suspend fun tryPerformCommand(spokenText: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString() ?: return false
        val snapshot = ScreenSnapshot(
            packageName = packageName,
            nodes = root.toScreenNodes(),
            timestampMillis = System.currentTimeMillis(),
        )
        val target = buildMatcher().match(spokenText, snapshot) ?: return false

        val liveNode = target.resourceId?.let { root.findByResourceId(it) }
        if (liveNode != null && liveNode.isClickable && liveNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return tapAt(target.centerX, target.centerY)
    }

    private suspend fun buildMatcher(): CommandMatcher {
        val extraStartWork = phraseRepository.extraPhrasesSnapshot(EditableCommand.START_WORK)
        val hiddenStartWork = phraseRepository.hiddenDefaultsSnapshot(EditableCommand.START_WORK)
        val startWorkPhrases = (BaeminRules.startWorkRule.spokenPhrases - hiddenStartWork) + extraStartWork
        val startWorkRule = if (startWorkPhrases == BaeminRules.startWorkRule.spokenPhrases) {
            BaeminRules.startWorkRule
        } else {
            BaeminRules.startWorkRule.copy(spokenPhrases = startWorkPhrases)
        }
        return CommandMatcher(BaeminRules.profiles, listOf(startWorkRule, BaeminRules.toggleNewDispatchRule))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastProcessedAtMillis < DEBOUNCE_MILLIS) return
        lastProcessedAtMillis = now

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return

        if (packageName != currentForegroundPackage) {
            currentForegroundPackage = packageName
            updateContinuousListening()
        }

        // Skip our own app and the launcher (home screen / recents overview) so switching
        // between apps doesn't overwrite the captured target-app snapshot with a transit screen.
        if (packageName == applicationContext.packageName) return
        if (packageName == homePackageName || packageName == "com.android.systemui") return
        val snapshot = ScreenSnapshot(
            packageName = packageName,
            nodes = root.toScreenNodes(),
            timestampMillis = System.currentTimeMillis(),
        )
        ScreenStateHolder.updateSnapshot(snapshot)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousListening()
        stopManagerChatLoop()
        overlayController.hide()
        serviceScope.cancel()
        if (::ttsSpeaker.isInitialized) ttsSpeaker.shutdown()
        instance = null
        ScreenStateHolder.setServiceRunning(false)
    }

    companion object {
        private const val LOG_TAG = "TouchAccessibility"
        private const val DEBOUNCE_MILLIS = 300L
        private const val VOICE_COMMAND_COUNTDOWN_SECONDS = 1
        private const val LISTEN_START_VIBRATION_MILLIS = 50L
        private const val MIC_PERMISSION_RECHECK_MILLIS = 3000L
        private const val CONTINUOUS_LISTEN_GAP_MILLIS = 400L
        private const val MALE_PITCH = 0.85f
        private const val FEMALE_PITCH = 1.15f

        /** user+assistant 합쳐서 최대 이만큼만 맥락으로 들고 간다 - 라이딩 내내 무한히 쌓이면
         * 매 요청이 점점 느려지고(토큰 수 증가) 비용도 늘어난다. */
        private const val MAX_MANAGER_HISTORY_ENTRIES = 12

        /** [managerReplyFor]가 text=null(대화 시작)일 때 AI에게 보내는 가짜 "사용자 메시지" -
         * 대화 기록에도 그대로 남아 이후 맥락이 자연스럽게 이어진다. */
        private const val GREETING_PROMPT = "(대화를 막 시작했어요. 먼저 반갑게 인사해주세요.)"
        private const val NOTIFICATION_CHANNEL_ID = "touch_service"
        private const val NOTIFICATION_ID = 1

        /** 배달 모드에서 손 안 대고 계속 듣기가 켜지는 대상 앱. 쿠팡이츠는 아직 명령어가
         * 연결돼 있지 않지만(배민만 구현됨) 포함해 둬도 무해함 - 매칭되는 게 없으면 그냥
         * 조용히 지나간다 ([handleRecognizedText]의 silent=true). */
        private val TARGET_PACKAGES = setOf(BaeminRules.PACKAGE_NAME, "com.coupang.mobile.eats.courier")

        var instance: TouchAccessibilityService? = null
            private set
    }
}
