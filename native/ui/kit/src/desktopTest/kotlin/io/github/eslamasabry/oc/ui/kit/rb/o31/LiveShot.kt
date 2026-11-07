package io.github.eslamasabry.oc.ui.kit.rb.o31

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLivePalette
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.live.KitLiveText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** The second every look golden is drawn at (test/kit_live_styles_industrial_test.dart `_t`). */
internal const val GOLDEN_T: Double = 7.3

/**
 * The scene of test/kit_live_styles_industrial_test.dart's goldens: the page ground and [style] painted over it at
 * [GOLDEN_T], idle, strength 1 (the test's `_Painter`).
 */
@Composable
internal fun LiveShot(style: KitLiveStyle) {
    val colors = LocalKitColors.current
    val palette = KitLivePalette.of(colors)
    val memory = remember { KitLiveMemory() }
    val measurer = rememberTextMeasurer()
    val mono = LocalKitTypography.current.style(TypeRole.Mono).fontFamily!!
    Canvas(Modifier.fillMaxSize()) {
        drawRect(palette.ground)
        style.paint(this, size, KitLiveFrame(GOLDEN_T, 0.0, 1.0, palette, memory, KitLiveText(measurer, mono)))
    }
}
