package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AlwaysConfirmUi
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
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S30 story 1: the permission card's Always allow says what Flutter's card says (permission_ask.dart), and its one
 * confirm names what and where with the scope sentence (Flutter `_confirmAlways`).
 */
@OptIn(ExperimentalTestApi::class)
class AlwaysAllowWordsScreenTest {
    private val session = SessionId("ses_1")

    /** The words a person reads: bidi isolates around commands and paths are invisible. */
    private fun reads(words: String, substring: Boolean = false) =
        SemanticsMatcher("reads \"$words\"") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                val seen = it.text.filterNot { c -> c in "\u2066\u2067\u2068\u2069\u200E\u200F\u202A\u202B\u202C" }
                if (substring) words in seen else seen == words
            }
        }

    private fun permission(id: String, permission: String, pattern: String): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId(id), session, permission, persistentListOf(pattern), JsonObject(emptyMap()),
                persistentListOf("$pattern *"), null, true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = CardState.Waiting,
            ask = PermissionAsk.of(request, project = "neon-breaker", directory = "/home/me/neon-breaker"),
            canAlwaysAllow = true,
            captured = PendingRequest.Permission(request),
        )
    }

    private class Actions : ChatRequestActions {
        val asked = mutableListOf<RequestId>()
        override fun shown(id: RequestId) = Unit
        override fun allowOnce(id: RequestId) = Unit
        override fun reject(id: RequestId) = Unit
        override fun askAlwaysAllow(id: RequestId) {
            asked += id
        }
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
    private fun Slot(state: RequestSlotUi, actions: ChatRequestActions) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitChatEnvironment(Redactor()).Provide { KitSheetHost { ChatRequestSlot(state, actions, nowMs = null) } }
        }
    }

    @Test
    fun `S30-CHAT-001 a command's Always allow names its family as Flutter does, beside Deny Allow and Details`() =
        runComposeUiTest {
            val shell = permission("p1", "bash", "npm test")
            val actions = Actions()
            setContent { Slot(RequestSlotUi(shell), actions) }
            onNodeWithText("Deny").assertIsDisplayed()
            onNodeWithText("Allow").assertIsDisplayed()
            onNodeWithText("Details").assertIsDisplayed()
            // Flutter: "Always allow npm test", never the raw pattern "npm test *".
            onNode(reads("Always allow npm test")).assertIsDisplayed()
            onNode(reads("Always allow npm test")).performClick()
            assertEquals(listOf(RequestId("p1")), actions.asked, "the tap opens the one confirm; nothing is sent")
        }

    @Test
    fun `S30-CHAT-001 an edit's Always allow says requests like this as Flutter does`() = runComposeUiTest {
        val edit = permission("p2", "edit", "src/game.js")
        setContent { Slot(RequestSlotUi(edit), Actions()) }
        onNodeWithText("Always allow requests like this").assertIsDisplayed()
    }

    @Test
    fun `S30-CHAT-001 the confirm names what and where and says what it covers`() = runComposeUiTest {
        val edit = permission("p2", "edit", "src/game.js")
        val state =
            RequestSlotUi(
                cards = persistentListOf(edit),
                confirmAlways = AlwaysConfirmUi(edit.id, edit.ask.always, edit.captured),
            )
        setContent { Slot(state, Actions()) }
        onNode(reads("Always allow src/game.js in this project?", substring = true)).assertIsDisplayed()
        onNode(reads("runs without asking you, in this project", substring = true)).assertIsDisplayed()
    }
}
