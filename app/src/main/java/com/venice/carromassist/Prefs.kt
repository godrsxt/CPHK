package com.venice.carromassist

import android.content.Context
import com.venice.carromassist.physics.PhysParams

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("carrom_assist", Context.MODE_PRIVATE)

    var homography: FloatArray?
        get() {
            val s = sp.getString("H", null) ?: return null
            val parts = s.split(",")
            if (parts.size != 9) return null
            return FloatArray(9) { parts[it].toFloat() }
        }
        set(v) { sp.edit().putString("H", v?.joinToString(",")).apply() }

    var myColorIsWhite: Boolean
        get() = sp.getBoolean("myWhite", true)
        set(v) = sp.edit().putBoolean("myWhite", v).apply()

    var ourBaselineBottom: Boolean
        get() = sp.getBoolean("baseBottom", true)
        set(v) = sp.edit().putBoolean("baseBottom", v).apply()

    var autoShoot: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    var showAllCandidates: Boolean
        get() = sp.getBoolean("allCand", false)
        set(v) = sp.edit().putBoolean("allCand", v).apply()

    var chaseQueen: Boolean
        get() = sp.getBoolean("queen", true)
        set(v) = sp.edit().putBoolean("queen", v).apply()

    var maxDragPx: Float
        get() = sp.getFloat("maxDrag", 0.28f)
        set(v) = sp.edit().putFloat("maxDrag", v).apply()

    var invertDrag: Boolean
        get() = sp.getBoolean("invert", false)
        set(v) = sp.edit().putBoolean("invert", v).apply()

    var coinScale: Float
        get() = sp.getFloat("coinScale", 1.0f)
        set(v) = sp.edit().putFloat("coinScale", v).apply()

    var strikerScale: Float
        get() = sp.getFloat("strikerScale", 1.0f)
        set(v) = sp.edit().putFloat("strikerScale", v).apply()

    var pocketScale: Float
        get() = sp.getFloat("pocketScale", 1.0f)
        set(v) = sp.edit().putFloat("pocketScale", v).apply()

    var detectSens: Float
        get() = sp.getFloat("detectSens", 46f)
        set(v) = sp.edit().putFloat("detectSens", v).apply()

    var params: PhysParams
        get() = PhysParams(
            drag = sp.getFloat("drag", 2.10f),
            discRest = sp.getFloat("discRest", 0.94f),
            wallRest = sp.getFloat("wallRest", 0.68f),
            maxV = sp.getFloat("maxV", 2650f)
        )
        set(v) = sp.edit()
            .putFloat("drag", v.drag)
            .putFloat("discRest", v.discRest)
            .putFloat("wallRest", v.wallRest)
            .putFloat("maxV", v.maxV)
            .apply()
}
