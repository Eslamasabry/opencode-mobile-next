package io.github.eslamasabry.oc.ui.kit.effects

import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Live glass, first build (KitLiquidJoinLive.kt): the bar's menu joined to the bar moves on one progress on one spring.
 * An open from rest is today's approved "drop, rebound, fill"; a turn anywhere continues from where the body is (no
 * value steps, nothing waits); a close never bounces and ends inside the menu's exit hold; nothing moves at rest.
 */
class KitLiquidJoinLiveMotionTest {
    private class Sample(val ms: Double, val length: Float, val fill: Float, val content: Float, val calm: Float, val progress: Float)

    /** Frames of a spring from [from] with [turns] (ms, leaving) at [hz], for [ms]. */
    private fun run(turns: List<Pair<Int, Boolean>>, ms: Int, hz: Int = 120, from: LiveJoinSpring = LiveJoinSpring()): List<Sample> {
        val s = from
        val dt = 1f / hz
        val out = mutableListOf<Sample>()
        val left = turns.toMutableList()
        var t = 0.0
        val frame = LiveJoinFrame()
        while (t <= ms) {
            while (left.isNotEmpty() && left.first().first <= t) s.aim(left.removeAt(0).second)
            if (t > 0) LiveJoinMotion.step(s, dt, 1f)
            LiveJoinMotion.values(s.progress, s.rebound, frame)
            out += Sample(t, frame.length, frame.fill, frame.content, frame.calm, s.progress)
            t += 1000.0 / hz
        }
        return out
    }

    private fun opened(): LiveJoinSpring = LiveJoinSpring().also { s -> repeat(240) { if (it == 0) s.aim(false); LiveJoinMotion.step(s, 1f / 120, 1f) } }

    private val fillAt = (LiquidJoin.turnMs(LiquidJoin.DROP_ZETA, LiquidJoin.DROP_K) + LiquidJoin.FILL_AFTER_TURN_MS) / 1000f

    @Test
    fun `B-GLASS-LIVE-001 an open from rest is today's drop, rebound and fill, frame for frame`() {
        val frames = run(listOf(0 to false), 600)
        for (f in frames) {
            val t = (f.ms / 1000.0).toFloat()
            if (t <= 0f || t > LiveJoinMotion.endS - .02f) continue
            assertEquals(LiveJoinMotion.response(LiquidJoin.DROP_ZETA, LiquidJoin.DROP_K, t), f.length, .01f, "the column at ${f.ms} ms")
            assertEquals(LiveJoinMotion.response(LiquidJoin.FILL_ZETA, LiquidJoin.FILL_K, t - fillAt).coerceIn(0f, 1f), f.fill, .01f, "the fill at ${f.ms} ms")
        }
        assertTrue(frames.maxOf { it.length } in 1.06f..1.10f, "the drop passes its line once by about 8 %: ${frames.maxOf { it.length }}")
        assertTrue(frames.none { it.fill > 0f && it.ms < 190 }, "the fill waits for the drop's turn")
        val rest = frames.last()
        assertEquals(1f, rest.progress)
        assertEquals(1f, rest.length)
        assertEquals(1f, rest.fill)
        assertEquals(1f, rest.content)
        assertTrue(frames.first { it.progress >= 1f }.ms <= 520, "at rest 500 ms after the open, nothing creeps after")
    }

    /** Frames where a value jumps against both its neighbours' changes (more than [tol] and three times either). */
    private fun steps(frames: List<Sample>, tol: Float, value: (Sample) -> Float): List<String> {
        val d = (1 until frames.size).map { value(frames[it]) - value(frames[it - 1]) }
        return (1 until d.size - 1).filter { abs(d[it]) > tol && abs(d[it]) > 3 * maxOf(abs(d[it - 1]), abs(d[it + 1])) }.map { "${frames[it + 1].ms.toInt()} ms ${d[it]}" }
    }

