package io.github.eslamasabry.oc.ui.kit.content.qr

import kotlin.math.abs

/**
 * Mask choice and module sizing for `KitQr` (plan 10 §14.1, MV-8). Pure.
 *
 * The penalty is a port of Dart `qr` 3.0.2 `QrImage._lostPoint` (Kazuhiko Arase's scoring, which the Flutter app drew
 * with): N1 counts same-coloured neighbours, N2 solid 2 × 2 blocks, N3 the 1:1:3:1:1 finder look-alike in rows and
 * columns, N4 the dark share's distance from half.
 */
public object QrRules {
    /** Modules of light margin on every side (Dart `KitTokens.qrQuietModules`). */
    public const val QUIET_MODULES: Int = 4

    /** The largest side a code is drawn at, in dp (Dart `KitTokens.qrMaxSize`). */
    public const val MAX_SIDE_DP: Float = 240f

    /** The lost-point score of one symbol; lower is easier to scan. */
    public fun penalty(m: QrMatrix): Double = sameNeighbours(m) + solidBlocks(m) + finderLookAlikes(m) + balance(m)

    /** N1: a module whose eight neighbours mostly share its colour scores 3 + (same − 5) when more than five do. */
    internal fun sameNeighbours(m: QrMatrix): Double {
        val n = m.size
        var lost = 0.0
        for (row in 0 until n) for (col in 0 until n) {
            var same = 0
            val dark = m.isDark(row, col)
            for (r in -1..1) {
                if (row + r !in 0 until n) continue
                for (c in -1..1) {
                    if (col + c !in 0 until n || (r == 0 && c == 0)) continue
                    if (dark == m.isDark(row + r, col + c)) same++
                }
            }
            if (same > 5) lost += 3 + same - 5
        }
        return lost
    }

    /** N2: 3 for every solid 2 × 2 block. */
    internal fun solidBlocks(m: QrMatrix): Double {
        val n = m.size
        var lost = 0.0
        for (row in 0 until n - 1) for (col in 0 until n - 1) {
            var count = 0
            if (m.isDark(row, col)) count++
            if (m.isDark(row + 1, col)) count++
            if (m.isDark(row, col + 1)) count++
            if (m.isDark(row + 1, col + 1)) count++
            if (count == 0 || count == 4) lost += 3
        }
        return lost
    }

    /** N3: 40 for every dark light dark dark dark light dark run, in rows then columns. */
    internal fun finderLookAlikes(m: QrMatrix): Double {
        val n = m.size
        var lost = 0.0
        for (row in 0 until n) for (col in 0 until n - 6) {
            if (finderLike { m.isDark(row, col + it) }) lost += 40
        }
        for (col in 0 until n) for (row in 0 until n - 6) {
            if (finderLike { m.isDark(row + it, col) }) lost += 40
        }
        return lost
    }

    /** N4: ten points per five percent the dark share is away from half. */
    internal fun balance(m: QrMatrix): Double {
        val n = m.size
        var dark = 0
        for (row in 0 until n) for (col in 0 until n) if (m.isDark(row, col)) dark++
        return abs(100.0 * dark / n / n - 50) / 5 * 10
    }

    private inline fun finderLike(at: (Int) -> Boolean): Boolean =
        at(0) && !at(1) && at(2) && at(3) && at(4) && !at(5) && at(6)

    /**
     * The mask (0…7) whose trial symbol scores lowest; the first wins a tie, as in Dart `qr`. [trial] builds the symbol
     * for one mask with format and version bits left light.
     */
    public fun bestMask(trial: (Int) -> QrMatrix): Int {
        var best = 0
        var bestLost = Double.MAX_VALUE
        for (mask in 0 until 8) {
            val lost = penalty(trial(mask))
            if (lost < bestLost) {
                best = mask
                bestLost = lost
            }
        }
        return best
    }

    /**
     * The drawn side in physical pixels: at most [MAX_SIDE_DP] and [maxWidthPx], snapped down so every module (quiet zone
     * included) is a whole number of physical pixels (LOOK-21). A box too narrow for one pixel per module keeps the width
     * it has, with fractional modules, rather than growing past it.
     */
    public fun sidePx(maxWidthPx: Float, moduleCount: Int, density: Float): Float {
        val total = moduleCount + QUIET_MODULES * 2
        val ideal = maxOf(0f, minOf(maxWidthPx, MAX_SIDE_DP * density))
        val modulePx = (ideal / total).toInt()
        return if (modulePx < 1) ideal else (modulePx * total).toFloat()
    }
}
