package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
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

/**
 * P-009 (auditor strip_allow): an answered approval card vanished in one frame and the list jumped. The slot now
 * keeps its last card while it folds away (fade + collapse over the kit's quick pace), and draws nothing at once
 * under reduced motion.
 */
@OptIn(ExperimentalTestApi::class)
class S8RequestSlotMotionTest {
    private val session = SessionId("ses_1")

    private fun permission(id: String, pattern: String, state: CardState): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId(id), session, "bash", persistentListOf(pattern), JsonObject(emptyMap()),
                persistentListOf("$pattern *"), null, true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = state,
            ask = PermissionAsk.of(request, project = "neon-breaker", directory = "/home/me/neon-breaker"),
            canAlwaysAllow = true,
            captured = PendingRequest.Permission(request),
        )
    }

    private fun countCardsWith(test: androidx.compose.ui.test.ComposeUiTest, words: String): Int =
        test.onAllNodesWithText(words, substring = true).fetchSemanticsNodes().size

    /** The slot's height: the leaving card is hidden from TalkBack while it folds, so it is measured, not read. */
    private var height = 0

    private fun leaving(motion: MotionLevel, check: androidx.compose.ui.test.ComposeUiTest.(full: Int) -> Unit) =
        runDesktopComposeUiTest(412, 900) {
            var cards by mutableStateOf<List<RequestCardUi>>(listOf(permission("p1", "npm test", CardState.Waiting)))
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = motion),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    KitChatEnvironment(Redactor()).Provide {
                        Box(Modifier.width(412.dp).onSizeChanged { height = it.height }) {
                            ChatRequestSlot(RequestSlotUi(cards.toImmutableList()), NoActions, nowMs = null)
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            mainClock.advanceTimeBy(1_000)
            assertEquals(1, countCardsWith(this, "Allow once"), "the waiting card is shown")
            val full = height
            assertTrue(full > 0)
            cards = emptyList()
            check(full)
        }

    @Test
    fun `P-009 the last answered card folds away instead of vanishing in one frame`() =
        leaving(MotionLevel.Full) { full ->
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(48)
            assertTrue(height in 1 until full, "the card folds over several frames: $height of $full")
            mainClock.advanceTimeBy(1_000)
            assertEquals(0, height, "and is gone once the fold ends")
        }

    @Test
    fun `P-009 under reduced motion the card goes at once`() =
        leaving(MotionLevel.Off) {
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            assertEquals(0, height)
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
