package com.venice.carromassist.solver

import com.venice.carromassist.core.*
import com.venice.carromassist.physics.PhysParams
import com.venice.carromassist.physics.Simulator

/**
 * Two-stage solver:
 *   1. Analytic candidate generation via the ghost-ball construction.
 *   2. Simulation verification + hill-climb refinement on the survivors.
 */
class Solver(private val params: PhysParams) {

    class Shot(
        val strikerPos: Vec2,
        val aimDir: Vec2,
        val power: Float,
        val targetIndex: Int,
        val pocketIndex: Int,
        val score: Float,
        val sim: Simulator.Outcome
    )

    private val sim = Simulator(params)

    private val POWERS = floatArrayOf(0.28f, 0.42f, 0.56f, 0.70f, 0.84f, 1.0f)

    fun solve(state: GameState, chaseQueen: Boolean, allowBounce: Boolean): Shot? {
        val discs = state.discs
        val si = state.strikerIndex
        if (si < 0) return null

        val myType = state.myColor
        val oppType = state.opponents()

        // targets: my coins always; queen if enabled and still on board
        val targets = discs.indices.filter { i ->
            val t = discs[i].type
            t != DiscType.STRIKER && (chaseQueen || t != DiscType.QUEEN)   // freestyle: every coin counts
        }
        if (targets.isEmpty()) return null

        val baseY = if (state.ourBaselineBottom) BoardSpec.BASE_Y[1] else BoardSpec.BASE_Y[0]
        val x0 = BoardSpec.BASE_X0 + BoardSpec.STRIKER_R
        val x1 = BoardSpec.BASE_X1 - BoardSpec.STRIKER_R

        val candidates = ArrayList<Shot>(256)
        val positions = ArrayList<Vec2>(24)
        for (k in 0..22) {
            val x = x0 + (x1 - x0) * k / 22f
            val pos = Vec2(x, baseY)
            // striker must not be overlapping a coin
            if (discs.indices.any { it != si && pos.dist(discs[it].pos) < BoardSpec.STRIKER_R + discs[it].r + 1.5f }) continue
            positions += pos
        }
        if (positions.isEmpty()) positions += Vec2((x0 + x1) / 2f, baseY)

        for (ti in targets) {
            val coin = discs[ti]
            for (pi in BoardSpec.POCKETS.indices) {
                val pocket = BoardSpec.POCKETS[pi]
                val u = (pocket - coin.pos).norm()
                if (u.len2() < 0.5f) continue

                // coin -> pocket must be clear
                if (!sim.segmentClear(discs, coin.pos, pocket, setOf(ti, si), coin.r * 0.92f)) continue

                val contact = coin.pos - u * (BoardSpec.STRIKER_R + BoardSpec.COIN_R)

                for (sp in positions) {
                    val aim = (contact - sp).norm()
                    if (aim.len2() < 0.5f) continue

                    // cut angle sanity: striker must actually push the coin toward the pocket
                    val cut = aim.dot(u)
                    if (cut < 0.20f) continue

                    // striker -> contact must be clear
                    if (!sim.segmentClear(discs, sp, contact, setOf(ti, si), BoardSpec.STRIKER_R * 0.96f)) continue

                    val heuristic = cut * 100f - sp.dist(contact) * 0.05f
                    candidates += Shot(sp, aim, 0.6f, ti, pi, heuristic, dummy())
                }
            }
        }

        // also always consider straight-in hits on the nearest coin (covers clutter)
        for (ti in targets) {
            for (sp in positions) {
                val aim = (discs[ti].pos - sp).norm()
                if (!sim.segmentClear(discs, sp, discs[ti].pos, setOf(ti, si), BoardSpec.STRIKER_R * 0.96f)) continue
                candidates += Shot(sp, aim, 0.7f, ti, nearestPocket(discs[ti].pos), 40f, dummy())
            }
        }
        if (candidates.isEmpty()) return null

        // prune to the most promising, then simulate
        val pruned = candidates.sortedByDescending { it.score }.take(70)

        var best: Shot? = null
        for (c in pruned) {
            for (pw in POWERS) {
                val s = evaluate(state, c.strikerPos, c.aimDir, pw, c.targetIndex, c.pocketIndex, myType, oppType)
                if (best == null || s.score > best!!.score) best = s
            }
        }
        best ?: return null

        // hill-climb refinement around the winner
        var refined = best!!
        for (round in 0 until 3) {
            val stepA = 0.045f / (round + 1)
            val stepP = 0.09f / (round + 1)
            val baseAngle = kotlin.math.atan2(refined.aimDir.y, refined.aimDir.x)
            for (da in intArrayOf(-1, 0, 1)) {
                for (dp in intArrayOf(-1, 0, 1)) {
                    val a = baseAngle + da * stepA
                    val pw = (refined.power + dp * stepP).coerceIn(0.12f, 1f)
                    val dir = Vec2(kotlin.math.cos(a), kotlin.math.sin(a))
                    val s = evaluate(state, refined.strikerPos, dir, pw, refined.targetIndex, refined.pocketIndex, myType, oppType)
                    if (s.score > refined.score) refined = s
                }
            }
        }
        return refined
    }

    private fun nearestPocket(p: Vec2): Int {
        var bi = 0; var bd = Float.MAX_VALUE
        for (i in BoardSpec.POCKETS.indices) {
            val d = p.dist(BoardSpec.POCKETS[i])
            if (d < bd) { bd = d; bi = i }
        }
        return bi
    }

    private fun evaluate(
        state: GameState, sp: Vec2, dir: Vec2, power: Float,
        target: Int, pocket: Int, myType: DiscType, oppType: DiscType
    ): Shot {
        val work = state.discs.map { it.copy() }.toMutableList()
        val si = state.strikerIndex
        work[si].pos = sp
        val outcome = sim.run(work, si, dir, sim.powerToSpeed(power))
        return Shot(sp, dir, power, target, pocket, score(state, outcome, myType, oppType), outcome)
    }

    // Freestyle scoring: every pocketed coin is good, whites worth more than blacks.
    private fun score(state: GameState, o: Simulator.Outcome, myType: DiscType, oppType: DiscType): Float {
        var s = 0f
        var count = 0
        for (p in o.pocketed) {
            when (p.type) {
                DiscType.BLACK -> { s += 70f; count++ }
                DiscType.WHITE -> { s += 110f; count++ }
                DiscType.QUEEN -> { s += 160f; count++ }
                else -> {}
            }
        }
        if (o.strikerPocketed) {
            s -= 130f
            if (count > 0) s += 55f
        }
        if (o.firstHitIndex < 0) s -= 90f   // whiffed entirely
        s += sim.safetyScore(state.discs, state.strikerIndex, o.finalPositions)
        return s
    }

    private fun dummy() = Simulator.Outcome(emptyList(), -1, false, emptyList(), emptyList(), emptyList(), null, 0f)

}
