package com.venice.carromassist.core

/**
 * Canonical board space in millimetres. Sizes of coin / striker / pocket are
 * user-adjustable (Tune screen) because every phone and game skin differs.
 */
object BoardSpec {

    const val PLAY = 736.6f

    const val DEF_POCKET_R = 23.0f
    const val DEF_POCKET_INSET = 31.0f
    const val DEF_COIN_R = 15.0f
    const val DEF_STRIKER_R = 20.5f

    var POCKET_R = DEF_POCKET_R
    var POCKET_INSET = DEF_POCKET_INSET
    var COIN_R = DEF_COIN_R
    var STRIKER_R = DEF_STRIKER_R

    const val COIN_M = 1.0f
    const val STRIKER_M = 1.25f

    const val BASE_OFFSET = 63.5f
    const val BASE_LEN = 466.0f

    fun applyScales(coin: Float, striker: Float, pocket: Float) {
        COIN_R = DEF_COIN_R * coin
        STRIKER_R = DEF_STRIKER_R * striker
        POCKET_R = DEF_POCKET_R * pocket
    }

    val POCKETS: List<Vec2>
        get() = listOf(
            Vec2(POCKET_INSET, POCKET_INSET),
            Vec2(PLAY - POCKET_INSET, POCKET_INSET),
            Vec2(PLAY - POCKET_INSET, PLAY - POCKET_INSET),
            Vec2(POCKET_INSET, PLAY - POCKET_INSET)
        )

    val BASE_Y: List<Float> = listOf(BASE_OFFSET, PLAY - BASE_OFFSET)
    val BASE_X0: Float = (PLAY - BASE_LEN) / 2f
    val BASE_X1: Float = (PLAY + BASE_LEN) / 2f

    fun inside(p: Vec2, r: Float): Boolean =
        p.x >= r && p.x <= PLAY - r && p.y >= r && p.y <= PLAY - r

    fun inPocket(p: Vec2, r: Float): Int {
        val pk = POCKETS
        for (i in pk.indices) if (p.dist(pk[i]) < POCKET_R - r * 0.5f) return i
        return -1
    }
}
