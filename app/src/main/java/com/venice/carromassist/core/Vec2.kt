package com.venice.carromassist.core

import kotlin.math.hypot

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    operator fun div(s: Float) = Vec2(x / s, y / s)
    operator fun unaryMinus() = Vec2(-x, -y)

    fun dot(o: Vec2) = x * o.x + y * o.y
    fun cross(o: Vec2) = x * o.y - y * o.x
    fun len() = hypot(x, y)
    fun len2() = x * x + y * y
    fun norm(): Vec2 {
        val l = len()
        return if (l < 1e-6f) Vec2(0f, 0f) else Vec2(x / l, y / l)
    }
    fun perp() = Vec2(-y, x)
    fun dist(o: Vec2) = hypot(x - o.x, y - o.y)
    fun dist2(o: Vec2) = (x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)
    fun lerp(o: Vec2, t: Float) = Vec2(x + (o.x - x) * t, y + (o.y - y) * t)

    companion object { val ZERO = Vec2(0f, 0f) }
}

/** Shortest distance from point p to segment ab. */
fun pointSegDist(p: Vec2, a: Vec2, b: Vec2): Float {
    val ab = b - a
    val l2 = ab.len2()
    if (l2 < 1e-6f) return p.dist(a)
    var t = (p - a).dot(ab) / l2
    t = t.coerceIn(0f, 1f)
    return p.dist(a + ab * t)
}
