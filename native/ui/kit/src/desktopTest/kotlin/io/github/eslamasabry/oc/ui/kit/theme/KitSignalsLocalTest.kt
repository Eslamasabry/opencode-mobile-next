package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.kitClearPanesOn
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidGlassOn
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrost
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassFrostAvailable
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceGlass
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.kitFrostedBarFace
import io.github.eslamasabry.oc.ui.kit.frame.kitStatusEdgeBlur
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class CountingPlatform : KitPlatform by FakeKitPlatform(frost = true, liquidGlass = true) {
    val backing = MutableStateFlow(KitSystemSignals())
    var collectCalls = 0
        private set

    override val signals: StateFlow<KitSystemSignals> =
        object : StateFlow<KitSystemSignals> by backing {
            override suspend fun collect(collector: FlowCollector<KitSystemSignals>): Nothing {
                collectCalls++
                backing.collect(collector)
            }
        }
}

@OptIn(ExperimentalTestApi::class)
class KitSignalsLocalTest {
    @Test
    fun `B-KIT-SIGNALS kit composables read collected signals from KitTheme without collecting again`() =
        runComposeUiTest {
            val platform = CountingPlatform()
            var frostAvailable = false
            setContent {
                KitTheme(
                    appearance = KitAppearance(style = Style.Glass, motion = MotionLevel.Full),
                    platform = platform,
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    val frost = rememberKitFrostState()
                    kitClearPanesOn()
                    kitLiquidGlassOn()
                    kitStatusEdgeBlur()
                    kitFrostedBarFace(frost)
                    frostAvailable = kitGlassFrostAvailable()
                    rememberKitChromeVeil()
                    Column {
                        KitIcon(KitIcons.workspaceSelected)
                        KitUndoBar(KitUndoOffer("1", "Archived"), onUndo = {}, onTimeout = {})
                        Box(Modifier.size(16.dp).kitFrost(FrostRole.Composer, frost))
                        Box(Modifier.size(16.dp).kitSurfaceGlass(SurfaceDecoration(frost = 20f)))
                    }
                }
            }
            waitForIdle()

            assertEquals(1, platform.collectCalls, "only KitTheme collects platform.signals")
            assertTrue(frostAvailable)

            platform.backing.value = KitSystemSignals(highContrast = true)
            waitForIdle()

            assertFalse(frostAvailable, "theme signal updates propagate through the composition local")
            assertEquals(1, platform.collectCalls, "updating signals creates no new collectors")
        }
}
