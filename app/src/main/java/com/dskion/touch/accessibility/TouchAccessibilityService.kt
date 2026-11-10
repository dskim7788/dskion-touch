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
import com.dskion.touch.voice.TtsSpeaker
import com.dskion.touch.voice.VoiceRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val newDispatchCommand = NewDispatchCommand(this)
    private val acceptDispatchCommand = AcceptDispatchCommand(this)
    private val respondDispatchCommand = RespondDispatchCommand(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var ttsSpeaker: TtsSpeaker

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
     * Counts down (giving the user time to switch to the target app), listens for one spoken
     * command, and tries to act on it. Publishes progress to [ScreenStateHolder.voiceStatus].
     */
    fun startVoiceCommand() {
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_mic_required))
            return
        }

        serviceScope.launch {
            for (remaining in VOICE_COMMAND_COUNTDOWN_SECONDS downTo 1) {
                ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_countdown, remaining))
                delay(1000)
            }
            ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_listening))
            VoiceRecognizer(this@TouchAccessibilityService).startListening(
                onResult = { text -> serviceScope.launch { handleRecognizedText(text) } },
                onError = { message ->
                    ScreenStateHolder.setVoiceStatus(message)
                    vibrate()
                },
            )
        }
    }

    private suspend fun handleRecognizedText(text: String?) {
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
        ScreenStateHolder.setVoiceStatus(
            when {
                text == null -> getString(R.string.console_voice_no_speech)
                tryPerformCommand(text) -> getString(R.string.console_voice_matched, text)
                else -> getString(R.string.console_voice_not_matched, text)
            },
        )
        vibrate()
    }

    private fun reportDispatchResult(spokenText: String, resultMessage: String) {
        ScreenStateHolder.setVoiceStatus(getString(R.string.console_voice_dispatch_result, spokenText, resultMessage))
        ttsSpeaker.speak(resultMessage)
        vibrate()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
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
        serviceScope.cancel()
        if (::ttsSpeaker.isInitialized) ttsSpeaker.shutdown()
        instance = null
        ScreenStateHolder.setServiceRunning(false)
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 300L
        private const val VOICE_COMMAND_COUNTDOWN_SECONDS = 3
        private const val NOTIFICATION_CHANNEL_ID = "touch_service"
        private const val NOTIFICATION_ID = 1

        var instance: TouchAccessibilityService? = null
            private set
    }
}
