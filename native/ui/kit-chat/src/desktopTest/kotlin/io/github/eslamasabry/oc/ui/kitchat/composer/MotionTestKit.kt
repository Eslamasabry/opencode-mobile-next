package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.coroutines.flow.MutableStateFlow

/** A silent platform that records haptics and lets a test set battery saver, heat and the animator scale. */
class RecordingPlatform(signals: KitSystemSignals = KitSystemSignals()) : KitPlatform {
    val haptics = mutableListOf<KitHaptic>()
    override val name: String = "recording"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(signals)
    override val isDebug: Boolean = true

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic) {
        haptics += kind
    }

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
}

/** The real KitTheme at a chosen motion level (the chat's own test theme is pinned to Off + still frames). */
@Composable
fun MotionTheme(
    level: MotionLevel = MotionLevel.Full,
    stillFrame: Boolean = false,
    saver: Boolean = false,
    dark: Boolean = true,
    platform: KitPlatform = RecordingPlatform(),
    localeTag: String = "en",
    content: @Composable () -> Unit,
) {
    val mode = when {
        saver -> AppearanceMode.Saver
        dark -> AppearanceMode.Dark
        else -> AppearanceMode.Light
    }
    KitTheme(KitAppearance(mode = mode, motion = level), platform, localeTag, stillFrame, content)
}
