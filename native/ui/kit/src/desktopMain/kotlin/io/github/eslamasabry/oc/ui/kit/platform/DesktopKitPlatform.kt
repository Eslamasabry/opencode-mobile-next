package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.foundation.Indication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToSvgPainter

/**
 * Desktop (plan 06 §6): hover/press fill instead of a ripple, no haptics, a fine pointer, frost
 * drawn solid.
 */
public class DesktopKitPlatform : KitPlatform {
    override val name: String = "desktop"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: StateFlow<KitSystemSignals> =
        MutableStateFlow(KitSystemSignals(finePointer = true)).asStateFlow()

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic): Unit = Unit // desktops have no haptics

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null

    @OptIn(ExperimentalResourceApi::class)
    override fun svgPainter(text: String, pixels: Int): Painter? {
        if (!isStaticKitSvg(text) || pixels !in 1..256) return null
        return try {
            text.encodeToByteArray().decodeToSvgPainter(Density(1f))
        } catch (_: Exception) {
            null
        }
    }
}
