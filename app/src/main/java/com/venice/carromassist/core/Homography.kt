package com.venice.carromassist.core

/**
 * 3x3 row-major homography. Maps `src` quad onto `dst` quad.
 * Solved with a straight 8x8 DLT (Gauss-Jordan) — exact for 4 point pairs.
 */
object Homography {

    fun compute(src: List<Vec2>, dst: List<Vec2>): FloatArray {
        require(src.size == 4 && dst.size == 4)
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = src[i].x.toDouble(); val y = src[i].y.toDouble()
            val u = dst[i].x.toDouble(); val v = dst[i].y.toDouble()
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
        }
        val h = gauss(a)
        return floatArrayOf(
            h[0].toFloat(), h[1].toFloat(), h[2].toFloat(),
            h[3].toFloat(), h[4].toFloat(), h[5].toFloat(),
            h[6].toFloat(), h[7].toFloat(), 1f
        )
    }

    private fun gauss(m: Array<DoubleArray>): DoubleArray {
        val n = 8
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[piv][col])) piv = r
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            val d = m[col][col]
            if (kotlin.math.abs(d) < 1e-12) continue
            for (c in col..n) m[col][c] /= d
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col]
                if (f == 0.0) continue
                for (c in col..n) m[r][c] -= f * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] }
    }

    fun apply(h: FloatArray, p: Vec2): Vec2 {
        val d = h[6] * p.x + h[7] * p.y + h[8]
        if (kotlin.math.abs(d) < 1e-9f) return Vec2.ZERO
        return Vec2(
            (h[0] * p.x + h[1] * p.y + h[2]) / d,
            (h[3] * p.x + h[4] * p.y + h[5]) / d
        )
    }

    /** Direction-only transform (ignores translation) — for mapping vectors between spaces. */
    fun applyDir(h: FloatArray, v: Vec2): Vec2 = apply(h, v).minus(apply(h, Vec2.ZERO))

    fun invert(h: FloatArray): FloatArray {
        val a = h[0]; val b = h[1]; val c = h[2]
        val d = h[3]; val e = h[4]; val f = h[5]
        val g = h[6]; val i = h[7]; val j = h[8]
        val A = e * j - f * i
        val B = -(d * j - f * g)
        val C = d * i - e * g
        val det = a * A + b * B + c * C
        if (kotlin.math.abs(det) < 1e-12f) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val id = 1f / det
        return floatArrayOf(
            A * id, -(b * j - c * i) * id, (b * f - c * e) * id,
            B * id, (a * j - c * g) * id, -(a * f - c * d) * id,
            C * id, -(a * i - b * g) * id, (a * e - b * d) * id
        )
    }
}
