package com.venice.carromassist.vision

import com.venice.carromassist.core.Vec2

/**
 * Cheap auto-locate: the Carrom Pool playing surface is by far the largest
 * bright, low-saturation region on screen. Returns a starting quad in *screen*
 * coordinates for the user to fine-tune by hand.
 */
object BoardFinder {

    private const val DS = 160  // downscale width

    fun find(frame: IntArray, w: Int, h: Int): List<Vec2>? {
        val scale = DS.toFloat() / w
        val dw = DS
        val dh = (h * scale).toInt()
        if (dh < 40) return null

        val lum = IntArray(dw * dh)
        var minL = 255; var maxL = 0
        for (y in 0 until dh) for (x in 0 until dw) {
            val p = frame[(y / scale).toInt() * w + (x / scale).toInt()]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = (r * 299 + g * 587 + b * 114) / 1000
            lum[y * dw + x] = l
            if (l < minL) minL = l
            if (l > maxL) maxL = l
        }
        val thr = minL + ((maxL - minL) * 0.62f).toInt()

        val mask = BooleanArray(dw * dh) { lum[it] > thr }
        // largest connected component via flood fill
        val seen = BooleanArray(dw * dh)
        var bestArea = 0
        var bx0 = 0; var by0 = 0; var bx1 = 0; var by1 = 0
        val stack = IntArray(dw * dh)
        for (start in 0 until dw * dh) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var area = 0
            var x0 = dw; var y0 = dh; var x1 = 0; var y1 = 0
            while (sp > 0) {
                val c = stack[--sp]
                val cx = c % dw; val cy = c / dw
                area++
                if (cx < x0) x0 = cx; if (cx > x1) x1 = cx
                if (cy < y0) y0 = cy; if (cy > y1) y1 = cy
                if (cx > 0 && mask[c - 1] && !seen[c - 1]) { seen[c - 1] = true; stack[sp++] = c - 1 }
                if (cx < dw - 1 && mask[c + 1] && !seen[c + 1]) { seen[c + 1] = true; stack[sp++] = c + 1 }
                if (cy > 0 && mask[c - dw] && !seen[c - dw]) { seen[c - dw] = true; stack[sp++] = c - dw }
                if (cy < dh - 1 && mask[c + dw] && !seen[c + dw]) { seen[c + dw] = true; stack[sp++] = c + dw }
            }
            // penalise components that hug the frame edge (usually UI chrome)
            val touchesEdge = x0 == 0 || y0 == 0 || x1 == dw - 1
            val score = if (touchesEdge) (area * 0.55f).toInt() else area
            if (score > bestArea) {
                bestArea = score; bx0 = x0; by0 = y0; bx1 = x1; by1 = y1
            }
        }
        if (bestArea < (dw * dh) * 0.08f) return null

        val inv = 1f / scale
        return listOf(
            Vec2(bx0 * inv, by0 * inv),
            Vec2(bx1 * inv, by0 * inv),
            Vec2(bx1 * inv, by1 * inv),
            Vec2(bx0 * inv, by1 * inv)
        )
    }
}
