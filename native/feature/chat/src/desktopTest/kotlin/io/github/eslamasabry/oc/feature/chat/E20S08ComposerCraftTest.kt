package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** E20-S08 (craft walk of 3062b719a): the composer's phrase row and its chip line. */
@OptIn(ExperimentalTestApi::class)
class E20S08ComposerCraftTest {
    private class Actions : BasicComposerActions {
        val hidden = mutableListOf<String>()

        override fun draftChanged(text: String) {}

        override suspend fun send(text: String): Boolean = true

        override fun loadModels() {}

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true

        override fun attachFile() {}

        override fun removeAttachment(index: Int) {}

        override fun dismissAttachmentFailure() {}

        override suspend fun selectAgent(agent: String): Boolean = true

        override fun mentionFile(path: String) {}

        override fun attach(kind: AttachKind) {}

        override fun hidePhrase(text: String) {
            hidden += text
        }
    }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = "en",
            content = content,
        )
    }

    private fun ui(models: ComposerModels = ComposerModels(), phrases: List<String> = emptyList()) =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            draft = DraftFacts(),
            targetKey = "p/s",
            models = models,
            pickingFile = false,
            attachmentFailure = null,
            filesAvailable = true,
            phrases = phrases,
        )

    private fun chrome(rowChips: @Composable () -> Unit = {}) =
        ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false }, rowChips = rowChips)

    @Test
    fun `E20-S08 phrase chips show the phrases only on one row`() = runComposeUiTest {
        val phrases = listOf("Continue", "Run the tests", "Commit this", "Explain the last change")
        setContent { Theme { Box(Modifier.width(360.dp)) { BasicComposer(ui(phrases = phrases), Actions(), chrome()) } } }
        // No "Hide <phrase>" chip beside each phrase: hiding is the chip's long-press menu.
        phrases.forEach { phrase ->
            assertEquals(0, onAllNodesWithText("Hide $phrase", useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        // One line (it scrolls sideways when full), never wrapped to a second row.
        val tops =
            phrases.map { p ->
                onNodeWithText(p, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
            }
        assertTrue(tops.all { abs(it - tops.first()) < 1f }, "phrase tops $tops")
        onNodeWithTag("chat-phrase-chips", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `E20-S08 the approval chip is never scrolled under the mic or Send`() = runComposeUiTest {
        val long = ComposerModel("zai-coding-plan", "glm-5.3-flash", "GLM-5.3-Flash Extended Thinking Preview", true)
        val withChip = chrome {
            KitComposerChip("Approve everything", onClick = {}, modifier = Modifier.testTag("approval-chip"), active = true)
        }
        setContent {
            Theme {
                Box(Modifier.width(360.dp)) {
                    BasicComposer(ui(models = ComposerModels(choices = listOf(long), selected = long)), Actions(), withChip)
                }
            }
        }
        val send = onNodeWithTag("chat-send-button", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chip = onNodeWithTag("approval-chip", useUnmergedTree = true).fetchSemanticsNode()
        val shown = chip.boundsInRoot
        // Shown whole: its box on screen is as wide as it was laid out (a chip scrolled under the button is clipped).
        assertTrue(abs(shown.width - chip.size.width) < 1f, "chip shows ${shown.width} of ${chip.size.width} px")
        assertTrue(shown.right <= send.left + 0.5f, "chip $shown clear of Send $send")
        // E20-S11 reversed the priority: beside a long model name the approval words may end in "…" (in the chip's own
        // box), but the chip keeps room for its start and is never pushed under Send.
        assertTrue(shown.width >= 96f - 0.5f, "the approval chip keeps its least room, was ${shown.width}")
        val model = onNodeWithTag("chat-model-button", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(model.right <= shown.left + 0.5f, "the model chip $model ends before the approval chip $shown")
    }
}
