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
import com.dskion.touch.core.match.CommandMatcher
import com.dskion.touch.core.model.ScreenSnapshot
import com.dskion.touch.core.rules.BaeminRules
import com.dskion.touch.data.CommandPhraseRepository
import com.dskion.touch.data.EditableCommand
import com.dskion.touch.data.ManagerPersona
import com.dskion.touch.data.ManagerRepository
import com.dskion.touch.data.ModeRepository
import com.dskion.touch.data.MonitoredAppsRepository
import com.dskion.touch.data.OperatingMode
import com.dskion.touch.overlay.VoiceOverlayController
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
    private var managerVoiceChatEnabled = true
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
                ttsSpeaker.setPitch(if (persona == ManagerPersona.MALE) MALE_PITCH else FEMALE_PITCH)
            }
        }
        serviceScope.launch {
            managerRepository.voiceChatEnabled.collect { enabled -> managerVoiceChatEnabled = enabled }
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

    private fun startAsForegroundService() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "dskion touch 서비스", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.service_status_title))
            .setContentText(getString(R.string.service_status_on))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
     * The explicit "매니저와 대화하기" button on the Home tab - listens once (no countdown, since
     * the user is already looking at the phone and just tapped a button, unlike the other voice
     * paths) and always treats whatever was said as directed at the manager, regardless of
     * whether it contains the "매니저" wake word - the button itself is the wake word here.
     */
    fun startManagerChat() {
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
        if (ScreenStateHolder.isListening.value) return

        ScreenStateHolder.setListening(true)
        vibrate(LISTEN_START_VIBRATION_MILLIS)
        VoiceRecognizer(this).startListening(
            onResult = { text ->
                val reply = managerReplyFor(text)
                ScreenStateHolder.setVoiceStatus(reply)
                ttsSpeaker.speak(reply)
                vibrate()
                ScreenStateHolder.setListening(false)
            },
            onError = { message ->
                ScreenStateHolder.setVoiceStatus(message)
                ScreenStateHolder.setListening(false)
                vibrate()
            },
        )
    }

    private fun managerReplyFor(text: String?): String {
        if (text == null) return ManagerLines.forGreeting()
        val normalized = text.replace(" ", "")
        val asksForStats = listOf("몇건", "오늘", "수락", "거절", "확인", "리뷰").any(normalized::contains)
        return if (asksForStats) RideStatsHolder.summary() else ManagerLines.forGreeting()
    }

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

            // "매니저" 호출 - 비서 역할. 세션 동안의 수락/거절 집계를 읽어주거나(물어보면),
            // 아니면 그냥 가벼운 인사로 응답한다. 로컬 스크립트 기반이라 외부 API/네트워크는
            // 쓰지 않는다 - 진짜 자유 대화가 아니라 정해진 대사 중 하나를 고르는 수준.
            if (normalized.contains("매니저") && managerVoiceChatEnabled) {
                val reply = managerReplyFor(text)
                ScreenStateHolder.setVoiceStatus(reply)
                ttsSpeaker.speak(reply)
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
