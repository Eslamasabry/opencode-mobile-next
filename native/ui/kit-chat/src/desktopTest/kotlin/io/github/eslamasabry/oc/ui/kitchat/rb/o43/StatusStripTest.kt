package io.github.eslamasabry.oc.ui.kitchat.rb.o43

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStatusStrip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StatusStripTest {
    @Test fun `C19 Stop keeps its full target outside the overflowing status line`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                Box(Modifier.width(320.dp)) {
                    KitComposerStatusStrip(
                        hasChips = true,
                        model = { KitChip("A long model name", Modifier.testTag("model"), kind = KitChipKind.Action, onClick = {}) },
                        stop = { KitChip("Stop", Modifier.testTag("stop"), kind = KitChipKind.Action, onClick = {}) },
                    ) {
                        KitChip("Auto-approve on", kind = KitChipKind.Action, onClick = {})
                        KitChip("Background", kind = KitChipKind.Action, onClick = {})
                    }
                }
            }
        }
        val stop = onNodeWithTag("stop").fetchSemanticsNode().boundsInRoot
        val model = onNodeWithTag("model").fetchSemanticsNode().boundsInRoot
        val scroll = onNodeWithTag("composer-status-scroll").fetchSemanticsNode().boundsInRoot
        assertTrue(stop.width >= 48 && stop.height >= 48)
        assertTrue(stop.left >= model.right)
        assertTrue(model.left >= scroll.right)
        onNodeWithTag("composer-status-scroll").performTouchInput { swipeLeft() }
        assertEquals(stop, onNodeWithTag("stop").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `C19 a model without status chips ends at the strip edge`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                KitComposerStatusStrip(false, Modifier.width(360.dp).testTag("strip"), model = {
                    KitChip("Server default", Modifier.testTag("model"), kind = KitChipKind.Action, onClick = {})
                }) {}
            }
        }
        val strip = onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
        val model = onNodeWithTag("model").fetchSemanticsNode().boundsInRoot
        assertEquals(strip.right, model.right)
        onNodeWithTag("composer-status-scroll").assertDoesNotExist()
    }
}
