package com.venice.carromassist.vision

import com.venice.carromassist.core.*

/**
 * Finds the striker, both coin colours and the queen on a warped board image.
 *
 * Strategy: the playing surface is the dominant colour, so anything that is
 * far from it in RGB is a candidate disc. Thin board markings (base lines,
 * circles, arrows) die in the size/circularity filter.
 */
object DiscDetector {

    @JvmField var minDist = 46f          // RGB distance from surface colour (user-tunable)
    private const val PX_PER_MM = Warp.OUT / BoardSpec.PLAY

    class Result(val discs: MutableList<Disc>, val surfaceRgb: Int)

    fun detect(board: IntArray): Result {
        val surface = medianSurface(board)

        // --- mask ---
        val n = board.size
        val mask = BooleanArray(n)
        for (i in 0 until n) {
            val p = board[i]
            val dr = ((p shr 16) and 0xFF) - ((surface shr 16) and 0xFF)
            val dg = ((p shr 8) and 0xFF) - ((surface shr 8) and 0xFF)
            val db = (p and 0xFF) - (surface and 0xFF)
            if (dr * dr + dg * dg + db * db > minDist * minDist) mask[i] = true
        }

        // --- connected components ---
        val seen = BooleanArray(n)
        val stack = IntArray(n)
        val discs = mutableListOf<Disc>()
        val minArea = (Math.PI * Math.pow((BoardSpec.COIN_R * 0.62 * PX_PER_MM).toDouble(), 2.0)).toInt()
        val maxArea = (Math.PI * Math.pow((BoardSpec.STRIKER_R * 1.30 * PX_PER_MM).toDouble(), 2.0)).toInt()

        for (start in 0 until n) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var area = 0
            var sx = 0L; var sy = 0L
            var srr = 0L; var sgg = 0L; var sbb = 0L
            var x0 = Warp.OUT; var x1 = 0; var y0 = Warp.OUT; var y1 = 0
            while (sp > 0) {
                val c = stack[--sp]
                val cx = c % Warp.OUT; val cy = c / Warp.OUT
                area++
                sx += cx; sy += cy
                val p = board[c]
                srr += (p shr 16) and 0xFF; sgg += (p shr 8) and 0xFF; sbb += p and 0xFF
                if (cx < x0) x0 = cx; if (cx > x1) x1 = cx
                if (cy < y0) y0 = cy; if (cy > y1) y1 = cy
                if (cx > 0 && mask[c - 1] && !seen[c - 1]) { seen[c - 1] = true; stack[sp++] = c - 1 }
                if (cx < Warp.OUT - 1 && mask[c + 1] && !seen[c + 1]) { seen[c + 1] = true; stack[sp++] = c + 1 }
                if (cy > 0 && mask[c - Warp.OUT] && !seen[c - Warp.OUT]) { seen[c - Warp.OUT] = true; stack[sp++] = c - Warp.OUT }
                if (cy < Warp.OUT - 1 && mask[c + Warp.OUT] && !seen[c + Warp.OUT]) { seen[c + Warp.OUT] = true; stack[sp++] = c + Warp.OUT }
            }
            if (area < minArea || area > maxArea) continue

            // circularity: bbox fill ratio must be disc-like, kills lines & rings
            val bw = (x1 - x0 + 1).toFloat(); val bh = (y1 - y0 + 1).toFloat()
            if (bw < 4f || bh < 4f) continue
            val aspect = minOf(bw, bh) / maxOf(bw, bh)
            val fill = area / (bw * bh)
            if (aspect < 0.72f || fill < 0.62f) continue

            val cxMm = (sx.toFloat() / area) / PX_PER_MM
            val cyMm = (sy.toFloat() / area) / PX_PER_MM
            val rMm = Math.sqrt(area / Math.PI).toFloat() / PX_PER_MM
            val pos = Vec2(cxMm, cyMm)

            // reject the four pocket holes
            if (rMm > BoardSpec.COIN_R * 1.25f &&
                BoardSpec.POCKETS.any { pos.dist(it) < BoardSpec.POCKET_R * 1.35f }) continue

            val mr = (srr / area).toInt(); val mg = (sgg / area).toInt(); val mb = (sbb / area).toInt()
            val lum = (mr * 299 + mg * 587 + mb * 114) / 1000

            val type = when {
                rMm > BoardSpec.COIN_R * 1.18f -> DiscType.STRIKER
                mr > mg * 1.30f && mr > mb * 1.30f && mr > 90 -> DiscType.QUEEN
                lum < 118 -> DiscType.BLACK
                else -> DiscType.WHITE
            }
            val r = if (type == DiscType.STRIKER) BoardSpec.STRIKER_R else BoardSpec.COIN_R
            val m = if (type == DiscType.STRIKER) BoardSpec.STRIKER_M else BoardSpec.COIN_M
            discs += Disc(pos, Vec2.ZERO, type, r, m)
        }

        // exactly one striker: keep the largest, demote the rest to coins
        val strikers = discs.filter { it.type == DiscType.STRIKER }
        if (strikers.size > 1) {
            strikers.sortedByDescending { it.r }
            for (s in strikers.drop(1)) {
                discs.remove(s)
                discs += Disc(s.pos, Vec2.ZERO, DiscType.WHITE, BoardSpec.COIN_R, BoardSpec.COIN_M)
            }
        }
        return Result(discs, surface)
    }

    /** Rough surface colour: median of a decimated sample, robust to discs. */
    private fun medianSurface(board: IntArray): Int {
        val rs = ArrayList<Int>(4096); val gs = ArrayList<Int>(4096); val bs = ArrayList<Int>(4096)
        var i = 0
        while (i < board.size) {
            val p = board[i]
            rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
            i += 7
        }
        rs.sort(); gs.sort(); bs.sort()
        val m = rs.size / 2
        return (0xFF shl 24) or (rs[m] shl 16) or (gs[m] shl 8) or bs[m]
    }
}