    @Test
    fun `B-GLASS-LIVE-002 a turn anywhere continues from where it is - no value steps, no stall`() {
        val open = run(listOf(0 to false), 600)
        fun fastest(value: (Sample) -> Float, frames: List<Sample>) = (1 until frames.size).maxOf { abs(value(frames[it]) - value(frames[it - 1])) }
        // What counts as a step (a jump against both neighbours): 2 % of the column, the fill or the reach; 8 % of the
        // rows' alpha (a turn just as the fill starts shows them for a frame or two at a few per cent).
        val values =
            listOf<Triple<String, Float, (Sample) -> Float>>(
                Triple("column", .02f) { it.length },
                Triple("fill", .02f) { it.fill },
                Triple("rows", .08f) { it.content },
                Triple("reach", .02f) { it.calm },
            )
        for (hz in listOf(60, 120)) for (turn in 0..640 step 8) for (back in listOf(0, 40, 100, 200)) {
            val turns = if (back == 0) listOf(0 to false, turn to true) else listOf(0 to false, turn to true, turn + back to false)
            val frames = run(turns, turn + back + 800, hz)
            for ((name, tol, value) in values) {
                assertEquals(emptyList(), steps(frames, tol, value), "$name, turned at $turn ms, back after $back ms, $hz Hz")
                // Never faster than two and a half times an open's fastest frame: a turn moves, it does not jump.
                assertTrue(fastest(value, frames) <= 2.5f * fastest(value, open) * 120f / hz + .01f, "$name moves too fast after a turn at $turn ms ($hz Hz)")
            }
            // No stall: from a turn until it rests the progress never stands still for two frames running (today's
            // reopen waited the drop's whole turn, 206 ms, before it filled again).
            val moving = frames.filter { it.ms > turn && it.progress > 0f && it.progress < 1f }
            for (i in 2 until moving.size) {
                val still = abs(moving[i].progress - moving[i - 1].progress) < 1e-4f && abs(moving[i - 1].progress - moving[i - 2].progress) < 1e-4f
                assertFalse(still, "still at ${moving[i].ms} ms after a turn at $turn ms ($hz Hz)")
            }
        }
    }

    @Test
    fun `B-GLASS-LIVE-003 a close never bounces - the width empties, then the column is drawn up`() {
        val frames = run(listOf(0 to true), 500, from = opened())
        for (i in 1 until frames.size) {
            assertTrue(frames[i].length <= frames[i - 1].length + 1e-4f, "the column grows while closing at ${frames[i].ms} ms")
            assertTrue(frames[i].fill <= frames[i - 1].fill + 1e-4f, "the fill grows while closing at ${frames[i].ms} ms")
        }
        val emptied = frames.first { it.fill <= 0f }.ms
        assertTrue(frames.filter { it.ms < emptied }.all { it.length > .97f }, "the column waits for the width")
    }

    @Test
    fun `B-GLASS-LIVE-004 a close from anywhere ends inside the menu's exit hold`() {
        for (turn in 0..640 step 8) {
            val frames = run(listOf(0 to false, turn to true), turn + LiquidJoin.CLOSE_MS + 200)
            val end = frames.first { it.ms > turn && it.progress <= 0f }.ms - turn
            assertTrue(end <= LiquidJoin.CLOSE_MS - 17, "a close at $turn ms ends $end ms later (hold ${LiquidJoin.CLOSE_MS} ms)")
            assertEquals(0f, frames.last().length)
        }
    }

    @Test
    fun `B-GLASS-LIVE-005 at rest nothing moves - no frame is asked for while the menu is closed or open`() {
        val closed = LiveJoinSpring()
        closed.aim(true)
        assertFalse(closed.moving, "closed and told to close")
        val open = opened()
        assertFalse(open.moving, "at rest open")
        open.aim(false)
        assertFalse(open.moving, "open and told to open")
        val before = open.progress
        LiveJoinMotion.step(open, 1f / 60, 1f)
        assertEquals(before, open.progress)
    }

    @Test
    fun `B-GLASS-LIVE-006 a slowed recording slows the same motion - the time scale stretches it exactly`() {
        val normal = run(listOf(0 to false), 600)
        val s = LiveJoinSpring()
        s.aim(false)
        val frame = LiveJoinFrame()
        val dt = 1f / 120
        // At four times slower, frame 4n shows what frame n showed.
        for (n in 1..60) {
            repeat(4) { LiveJoinMotion.step(s, dt, 4f) }
            LiveJoinMotion.values(s.progress, s.rebound, frame)
            assertEquals(normal[n].length, frame.length, .003f, "column at frame $n")
            assertEquals(normal[n].fill, frame.fill, .003f, "fill at frame $n")
        }
    }
}
