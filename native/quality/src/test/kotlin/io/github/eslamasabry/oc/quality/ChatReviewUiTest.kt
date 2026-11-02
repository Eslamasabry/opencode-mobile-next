package io.github.eslamasabry.oc.quality

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.*
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.composer.*
import io.github.eslamasabry.oc.feature.chat.presentation.*
import io.github.eslamasabry.oc.feature.chat.requests.presentation.*
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ChatReviewUiTest {
    private val ref = ConversationRef(ProfileId("fixture"), SessionId("session"))
    private inline fun <reified T> actions(crossinline call: (String, Array<out Any?>?) -> Unit): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args); null } as T
    private class Back : NavigationEventInput() {
        fun press() { dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f)); dispatchOnBackCompleted() }
    }
    private val composer = object : ComposerHost {
        @Composable override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
            androidx.compose.foundation.layout.Column {
                io.github.eslamasabry.oc.ui.kit.KitText(if (chrome.requestWaiting()) "Request owns composer" else "Ordinary composer")
                BasicComposer(io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi(true, false, false),
                    object : io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions {
                        override fun draftChanged(text: String) {}
                        override suspend fun send(text: String): Boolean = false
                    }, chrome, modifier)
            }
        }
    }

    @Test
    fun `chat finding 1 system Back with unsaved draft reaches the guard`() = runComposeUiTest {
        val back = Back()
        val dispatcher = NavigationEventDispatcher().also { it.addInput(back) }
        val owner = object : NavigationEventDispatcherOwner { override val navigationEventDispatcher = dispatcher }
        var leaves = 0
        val actions = actions<ChatActions> { name, _ -> if (name == "leaveRequested") leaves++ }
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                    ChatScreen(ChatUi.loading(ref).copy(guardBack = true), actions, composer)
                }
            }
        }
        runOnIdle { back.press() }
        assertEquals(1, leaves)
        dispatcher.dispose()
    }

    @Test
    fun `chat finding 7 permission slot responds during every load phase`() {
        for (phase in listOf(ChatPhase.Loading, ChatPhase.Live, ChatPhase.Failed(io.github.eslamasabry.oc.core.common.Failure.UnreadableReply()))) runComposeUiTest {
            val id = RequestId("permission")
            val ask = PermissionRequest(id, ref.sessionId, "edit", kotlinx.collections.immutable.persistentListOf("*.txt"), kotlinx.serialization.json.JsonObject(emptyMap()), kotlinx.collections.immutable.persistentListOf(), null, false, null)
            val slot = RequestSlotUi(RequestCardUi.Permission(id, null, CardState.Waiting, PermissionAsk.of(ask), false))
            var allowed = 0
            val requests = actions<ChatRequestActions> { name, _ -> if (name.startsWith("allowOnce")) allowed++ }
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                    io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment(io.github.eslamasabry.oc.core.security.Redactor()).Provide {
                        ChatScreen(ChatUi.loading(ref).copy(phase = phase), actions<ChatActions> { _, _ -> }, composer, requests = slot, requestActions = requests)
                    }
                }
            }
            onNodeWithText("Allow once").performClick()
            assertEquals(1, allowed)
            onNodeWithText("Request owns composer").assertExists()
            onNodeWithTag("chat-stop-button").assertExists()
            onNodeWithTag("chat-send-button").assertDoesNotExist()
            onNodeWithTag("chat-composer-field").assertDoesNotExist()
        }
    }
}
