@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.ui.kit.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.scanner.KitScannerState
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ScannerStateTest {
    @Test fun `a missing camera keeps a visible way back to paste`() = runComposeUiTest {
        var exits = 0
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            KitScannerState(KitStateKind.Missing, "No camera", icon = KitIcons.cameraOff,
                primary = KitAction("Paste it instead", { exits++ }))
        } }
        onNodeWithText("Paste it instead").performClick()
        assertEquals(1, exits)
    }
}
