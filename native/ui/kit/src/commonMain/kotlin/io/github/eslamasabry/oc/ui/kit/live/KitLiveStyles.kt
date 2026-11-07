package io.github.eslamasabry.oc.ui.kit.live

import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleAurora
import io.github.eslamasabry.oc.ui.words.res.liveStyleBlueprint
import io.github.eslamasabry.oc.ui.words.res.liveStyleBokeh
import io.github.eslamasabry.oc.ui.words.res.liveStyleBrushedMetal
import io.github.eslamasabry.oc.ui.words.res.liveStyleCaustics
import io.github.eslamasabry.oc.ui.words.res.liveStyleCircuit
import io.github.eslamasabry.oc.ui.words.res.liveStyleConstellation
import io.github.eslamasabry.oc.ui.words.res.liveStyleDotRipple
import io.github.eslamasabry.oc.ui.words.res.liveStyleFlowThreads
import io.github.eslamasabry.oc.ui.words.res.liveStyleGlowSpill
import io.github.eslamasabry.oc.ui.words.res.liveStyleHalftone
import io.github.eslamasabry.oc.ui.words.res.liveStyleHoneycomb
import io.github.eslamasabry.oc.ui.words.res.liveStyleLightRays
import io.github.eslamasabry.oc.ui.words.res.liveStyleLiquidBlobs
import io.github.eslamasabry.oc.ui.words.res.liveStyleMatrixRain
import io.github.eslamasabry.oc.ui.words.res.liveStyleMesh
import io.github.eslamasabry.oc.ui.words.res.liveStyleOscilloscope
import io.github.eslamasabry.oc.ui.words.res.liveStyleRadar
import io.github.eslamasabry.oc.ui.words.res.liveStyleSilk
import io.github.eslamasabry.oc.ui.words.res.liveStyleTopo
import org.jetbrains.compose.resources.StringResource

/**
 * Every look id in the order the picker shows them (Dart `kitLiveStyles`, `kit_live_styles.dart:29-50`): the ambient
 * looks, then the industrial ones, then the premium ones. Stored in `oc.effectsLiveStyle`; never renamed.
 */
public val kitLiveStyleIds: List<String> = listOf(
    "aurora", "mesh", "dotRipple", "topo", "flowThreads", "glowSpill", "lightRays", "constellation", "halftone",
    "liquidBlobs", "matrixRain", "blueprint", "circuit", "oscilloscope", "radar", "honeycomb", "brushedMetal", "silk",
    "caustics", "bokeh",
)

/** The look shipped as the default (kit_live_styles.dart:53). */
public const val KIT_LIVE_DEFAULT_STYLE_ID: String = "aurora"

/**
 * The looks the native app draws, in picker order. Orders 31, 32 and 33 each add their looks here, one line per look;
 * an id stored by the Flutter app whose look is not ported yet keeps the page its plain ground.
 */
public val kitLiveStyles: List<KitLiveStyle> = listOf<KitLiveStyle>(
    KitLiveTopo, // order 33
    KitLiveOscilloscope, // order 33
    KitLiveRadar, // order 33
    KitLiveSilk, // order 33
).sortedBy { kitLiveStyleIds.indexOf(it.id) }

/** The look stored under [id], or null when the native app does not draw it (the page keeps its ground). */
public fun kitLiveStyleById(id: String?): KitLiveStyle? = kitLiveStyles.firstOrNull { it.id == id }

/** The name the picker shows for the look [id] (Dart `KitLiveStyle.label`), or null for an unknown id. */
public fun kitLiveStyleLabel(id: String): StringResource? = labels[id]

private val labels: Map<String, StringResource> = mapOf(
    "aurora" to Res.string.liveStyleAurora,
    "mesh" to Res.string.liveStyleMesh,
    "dotRipple" to Res.string.liveStyleDotRipple,
    "topo" to Res.string.liveStyleTopo,
    "flowThreads" to Res.string.liveStyleFlowThreads,
    "glowSpill" to Res.string.liveStyleGlowSpill,
    "lightRays" to Res.string.liveStyleLightRays,
    "constellation" to Res.string.liveStyleConstellation,
    "halftone" to Res.string.liveStyleHalftone,
    "liquidBlobs" to Res.string.liveStyleLiquidBlobs,
    "matrixRain" to Res.string.liveStyleMatrixRain,
    "blueprint" to Res.string.liveStyleBlueprint,
    "circuit" to Res.string.liveStyleCircuit,
    "oscilloscope" to Res.string.liveStyleOscilloscope,
    "radar" to Res.string.liveStyleRadar,
    "honeycomb" to Res.string.liveStyleHoneycomb,
    "brushedMetal" to Res.string.liveStyleBrushedMetal,
    "silk" to Res.string.liveStyleSilk,
    "caustics" to Res.string.liveStyleCaustics,
    "bokeh" to Res.string.liveStyleBokeh,
)
