package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertFalse

/** Issue 147: an action chip on a half-width tile at 360 dp wraps its words; it is never cut to "New conver…". */
@OptIn(ExperimentalTestApi::class)
class KitChipActionNotCutTest {
    @Test
    fun `I147-FIRST-RUN an action chip label that does not fit on one line wraps and is not cut`() =
        runDesktopComposeUiTest(360, 400) {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                    Box(Modifier.width(140.dp)) {
                        KitChip("New conversation", kind = KitChipKind.Action, icon = KitIcons.chat, onClick = {})
                    }
                }
            }
            waitForIdle()
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithTag("kit-chip-label", useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!(results)
            assertFalse(results.first().hasVisualOverflow, "the label is cut")
        }
}
