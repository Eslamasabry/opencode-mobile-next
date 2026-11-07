package io.github.eslamasabry.oc.ui.kit.live

import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.rb34LiveStyleAuroraImage
import org.jetbrains.compose.resources.StringResource

/**
 * Every look the native app draws, in the order the picker shows them (Dart `kitLiveStyles`,
 * `lib/ui/kit/live_background/kit_live_styles.dart`): the ambient looks, then the industrial ones, then the premium
 * ones. Add a style by writing one [KitLiveStyle] and listing it here, one line, at its Dart place (its label is
 * already in `strings_kit.xml`). The commented lines are the looks orders 32 and 33 port; each uncomments its own.
 */
public val kitLiveStyles: List<KitLiveStyle> =
    listOf(
        KitLiveAurora,
        // KitLiveMesh, // order 32
        // KitLiveDotRipple, // order 32
        // KitLiveTopo, // order 33
        // KitLiveFlowThreads, // order 32
        // KitLiveGlowSpill, // order 32
        // KitLiveLightRays, // order 32
        // KitLiveConstellation, // order 32
        // KitLiveHalftone, // order 32
        // KitLiveLiquidBlobs, // order 32
        // KitLiveMatrixRain, // order 32
        KitLiveBlueprint,
        KitLiveCircuit,
        // KitLiveOscilloscope, // order 33
        // KitLiveRadar, // order 33
        // KitLiveHoneycomb, // order 32
        KitLiveBrushedMetal,
        // KitLiveSilk, // order 33
        KitLiveCaustics,
        KitLiveBokeh,
    )

/** The look shipped as the default (Dart `kitLiveDefaultStyleId`). */
public const val KIT_LIVE_DEFAULT_STYLE_ID: String = "aurora"

/** The style stored under [id]; the default when [id] is unknown or null (Dart `kitLiveStyleById`). */
public fun kitLiveStyleById(id: String?): KitLiveStyle =
    kitLiveStyles.firstOrNull { it.id == id } ?: kitLiveStyles.first { it.id == KIT_LIVE_DEFAULT_STYLE_ID }

/** The drawn style stored under [id], or null when the native app draws no such painted look (the painted image too). */
public fun kitLiveStyleOrNull(id: String?): KitLiveStyle? = kitLiveStyles.firstOrNull { it.id == id }

/**
 * Every look a person can choose, as stored ids, in picker order: the painted aurora image second, as GLASS_REF lists it
 * (GOLDEN-STANDARD §5.5), then every drawn look in [kitLiveStyles] order.
 */
public val kitLiveLookIds: List<String>
    get() = buildList {
        val drawn = kitLiveStyles.map { it.id }
        add(drawn.first())
        add(AuroraImage.ID)
        addAll(drawn.drop(1))
    }

/** The picker's name for the look stored as [id] (the painted aurora's or a drawn look's; the default look's otherwise). */
public fun kitLiveLookLabel(id: String): StringResource =
    if (id == AuroraImage.ID) Res.string.rb34LiveStyleAuroraImage else kitLiveStyleById(id).label
