@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-438 (the box's bottom row at 360 dp): the approval control is the shield alone, the model takes the rest on one line
 * with an end ellipsis and at least its first 12 characters, else it takes its own line inside the box above the plus
 * and send row; the plus and send stay 48 dp, inside the box, and nothing is cut to a letter.
 */
class ComposerChipsFitTest {
    private fun check(fontScale: Float, model: String, rtl: Boolean) {
        runDesktopComposeUiTest(360, 800) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    KitTheme(KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off), DesktopKitPlatform(), if (rtl) "ar" else "en", stillFrame = true) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            KitComposerRowFit("", leadingTargets = 1, stack = true) { stacked ->
                                KitComposerBox(
                                    running = false,
                                    stacked = stacked,
                                    hasLeading = true,
                                    field = {
                                        KitText(
                                            if (rtl) "اسأل" else "Ask",
                                            role = TypeRole.Body,
                                            tone = TextTone.Tertiary,
                                            kind = KitTextKind.Frame,
                                        )
                                    },
                                    leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
                                    chips = {
                                        KitComposerChip(label = "Auto-approve on", onClick = {}, icon = KitIcons.shield, opensPicker = false, semanticsLabel = "Approval", collapsible = true)
                                        KitComposerChip(label = model, onClick = {}, icon = KitIcons.model, shrinks = true)
                                    },
                                    trailing = { KitComposerSend("Send", onClick = {}) },
                                    modifier = Modifier.fillMaxWidth().testTag("box"),
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            fun b(tag: String): Rect = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val box = b("box")
            val text = onNodeWithText(model, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(text.left >= box.left && text.right <= box.right, "the model chip's words are cut by the box: $text in $box (scale $fontScale, rtl $rtl)")
            assertTrue(onAllNodesWithText("Auto-approve on", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "the approval control still shows its words beside the model at 360 dp (scale $fontScale)")
            for (name in listOf("Send", "Tools")) {
                val target = onNodeWithContentDescription(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(target.width >= 47f && target.height >= 47f, "$name is ${target.width} x ${target.height}, under 48 dp")
                assertTrue(target.left >= box.left && target.right <= box.right, "$name is outside the box")
            }
            // One line, or its own line above the plus and send row: the words are never wrapped into a sliver.
            assertTrue(text.width >= MIN_WIDTH * fontScale || text.top < box.bottom - 96f, "the model keeps fewer than 12 characters on the row: ${text.width} (scale $fontScale)")
        }
    }

    @Test
    fun `B-CHAT-EDGE-018 at 360 dp text 1_0 the model keeps its name on the row beside the shield`() = check(1f, "Gemini 3.8 Flash (High)", rtl = false)

    @Test
    fun `B-CHAT-EDGE-019 at 360 dp text 1_3 nothing is cut to a letter and the plus and send stay 48 dp`() = check(1.3f, "Gemini 3.8 Flash (High)", rtl = false)

    @Test
    fun `B-CHAT-EDGE-020 at 360 dp text 1_3 in Arabic the chips fit the box and mirror`() = check(1.3f, "جيميني 3.8 فلاش (عالي)", rtl = true)

    private companion object {
        /** Twelve characters of the label at its average glyph width (7.2 dp) is 86 dp; less is a cut. */
        const val MIN_WIDTH = 80f
    }
}
