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
class FakeKitPlatform(signals: KitSystemSignals = KitSystemSignals(), private val frost: Boolean = false) : KitPlatform {
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
