package com.venice.carromassist.physics

import com.venice.carromassist.core.*
import kotlin.math.abs

/**
 * Forward rigid-body simulation of the whole board for one shot.
 * Disc-disc and disc-cushion impulses, exponential drag, pocket capture.
 */
class Simulator(private val p: PhysParams) {

    data class Pocketed(val discIndex: Int, val type: DiscType, val pocketIndex: Int)

    class Outcome(
        val pocketed: List<Pocketed>,
        val firstHitIndex: Int,
        val strikerPocketed: Boolean,
        val finalPositions: List<Vec2>,
        val strikerPath: List<Vec2>,
        val hitPath: List<Vec2>,
        val contactPoint: Vec2?,
        val time: Float
    )

    fun run(input: List<Disc>, strikerIndex: Int, aimDir: Vec2, speed: Float): Outcome {
        val d = input.map { it.copy() }.toMutableList()
        val alive = BooleanArray(d.size) { true }
        d[strikerIndex].vel = aimDir.norm() * speed

        val pocketed = ArrayList<Pocketed>()
        var firstHit = -1
        var contact: Vec2? = null
        val strikerPath = ArrayList<Vec2>(512)
        val hitPath = ArrayList<Vec2>(512)
        var t = 0f
        var pathTick = 0

        while (t < p.maxTime) {
            // integrate + drag
            var moving = false
            for (i in d.indices) {
                if (!alive[i]) continue
                val disc = d[i]
                if (disc.vel.len2() < p.stopV * p.stopV) { disc.vel = Vec2.ZERO; continue }
                moving = true
                disc.pos += disc.vel * p.dt
                disc.vel *= (1f - p.drag * p.dt).coerceAtLeast(0f)
            }
            if (!moving) break

            // disc-disc
            for (i in d.indices) {
                if (!alive[i]) continue
                for (j in i + 1 until d.size) {
                    if (!alive[j]) continue
                    val a = d[i]; val b = d[j]
                    val delta = b.pos - a.pos
                    val dist = delta.len()
                    val minD = a.r + b.r
                    if (dist >= minD || dist < 1e-4f) continue

                    val n = delta / dist
                    val invA = 1f / a.m; val invB = 1f / b.m
                    val invSum = invA + invB

                    val overlap = minD - dist
                    a.pos -= n * (overlap * invA / invSum)
                    b.pos += n * (overlap * invB / invSum)

                    val vn = (b.vel - a.vel).dot(n)
                    if (vn < 0f) {
                        val jImp = -(1f + p.discRest) * vn / invSum
                        a.vel -= n * (jImp * invA)
                        b.vel += n * (jImp * invB)

                        if (firstHit < 0 && (i == strikerIndex || j == strikerIndex)) {
                            firstHit = if (i == strikerIndex) j else i
                            contact = a.pos.lerp(b.pos, 0.5f)
                        }
                    }
                }
            }

            // cushions + pockets
            for (i in d.indices) {
                if (!alive[i]) continue
                val disc = d[i]
                val pk = BoardSpec.inPocket(disc.pos, disc.r)
                if (pk >= 0) {
                    alive[i] = false
                    disc.vel = Vec2.ZERO
                    pocketed += Pocketed(i, disc.type, pk)
                    continue
                }
                if (disc.pos.x < disc.r) { disc.pos = Vec2(disc.r, disc.pos.y); disc.vel = Vec2(-disc.vel.x * p.wallRest, disc.vel.y) }
                if (disc.pos.x > BoardSpec.PLAY - disc.r) { disc.pos = Vec2(BoardSpec.PLAY - disc.r, disc.pos.y); disc.vel = Vec2(-disc.vel.x * p.wallRest, disc.vel.y) }
                if (disc.pos.y < disc.r) { disc.pos = Vec2(disc.pos.x, disc.r); disc.vel = Vec2(disc.vel.x, -disc.vel.y * p.wallRest) }
                if (disc.pos.y > BoardSpec.PLAY - disc.r) { disc.pos = Vec2(disc.pos.x, BoardSpec.PLAY - disc.r); disc.vel = Vec2(disc.vel.x, -disc.vel.y * p.wallRest) }
            }

            if (pathTick % 3 == 0) {
                if (alive[strikerIndex]) strikerPath += d[strikerIndex].pos
                if (firstHit >= 0 && alive[firstHit]) hitPath += d[firstHit].pos
            }
            pathTick++
            t += p.dt

            if (alive.count { it } <= 1) break
        }

        return Outcome(
            pocketed = pocketed,
            firstHitIndex = firstHit,
            strikerPocketed = !alive[strikerIndex],
            finalPositions = d.map { it.pos },
            strikerPath = strikerPath,
            hitPath = hitPath,
            contactPoint = contact,
            time = t
        )
    }

    /** Fast analytic clearance test: is segment a->b free of every disc except `ignore`? */
    fun segmentClear(discs: List<Disc>, a: Vec2, b: Vec2, ignore: Set<Int>, margin: Float): Boolean {
        for (i in discs.indices) {
            if (i in ignore) continue
            val d = discs[i]
            if (pointSegDist(d.pos, a, b) < d.r + margin) return false
        }
        return true
    }

    /** Predicted resting spot quality: distance from the nearest cushion corner-ish. */
    fun safetyScore(discs: List<Disc>, strikerIndex: Int, finalPositions: List<Vec2>): Float {
        val sp = finalPositions.getOrNull(strikerIndex) ?: return 0f
        var nearest = Float.MAX_VALUE
        for (p in BoardSpec.POCKETS) nearest = minOf(nearest, sp.dist(p))
        // being close to a pocket while the opponent shoots is bad; mid-board is safer
        return (nearest - 120f).coerceIn(-80f, 60f) * 0.35f
    }

    fun powerToSpeed(power: Float) = p.maxV * power.coerceIn(0.05f, 1f)
}
