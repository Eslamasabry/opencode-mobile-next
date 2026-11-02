package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.model.PromptAttachment
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
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exact-screen rows for the composer's model choice and file attachments (full-composer 2026-11-02). */
@OptIn(ExperimentalTestApi::class)
class ComposerModelsFilesTest {
    private val chrome =
        ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    private val mini = ComposerModel("prov", "mini", "Mini", supportsAttachments = true)
    private val big = ComposerModel("prov", "big", "Big", supportsAttachments = false)

    private class Actions : BasicComposerActions {
        val calls = mutableListOf<String>()
        var selectResult = true

        override fun draftChanged(text: String) {
            calls += "draftChanged"
        }

        override suspend fun send(text: String): Boolean {
            calls += "send <$text>"
            return true
        }

        override fun loadModels() {
            calls += "loadModels"
        }

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean {
            calls += "select <${choice?.label}>"
            return selectResult
        }

        override fun attachFile() {
            calls += "attachFile"
        }

        override fun removeAttachment(index: Int) {
            calls += "remove $index"
        }

        override fun dismissAttachmentFailure() {
            calls += "dismiss"
        }
    }

    @Composable
    private fun Theme(localeTag: String = "en", content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = localeTag,
            content = content,
        )
    }

    private fun file(name: String) = PromptAttachment("text/plain", name, "data:text/plain;base64,eA==")

    private fun ui(
        draft: DraftFacts = DraftFacts(),
        models: ComposerModels = ComposerModels(),
        filesAvailable: Boolean = true,
        pickingFile: Boolean = false,
        attachmentFailure: Failure? = null,
    ) = BasicComposerUi(
        available = true,
        sending = false,
        steers = false,
        draft = draft,
        targetKey = "p/s",
        models = models,
        pickingFile = pickingFile,
        attachmentFailure = attachmentFailure,
        filesAvailable = filesAvailable,
    )

    @Test
    fun `B-STATE-103 the model button names the server default until a verified model is chosen`() =
        runComposeUiTest {
            setContent { Theme { BasicComposer(ui(), Actions(), chrome) } }
            onNodeWithContentDescription("Model").assertExists()
            onNodeWithText("Server default").assertIsDisplayed()
        }

    @Test
    fun `B-STATE-103 a chosen model appears on the button only from the published selection`() =
        runComposeUiTest {
            val state =
                mutableStateOf(ui(models = ComposerModels(choices = listOf(mini, big), selected = big)))
            setContent { Theme { BasicComposer(state.value, Actions(), chrome) } }
            onNodeWithText("Big", substring = true).assertIsDisplayed()
            onNodeWithText("Server default").assertDoesNotExist()
        }

    @Test
    fun `B-STATE-103 the model sheet loads on open and a failed load offers plain-word retry`() =
        runComposeUiTest {
            val actions = Actions()
            val state = mutableStateOf(ui())
            setContent { Theme { KitSheetHost { BasicComposer(state.value, actions, chrome) } } }
            onNodeWithContentDescription("Model").performClick()
            onNodeWithText("Choose a model").assertIsDisplayed()
            assertTrue("loadModels" in actions.calls)
            runOnIdle {
                state.value = ui(
                    models = ComposerModels(failure = Failure.Network(io.github.eslamasabry.oc.core.common.NetworkWhy.Offline))
                )
            }
            onNodeWithText("The model list could not be loaded.").assertIsDisplayed()
            onNodeWithText("Try again").performClick()
            assertEquals(2, actions.calls.count { it == "loadModels" })
        }

    @Test
    fun `B-STATE-103 the model sheet shows loading and empty copy`() = runComposeUiTest {
        val actions = Actions()
        val state = mutableStateOf(ui(models = ComposerModels(loading = true)))
        setContent { Theme { KitSheetHost { BasicComposer(state.value, actions, chrome) } } }
        onNodeWithContentDescription("Model").performClick()
        onNodeWithText("Loading models…").assertIsDisplayed()
        assertTrue("loadModels" !in actions.calls)
        runOnIdle { state.value = ui(models = ComposerModels()) }
        onNodeWithText("No models are available right now.").assertIsDisplayed()
    }

    @Test
    fun `B-STATE-103 a model choice closes the sheet only after the world verified it`() =
        runComposeUiTest {
            val actions = Actions().apply { selectResult = false }
            val state = mutableStateOf(ui(models = ComposerModels(choices = listOf(mini, big))))
            setContent { Theme { KitSheetHost { BasicComposer(state.value, actions, chrome) } } }
            onNodeWithContentDescription("Model").performClick()
            onNodeWithText("Mini").performClick()
            onNodeWithText("Choose a model").assertIsDisplayed()
            assertEquals(listOf("select <Mini>"), actions.calls.filter { it.startsWith("select") })
            actions.selectResult = true
            runOnIdle { state.value = ui(models = ComposerModels(choices = listOf(mini, big), selected = mini)) }
            onAllNodesWithText("Mini").onLast().performClick()
            onNodeWithText("Choose a model").assertDoesNotExist()
            onNodeWithText("Mini").assertIsDisplayed()
        }

    @Test
    fun `B-STATE-103 attached files show their names with an explicit remove control`() =
        runComposeUiTest {
            val actions = Actions()
            val draft = DraftFacts(attachments = listOf(file("notes.txt"), file("more.txt")))
            setContent { Theme { BasicComposer(ui(draft = draft), actions, chrome) } }
            onNodeWithText("notes.txt").assertIsDisplayed()
            onNodeWithText("more.txt").assertIsDisplayed()
            onAllNodesWithContentDescription("Remove").onFirst().performClick()
            assertEquals(listOf("remove 0"), actions.calls)
        }

    @Test
    fun `B-STATE-103 attach is explained-disabled without a picker or when the chosen model refuses files`() =
        runComposeUiTest {
            val actions = Actions()
            val state =
                mutableStateOf(ui(models = ComposerModels(choices = listOf(mini, big), selected = big)))
            setContent { Theme { BasicComposer(state.value, actions, chrome) } }
            onNodeWithTag("chat-attach-button").assertIsNotEnabled()
            assertTrue(
                onNodeWithTag("chat-attach-button")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription]
                    .let { listOf(it) }
                    .any { "does not accept files" in it },
                "the disabled attach button must say why in plain words"
            )
            runOnIdle {
                state.value = ui(models = ComposerModels(choices = listOf(mini, big), selected = mini), filesAvailable = false)
            }
            onNodeWithTag("chat-attach-button").assertIsNotEnabled()
            assertTrue(
                onNodeWithTag("chat-attach-button")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription]
                    .let { listOf(it) }
                    .any { "cannot attach files" in it }
            )
            runOnIdle { state.value = ui(models = ComposerModels(choices = listOf(mini, big), selected = mini)) }
            onNodeWithTag("chat-attach-button").assertIsEnabled()
            onNodeWithTag("chat-attach-button").performClick()
            assertEquals(listOf("attachFile"), actions.calls)
        }

    @Test
    fun `B-STATE-103 a running pick keeps Send off and files alone can send`() = runComposeUiTest {
        val actions = Actions()
        val state = mutableStateOf(ui(pickingFile = true))
        setContent { Theme { BasicComposer(state.value, actions, chrome) } }
        onNodeWithTag("chat-send-button").assertIsNotEnabled()
        runOnIdle { state.value = ui(draft = DraftFacts(attachments = listOf(file("only.txt")))) }
        onNodeWithTag("chat-send-button").assertIsEnabled()
        onNodeWithTag("chat-send-button").performClick()
        assertEquals(listOf("send <>"), actions.calls)
    }

    @Test
    fun `B-STATE-103 attachment failures use plain words with dismiss and retry`() = runComposeUiTest {
        val actions = Actions()
        val state =
            mutableStateOf(ui(attachmentFailure = Failure.Media(MediaProblem.AttachmentTooLarge)))
        setContent { Theme { BasicComposer(state.value, actions, chrome) } }
        onNodeWithText("This file is too large to attach.").assertIsDisplayed()
        onNodeWithText("Try again").performClick()
        onNodeWithText("Dismiss").performClick()
        assertEquals(listOf("attachFile", "dismiss"), actions.calls)
        runOnIdle { state.value = ui(attachmentFailure = Failure.StorageRefused()) }
        onNodeWithText("The file could not be saved on this device.").assertIsDisplayed()
    }

    @Test
    fun `B-STATE-103 Arabic copy renders for the model control and the sheet title`() =
        runComposeUiTest {
            val before = Locale.getDefault()
            Locale.setDefault(Locale.forLanguageTag("ar"))
            try {
                setContent {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(motion = MotionLevel.Off),
                        stillFrame = true,
                        localeTag = "ar",
                    ) { BasicComposer(ui(), Actions(), chrome) }
                }
                onNodeWithContentDescription("النموذج").assertExists()
                onNodeWithText("الافتراضي من الخادم").assertIsDisplayed()
            } finally {
                Locale.setDefault(before)
            }
        }
}
