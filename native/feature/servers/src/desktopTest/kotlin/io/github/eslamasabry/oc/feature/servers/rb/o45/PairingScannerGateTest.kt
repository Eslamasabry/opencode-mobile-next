@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.servers.rb.o45

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.servers.pairing.*
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class PairingScannerGateTest {
    private class Actions : PairingScannerActions {
        var closed = 0
        override fun close() { closed++ }
        override fun scanned(text: String) {}
        override fun rejected() {}
        override fun pasted(text: String?) {}
    }
    private class Camera(private val stage: ScanStage) : QrScannerSlot {
        override val supported = true
        @Composable override fun Viewfinder(handle: QrScannerHandle, accept: (String) -> Boolean, onCode: (String) -> Unit, onRejected: () -> Unit, modifier: Modifier) {
            SideEffect { handle.stage = stage }
            Box(modifier)
        }
    }
    @Test fun denied() = shot("denied", ScanStage.Denied)
    @Test fun denied_wide() = shot("denied_1280x800", ScanStage.Denied)
    @Test fun blocked() = shot("blocked", ScanStage.Blocked)
    @Test fun no_camera() = shot("no_camera", ScanStage.NoCamera)
    @Test fun starting() = shot("starting", ScanStage.Starting)
    private fun shot(name: String, stage: ScanStage) {
        FlutterGoldenGate.check("servers_pairing_scanner_$name", frame = FlutterGoldenGate.Frame.Screen) {
            PairingScannerScreen(PairingScannerUi(), Actions(), Camera(stage), { false })
        }
    }
    @Test fun `paste instead closes scanner back to the editor`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            PairingScannerScreen(PairingScannerUi(), actions, NoQrScannerSlot(), { false })
        } }
        onNodeWithText("Paste it instead").performClick()
        assertEquals(1, actions.closed)
        onNodeWithText("Pairing code").assertDoesNotExist()
    }
}
