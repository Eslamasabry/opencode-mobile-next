package io.github.eslamasabry.oc.ui.design.color

import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

/** One colour as 0xAARRGGBB (plan 06 §4.1). Pure Kotlin: the kit turns it into a Compose `Color`. */
@JvmInline
public value class Argb(public val v: Int) {
    public val alpha: Int get() = (v ushr 24) and 0xFF
    public val red: Int get() = (v shr 16) and 0xFF
    public val green: Int get() = (v shr 8) and 0xFF
    public val blue: Int get() = v and 0xFF

    /** This colour with its alpha replaced (Flutter `withValues(alpha:)`); rounding as Flutter's 8-bit storage. */
    public fun withAlpha(alpha: Float): Argb = Argb(((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (v and 0xFFFFFF))

    override fun toString(): String = "#" + v.toUInt().toString(16).uppercase().padStart(8, '0')
}

/** `argb(0xFF0B0C0E)`: a literal as Flutter writes it. */
public fun argb(hex: Long): Argb = Argb(hex.toInt())

/** Flutter `Color.lerp` per channel (the Dart `_mix`). */
public fun lerp(a: Argb, b: Argb, t: Float): Argb {
    fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
    return Argb((ch(a.alpha, b.alpha) shl 24) or (ch(a.red, b.red) shl 16) or (ch(a.green, b.green) shl 8) or ch(a.blue, b.blue))
}

/** Flutter `Color.alphaBlend(foreground, background)`: [this] painted over [background]. */
public fun Argb.over(background: Argb): Argb {
    val fa = alpha / 255f
    if (fa == 0f) return background
    val ba = background.alpha / 255f
    val outA = fa + ba * (1 - fa)
    if (outA == 0f) return Argb(0)
    fun ch(f: Int, b: Int) = ((f * fa + b * ba * (1 - fa)) / outA).roundToInt().coerceIn(0, 255)
    val a = (outA * 255f).roundToInt()
    return Argb((a shl 24) or (ch(red, background.red) shl 16) or (ch(green, background.green) shl 8) or ch(blue, background.blue))
}
