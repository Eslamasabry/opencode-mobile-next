package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode

/**
 * A pixel-art icon authored as a dot grid (theme-switch-premium §2): `#` is a dot, `.` is empty. [vector] is the
 * line icon it stands for, used where a dot grid would not fit (the stacked layout at large text).
 */
@Immutable
public class KitPixelIcon(public val name: String, rows: List<String>, public val vector: ImageVector) {
    public val size: Int = rows.size

    /** The dots' centres in grid cells (column + 0.5, row + 0.5), top row first, left to right. */
    public val dots: List<Offset>

    init {
        require(rows.isNotEmpty() && rows.all { it.length == rows.size }) { "$name: a pixel icon is a square grid" }
        require(rows.all { r -> r.all { it == '#' || it == '.' } }) { "$name: only # and . in a pixel icon" }
        dots = rows.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, ch -> if (ch == '#') Offset(x + 0.5f, y + 0.5f) else null } }
    }

    override fun toString(): String = "KitPixelIcon($name)"
}

/** The theme modes' pixel icons, 12 × 12 dots each (sun, moon with a sparkle, auto, leaf). */
public object KitPixelIcons {
    public const val GRID: Int = 12

    public val sun: KitPixelIcon by lazy {
        KitPixelIcon(
            "sun",
            listOf(
                ".....##.....",
                ".....##.....",
                ".#........#.",
                "..#..##..#..",
                "....####....",
                "##.######.##",
                "##.######.##",
                "....####....",
                "..#..##..#..",
                ".#........#.",
                ".....##.....",
                ".....##.....",
            ),
            KitIcons.lightMode,
        )
    }

    public val moon: KitPixelIcon by lazy {
        KitPixelIcon(
            "moon",
            listOf(
                ".........#..",
                "........###.",
                "...###...#..",
                "..###.......",
                ".####.......",
                ".####.......",
                ".#####......",
                ".######...##",
                "..#########.",
                "..########..",
                "...######...",
                "............",
            ),
            KitIcons.darkMode,
        )
    }

    public val auto: KitPixelIcon by lazy {
        KitPixelIcon(
            "auto",
            listOf(
                ".....##.....",
                "...######...",
                "..####..##..",
                ".#####...##.",
                ".#####....#.",
                "######....##",
                "######....##",
                ".#####....#.",
                ".#####...##.",
                "..####..##..",
                "...######...",
                ".....##.....",
            ),
            KitIcons.systemTheme,
        )
    }

    public val leaf: KitPixelIcon by lazy {
        KitPixelIcon(
            "leaf",
            listOf(
                "............",
                ".......####.",
                "......#####.",
                "....####.##.",
                "....###.###.",
                "...###.###..",
                "..###.###...",
                "..##.####...",
                "..#####.....",
                "..####......",
                ".#..........",
                "............",
            ),
            KitIcons.leaf,
        )
    }

    /** The icon of a theme mode (Auto follows the system). */
    public fun of(mode: AppearanceMode): KitPixelIcon =
        when (mode) {
            AppearanceMode.System -> auto
            AppearanceMode.Light -> sun
            AppearanceMode.Dark -> moon
            AppearanceMode.Saver -> leaf
        }
}

/** One dot of a morph frame: where it is, how opaque, and how large (1 = a resting dot). */
@Immutable
public data class MorphDot(val x: Float, val y: Float, val alpha: Float = 1f, val scale: Float = 1f)

/**
 * The dot morph (theme-switch-premium §2): the dots of the old icon move to the slots of the new one. The mapping is
 * worked out once per change (nearest free slot, closest pairs first); every dot gets a fixed jitter (a sideways drift
 * that peaks mid-flight, so the icon reads as dissolving and re-forming) and a stagger (dots nearer the travel's lead
 * leave first). Old dots with no slot fade out where they are; new slots with no dot fade in. Positions are in the
 * caller's pixels; [cell] is one grid cell in those pixels (it scales the jitter).
 */
@Immutable
public class KitDotMorph(from: List<MorphDot>, to: List<Offset>, private val cell: Float) {
    private val fromX = FloatArray(from.size) { from[it].x }
    private val fromY = FloatArray(from.size) { from[it].y }
    private val fromA = FloatArray(from.size) { from[it].alpha }
    private val toX = FloatArray(to.size) { to[it].x }
    private val toY = FloatArray(to.size) { to[it].y }

    /** For each target slot, the source dot that moves into it, or -1 when the slot fades in. */
    internal val sourceOf: IntArray = IntArray(to.size) { -1 }

    /** The source dots with no slot: they fade out. */
    internal val orphans: IntArray

    private val stagger: FloatArray
    private val jitterX: FloatArray
    private val jitterY: FloatArray

