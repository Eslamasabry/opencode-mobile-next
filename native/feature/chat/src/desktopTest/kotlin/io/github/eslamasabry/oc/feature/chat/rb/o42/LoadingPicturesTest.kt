package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.ui.ChatPane
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * Pictures for the coordinator (owner 2026-11-14, a loading conversation that showed nothing): the conversation while it
 * loads, a second in (the placeholder turns, the words, the message box in its place), phone size, in the looks named in
 * the brief. Glass is drawn with the lens, as on an Android 13+ phone (a blur only: the desktop has no bend).
 */
@OptIn(ExperimentalTestApi::class)
class LoadingPicturesTest {
    /** Desktop with the lens: a liquid pane draws what it reads blurred by its recipe's blur, under its tint. */
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            val draw = backdropDraw(backdrop)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = spec.recipe.blurDp.dp.toPx()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(r, r)
                        layer.record { draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }

        /** The backdrop's draw is kit-internal; a test outside the kit reaches it through its JVM getter. */
        @Suppress("UNCHECKED_CAST")
        private fun backdropDraw(backdrop: KitLiquidBackdrop): (DrawScope, LayoutCoordinates) -> Unit {
            val getter = KitLiquidBackdrop::class.java.methods.first { it.name.startsWith("getDraw") && it.parameterCount == 0 }
            return getter.invoke(backdrop) as (DrawScope, LayoutCoordinates) -> Unit
        }
    }

    private fun shot(name: String, style: Style, dark: Boolean, live: String?) =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val state = scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf())
            val appearance =
                KitAppearance(
                    style = style,
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = MotionLevel.Off,
                    liveBackground = live != null,
                    liveStyle = live ?: "aurora",
                )
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(appearance, if (style == Style.Glass) LensPlatform() else DesktopKitPlatform(), "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { scene.Screen(state) }
                    }
                }
            }
            mainClock.advanceTimeBy(ChatPane.LOADING_WORDS_AFTER_MS + 500)
            compareGolden(name)
        }

    @Test fun glassDarkContour() = shot("loading-glass-dark-contour", Style.Glass, dark = true, live = "topo")

    @Test fun glassLightAurora() = shot("loading-glass-light-aurora", Style.Glass, dark = false, live = AuroraImage.ID)

    @Test fun solidDark() = shot("loading-solid-dark", Style.Solid, dark = true, live = null)

    @Test fun brutalLight() = shot("loading-brutal-light", Style.Brutal, dark = false, live = null)

    @Test fun paperLight() = shot("loading-paper-light", Style.Paper, dark = false, live = null)
}
