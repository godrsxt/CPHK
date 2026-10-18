package com.venice.carromassist.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import com.venice.carromassist.core.*
import com.venice.carromassist.physics.PhysParams
import com.venice.carromassist.solver.Solver

class OverlayView(ctx: Context) : View(ctx) {

    var visible = true
    private var state: GameState? = null
    private var shot: Solver.Shot? = null
    private var boardToScreen: FloatArray? = null
    private var scale = 1f

    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = 0xFF35E08A.toInt()
    }
    private val pCoin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFFFC93C.toInt()
    }
    private val pGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFF6FA8FF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(9f, 9f), 0f)
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 34f
        setShadowLayer(6f, 0f, 2f, 0xFF000000.toInt())
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AE6B4.toInt(); textSize = 30f
        setShadowLayer(6f, 0f, 2f, 0xFF000000.toInt())
    }

    fun set(s: GameState?, sh: Solver.Shot?, h: FloatArray?, p: PhysParams?) {
        state = s; shot = sh
        boardToScreen = h?.let { Homography.invert(it) }
        if (s != null && h != null) {
            // rough px-per-mm so stroke widths look consistent across devices
            val a = Homography.apply(boardToScreen!!, Vec2(0f, 0f))
            val b = Homography.apply(boardToScreen!!, Vec2(BoardSpec.PLAY, 0f))
            scale = (b - a).len() / BoardSpec.PLAY
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!visible) return
        val s = state ?: return
        val H = boardToScreen ?: return

        // every detected disc, drawn at the current size settings (use this to tune sizes)
        for (d in s.discs) {
            val c = Homography.apply(H, d.pos)
            canvas.drawCircle(c.x, c.y, d.r * scale, if (d.type == DiscType.STRIKER) pLine else pCoin)
        }
        canvas.drawText("${s.discs.count { it.type != DiscType.STRIKER }} coins", 40f, 152f, pText)

        val sh = shot ?: return

        // striker predicted path
        drawPolyline(canvas, H, sh.sim.strikerPath, pLine)
        // struck coin path
        if (sh.sim.hitPath.size > 1) {
            val p2 = Paint(pCoin); p2.strokeWidth = 4f
            drawPolyline(canvas, H, sh.sim.hitPath, p2)
        }
        // ghost ball at the contact point
        sh.sim.contactPoint?.let {
            canvas.drawCircle(Homography.apply(H, it).x, Homography.apply(H, it).y,
                BoardSpec.STRIKER_R * scale, pGhost)
        }

        val strikerScreen = Homography.apply(H, sh.strikerPos)
        canvas.drawText("AIM", strikerScreen.x + 22f, strikerScreen.y - 22f, pLabel)

        // readout near the top
        canvas.drawText(
            "score %.0f   power %d%%".format(sh.score, (sh.power * 100).toInt()),
            40f, 110f, pText
        )
    }

    private fun drawPolyline(canvas: Canvas, H: FloatArray, pts: List<Vec2>, p: Paint) {
        if (pts.size < 2) return
        val path = Path()
        val first = Homography.apply(H, pts[0])
        path.moveTo(first.x, first.y)
        for (i in 1 until pts.size) {
            val q = Homography.apply(H, pts[i])
            path.lineTo(q.x, q.y)
        }
        canvas.drawPath(path, p)
    }
}
