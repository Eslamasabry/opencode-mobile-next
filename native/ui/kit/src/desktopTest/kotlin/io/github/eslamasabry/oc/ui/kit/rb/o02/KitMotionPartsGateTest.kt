package io.github.eslamasabry.oc.ui.kit.rb.o02

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.motion.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.*

class KitMotionPartsGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_motion_parts", caps = mapOf("KitAnimatedBox" to "wrapper", "KitAnimatedValue" to "wrapper", "KitDim" to "wrapper", "KitSpin" to "wrapper", "KitSwap" to "wrapper")) {
            Column {
                KitAnimatedBox(level = SurfaceLevel.Surface1, outlined = true) { KitText("Surface") }
                KitAnimatedValue(.8f) { value ->
                    SideEffect { assertEquals(.8f,value) }
                    val color = LocalKitColors.current.accent
                    Box(Modifier.width(80.dp * value).height(8.dp).background(color))
                }
                Box(Modifier.kitDim(level = KitDimLevel.Stale).size(32.dp).background(LocalKitColors.current.accent))
                KitSpin(.5f) { KitText("Turn") }
                KitSwap("Current") { KitText(it) }
                KitSwapCount(2) { KitText(it.toString()) }
                KitChevron(true)
            }
        }
    }
    @Test fun dimAndPacesMatchFlutter() {
        assertEquals(listOf("Quick","Standard"),KitPace.entries.map {it.name})
        assertEquals(.6f,kitDimAlpha(true,KitDimLevel.Stale))
        assertEquals(.38f,kitDimAlpha(true,KitDimLevel.Disabled))
        for (level in KitDimLevel.entries) assertEquals(1f,kitDimAlpha(false,level))
    }
}
