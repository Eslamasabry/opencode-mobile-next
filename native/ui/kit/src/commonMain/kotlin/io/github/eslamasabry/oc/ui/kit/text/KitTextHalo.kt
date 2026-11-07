package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

/**
 * The constants and rules of Dart `KitTextHalo` (`lib/ui/kit/kit_text_halo.dart`): a soft halo in
 * the ground's colour behind secondary and tertiary words that sit on a bright live background, the
 * way lock-screen text sits on a bright wallpaper.
 */
public object KitTextHaloSpec {
    /**
     * How much of the ground the halo puts back around a letter at least (0..1), as measured on the
     * kit's small text.
     */
    public const val COVER: Float = .55f // F: lib/ui/kit/kit_text_halo.dart:33

    /** One soft shadow, stacked this many times. */
    public const val LAYERS: Int = 6 // F: lib/ui/kit/kit_text_halo.dart:39

    /** Each layer's blur radius, in logical pixels. */
    public const val BLUR_RADIUS: Float = 3f // F: lib/ui/kit/kit_text_halo.dart:39

    /** A fill more opaque than this hides the live ground: no halo under it. */
    public const val SOLID_ALPHA: Float = .9f // F: lib/ui/kit/kit_text_halo.dart:96

    /** Dart `KitTextHalo.shadowsFor`: the same soft shadow in [ground], [LAYERS] times. */
    public fun shadowsFor(ground: Color): List<Shadow> =
        List(LAYERS) { Shadow(color = ground, blurRadius = BLUR_RADIUS) }

    /** Dart `_covers` for a fill (ColoredBox, BoxDecoration, ShapeDecoration, a kit surface, Material). */
    public fun coversFill(fill: Color?): Boolean = fill != null && fill.alpha > SOLID_ALPHA

    /** Dart `_covers` for a ShaderMask: any blend but `dstIn`/`dstOut` repaints the words. */
    public fun coversMask(blendMode: BlendMode): Boolean =
        blendMode != BlendMode.DstIn && blendMode != BlendMode.DstOut

    /** Dart `KitTextHalo.of`: the shadows, or null with no ground or when something covers it. */
    public fun shadows(ground: Color?, covered: Boolean): List<Shadow>? =
        if (ground == null || covered) null else shadowsFor(ground)
}

/** The ground the nearest [KitTextHalo] is made of; null for no halo (the default everywhere). */
public val LocalKitTextHaloGround: ProvidableCompositionLocal<Color?> = compositionLocalOf { null }

/** True under something solid (or a recolouring mask) between the words and the halo's ground. */
public val LocalKitTextHaloCovered: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * Provides the halo's [ground] to the words in [content] (Dart `KitTextHalo`). The live background
 * provides it while a bright look is painted; null is no halo, and words are exactly as before.
 * Words right under it are on the ground again, whatever covered an outer halo.
 *
 * States: ground, none
 * Text: none (a wrapper)
 * Cap: none (a wrapper)
 * Motion: none
 * Replaces: KitTextHalo (lib/ui/kit/kit_text_halo.dart)
 */
@Composable
public fun KitTextHalo(ground: Color?, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalKitTextHaloGround provides ground,
        LocalKitTextHaloCovered provides false,
        content = content,
    )
}

/**
 * Marks [content] as sitting on [fill] (a card, a row group, a chip, a sheet): when the fill is
 * solid the words under it get no halo (Dart `KitTextHalo._covers`).
 */
@Composable
public fun KitTextHaloCover(fill: Color?, content: @Composable () -> Unit) {
    if (!KitTextHaloSpec.coversFill(fill)) content()
    else CompositionLocalProvider(LocalKitTextHaloCovered provides true, content = content)
}

/** The halo's shadows for words composed here, or null for none (Dart `KitTextHalo.of`). */
@Composable
public fun kitTextHaloShadows(): List<Shadow>? =
    KitTextHaloSpec.shadows(LocalKitTextHaloGround.current, LocalKitTextHaloCovered.current)
