@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A clickable row in a sheet with pinned actions: a click takes effect and nothing under the pointer moves. */
class KitSheetRowClickTest {
    @Test
    fun `a click on a row in a sheet with pinned buttons takes effect and the row does not move`() = runComposeUiTest {
        var picked by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    InWindow(400, 700) {
                        KitSheetHost {
                            KitSheet(
                                visible = true,
                                onDismissRequest = {},
                                title = "Provider",
                                height = KitSheetHeight.Full,
                                primary = KitAction("Save", onClick = {}),
                            ) {
                                Column {
                                    KitRow("GitHub Enterprise", onClick = { picked = true })
                                    if (picked) KitText("Enterprise URL")
                                }
                            }
                        }
                    }
                }
            }
        }
        val before = onNodeWithText("GitHub Enterprise").getBoundsInRoot()
        onNodeWithText("GitHub Enterprise").performClick()
        waitForIdle()
        assertTrue(picked, "the click took effect")
        onNodeWithText("Enterprise URL").assertExistsCompat()
        assertEquals(before, onNodeWithText("GitHub Enterprise").getBoundsInRoot(), "the row stayed where it was")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
