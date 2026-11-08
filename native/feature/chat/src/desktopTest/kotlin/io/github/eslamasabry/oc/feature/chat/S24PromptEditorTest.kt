package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerPromptEditor
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S24-CHAT story 4 (Flutter `/editor`, prompt_editor.dart): the composer's "More" list and the
 * commands launcher open the full-screen prompt editor over the composer's own field, and what it
 * wrote becomes the draft.
 */
@OptIn(ExperimentalTestApi::class)
class S24PromptEditorTest {
    private class Actions : BasicComposerActions {
        val drafts = mutableListOf<String>()

        override fun draftChanged(text: String) {
            drafts += text
        }

        override suspend fun send(text: String): Boolean = true

        override fun attachFile() {}

        override fun attach(kind: AttachKind) {}
    }

    /** Stands in for :feature:chat-composer's editor: it writes the words and closes when asked. */
    private class FakeEditor : ComposerPromptEditor {
        var shown = false
        var field: TextFieldState? = null
        var close: (() -> Unit)? = null

        @Composable
        override fun Editor(visible: Boolean, field: TextFieldState, onClose: () -> Unit) {
            shown = visible
            this.field = field
            close = onClose
        }
    }

    private fun ui() =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            draft = DraftFacts(ready = true),
            targetKey = "p/s",
            filesAvailable = true,
            attachKinds = setOf(AttachKind.Photos),
            attachmentsSupported = true,
        )

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = "en",
        ) {
            KitSheetHost { content() }
        }
    }

    @Test
    fun `B-CHAT-S24-40 More offers Prompt editor, and the words written there become the draft`() =
        runComposeUiTest {
            val actions = Actions()
            val editor = FakeEditor()
            val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })
            setContent { Theme { BasicComposer(ui(), actions, chrome, promptEditor = editor) } }
            onNodeWithContentDescription("Attach and more").performClick()
            onNodeWithText("More").performClick()
            onNodeWithText("Prompt editor").assertIsDisplayed().performClick()
            waitForIdle()
            assertTrue(editor.shown)
            editor.field!!.edit { replace(0, length, "A long prompt written in the editor") }
            editor.close!!()
            waitForIdle()
            assertTrue(!editor.shown)
            assertEquals("A long prompt written in the editor", actions.drafts.last())
        }

    @Test
    fun `B-CHAT-S24-41 the launcher's editor request opens it once, and no editor means no row`() =
        runComposeUiTest {
            val editor = FakeEditor()
            var request: Long? = 7L
            val done = mutableListOf<Long>()
            val chrome =
                ComposerChrome(
                    { false }, { false }, {}, { null }, {}, { null }, {}, {}, { false },
                    editorRequest = { request },
                    editorDone = { done += it; request = null },
                )
            setContent { Theme { BasicComposer(ui(), Actions(), chrome, promptEditor = editor) } }
            waitForIdle()
            assertTrue(editor.shown)
            assertEquals(listOf(7L), done)
        }

    @Test
    fun `B-CHAT-S24-42 without an editor the More list has no Prompt editor row`() = runComposeUiTest {
        val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })
        setContent { Theme { BasicComposer(ui(), Actions(), chrome) } }
        onNodeWithContentDescription("Attach and more").performClick()
        onNodeWithText("More").performClick()
        onNodeWithText("Prompt editor").assertDoesNotExist()
    }
}
