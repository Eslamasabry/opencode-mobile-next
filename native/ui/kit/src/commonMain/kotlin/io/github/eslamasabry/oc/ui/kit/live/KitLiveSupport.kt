package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 * Small helpers shared by the industrial and premium looks (Dart `kit_live_support.dart`). Everything here is
 * deterministic (no global randomness), so a fixed clock gives a fixed picture and goldens stay stable.
 */

/** A hash of up to three numbers into 0..1 (kit_live_support.dart:10-14). */
internal fun liveHash(a: Double, b: Double = 0.0, c: Double = 0.0): Double {
    val s = sin(a * 127.1 + b * 311.7 + c * 74.7) * 43758.5453 // F: lib/ui/kit/live_background/styles/kit_live_support.dart:13
    return s - floor(s)
}

private fun liveValueNoise(x: Double, y: Double): Double {
    val xi = floor(x)
    val yi = floor(y)
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3 - 2 * xf) // F: lib/ui/kit/live_background/styles/kit_live_support.dart:20
    val v = yf * yf * (3 - 2 * yf) // F: lib/ui/kit/live_background/styles/kit_live_support.dart:20
    val a = liveHash(xi, yi)
    val b = liveHash(xi + 1, yi)
    val c = liveHash(xi, yi + 1)
    val d = liveHash(xi + 1, yi + 1)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

/** Three octaves of value noise, 0..1 (kit_live_support.dart:26-30). */
internal fun liveFbm(x: Double, y: Double): Double =
    liveValueNoise(x, y) * .6 + // F: lib/ui/kit/live_background/styles/kit_live_support.dart:28
        liveValueNoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * .3 + // F: lib/ui/kit/live_background/styles/kit_live_support.dart:29
        liveValueNoise(x * 4.1 + 9.1, y * 4.1 + 3.7) * .1 // F: lib/ui/kit/live_background/styles/kit_live_support.dart:30

internal fun liveSmooth(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t) // F: lib/ui/kit/live_background/styles/kit_live_support.dart:34
}

/**
 * Dart `Color.lerp` on float channels (no colour-space change, unlike Compose's `lerp`, which mixes in Oklab), then
 * stored as the sRGB colour Compose keeps.
 */
internal fun liveLerp(a: Color, b: Color, t: Double): Color {
    val k = t.toFloat()
    return Color(
        red = (a.red + (b.red - a.red) * k).coerceIn(0f, 1f),
        green = (a.green + (b.green - a.green) * k).coerceIn(0f, 1f),
        blue = (a.blue + (b.blue - a.blue) * k).coerceIn(0f, 1f),
        alpha = (a.alpha + (b.alpha - a.alpha) * k).coerceIn(0f, 1f),
    )
}

/** Dart `liveMix` (kit_live_support.dart:37-38). */
internal fun liveMix(a: Color, b: Color, t: Double): Color = liveLerp(a, b, t.coerceIn(0.0, 1.0))

/** Dart `withValues(alpha:)`: [c] with its alpha replaced, clamped. */
internal fun liveAlpha(c: Color, a: Double): Color = c.copy(alpha = a.toFloat().coerceIn(0f, 1f))

/** The most opaque one layer may be at this frame (kit_live_support.dart:41). */
internal fun livePeak(f: KitLiveFrame): Double = f.palette.ceiling * f.strength

/** A seeded generator for a look's static geometry (kit_live_support.dart:44-45): Dart's `math.Random(seed)`. */
internal fun liveRandom(id: String): DartRandom {
    var h = 7L // F: lib/ui/kit/live_background/styles/kit_live_support.dart:45
    for (ch in id) h = (h * 31 + ch.code) and 0x7fffffffL // F: lib/ui/kit/live_background/styles/kit_live_support.dart:45
    return DartRandom(h)
}

