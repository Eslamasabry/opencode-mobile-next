package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.Indication
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Plan 06 §6's `FakeKitPlatform` (it moves to `:ui:testing` when that module exists): fixed signals, records haptics,
 * and draws frost through the common `BlurEffect` when [frost] is true.
 */
class FakeKitPlatform(
    signals: KitSystemSignals = KitSystemSignals(),
    private val frost: Boolean = false,
    /** The platform can draw liquid glass (Android 13+). */
    override val liquidGlass: Boolean = false,
    /** The share of a frosted liquid pane's pixels its backdrop is drawn at (Android: half). */
    override val liquidResolution: Float = 1f,
    /** The shader compiler (desktop's in desktop tests; none by default). */
    override val liveShaders: io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler? = null,
    /** Joined glass's lens pass (desktop's in desktop tests; none by default, so panes are never joined). */
    private val joinEffect: ((io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram, Float) -> RenderEffect?)? = null,
) : KitPlatform {
    override fun liquidJoinEffect(program: io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram, blurPx: Float): RenderEffect? =
        joinEffect?.invoke(program, blurPx)

    /** Every liquid pane this platform was asked to draw, in order (the spec it was given). */
    val liquidSpecs: MutableList<io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec> = mutableListOf()

    override fun liquidPane(
        spec: io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec,
        backdrop: io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop,
        shape: androidx.compose.foundation.shape.CornerBasedShape,
        transform: () -> Float,
    ): androidx.compose.ui.Modifier {
        liquidSpecs += spec
        return io.github.eslamasabry.oc.ui.kit.effects.kitLiquidStandIn(spec, shape)
    }

    val haptics: MutableList<KitHaptic> = mutableListOf()
    override val name: String = "fake"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(signals)
    override val isDebug: Boolean = true

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic) {
        haptics += kind
    }

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? =
        if (frost) FrostPolicy.blurRadiusPx(spec.sigmaDp, density).let { BlurEffect(it, it) } else null
}
