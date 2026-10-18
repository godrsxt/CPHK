package com.venice.carromassist.overlay

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import com.venice.carromassist.AssistApp

class ControlPanel(ctx: Context) : LinearLayout(ctx) {

    var onToggleAuto: ((Boolean) -> Unit)? = null
    var onToggleColor: (() -> Unit)? = null
    var onToggleQueen: (() -> Unit)? = null
    var onHide: (() -> Unit)? = null
    var onMove: ((Int, Int) -> Unit)? = null

    private lateinit var autoBtn: Button
    private lateinit var queenBtn: Button

    init {
        orientation = VERTICAL
        setBackgroundColor(0xAA101018.toInt())
        setPadding(10, 10, 10, 10)

        autoBtn = mk("AUTO: ${if (AssistApp.prefs.autoShoot) "ON" else "OFF"}") {
            AssistApp.prefs.autoShoot = !AssistApp.prefs.autoShoot
            autoBtn.text = "AUTO: ${if (AssistApp.prefs.autoShoot) "ON" else "OFF"}"
            onToggleAuto?.invoke(AssistApp.prefs.autoShoot)
        }
        queenBtn = mk("QUEEN: ${if (AssistApp.prefs.chaseQueen) "ON" else "OFF"}") {
            onToggleQueen?.invoke()
            queenBtn.text = "QUEEN: ${if (AssistApp.prefs.chaseQueen) "ON" else "OFF"}"
        }
        mk("HIDE LINES") { onHide?.invoke() }
        mk("DRAG ME") { }
    }

    private fun mk(label: String, on: () -> Unit): Button {
        val b = Button(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1E1E2A.toInt())
            minWidth = 0; minimumWidth = 0
            setPadding(24, 6, 24, 6)
        }
        b.setOnClickListener { on() }
        addView(b)
        return b
    }

    private var downX = 0f; private var downY = 0f
    private var moved = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; moved = false; return true }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 12) {
                    moved = true
                    onMove?.invoke(dx, dy)
                    downX = e.rawX; downY = e.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> return true
        }
        return super.onTouchEvent(e)
    }
}
