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
import com.dskion.touch.data.ModeRepository
import com.dskion.touch.data.OperatingMode
import com.dskion.touch.overlay.VoiceOverlayController
import com.dskion.touch.voice.TtsSpeaker
import com.dskion.touch.voice.VoiceRecognizer
import kotlinx.coroutines.CancellationException
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
    private val newDispatchCommand = NewDispatchCommand(this)
    private val acceptDispatchCommand = AcceptDispatchCommand(this)
    private val respondDispatchCommand = RespondDispatchCommand(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
        overlayController.show()
        ScreenStateHolder.setServiceRunning(true)
        serviceScope.launch {
            modeRepository.mode.collect { mode ->
                currentMode = mode
                updateContinuousListening()
            }
        }
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
            // User-added synonym phrases (명령어 탭에서 편집) always win first, on top of each
            // command's own hardcoded keyword logic below.
            val customHandlers = listOf(
                EditableCommand.NEW_DISPATCH_ON to { newDispatchCommand.execute(true) { m -> reportDispatchResult(text, m) } },
                EditableCommand.NEW_DISPATCH_OFF to { newDispatchCommand.execute(false) { m -> reportDispatchResult(text, m) } },
                EditableCommand.ACCEPT_DISPATCH to { acceptDispatchCommand.execute { m -> reportDispatchResult(text, m) } },
                EditableCommand.RESPOND_ACCEPT to { respondDispatchCommand.execute(true) { m -> reportDispatchResult(text, m) } },
                EditableCommand.RESPOND_REJECT to { respondDispatchCommand.execute(false) { m -> reportDispatchResult(text, m) } },
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
                newDispatchCommand.execute(dispatchIntent) { resultMessage -> reportDispatchResult(text, resultMessage) }
                return
            }
            if (acceptDispatchCommand.parseIntent(text)) {
                acceptDispatchCommand.execute { resultMessage -> reportDispatchResult(text, resultMessage) }
                return
            }
            val respondIntent = respondDispatchCommand.parseIntent(text)
            if (respondIntent != null) {
                respondDispatchCommand.execute(respondIntent) { resultMessage -> reportDispatchResult(text, resultMessage) }
                return
            }
        }

        // The match attempt needs the target app in the foreground at the exact moment it runs -
        // a visible/audible "done, safe to switch back" cue matters more than any screen text,
        // since the user has no overlay to watch yet.
        if (text != null && tryPerformCommand(text)) {
            ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_matched, text))
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

    private fun reportDispatchResult(spokenText: String, resultMessage: String) {
        ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_dispatch_result, spokenText, resultMessage))
        ttsSpeaker.speak(resultMessage)
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
        private const val DEBOUNCE_MILLIS = 300L
        private const val VOICE_COMMAND_COUNTDOWN_SECONDS = 1
        private const val LISTEN_START_VIBRATION_MILLIS = 50L
        private const val MIC_PERMISSION_RECHECK_MILLIS = 3000L
        private const val CONTINUOUS_LISTEN_GAP_MILLIS = 400L
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
