package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
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
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.feature.chat.requests.ui.RequestShow
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-LAMP-C1-003: "show this request" scrolls the slot to the card, once, and tells the page it did. */
@OptIn(ExperimentalTestApi::class)
class RequestShowSlotTest {
    private val session = SessionId("ses_1")

    private fun permission(id: String, pattern: String): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId(id), session, "bash", persistentListOf(pattern), JsonObject(emptyMap()),
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

    private val cards = (1..10).map { permission("p$it", "npm test $it") }

    private fun slot(show: RequestShow?, shown: MutableList<Long>, focused: RequestId? = show?.id, list: List<RequestCardUi> = cards) =
        runDesktopComposeUiTest(412, 900) {
            var current by mutableStateOf(show)
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    KitChatEnvironment(Redactor()).Provide {
                        Box(Modifier.width(412.dp)) {
                            ChatRequestSlot(
                                RequestSlotUi(list.toImmutableList(), focused = focused),
                                NoActions,
                                nowMs = null,
                                show = current,
                                onShown = { seq ->
                                    shown += seq
                                    current = null // the page consumed it: the navigator offers it no more
                                },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()
            assertShownInView()
        }

    private fun androidx.compose.ui.test.ComposeUiTest.assertShownInView() {
        val stack = onNodeWithTag("kit-request-stack").getBoundsInRoot()
        val open = onAllNodesWithText("Allow once").fetchSemanticsNodes()
        assertEquals(1, open.size, "only the shown card is open in full")
        val button = onAllNodesWithText("Allow once")[0].getBoundsInRoot()
        assertTrue(button.top >= stack.top - 1.dp && button.bottom <= stack.bottom + 1.dp, "its buttons are inside the slot: $button in $stack")
    }

    @Test
    fun `B-LAMP-C1-003 the shown request is scrolled into view once and consumed`() {
        val shown = mutableListOf<Long>()
        slot(RequestShow(RequestId("p10"), seq = 7), shown)
        assertEquals(listOf(7L), shown, "the page is told once, with the request's seq")
    }

    @Test
    fun `B-LAMP-C1-003 a request whose card is not in the slot is not consumed`() {
        val shown = mutableListOf<Long>()
        runDesktopComposeUiTest(412, 900) {
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    KitChatEnvironment(Redactor()).Provide {
                        Box(Modifier.width(412.dp)) {
                            ChatRequestSlot(
                                RequestSlotUi(cards.take(3).toImmutableList()),
                                NoActions,
                                nowMs = null,
                                show = RequestShow(RequestId("gone"), seq = 9),
                                onShown = { shown += it },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()
        }
        assertEquals(emptyList(), shown, "left for the navigator's 10 s expiry; nothing is claimed")
    }

    private object NoActions : ChatRequestActions {
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

}
