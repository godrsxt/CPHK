package com.venice.carromassist.physics

data class PhysParams(
    var drag: Float = 2.10f,       // 1/s, velocity-proportional damping
    var discRest: Float = 0.94f,   // disc-disc restitution
    var wallRest: Float = 0.68f,   // cushion restitution
    var stopV: Float = 28f,        // mm/s, sleep threshold
    var maxV: Float = 2650f,       // mm/s at power 1.0
    var dt: Float = 1f / 360f,
    var maxTime: Float = 4.5f
)
