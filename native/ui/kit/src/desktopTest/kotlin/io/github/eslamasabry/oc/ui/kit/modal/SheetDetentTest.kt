@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit 2 (2026-11-14): the tall model sheet's top edge stopped inside the top bar and sliced its subtitle through the
 * letters. The kit's tall detent rests a sheet's top clearly below the page's top chrome (status bar, bar, its second
 * row), never through a line of its words; without a page that says where its chrome ends, at most 90 % of the window.
 */
class SheetDetentTest {
    @Test
    fun `SHEET-DETENT-01 the tall detent rests below the chrome, or at 90 percent without one`() {
        assertEquals(720.dp, SheetLayout.tallHeight(800.dp, null))
        // 800 tall, chrome ending at 120: the sheet's top rests 8 dp below it, not at 80 inside it.
        assertEquals(672.dp, SheetLayout.tallHeight(800.dp, 120.dp))
        // Short chrome: the share stays the cap.
        assertEquals(720.dp, SheetLayout.tallHeight(800.dp, 40.dp))
    }

    @Test
    fun `SHEET-DETENT-02 a full sheet over a page with a two-line bar rests below the bar`() {
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 16.dp)) {
                            Box(Modifier.size(W.dp, H.dp)) {
                                KitScreen(
                                    page = true,
                                    topBar = { Box(Modifier.fillMaxWidth().height(BAR_DP.dp).testTag("bar")) },
                                ) {
                                    KitSheet(visible = true, onDismissRequest = {}, title = "Choose a model", height = KitSheetHeight.Full) {
                                        KitText("Server default")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val bar = onNodeWithTag("bar").getBoundsInRoot()
            val sheet = onNodeWithTag("kit-sheet", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(sheet.top >= bar.bottom + 8.dp - 0.5.dp, "the sheet's top ${sheet.top} lies in the bar ending at ${bar.bottom}")
        }
    }

    private companion object {
        const val W = 411
        const val H = 760
        const val D = 2.625f
        const val BAR_DP = 88
    }
}
