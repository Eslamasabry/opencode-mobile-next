package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Review 2026-11-13 #1 ("approve a file change without seeing the change"): the permission card of a file write or
 * edit shows what will be written, in true words, before the answers; Details holds the whole change; a request that
 * carries no content says so. The requests are shaped as each server sends them (OpenCode 2.2.6 `FileWrite.authorize`:
 * `metadata.files[]{file, patch, status, additions, deletions}`; OpenCode 2 through Paseo 0.15.3: a path only).
 */
@OptIn(ExperimentalTestApi::class)
class PermissionChangeScreenTest {
    private val session = SessionId("ses_1")

    /** The words a person reads: bidi isolates around commands and paths are invisible. */
    private fun reads(words: String) =
        SemanticsMatcher("reads \"$words\"") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                words in it.text.filterNot { c -> c in "⁦⁧⁨⁩‎‏‪‫‬" }
            }
        }

    /** A screen-reader name, read the same way (the path inside it sits in a left-to-right isolate). */
    private fun describes(words: String) =
        SemanticsMatcher("described as \"$words\"") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any {
                it.filterNot { c -> c in "\u2066\u2067\u2068\u2069\u200E\u200F\u202A\u202B\u202C" } == words
            }
        }

    private fun oc2File(path: String, patch: String, status: String, added: Int, removed: Int) =
        JsonObject(
            mapOf(
                "file" to JsonPrimitive(path),
                "patch" to JsonPrimitive(patch),
                "status" to JsonPrimitive(status),
                "additions" to JsonPrimitive(added),
                "deletions" to JsonPrimitive(removed),
            ),
        )

    private fun card(id: String, path: String, metadata: JsonObject): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId(id), session, "edit", persistentListOf(path), metadata, persistentListOf("*"),
                PermissionTool(MessageId("msg_1"), "call_$id"), true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = CardState.Waiting,
            ask = PermissionAsk.of(request),
            canAlwaysAllow = false,
            captured = PendingRequest.Permission(request),
        )
    }

    private fun newFile(lines: List<String>): RequestCardUi.Permission {
        val patch =
            "Index: e9.txt\n===================================================================\n--- e9.txt\n+++ e9.txt\n" +
                "@@ -0,0 +1,${lines.size} @@\n" + lines.joinToString("") { "+$it\n" } + "\\ No newline at end of file\n"
        return card("p1", "e9.txt", JsonObject(mapOf("files" to JsonArray(listOf(oc2File("e9.txt", patch, "added", lines.size, 0))))))
    }

    private val edited =
        card(
            "p2",
            "src/e2.txt",
            JsonObject(
                mapOf(
                    "files" to
                        JsonArray(
                            listOf(
                                oc2File(
                                    "src/e2.txt",
                                    "--- src/e2.txt\n+++ src/e2.txt\n@@ -1,2 +1,2 @@\n line one\n-the old line\n+the new line\n",
                                    "modified",
                                    1,
                                    1,
                                ),
                            ),
                        ),
                ),
            ),
        )

    private class Actions : ChatRequestActions {
        override fun shown(id: RequestId) = Unit
        override fun allowOnce(id: RequestId) = Unit
        override fun reject(id: RequestId) = Unit
        override fun askAlwaysAllow(id: RequestId) = Unit
        override fun confirmAlwaysAllow() = Unit
        override fun cancelAlwaysAllow() = Unit
        override fun keepAsking(id: RequestId) = Unit
        override fun choose(id: RequestId, option: String) = Unit
        override fun openOther(id: RequestId) = Unit
        override fun editOther(id: RequestId, text: String) = Unit
        override fun sendOther(id: RequestId) = Unit
        override fun openSheet(id: RequestId) = Unit
        override fun closeSheet(id: RequestId) = Unit
        override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit
        override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit
        override fun sendSheet(id: RequestId) = Unit
        override fun undo(id: RequestId) = Unit
        override fun retry(id: RequestId) = Unit
    }

    @Composable
    private fun Slot(state: RequestSlotUi) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitChatEnvironment(Redactor()).Provide { KitSheetHost { ChatRequestSlot(state, Actions(), nowMs = null) } }
        }
    }

    @Test
    fun `B-CHAT-REV1-001 a new file's card says Create and shows its first lines before the answers`() = runComposeUiTest {
        setContent { Slot(RequestSlotUi(newFile(listOf("hello from the agent")))) }
        onNode(reads("Create e9.txt?")).assertIsDisplayed()
        onNode(describes("The change to e9.txt"), useUnmergedTree = true).assertExists()
        assertTrue(
            onAllNodes(hasText("hello from the agent", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "the line it will write is on the card",
        )
    }

    @Test
    fun `B-CHAT-REV1-002 an edit's card shows the removed and the added line`() = runComposeUiTest {
        setContent { Slot(RequestSlotUi(edited)) }
        onNode(describes("The change to src/e2.txt"), useUnmergedTree = true).assertExists()
        for (line in listOf("the old line", "the new line"))
            assertTrue(
                onAllNodes(hasText(line, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "\"$line\" is on the card",
            )
    }

    @Test
    fun `B-CHAT-REV1-003 a long new file shows a few lines and Show all opens Details with the whole change`() =
        runComposeUiTest {
            val lines = (1..20).map { "line number $it of the new file" }
            setContent { Slot(RequestSlotUi(newFile(lines))) }
            assertTrue(
                onAllNodes(hasText("line number 20 of the new file"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
                "the card keeps to a few lines",
            )
            // The card's words scroll inside it while the answers stay in sight.
            onNodeWithText("Show all").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("chat-permission-change", useUnmergedTree = true).assertExists()
            assertTrue(
                onAllNodes(
                    hasText("line number 20 of the new file", substring = true) and
                        androidx.compose.ui.test.hasAnyAncestor(hasTestTag("chat-permission-details")),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty(),
                "Details holds every line of the change",
            )
            // Audit 1: the tool is said in plain words, never as its id.
            val inDetails = androidx.compose.ui.test.hasAnyAncestor(hasTestTag("chat-permission-details"))
            assertTrue(
                onAllNodes(hasText("File change") and inDetails, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "Details names the tool as a file change",
            )
            assertTrue(
                onAllNodes(hasText("edit") and inDetails, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
                "Details never shows the raw tool id",
            )
        }

    @Test
    fun `B-CHAT-REV1-004 a request that names only a path says the agent did not say what it will write`() =
        runComposeUiTest {
            // OpenCode 2 through Paseo 0.15.3 asks with a name and a path and nothing else.
            val bare = card("p3", "shade-probe.txt", JsonObject(mapOf("filePath" to JsonPrimitive("shade-probe.txt"))))
            setContent { Slot(RequestSlotUi(bare)) }
            onNodeWithText("The agent did not say what it will write.").assertIsDisplayed()
        }
}
