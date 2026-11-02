package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
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
import kotlinx.coroutines.CompletableDeferred

@OptIn(ExperimentalTestApi::class)
class ComposerDraftRestoreTest {
    private val chrome =
        ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    private class Actions : BasicComposerActions {
        val changes = mutableListOf<String>()
        val repeats = mutableListOf<Pair<String, Long>>()
        var sendWait: CompletableDeferred<Boolean>? = null

        override fun draftChanged(text: String) {
            changes += text
        }

        override suspend fun send(text: String): Boolean = sendWait?.await() ?: true

        override suspend fun sendAgain(text: String, expectedRevision: Long): Boolean {
            repeats += text to expectedRevision
            return true
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

    private fun ui(text: String, ready: Boolean = true, revision: Long = 1, key: String = "p/s") =
        BasicComposerUi(
            true,
            false,
            false,
            DraftFacts(text, text, ready = ready, restoreRevision = revision),
            key,
        )

    @Test
    fun `B-STATE-102 restored exact text appears without forwarding an initial empty editor`() =
        runComposeUiTest {
            val actions = Actions()
            setContent { Theme { BasicComposer(ui("  preserved draft\n"), actions, chrome) } }
            onNode(hasSetTextAction()).assertTextContains("  preserved draft\n")
            assertTrue(
                actions.changes.none { it.isEmpty() },
                "initial empty editor must never overwrite restored data",
            )
        }

    @Test
    fun `B-STATE-102 unread draft disables editor until restoration is ready`() = runComposeUiTest {
        setContent { Theme { BasicComposer(ui("", ready = false), Actions(), chrome) } }
        // Disabled text fields intentionally expose no SetText action; find the labeled control instead.
        onNodeWithContentDescription("Message", useUnmergedTree = true).assertIsNotEnabled()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun `B-STATE-102 a local send receipt alone never clears the editor`() = runComposeUiTest {
        setContent { Theme { BasicComposer(ui("kept until acknowledged"), Actions(), chrome) } }
        onNode(hasSetTextAction()).performTextReplacement("kept until acknowledged")
        onNodeWithTag("chat-send-button").performClick()
        onNode(hasSetTextAction()).assertTextContains("kept until acknowledged")
    }

    @Test
    fun `B-STATE-102 same text edited during pending send survives and target switch restores independently`() =
        runComposeUiTest {
            val actions = Actions().apply { sendWait = CompletableDeferred() }
            val state = mutableStateOf(ui("original"))
            setContent { Theme { BasicComposer(state.value, actions, chrome) } }
            onNode(hasSetTextAction()).performTextReplacement("original")
            onNodeWithTag("chat-send-button").performClick()
            onNode(hasSetTextAction()).performTextReplacement("a later edit")
            onNode(hasSetTextAction()).performTextReplacement("original")
            runOnIdle { actions.sendWait!!.complete(true) }
            onNode(hasSetTextAction()).assertTextContains("original")
            runOnIdle { state.value = ui("other conversation", key = "p/other") }
            onNode(hasSetTextAction()).assertTextContains("other conversation")
        }

    @Test
    fun `B-STATE-102 accepted edits report B then A synchronously without moving caret on state updates`() =
        runComposeUiTest {
            val actions = Actions()
            val state = mutableStateOf(ui("original"))
            setContent { Theme { BasicComposer(state.value, actions, chrome) } }
            onNode(hasSetTextAction()).performTextReplacement("temporary")
            onNode(hasSetTextAction()).performTextReplacement("original")
            assertEquals(listOf("temporary", "original"), actions.changes)
            onNode(hasSetTextAction()).performTextInputSelection(TextRange(2))
            runOnIdle { state.value = state.value.copy(sending = true) }
            assertEquals(
                TextRange(2),
                onNode(hasSetTextAction())
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.TextSelectionRange],
            )
        }

    private fun protectedUi(key: String = "p/s", revision: Long = 5) =
        ui("already sent", key = key).let {
            it.copy(
                draft =
                    it.draft.copy(
                        sendBlocked = true,
                        editRevision = revision,
                        repeatAllowed = true,
                    ),
                canRepeat = true,
            )
        }

    @Test
    fun `B-STATE-102 an explicit repeat confirmation dispatches only its captured draft revision`() =
        runComposeUiTest {
            val actions = Actions()
            setContent { Theme { KitSheetHost { BasicComposer(protectedUi(), actions, chrome) } } }
            onNodeWithText("Send again").performClick()
            onNodeWithText("Send this text again?").assertIsDisplayed()
            onAllNodesWithText("Send again").onLast().performClick()
            assertEquals(listOf("already sent" to 5L), actions.repeats)
        }

    @Test
    fun `B-STATE-102 a newer draft revision invalidates an open repeat confirmation`() =
        runComposeUiTest {
            val actions = Actions()
            val state = mutableStateOf(protectedUi())
            setContent { Theme { KitSheetHost { BasicComposer(state.value, actions, chrome) } } }
            onNodeWithText("Send again").performClick()
            runOnIdle { state.value = protectedUi(revision = 6) }
            onNodeWithText("Send this text again?").assertDoesNotExist()
            assertTrue(actions.repeats.isEmpty())
        }

    @Test
    fun `B-STATE-102 switching targets invalidates the old repeat confirmation`() =
        runComposeUiTest {
            val actions = Actions()
            val state = mutableStateOf(protectedUi())
            setContent { Theme { KitSheetHost { BasicComposer(state.value, actions, chrome) } } }
            onNodeWithText("Send again").performClick()
            runOnIdle { state.value = protectedUi(key = "other/server") }
            onNodeWithText("Send this text again?").assertDoesNotExist()
            assertTrue(actions.repeats.isEmpty())
        }

    @Test
    fun `B-STATE-102 unknown receipt protection never offers a repeat confirmation`() =
        runComposeUiTest {
            val state =
                protectedUi()
                    .copy(
                        canRepeat = false,
                        draft = protectedUi().draft.copy(repeatAllowed = false),
                    )
            setContent { Theme { BasicComposer(state, Actions(), chrome) } }
            onAllNodesWithText("Send again").assertCountEquals(0)
            onNodeWithTag("chat-send-button").assertIsNotEnabled()
        }
}
