package com.venice.carromassist

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.venice.carromassist.capture.AssistService
import com.venice.carromassist.core.BoardSpec
import com.venice.carromassist.core.Homography
import com.venice.carromassist.core.Vec2
import com.venice.carromassist.vision.BoardFinder
import kotlin.math.min

class CalibrationActivity : AppCompatActivity() {

    private lateinit var view: CalibrationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        view = findViewById(R.id.calibView)

        val bmp = AssistService.lastFrame
        if (bmp == null) { finish(); return }
        view.setFrame(bmp)

        findViewById<Button>(R.id.btnAuto).setOnClickListener {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val q = BoardFinder.find(px, bmp.width, bmp.height)
            if (q == null) toast("Auto-find failed, tap manually") else view.setQuad(q)
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener { view.setQuad(emptyList()) }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val q = view.quad
            if (q.size != 4) { toast("Need 4 corners"); return@setOnClickListener }
            val dst = listOf(
                Vec2(0f, 0f), Vec2(BoardSpec.PLAY, 0f),
                Vec2(BoardSpec.PLAY, BoardSpec.PLAY), Vec2(0f, BoardSpec.PLAY)
            )
            AssistApp.prefs.homography = Homography.compute(q, dst)
            toast("Saved")
            finish()
        }
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
}

class CalibrationView(ctx: Context) : View(ctx) {

    private var frame: Bitmap? = null
    var quad: List<Vec2> = emptyList()
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF35E08A.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC93C.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    fun setFrame(b: Bitmap) { frame = b; invalidate() }
    fun setQuad(q: List<Vec2>) { quad = q; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val b = frame ?: return
        val s = min(width.toFloat() / b.width, height.toFloat() / b.height)
        scale = s
        offX = (width - b.width * s) / 2f
        offY = (height - b.height * s) / 2f
        val dst = RectF(offX, offY, offX + b.width * s, offY + b.height * s)
        canvas.drawBitmap(b, null, dst, null)

        for (i in quad.indices) {
            val p = toScreen(quad[i])
            canvas.drawCircle(p.x, p.y, 18f, dot)
            val label = listOf("TL", "TR", "BR", "BL")[i]
            paint.color = Color.WHITE; paint.textSize = 40f
            canvas.drawText(label, p.x + 24f, p.y - 24f, paint)
            if (i > 0) canvas.drawLine(toScreen(quad[i - 1]).x, toScreen(quad[i - 1]).y, p.x, p.y, line)
            if (i == 3) canvas.drawLine(p.x, p.y, toScreen(quad[0]).x, toScreen(quad[0]).y, line)
        }
    }

    private fun toScreen(b: Vec2) = Vec2(offX + b.x * scale, offY + b.y * scale)
    private fun toBitmap(sx: Float, sy: Float) = Vec2((sx - offX) / scale, (sy - offY) / scale)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val p = toBitmap(e.x, e.y)
        if (quad.size < 4) quad = quad + p
        else {
            // tap near an existing corner to move it, otherwise restart
            var bi = -1; var bd = 70f
            for (i in quad.indices) {
                val d = quad[i].dist(p); if (d < bd) { bd = d; bi = i }
            }
            if (bi >= 0) { quad = quad.toMutableList().also { it[bi] = p } } else quad = listOf(p)
        }
        invalidate()
        return true
    }
}
