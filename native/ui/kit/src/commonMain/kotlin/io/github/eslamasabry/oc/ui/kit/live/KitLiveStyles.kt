package io.github.eslamasabry.oc.ui.kit.live

/**
 * The looks the native app draws, in the order the picker shows them (Dart `kitLiveStyles`,
 * `lib/ui/kit/live_background/kit_live_styles.dart`): the ambient looks, then the industrial ones, then the premium
 * ones. Orders 31-33 port the looks in parallel; each adds its looks here as one list entry per look, at its Dart
 * place, so the merges stay one line each. A stored id with no entry yet keeps the page its flat ground.
 */
public val kitLiveStyles: List<KitLiveStyle> =
    listOf(
        KitLiveMesh(),
        KitLiveDotRipple(),
        KitLiveFlowThreads(),
        KitLiveGlowSpill(),
        KitLiveLightRays(),
        KitLiveConstellation(),
        KitLiveHalftone(),
        KitLiveLiquidBlobs(),
        KitLiveMatrixRain(),
        KitLiveHoneycomb(),
    )

/** The look shipped as the default (Dart `kitLiveDefaultStyleId`). */
public const val KIT_LIVE_DEFAULT_STYLE_ID: String = "aurora"

/**
 * The look stored under [id] (Dart `kitLiveStyleById`); the default look when [id] is unknown, and null while the
 * native app does not draw that one either.
 */
public fun kitLiveStyleById(id: String?): KitLiveStyle? =
    kitLiveStyles.firstOrNull { it.id == id } ?: kitLiveStyles.firstOrNull { it.id == KIT_LIVE_DEFAULT_STYLE_ID }
