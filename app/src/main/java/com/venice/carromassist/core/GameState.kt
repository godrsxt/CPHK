package com.venice.carromassist.core

enum class DiscType { STRIKER, WHITE, BLACK, QUEEN }

class Disc(
    var pos: Vec2,
    var vel: Vec2,
    val type: DiscType,
    val r: Float,
    val m: Float
) {
    fun copy() = Disc(pos, vel, type, r, m)
}

/** Everything the solver needs to know about the board right now. */
class GameState(
    val discs: MutableList<Disc>,
    val myColor: DiscType,          // WHITE or BLACK
    val ourBaselineBottom: Boolean
) {
    val strikerIndex: Int get() = discs.indexOfFirst { it.type == DiscType.STRIKER }
    val myColor_: DiscType get() = myColor
    fun opponents(): DiscType = if (myColor == DiscType.WHITE) DiscType.BLACK else DiscType.WHITE
}
