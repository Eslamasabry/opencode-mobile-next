package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.frame.KitCarousel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test

/** test/goldens/kit/kit_carousel_golden_test.dart: five cards (dots), opened on the second. */
class KitCarouselGateTest {
    @Test fun five() {
        FlutterGoldenGate.check("kit_carousel_five") {
            Box(Modifier.padding(vertical = 16.dp).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                KitCarousel("Themes", 5, 130f, initialIndex = 1) { i ->
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.surface2))
                }
            }
        }
    }
}
