package com.venice.carromassist

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TuneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tune)
        val root = findViewById<LinearLayout>(R.id.root)

        val p = AssistApp.prefs.params
        slider(root, "Friction drag (1/s)", p.drag, 0.4f, 6f) { AssistApp.prefs.params = AssistApp.prefs.params.copy(drag = it) }
        slider(root, "Cushion restitution", p.wallRest, 0.1f, 0.98f) { AssistApp.prefs.params = AssistApp.prefs.params.copy(wallRest = it) }
        slider(root, "Disc restitution", p.discRest, 0.3f, 1.0f) { AssistApp.prefs.params = AssistApp.prefs.params.copy(discRest = it) }
        slider(root, "Max launch speed (mm/s)", p.maxV, 800f, 5000f) { AssistApp.prefs.params = AssistApp.prefs.params.copy(maxV = it) }
        slider(root, "Max drag length (screen height frac)", AssistApp.prefs.maxDragPx, 0.10f, 0.60f) { AssistApp.prefs.maxDragPx = it }

        root.addView(TextView(this).apply {
            text = "SIZES - set these first"; setTextColor(0xFF35E08A.toInt()); textSize = 16f
            setPadding(0, 36, 0, 0)
        })
        slider(root, "Coin size", AssistApp.prefs.coinScale, 0.5f, 1.8f) { AssistApp.prefs.coinScale = it }
        slider(root, "Striker size", AssistApp.prefs.strikerScale, 0.5f, 1.8f) { AssistApp.prefs.strikerScale = it }
        slider(root, "Pocket size", AssistApp.prefs.pocketScale, 0.5f, 1.8f) { AssistApp.prefs.pocketScale = it }
        slider(root, "Detect sensitivity (lower = finds more)", AssistApp.prefs.detectSens, 15f, 90f) { AssistApp.prefs.detectSens = it }

        note(root, "Sizes: open the game, let the overlay draw. Every detected disc gets a ring. Ring bigger than the real coin -> lower Coin size. Smaller -> raise it. Same for the striker. If coins are missed, lower Detect sensitivity; if board markings show up as fake coins, raise it.")
        note(root, "Physics: take one full-power shot into empty space and compare where the striker really stops with the green line. Too long -> raise Friction drag. Bounces too lively -> lower Cushion restitution. Then adjust Max drag length so 100% power really reaches the far cushion.")
        root.addView(android.widget.CheckBox(this).apply {
            text = "Invert drag direction (use if autoplay pulls the striker the wrong way)"
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = AssistApp.prefs.invertDrag
            setOnCheckedChangeListener { _, v -> AssistApp.prefs.invertDrag = v }
        })
    }

    private fun slider(root: LinearLayout, label: String, initial: Float, lo: Float, hi: Float, onChange: (Float) -> Unit) {
        val tv = TextView(this).apply {
            text = "$label: %.2f".format(initial)
            setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
            setPadding(0, 28, 0, 4)
        }
        val sb = SeekBar(this).apply {
            max = 1000
            progress = (((initial - lo) / (hi - lo)) * 1000).toInt().coerceIn(0, 1000)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    val val_ = lo + (hi - lo) * v / 1000f
                    tv.text = "$label: %.2f".format(val_)
                    if (fromUser) onChange(val_)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(tv); root.addView(sb)
    }

    private fun note(root: LinearLayout, s: String) {
        root.addView(TextView(this).apply {
            text = s; setTextColor(0xFF8A8A9A.toInt()); textSize = 12f
            setPadding(0, 22, 0, 0)
        })
    }
}
