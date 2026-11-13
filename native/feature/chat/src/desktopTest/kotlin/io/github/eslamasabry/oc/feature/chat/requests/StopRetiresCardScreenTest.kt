package io.github.eslamasabry.oc.feature.chat.requests

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.FakeGatewayEvents
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import io.github.eslamasabry.oc.engine.requests.RequestsEngine
import io.github.eslamasabry.oc.engine.requests.RequestsPort
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestsModel
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestPlace
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/**
 * RC1 device pass (picture w1): with Stop pressed the card still offered Deny and Allow once and said "The agent waits
 * until you answer. Nothing is lost." beside "Stopping". The card the model draws while the run ends has no answers.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class StopRetiresCardScreenTest {
    private val chat = SessionId("ses_1")

    private class Port : RequestsPort {
        override val events = FakeGatewayEvents()
        override val capabilities = MutableStateFlow(ServerCapabilities())
        var permissions: List<PermissionRequest> = emptyList()

        override suspend fun awaitActionTransport(): Outcome<Unit> = Unit.ok()

        override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> = permissions.toImmutableList().ok()

        override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> = persistentListOf<PendingQuestion>().ok()

        override suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, note: String?): Outcome<Unit> =
            Unit.ok()

        override suspend fun answerQuestion(session: SessionId, request: RequestId, answers: ImmutableList<ImmutableList<String>>): Outcome<Unit> =
            Unit.ok()

        override suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> = Unit.ok()
    }

    /** The slot the real model gives for one waiting edit, after the chat's Stop was pressed. */
    private fun endingSlot(): RequestSlotUi {
        var slot = RequestSlotUi()
        runTest {
            val port = Port()
            val engine = RequestsEngine(port, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), TestClock(), TestPacer())
            val model = ChatRequestsModel(engine, chat, port.capabilities, MutableStateFlow(RequestPlace("p", "/root/p")), backgroundScope)
            runCurrent()
            val ask =
                PermissionRequest(
                    RequestId("perm1"), chat, "edit", persistentListOf("w1.txt"), JsonObject(emptyMap()),
                    persistentListOf("*"), null, true, null,
                )
            port.permissions = listOf(ask)
            port.events.emit(GatewayEvent.PermissionAsked(ask))
            runCurrent()
            model.runEnding(true)
            runCurrent()
            slot = model.state.value
        }
        return slot
    }

    private fun reads(words: String) =
        SemanticsMatcher("reads \"$words\"") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { words in it.text }
        }

    @Test
    fun `RC1-STOP-003 a card whose run is ending shows no answers and says nothing was sent`() {
        // Built first, outside the UI test: the engine runs on its own test scheduler.
        val slot = endingSlot()
        shows(slot)
    }

    private fun shows(slot: RequestSlotUi) = runComposeUiTest {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                KitChatEnvironment(Redactor()).Provide {
                    KitSheetHost { ChatRequestSlot(slot, NoAnswers, nowMs = null) }
                }
            }
        }
        onNode(reads("The run is ending, so nothing was sent."), useUnmergedTree = true).assertExists()
        assertTrue(onAllNodes(reads("Allow once"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodes(reads("Deny"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(
            onAllNodes(reads("The agent waits until you answer"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        )
    }

    private object NoAnswers : io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions {
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
