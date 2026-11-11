package com.dskion.touch.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import com.dskion.touch.R
import com.dskion.touch.accessibility.ScreenStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs

/**
 * A small draggable floating mic button drawn through the accessibility service's own
 * TYPE_ACCESSIBILITY_OVERLAY window - this window type is reserved for accessibility services and
 * needs no separate "draw over other apps" (SYSTEM_ALERT_WINDOW) permission or user prompt.
 *
 * This is what makes voice commands usable while actually inside 배민/쿠팡이츠: tapping it starts
 * [onTap] (the same [android.accessibilityservice.AccessibilityService]'s voice-command flow)
 * right there, without switching back to 콘솔 탭 first.
 */
class VoiceOverlayController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val onTap: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlayView: ImageView? = null

    private val params = WindowManager.LayoutParams(
        SIZE_DP.dpToPx(service),
        SIZE_DP.dpToPx(service),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = START_X_DP.dpToPx(service)
        y = START_Y_DP.dpToPx(service)
    }

    fun show() {
        if (overlayView != null || windowManager == null) return
        val pad = PADDING_DP.dpToPx(service)
        val view = ImageView(service).apply {
            setImageResource(R.drawable.ic_mic_overlay)
            background = circleBackground(IDLE_COLOR)
            setPadding(pad, pad, pad, pad)
        }
        attachDragHandling(view)
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) return
        overlayView = view

        ScreenStateHolder.isListening
            .onEach { listening -> view.background = circleBackground(if (listening) ACTIVE_COLOR else IDLE_COLOR) }
            .launchIn(scope)
    }

    fun hide() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
    }

    private fun attachDragHandling(view: ImageView) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > TOUCH_SLOP_PX || abs(dy) > TOUCH_SLOP_PX) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager?.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    companion object {
        private const val SIZE_DP = 56
        private const val PADDING_DP = 14
        private const val START_X_DP = 24
        private const val START_Y_DP = 400
        private const val TOUCH_SLOP_PX = 16
        private const val IDLE_COLOR = 0xFF17252D.toInt()
        private const val ACTIVE_COLOR = 0xFFC5F36A.toInt()
    }
}

private fun Int.dpToPx(context: Context): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics).toInt()
