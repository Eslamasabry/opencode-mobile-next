package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 * Small helpers shared by the looks (Dart `lib/ui/kit/live_background/styles/kit_live_support.dart`). Everything here
 * is deterministic (no global randomness), so a fixed clock gives a fixed picture and goldens stay stable.
 */

/** A hash of up to three numbers into 0..1 (Dart `liveHash`). */
public fun liveHash(a: Double, b: Double = 0.0, c: Double = 0.0): Double {
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

/** Three octaves of value noise, 0..1 (Dart `liveFbm`). */
public fun liveFbm(x: Double, y: Double): Double =
    liveValueNoise(x, y) * .6 + // F: lib/ui/kit/live_background/styles/kit_live_support.dart:28
        liveValueNoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * .3 + // F: lib/ui/kit/live_background/styles/kit_live_support.dart:29
        liveValueNoise(x * 4.1 + 9.1, y * 4.1 + 3.7) * .1 // F: lib/ui/kit/live_background/styles/kit_live_support.dart:30

/** Smoothstep (Dart `liveSmooth`). */
public fun liveSmooth(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t) // F: lib/ui/kit/live_background/styles/kit_live_support.dart:34
}

/**
 * Flutter `Color.lerp` (each channel lerped in sRGB as doubles), not Compose's `lerp`, which mixes in Oklab and would
 * give other colours (Dart `liveMix`).
 */
public fun liveMix(a: Color, b: Color, t: Double): Color {
    val k = t.coerceIn(0.0, 1.0).toFloat()
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

/** [c] with alpha [a], clamped (Flutter `withValues(alpha:)`). */
public fun liveAlpha(c: Color, a: Double): Color = c.copy(alpha = a.coerceIn(0.0, 1.0).toFloat())

/** The most opaque one layer may be at this frame (Dart `livePeak`). */
public fun livePeak(f: KitLiveFrame): Double = f.palette.ceiling * f.strength

/** A seeded generator for a style's static geometry (Dart `liveRandom`): Dart's own `Random`, seeded from [id]. */
public fun liveRandom(id: String): DartRandom {
    var h = 7L // F: lib/ui/kit/live_background/styles/kit_live_support.dart:45
    for (c in id) h = (h * 31 + c.code) and 0x7fffffffL // F: lib/ui/kit/live_background/styles/kit_live_support.dart:45
    return DartRandom(h)
}

private class LiveClockSlot {
    var last: Double = Double.NaN
    var acc = 0.0
}

/**
 * A clock that runs at [rate] (1 = the idle speed) and never jumps when the rate changes: it adds the elapsed time
 * times the rate each frame, at most a quarter second at a time. The first frame starts at `t * rate`, so a fixed `t`
 * is deterministic (Dart `liveClock`).
 */
public fun liveClock(f: KitLiveFrame, key: String, rate: Double): Double {
    val c = f.memory.of("clock.$key") { LiveClockSlot() }
    val last = c.last
    if (last.isNaN() || f.t < last) {
        c.acc = f.t * rate
    } else {
        c.acc += min(f.t - last, .25) * rate // F: lib/ui/kit/live_background/styles/kit_live_support.dart:61
    }
    c.last = f.t
    return c.acc
}

private class LiveCachedSlot<T> {
    var size: Size? = null
    var sig: Any? = null
    var value: T? = null
}

/**
 * Static geometry kept in [KitLiveFrame.memory], rebuilt only when the surface [size] or [sig] (colour, strength)
 * changes (Dart `liveCached`); [dispose] lets go of the old value.
 */
public fun <T : Any> liveCached(
    f: KitLiveFrame,
    key: String,
    size: Size,
    sig: Any? = null,
    dispose: ((T) -> Unit)? = null,
    make: () -> T,
): T {
    val s = f.memory.of(key) { LiveCachedSlot<T>() }
    val held = s.value
    if (held == null || s.size != size || s.sig != sig) {
        if (held != null) dispose?.invoke(held)
        s.value = make()
        s.size = size
        s.sig = sig
    }
    return s.value!!
}

/**
 * Dart's `math.Random(seed)` as the Dart VM implements it (`sdk/lib/_internal/vm/lib/math_patch.dart` and
 * `runtime/lib/math.cc`), so a look seeded the same draws the same geometry as the Flutter app: the seed is mixed with
 * Thomas Wang's 64-bit hash, then a multiply-with-carry generator steps a 64-bit state.
 */
public class DartRandom(seed: Long) {
    private var lo: Long
    private var hi: Long

    init {
        // A zero hash falls back to the VM's seed, then the state steps four times before the first value.
        val s = mix64(seed).let { if (it == 0L) 0x5A17L else it }; lo = s and MASK; hi = s ushr Int.SIZE_BITS; repeat(4) { next() } // NV: runtime/lib/math.cc and math_patch.dart _Random._withState
    }

    private fun next() {
        val n = A * lo + hi
        lo = n and MASK
        hi = n ushr Int.SIZE_BITS
    }

    /** A value in 0 until [max] (Dart `nextInt`). */
    public fun nextInt(max: Int): Int {
        require(max > 0)
        if ((max and -max) == max) {
            next()
            return (lo and (max - 1).toLong()).toInt()
        }
        var rnd32: Long
        var result: Long
        do {
            next()
            rnd32 = lo
            result = rnd32 % max
        } while (rnd32 - result + max > MASK + 1)
        return result.toInt()
    }

    /** A value in 0..1 (Dart `nextDouble`: 26 bits, then 27, over 2^53). */
    public fun nextDouble(): Double {
        next()
        val high = lo and 0x3FFFFFFL; next(); return (high * (1L shl 27).toDouble() + (lo and 0x7FFFFFFL)) / (1L shl 53).toDouble() // NV: math_patch.dart _nextInt26, _nextInt27, _POW2_53_D
    }

    private companion object {
        const val A = 0xffffda61L; const val MASK = 0xFFFFFFFFL // NV: the Dart VM generator's multiplier and its 32-bit word

        /** Thomas Wang's 64-bit mix (runtime/lib/math.cc `mix64`). */
        fun mix64(seed: Long): Long {
            var n = seed.inv() + (seed shl 21); n = n xor (n ushr 24); n *= 265; n = n xor (n ushr 14); n *= 21; n = n xor (n ushr 28); return n + (n shl 31) // NV: Thomas Wang's 64-bit mix, as the Dart VM seeds Random
        }
    }
}
