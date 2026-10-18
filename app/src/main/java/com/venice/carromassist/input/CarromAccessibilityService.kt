package com.venice.carromassist.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * The only root-free way to synthesise touches over another app.
 * enabled from Settings > Accessibility.
 */
class CarromAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: CarromAccessibilityService? = null
        val connected: Boolean get() = instance != null
    }

    override fun onServiceConnected() { instance = this }

    override fun onDestroy() { instance = null; super.onDestroy() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** Pull-back swipe: starts on the striker, drags backwards, releases. */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, onDone: (() -> Unit)? = null): Boolean {
        val svc = instance ?: return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo((x1 + x2) / 2f, (y1 + y2) / 2f)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(60, 1200))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return svc.dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { onDone?.invoke() }
            override fun onCancelled(g: GestureDescription?) { onDone?.invoke() }
        }, null)
    }

    fun tap(x: Float, y: Float, durationMs: Long = 60): Boolean {
        val svc = instance ?: return false
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