    init {
        val taken = BooleanArray(from.size)
        if (from.isNotEmpty() && to.isNotEmpty()) {
            val pairs = ArrayList<Long>(from.size * to.size)
            val dist = FloatArray(from.size * to.size)
            for (i in from.indices) for (j in to.indices) {
                val dx = fromX[i] - toX[j]
                val dy = fromY[i] - toY[j]
                dist[i * to.size + j] = dx * dx + dy * dy
                pairs += (i * to.size + j).toLong()
            }
            pairs.sortWith(compareBy<Long> { dist[it.toInt()] }.thenBy { it })
            for (p in pairs) {
                val i = p.toInt() / to.size
                val j = p.toInt() % to.size
                if (!taken[i] && sourceOf[j] == -1) {
                    taken[i] = true
                    sourceOf[j] = i
                }
            }
        }
        orphans = from.indices.filter { !taken[it] }.toIntArray()
        // Stagger: by each target's place along the line of travel (the lead edge first).
        val dirX = if (to.isEmpty() || from.isEmpty()) 1f else (to.map { it.x }.average() - fromX.average()).toFloat()
        val sign = if (dirX < 0f) -1f else 1f
        val keys = FloatArray(to.size) { -sign * toX[it] + toY[it] * 0.25f }
        val order = to.indices.sortedBy { keys[it] }
        stagger = FloatArray(to.size)
        order.forEachIndexed { rank, j -> stagger[j] = if (to.size <= 1) 0f else rank / (to.size - 1f) }
        jitterX = FloatArray(to.size) { j -> hash(j * 2 + 1) * JITTER_CELLS * cell }
        jitterY = FloatArray(to.size) { j -> hash(j * 2 + 7) * JITTER_CELLS * cell }
    }

    public val slots: Int get() = toX.size

    /**
     * Fills [out] with the frame [elapsedMs] after the change started ([ThemeSwitchTimings.MORPH_MS] long), one entry
     * per target slot then one per orphan, and returns how many it wrote. No allocation: the draw loop calls it.
     */
    public fun frame(elapsedMs: Float, out: FloatArray): Int {
        var k = 0
        val spread = MORPH_MS - STAGGER_MS
        for (j in toX.indices) {
            val t = ((elapsedMs - stagger[j] * STAGGER_MS) / spread).coerceIn(0f, 1f)
            val e = ease(t)
            val bump = kotlin.math.sin(kotlin.math.PI.toFloat() * t)
            val s = sourceOf[j]
            if (s >= 0) {
                out[k++] = lerp(fromX[s], toX[j], e) + jitterX[j] * bump
                out[k++] = lerp(fromY[s], toY[j], e) + jitterY[j] * bump
                out[k++] = lerp(fromA[s], 1f, e)
                out[k++] = 1f - DUST_SHRINK * bump
            } else {
                // A new dot: it gathers in from its jitter and fades up.
                out[k++] = toX[j] + jitterX[j] * (1f - e) * 2f
                out[k++] = toY[j] + jitterY[j] * (1f - e) * 2f
                out[k++] = e
                out[k++] = 0.4f + 0.6f * e
            }
        }
        val fade = (elapsedMs / (MORPH_MS * 0.5f)).coerceIn(0f, 1f)
        for (i in orphans) {
            out[k++] = fromX[i]
            out[k++] = fromY[i]
            out[k++] = fromA[i] * (1f - fade)
            out[k++] = 1f - 0.5f * fade
        }
        return k / 4
    }

    /** The values per dot in [frame]'s output. */
    public val floats: Int get() = (toX.size + orphans.size) * 4

    /** The frame as dots, for tests and for an interruption to start from. */
    public fun sample(elapsedMs: Float): List<MorphDot> {
        val out = FloatArray(floats)
        val n = frame(elapsedMs, out)
        return List(n) { MorphDot(out[it * 4], out[it * 4 + 1], out[it * 4 + 2], out[it * 4 + 3]) }
    }

    public companion object {
        /** The morph's length and the most a dot waits to leave (theme-switch-premium beat table: re-formed by ~450). */
        public const val MORPH_MS: Float = 450f
        public const val STAGGER_MS: Float = 120f
        private const val JITTER_CELLS = 1.6f
        private const val DUST_SHRINK = 0.35f

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** Ease-out cubic: fast away, soft landing. */
        private fun ease(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

        /** A fixed pseudo-random value in -1 … 1 for [i] (the jitter is the same on every run). */
        internal fun hash(i: Int): Float {
            var h = i * -0x61c88647 + 0x3c6ef372
            h = h xor (h ushr 15)
            h *= 0x2c1b3c6d
            h = h xor (h ushr 12)
            return ((h and 0xffff) / 32767.5f) - 1f
        }
    }
}
