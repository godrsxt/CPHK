package com.venice.carromassist.vision

import android.graphics.Bitmap
import com.venice.carromassist.core.Homography
import com.venice.carromassist.core.Vec2

/**
 * Rasterises the screen region defined by the board homography into a flat
 * top-down ARGB IntArray. Pure Kotlin nearest-neighbour — no OpenCV.
 */
object Warp {

    const val OUT = 720

    /** @param screenToBoard homography mapping screen px -> board mm */
    fun extract(frame: IntArray, frameW: Int, frameH: Int, screenToBoard: FloatArray): IntArray {
        val out = IntArray(OUT * OUT)
        val boardToScreen = Homography.invert(screenToBoard)
        val s = OUT.toFloat() / com.venice.carromassist.core.BoardSpec.PLAY

        var oi = 0
        for (y in 0 until OUT) {
            val by = y / s
            for (x in 0 until OUT) {
                val sp = Homography.apply(boardToScreen, Vec2(x / s, by))
                val px = sp.x.toInt()
                val py = sp.y.toInt()
                out[oi++] = if (px in 0 until frameW && py in 0 until frameH)
                    frame[py * frameW + px] else 0xFF202020.toInt()
            }
        }
        return out
    }

    /** Unused fallback: direct Bitmap warp (slower, kept for the calibration screen). */
    fun warpBitmap(bmp: Bitmap, screenToBoard: FloatArray): Bitmap {
        val out = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        val boardToScreen = Homography.invert(screenToBoard)
        val s = OUT.toFloat() / com.venice.carromassist.core.BoardSpec.PLAY
        val px = IntArray(OUT * OUT)
        var i = 0
        for (y in 0 until OUT) for (x in 0 until OUT) {
            val sp = Homography.apply(boardToScreen, Vec2(x / s, y / s))
            px[i++] = bmp.getPixel(
                sp.x.toInt().coerceIn(0, bmp.width - 1),
                sp.y.toInt().coerceIn(0, bmp.height - 1)
            )
        }
        out.setPixels(px, 0, OUT, 0, 0, OUT, OUT)
        return out
    }
}
