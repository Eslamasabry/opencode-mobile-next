@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.teaching

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test

/**
 * F-005 (first-time-user walk): a tip drew on top of the Details sheet and hid its content. One rule in the tip's
 * host, for every tip: while a sheet or dialog is open the tip is not drawn, and it shows once the sheet has gone.
 */
class KitTipOverSheetTest {
    @Test
    fun `B-NAV-TIP-SHEET-01 a tip is not drawn over an open sheet and appears after it closes`() = runComposeUiTest {
        var sheet by mutableStateOf(true)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                KitSheetHost {
                    Column(Modifier.size(412.dp, 720.dp)) {
                        KitTip("Hold a message for Details", visible = true, onDismissed = {}) { Box(Modifier.size(48.dp)) }
                        KitSheet(visible = sheet, onDismissRequest = { sheet = false }, title = "Details") {}
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("kit-tip").assertDoesNotExist()
        sheet = false
        waitForIdle()
        onNodeWithTag("kit-tip").assertExists()
    }
}
