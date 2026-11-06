package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** QA wave 1 (docs/native/qa/wave1): the composer's layout and the model picker. */
@OptIn(ExperimentalTestApi::class)
class QaWave1ComposerTest {
    private fun chrome(busy: Boolean) =
        ComposerChrome({ busy }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    private class Actions : BasicComposerActions {
        val calls = mutableListOf<String>()

        override fun draftChanged(text: String) {}

        override suspend fun send(text: String): Boolean = true

        override fun loadModels() {
            calls += "loadModels"
        }

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean {
            calls += "select <${choice?.providerId}/${choice?.modelId}>"
            return true
        }

        override fun attachFile() {}

        override fun removeAttachment(index: Int) {}

        override fun dismissAttachmentFailure() {}

        override suspend fun selectAgent(agent: String): Boolean = true

        override fun mentionFile(path: String) {}

        override fun attach(kind: io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind) {}
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

    private fun ui(models: ComposerModels = ComposerModels(), steers: Boolean = true) =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = steers,
            draft = DraftFacts(),
            targetKey = "p/s",
            models = models,
            pickingFile = false,
            attachmentFailure = null,
            filesAvailable = true,
        )

    @Test
    fun `B8 QA wave1 while a reply runs the message box keeps the full width`() = runComposeUiTest {
        setContent { Theme { Box(Modifier.width(360.dp)) { BasicComposer(ui(), Actions(), chrome(busy = true)) } } }
        // Looks pass (owner pick, 2026-11-05): one message box, the words on top and the controls on the row
        // under them. Stop is the ringed round button; with words typed during a run Send joins the turn
        // ("Add to this turn") and Stop moves beside the plus. Neither ever shares the words' line.
        onNodeWithContentDescription("Stop", useUnmergedTree = true).assertIsDisplayed()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("chat-composer-field")), useUnmergedTree = true)
            .performTextInput("Also check the docs")
        waitForIdle()
        onNodeWithContentDescription("Stop", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Add to this turn", useUnmergedTree = true).assertIsDisplayed()
        val field = onNodeWithTag("chat-composer-field").fetchSemanticsNode().size.width
        val box = onNodeWithTag("kit-composer-box", useUnmergedTree = true).fetchSemanticsNode().size.width
        assertTrue(field >= box * 8 / 10, "the field is $field px of $box px: Stop and steer never squeeze it")
    }

    @Test
    fun `B6 QA wave1 an empty message box is one line tall, not three`() = runComposeUiTest {
        setContent {
            Theme {
                Box(Modifier.width(360.dp)) {
                    androidx.compose.foundation.layout.Column {
                        BasicComposer(ui(steers = false), Actions(), chrome(busy = false))
                        KitField(
                            state = rememberTextFieldState(),
                            label = "Reference",
                            kind = KitFieldKind.Multiline,
                            modifier = Modifier.testTag("reference-field"),
                        )
                    }
                }
            }
        }
        val composer = onNodeWithTag("chat-composer-field").fetchSemanticsNode().size.height
        val threeLines = onNodeWithTag("reference-field").fetchSemanticsNode().size.height
        assertTrue(composer < threeLines, "message box $composer px vs a 3-line field $threeLines px")
    }

    @Test
    fun `B6 QA wave4 the message box has no label row above it and names itself inside`() = runComposeUiTest {
        setContent { Theme { Box(Modifier.width(360.dp)) { BasicComposer(ui(steers = false), Actions(), chrome(busy = false)) } } }
        // "Ask OpenCode…" (804ce6713, Flutter `chatUiAskAgent`) shows once, inside the empty box
        // (placeholder), not as a row of its own above it.
        val field = onNodeWithTag("chat-composer-field").fetchSemanticsNode().boundsInRoot
        val words = onAllNodesWithText("Ask OpenCode…", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, words.size)
        assertTrue(words.single().boundsInRoot.top >= field.top, "the words sit inside the box")
        onNodeWithContentDescription("Ask OpenCode…", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `N2 QA wave4 a long model name never pushes the approval chip off the row`() = runComposeUiTest {
        val long = ComposerModel("zai-coding-plan", "glm-5.3-flash", "GLM-5.3-Flash Extended Thinking Preview", true)
        val withChip =
            ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false }, rowChips = {
                io.github.eslamasabry.oc.ui.kit.KitText("Ask first", Modifier.testTag("approval-chip"))
            })
        setContent {
            Theme {
                Box(Modifier.width(360.dp)) {
                    BasicComposer(ui(models = ComposerModels(choices = listOf(long), selected = long)), Actions(), withChip)
                }
            }
        }
        // The controls share the message box's lower row with Send at its end (N-52): the approval chip stays
        // whole and clear of Send, because the model chip's words are capped.
        val box = onNodeWithTag("kit-composer-box", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val send = onNodeWithTag("chat-send-button", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chip = onNodeWithTag("approval-chip", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(chip.right <= box.right + 0.5f && chip.width > 20f, "chip $chip inside box $box")
        assertTrue(chip.right <= send.left + 0.5f, "chip $chip never under Send $send")
    }

    @Test
    fun `B13 QA wave4 before the session is read the chip never claims Server default`() = runComposeUiTest {
        var models by androidx.compose.runtime.mutableStateOf(ComposerModels())
        setContent { Theme { Box(Modifier.width(360.dp)) { BasicComposer(ui(models = models), Actions(), chrome(busy = false)) } } }
        onAllNodesWithText("Server default", useUnmergedTree = true).assertCountEquals(0)
        models = ComposerModels(usesDefault = true)
        waitForIdle()
        onNodeWithText("Server default", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B3 QA wave1 the picker groups models under their provider and tells equal names apart`() = runComposeUiTest {
        val zai = ComposerModel("zai-coding-plan", "glm-5.3-flash", "GLM-5.3-Flash", true, providerName = "Z.AI Coding Plan")
        val other = ComposerModel("openrouter", "glm-5.3-flash", "GLM-5.3-Flash", true, providerName = "OpenRouter")
        val terra = ComposerModel("openai", "gpt-6.1-terra", "GPT-6.1 Terra", true, providerName = "OpenAI")
        val actions = Actions()
        val models = ComposerModels(choices = listOf(other, terra, zai), selected = zai)
        setContent { Theme { KitSheetHost { BasicComposer(ui(models = models), actions, chrome(busy = false)) } } }
        onNodeWithContentDescription("Model").performClick()
        // Regional routes stay explicit in the one kit model sheet (kitProviderName, N-51).
        onNodeWithText("Z.AI Coding Plan · Global").assertExists()
        onNodeWithText("OpenRouter").assertExists()
        // The chosen model reads once (its band and check): no "Current" word beside it.
        onNodeWithText("Current").assertDoesNotExist()
        onAllNodes(hasText("GLM-5.3-Flash") and isSelected()).assertCountEquals(1)
        // Server default is a choice row that says what it means.
        onNodeWithText("Server default").assertExists()
        onNodeWithText("The server’s own default model").assertExists()
        // A short list has no search field: it appears from KIT_MODEL_SEARCH_FROM (8) models.
        onNodeWithTag("chat-model-search").assertDoesNotExist()
        // The second GLM row (OpenRouter's) is a different choice, not the current one.
        onAllNodesWithText("GLM-5.3-Flash").onLast().performClick()
        waitForIdle()
        assertEquals(listOf("select <openrouter/glm-5.3-flash>"), actions.calls.filter { it.startsWith("select") })
    }
}
