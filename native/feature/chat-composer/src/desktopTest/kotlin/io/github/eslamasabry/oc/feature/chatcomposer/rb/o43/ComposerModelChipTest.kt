package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitComposerModelChip
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComposerModelChipTest {
    @Test fun `C19 narrow model keeps its full spoken name and opens the same choice`() = runComposeUiTest {
        var calls = 0
        val label = "Sonnet 4 with extended thinking"
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                Box(Modifier.width(92.dp)) { KitComposerModelChip(label, { calls++ }) }
            }
        }
        onNodeWithTag("kit-model-chip-label", useUnmergedTree = true).assertDoesNotExist()
        val target = onNodeWithContentDescription(label)
        val bounds = target.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width in 48f..92f && bounds.height >= 48f)
        target.performClick()
        assertEquals(1, calls)
    }

    @Test fun `C19 large text can keep the model glyph without a clipped chevron`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                KitTheme(platform = DesktopKitPlatform()) {
                    Box(Modifier.width(48.dp)) { KitComposerModelChip("Sonnet 4", {}) }
                }
            }
        }
        onNodeWithContentDescription("Sonnet 4").assertHasClickAction()
        onNodeWithTag("kit-model-chip-label", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("kit-chip-chevron", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `C19 model names wrap at large text when six characters still fit`() = runComposeUiTest {
        val label = "Sonnet 4 with extended thinking"
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                KitTheme(platform = DesktopKitPlatform()) {
                    Box(Modifier.width(240.dp)) { KitComposerModelChip(label, {}) }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithTag("kit-model-chip-label", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
    }

    @Test fun `C19 measured prefix never splits combining marks or emoji sequences`() {
        val platform = DesktopKitPlatform()
        val prefix = "a\u0301".repeat(6)
        assertEquals(prefix, platform.graphemePrefix(prefix + "other", 6))
        assertEquals("👩‍💻", platform.graphemePrefix("👩‍💻Model", 1))
        assertEquals("", platform.graphemePrefix("Model", 0))
    }
}