/**
 * The Dart VM's `math.Random(seed)` (sdk `math_patch.dart` and runtime `math.cc` `Random_setupSeed`): a 64-bit Thomas
 * Wang mix of the seed, cranked four times, then multiply-with-carry with A = 0xffffda61. Same seed, same numbers as
 * the Flutter app, so the geometry a look scatters lands where it lands in the goldens.
 */
internal class DartRandom(seed: Long) {
    private var lo: Long
    private var hi: Long

    init {
        var s = mix64(seed)
        if (s == 0L) s = SEED_ZERO
        lo = s and MASK32
        hi = s ushr 32 // NV: the high word of the state
        repeat(CRANKS) { next() }
    }

    private fun next() {
        // (A * lo + hi) fits in 64 unsigned bits: both factors are below 2^32.
        val state = (A.toULong() * lo.toULong() + hi.toULong())
        lo = (state and MASK32.toULong()).toLong()
        hi = (state shr 32).toLong() // NV: the carry
    }

    /** Dart `nextInt` for a power of two [max] (the fast path `nextDouble` uses). */
    private fun nextPow2(max: Long): Long {
        next()
        return lo and (max - 1)
    }

    fun nextDouble(): Double = (nextPow2(1L shl 26) * POW2_27 + nextPow2(1L shl 27)) / POW2_53 // NV: the VM's 53-bit double

    private companion object {
        const val A = 0xffffda61L // NV: the VM's MWC multiplier
        const val MASK32 = 0xFFFFFFFFL // NV: low 32 bits
        const val SEED_ZERO = 0x5A17L // NV: the VM's replacement for a zero hash
        const val CRANKS = 4 // NV: the VM cranks a new generator four times
        const val POW2_27 = 134217728.0 // NV: 2^27
        const val POW2_53 = 9007199254740992.0 // NV: 2^53

        /** The runtime's `mix64` (Thomas Wang's 64-bit integer hash), on the seed's 64 bits. */
        fun mix64(seed: Long): Long = (seed.inv() + (seed shl 21)).let { it xor (it ushr 24) }.let { it * 265 }.let { it xor (it ushr 14) }.let { it * 21 }.let { it xor (it ushr 28) }.let { it + (it shl 31) } // NV: Thomas Wang 64-bit mix
    }
}

private class LiveClockSlot {
    var last = Double.NaN
    var acc = 0.0
}

private val clockKeys = HashMap<String, String>()

/**
 * A clock that runs at [rate] (1 = the idle speed) and never jumps when the rate changes (kit_live_support.dart:53-63).
 * The first frame starts at `t * rate`, so a fixed `t` is deterministic.
 */
internal fun liveClock(f: KitLiveFrame, key: String, rate: Double): Double {
    val slotKey = clockKeys.getOrPut(key) { "clock.$key" }
    val c = f.memory.of(slotKey, ::LiveClockSlot)
    val last = c.last
    if (last.isNaN() || f.t < last) {
        c.acc = f.t * rate
    } else {
        c.acc += min(f.t - last, .25) * rate // F: lib/ui/kit/live_background/styles/kit_live_support.dart:61
    }
    c.last = f.t
    return c.acc
}

/** One slot of [liveCached]. */
@PublishedApi
internal class LiveSlot<T> {
    var size: Size = Size.Unspecified
    var sig: Any? = null
    var value: T? = null
    var made = false
}

/**
 * Static geometry kept in [KitLiveFrame.memory], rebuilt only when the surface [size] or [sig] (colour, strength)
 * changes (kit_live_support.dart:73-90). Inline, so the maker costs nothing on the frames that reuse the value.
 */
@Suppress("UNCHECKED_CAST")
internal inline fun <T> liveCached(
    f: KitLiveFrame,
    key: String,
    size: Size,
    sig: Any? = null,
    make: () -> T,
): T {
    val s = f.memory.of(key) { LiveSlot<T>() }
    if (!s.made || s.size != size || s.sig != sig) {
        s.value = make()
        s.made = true
        s.size = size
        s.sig = sig
    }
    return s.value as T
}
