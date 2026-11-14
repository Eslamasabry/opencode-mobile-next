package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ReviewedRequestAction
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject

/**
 * Audit 1 (2026-11-14) and the device pass of 2026-11-13: letters typed fast into the Details sheet's note ("Tell
 * the agent why, or what to do instead") were lost under load. 60 letters go in three at a time while the request
 * slot keeps re-sending the same request (a fresh card object with the same id, its waiting time ticking): the note
 * must hold exactly what was typed, cursor at the end, and Deny must send exactly those words.
 */
@OptIn(ExperimentalTestApi::class)
class PermissionNoteFastTypingTest {
    private val typed = "This is a draft that wraps; create c10.txt, count 1 to 60 ok"

    private fun card(tick: Long): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId("p1"), SessionId("ses_1"), "edit", persistentListOf("e9.txt"),
                JsonObject(emptyMap()), persistentListOf("*"), null, true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = tick,
            state = CardState.Waiting,
            ask = PermissionAsk.of(request),
            canAlwaysAllow = false,
            captured = PendingRequest.Permission(request),
        )
    }

    private class Recording : ChatRequestActions {
        val answers = mutableListOf<ReviewedRequestAction>()

        override fun performReviewed(card: RequestCardUi, action: ReviewedRequestAction) {
            answers += action
        }

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

    @Test
    fun `A1-TYPE-004 sixty letters typed fast into the Details note stay in order and go with Deny`() =
        runComposeUiTest {
            val actions = Recording()
            val slot = mutableStateOf(RequestSlotUi(card(0)))
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    KitChatEnvironment(Redactor()).Provide {
                        KitSheetHost { ChatRequestSlot(slot.value, actions, nowMs = null) }
                    }
                }
            }
            onAllNodesWithText("Details").onFirst().performClick()
            waitForIdle()
            val note = { onNode(hasSetTextAction(), useUnmergedTree = true) }
            note().performTouchInput { click() }
            typed.chunked(3).forEachIndexed { chunk, letters ->
                val node = note().fetchSemanticsNode()
                val insert = node.config[SemanticsActions.InsertTextAtCursor].action!!
                runOnUiThread {
                    letters.forEach { insert(AnnotatedString(it.toString())) }
                    slot.value = RequestSlotUi(card(chunk + 1L))
                }
            }
            waitForIdle()
            val node = note().fetchSemanticsNode()
            assertEquals(typed, node.config[SemanticsProperties.EditableText].text, "every letter, in order")
            assertEquals(TextRange(typed.length), node.config[SemanticsProperties.TextSelectionRange], "cursor at the end")
            note().assertIsFocused()
            onAllNodesWithText("Deny", useUnmergedTree = true).onLast().performClick()
            waitForIdle()
            assertEquals(listOf<ReviewedRequestAction>(ReviewedRequestAction.RejectWith(typed)), actions.answers)
        }
}
