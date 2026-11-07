package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 * Small helpers shared by the industrial and premium looks (Dart `styles/kit_live_support.dart`). Everything here is
 * deterministic (no global randomness), so a fixed clock gives a fixed picture and goldens stay stable.
 *
 * Interim copy of order 31's support file (orders 31-33 run in parallel).
 */

/** Dart's `%` on doubles: never negative for a positive divisor. */
internal fun dmod(a: Double, b: Double): Double {
    val r = a % b
    return if (r < 0) r + kotlin.math.abs(b) else r
}

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

/** Dart `liveSmooth`. */
public fun liveSmooth(e0: Double, e1: Double, x: Double): Double = smooth(e0, e1, x)

/** Dart `liveMix` (`Color.lerp`, clamped). */
public fun liveMix(a: Color, b: Color, t: Double): Color = mixColor(a, b, t)

/** The most opaque one layer may be at this frame (Dart `livePeak`). */
public fun livePeak(f: KitLiveFrame): Double = f.palette.ceiling * f.strength

private class LiveClockSlot {
    var last: Double? = null
    var acc = 0.0
}

/**
 * A clock that runs at [rate] (1 = the idle speed) and never jumps when the rate changes (Dart `liveClock`): it adds
 * the elapsed time times the rate each frame. The first frame starts at `t * rate`, so a fixed `t` is deterministic.
 */
public fun liveClock(f: KitLiveFrame, key: String, rate: Double): Double {
    val c = f.memory.of("clock.$key") { LiveClockSlot() }
    val last = c.last
    if (last == null || f.t < last) {
        c.acc = f.t * rate
    } else {
        c.acc += min(f.t - last, .25) * rate // F: lib/ui/kit/live_background/styles/kit_live_support.dart:61
    }
    c.last = f.t
    return c.acc
}

private class LiveSlot<T> {
    var size: Size? = null
    var sig: Any? = null
    var value: T? = null
}

/**
 * Static geometry kept in [KitLiveFrame.memory], rebuilt only when the surface [size] or [sig] changes (Dart
 * `liveCached`).
 */
public fun <T : Any> liveCached(f: KitLiveFrame, key: String, size: Size, sig: Any? = null, make: () -> T): T {
    val s = f.memory.of(key) { LiveSlot<T>() }
    val v = s.value
    if (v == null || s.size != size || s.sig != sig) {
        val made = make()
        s.value = made
        s.size = size
        s.sig = sig
        return made
    }
    return v
}

/**
 * Dart's `math.Random(seed)` on the Dart VM (`runtime/lib/math.cc` `Random_setupSeed`, `math_patch.dart` `_Random`):
 * a multiply-with-carry generator seeded through Thomas Wang's 64-bit mix and cranked four times, so a look seeded as
 * Flutter seeds it lays out the same stars, threads and grain.
 */
public class DartRandom(seed: Int) {
    private var lo: Long
    private var hi: Long

    init {
        val n = mix64(seed.toLong()).let { if (it == 0L) 0x5a17L else it } // NV: Dart VM Random_setupSeed's zero fallback
        lo = n and MASK32
        hi = (n ushr 32) and MASK32 // NV: the high word of the VM's 64-bit state
        repeat(4) { nextState() } // NV: the VM cranks a new generator four times
    }

    /** state = A * lo + hi (mod 2^64); A * lo + hi < 2^64, so unsigned maths is exact. */
    private fun nextState() {
        val prod = A.toULong() * lo.toULong() + hi.toULong()
        lo = (prod and MASK32.toULong()).toLong()
        hi = (prod shr 32).toLong() // NV: the carry is the high word
    }

    /** Dart `nextDouble`: 26 then 27 random bits, over 2^53, in [0, 1). */
    public fun nextDouble(): Double {
        nextState()
        val a = lo and ((1L shl 26) - 1) // NV: Dart _Random.nextDouble's 26 bits
        nextState()
        val b = lo and ((1L shl 27) - 1) // NV: Dart _Random.nextDouble's 27 bits
        return (a * (1L shl 27).toDouble() + b) / (1L shl 53).toDouble() // NV: Dart _Random.nextDouble's scale
    }

    private companion object {
        const val A: Long = 0xffffda61L // NV: the VM's multiply-with-carry constant

        /** Thomas Wang's 64-bit mix (Dart VM `mix64`). */
        fun mix64(seed: Long): Long {
            var n = seed.inv() + (seed shl 21) // NV: Thomas Wang 64-bit mix, step 1
            n = (n xor (n ushr 24)) * 265 // NV: Thomas Wang 64-bit mix, steps 2-3
            n = (n xor (n ushr 14)) * 21 // NV: Thomas Wang 64-bit mix, steps 4-5
            n = n xor (n ushr 28) // NV: Thomas Wang 64-bit mix, step 6
            return n + (n shl 31) // NV: Thomas Wang 64-bit mix, step 7
        }
    }
}
