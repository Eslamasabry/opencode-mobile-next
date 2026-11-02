package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
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
        onNodeWithText("Stop").assertIsDisplayed()
        onNodeWithText("Add to this turn").assertIsDisplayed()
        val field = onNodeWithTag("chat-composer-field").fetchSemanticsNode().size.width
        val controls = onNodeWithTag("chat-composer-controls").fetchSemanticsNode().size.width
        assertTrue(field >= controls * 9 / 10, "the field is $field px of $controls px: Stop and steer never squeeze it")
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
    fun `B3 QA wave1 the picker groups models under their provider and tells equal names apart`() = runComposeUiTest {
        val zai = ComposerModel("zai-coding-plan", "glm-5.3-flash", "GLM-5.3-Flash", true, providerName = "Z.AI Coding Plan")
        val other = ComposerModel("openrouter", "glm-5.3-flash", "GLM-5.3-Flash", true, providerName = "OpenRouter")
        val terra = ComposerModel("openai", "gpt-6.1-terra", "GPT-6.1 Terra", true, providerName = "OpenAI")
        val actions = Actions()
        val models = ComposerModels(choices = listOf(other, terra, zai), selected = zai)
        setContent { Theme { KitSheetHost { BasicComposer(ui(models = models), actions, chrome(busy = false)) } } }
        onNodeWithContentDescription("Model").performClick()
        onNodeWithText("Z.AI Coding Plan").assertExists()
        onNodeWithText("OpenRouter").assertExists()
        onNodeWithText("Current").assertExists()
        onNodeWithTag("chat-model-search").assertExists()
        // The second GLM row (OpenRouter's) is a different choice, not the current one.
        onAllNodesWithText("GLM-5.3-Flash").onLast().performClick()
        waitForIdle()
        assertEquals(listOf("select <openrouter/glm-5.3-flash>"), actions.calls.filter { it.startsWith("select") })
    }
}
