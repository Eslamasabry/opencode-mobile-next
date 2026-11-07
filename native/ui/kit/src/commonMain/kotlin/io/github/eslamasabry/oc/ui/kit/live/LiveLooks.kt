package io.github.eslamasabry.oc.ui.kit.live

import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.kit.live.looks.AuroraLook
import io.github.eslamasabry.oc.ui.kit.live.looks.CausticsLook
import io.github.eslamasabry.oc.ui.kit.live.looks.GlowSpillLook
import io.github.eslamasabry.oc.ui.kit.live.looks.LiquidBlobsLook
import io.github.eslamasabry.oc.ui.kit.live.looks.MeshLook
import io.github.eslamasabry.oc.ui.kit.live.looks.SilkLook
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
import io.github.eslamasabry.oc.ui.words.res.rb34LiveStyleAuroraImage
import io.github.eslamasabry.oc.ui.words.res.Res
import org.jetbrains.compose.resources.StringResource

/**
 * Every live look the app draws, by its stored id (`oc.effectsLiveStyle`). Adding a look is one file under `looks/`
 * and one line in [shaderLooks]; the picker and the background pick it up from here.
 */
public object LiveLooks {
    /**
     * The stored ids in the order Flutter offers them (Dart `KitLiveStyle` ids). Kept whole, so a choice made in the
     * Flutter app keeps its meaning even while the native app does not draw that look yet.
     */
    public val ids: List<String> =
        listOf(
            "aurora",
            AuroraImage.ID,
            "mesh",
            "dotRipple",
            "topo",
            "flowThreads",
            "glowSpill",
            "lightRays",
            "constellation",
            "halftone",
            "liquidBlobs",
            "matrixRain",
            "blueprint",
            "circuit",
            "oscilloscope",
            "radar",
            "honeycomb",
            "brushedMetal",
            "silk",
            "caustics",
            "bokeh",
        )

    /** The shader looks. One line per look; builders B and C add theirs here. */
    private val shaderLooks: List<LiveLook> =
        listOf(
            AuroraLook,
            MeshLook,
            SilkLook,
            CausticsLook,
            LiquidBlobsLook,
            GlowSpillLook,
        )

    private val byId: Map<String, LiveLook> = shaderLooks.associateBy { it.id }

    /** The shader look for [id], or null for the painted aurora, an unknown id or a look not drawn yet. */
    public fun shader(id: String?): LiveLook? = id?.let(byId::get)

    /** True for every id the app can show: the painted aurora and each shader look. */
    public fun draws(id: String?): Boolean = id == AuroraImage.ID || shader(id) != null

    /** The look's name for the picker (Dart `KitLiveStyle.label`), in English and Arabic from `:ui:words`. */
    public fun label(id: String): StringResource =
        when (id) {
            "auroraImage" -> Res.string.rb34LiveStyleAuroraImage
            "mesh" -> Res.string.liveStyleMesh
            "dotRipple" -> Res.string.liveStyleDotRipple
            "topo" -> Res.string.liveStyleTopo
            "flowThreads" -> Res.string.liveStyleFlowThreads
            "glowSpill" -> Res.string.liveStyleGlowSpill
            "lightRays" -> Res.string.liveStyleLightRays
            "constellation" -> Res.string.liveStyleConstellation
            "halftone" -> Res.string.liveStyleHalftone
            "liquidBlobs" -> Res.string.liveStyleLiquidBlobs
            "matrixRain" -> Res.string.liveStyleMatrixRain
            "blueprint" -> Res.string.liveStyleBlueprint
            "circuit" -> Res.string.liveStyleCircuit
            "oscilloscope" -> Res.string.liveStyleOscilloscope
            "radar" -> Res.string.liveStyleRadar
            "honeycomb" -> Res.string.liveStyleHoneycomb
            "brushedMetal" -> Res.string.liveStyleBrushedMetal
            "silk" -> Res.string.liveStyleSilk
            "caustics" -> Res.string.liveStyleCaustics
            "bokeh" -> Res.string.liveStyleBokeh
            else -> Res.string.liveStyleAurora
        }

    /** What the Look picker offers, in Flutter's order. */
    public val offered: List<String>
        get() = ids.filter(::draws)
}

/** How a page ground draws its live look. */
public enum class LiveRender {
    /** The flat ground: Background off, Saver, a style that hides it, or a look the app does not draw. */
    Flat,

    /** The painted aurora picture (`auroraImage`), drawn on every API level. */
    Painted,

    /** The look's runtime shader (Android 13+, desktop). */
    Shader,

    /** A shader look where the platform has no runtime shaders (Android 8–12): a still gradient in the theme's colours. */
    Still,
}

/** Android runs the looks' AGSL from API 33 (Android 13, `RuntimeShader`); below it the page shows the still fallback. */
public const val LIVE_SHADER_MIN_SDK: Int = 33

/** True where an Android release at [sdkInt] can run the looks' shaders. */
public fun androidRunsLiveShaders(sdkInt: Int): Boolean = sdkInt >= LIVE_SHADER_MIN_SDK

/** Picks the renderer for [id]; [shadersSupported] is false below Android 13 (API 33). */
public fun liveRenderFor(id: String?, shows: Boolean, shadersSupported: Boolean): LiveRender =
    when {
        !shows || id == null -> LiveRender.Flat
        id == AuroraImage.ID -> LiveRender.Painted
        LiveLooks.shader(id) == null -> LiveRender.Flat
        shadersSupported -> LiveRender.Shader
        else -> LiveRender.Still
    }
