package io.github.eslamasabry.oc.quality

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderPresenter
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.world.ChatHeaderPort
import io.github.eslamasabry.oc.feature.chat.header.world.HeaderFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

/**
 * The conversation header on the real chat screen, found by the words a person sees (P12 CHAT-HEADER). It lives in
 * :quality because the screen needs the kit's redactor, which feature modules may not name (ADR §6.1).
 */
@OptIn(ExperimentalTestApi::class)
class ChatHeaderUiTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("s1"))
    private val gpt = ComposerModel("openai", "gpt-5", "GPT-5", supportsAttachments = true)
    private val sonnet = ComposerModel("anthropic", "sonnet", "Sonnet", supportsAttachments = true)
    private val immediate = ChatLauncher { block -> block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() }) }
    private val platform =
        object : KitPlatform by DesktopKitPlatform() {
            override val isDebug: Boolean = true
        }
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    private inner class World : ChatWorld {
        override val ref: ConversationRef = this@ChatHeaderUiTest.ref
        override val transcript = MutableStateFlow(TranscriptState.Empty)
        override val load = MutableStateFlow<ChatLoad>(ChatLoad.Live)
        override val title = MutableStateFlow<String?>("Fix the build")
        override val capabilities = MutableStateFlow(ServerCapabilities())
        override val link = MutableStateFlow(ChatLink.Live)
        override val pending = MutableStateFlow<ImmutableList<PendingSend>>(persistentListOf())
        override val draft = MutableStateFlow(DraftFacts())
        override val work = EmptyCoroutineContext
        val modelFlow = MutableStateFlow(ComposerModels())
        override val models: StateFlow<ComposerModels> = modelFlow
        val picked = mutableListOf<String>()

        override suspend fun loadModels(): Outcome<Unit> {
            modelFlow.value = ComposerModels(choices = listOf(gpt, sonnet), selected = gpt)
            return Outcome.Ok(Unit)
        }

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
            picked += choice?.modelId ?: "default"
            modelFlow.value = modelFlow.value.copy(selected = choice)
            return Outcome.Ok(Unit)
        }

        override fun draftChanged(text: String) {}

        override suspend fun saveDraft(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun send(text: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun retrySend(localId: Long): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun stop(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun reload(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun fork(from: MessageId): Outcome<SessionId> = Outcome.Ok(SessionId("s2"))

        override suspend fun deleteMessage(id: MessageId): Outcome<Unit> = Outcome.Ok(Unit)

        override fun markRead() {}
    }

    private class Port : ChatHeaderPort {
        override val facts = MutableStateFlow(HeaderFacts(serverName = "Home PC", directory = "/home/me/projects/app/", connected = true))
        val calls = mutableListOf<String>()
        var renameResult: Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun refresh(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun rename(title: String): Outcome<Unit> = renameResult.also { calls += "rename $title" }

        override suspend fun share(): Outcome<String?> = Outcome.Ok(null)

        override suspend fun unshare(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun fork(): Outcome<SessionId> = Outcome.Ok(SessionId("s9"))

        override suspend fun archive(): Outcome<Unit> = Outcome.Ok(Unit).also { calls += "archive" }

        override suspend fun delete(): Outcome<Unit> = Outcome.Ok(Unit).also { calls += "delete" }
    }

    private class Nav : AppNavigator {
        val calls = mutableListOf<String>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            calls += "open"
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            calls += "openChat ${ref.sessionId.raw}"
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            calls += "back"
        }
    }

    private val world = World()
    private val port = Port()
    private val nav = Nav()
    private val header = ChatHeaderPresenter(world, port, nav, immediate)
    private val chat = ChatPresenter(world, nav, immediate)

    @Composable
    private fun Screen(localeTag: String = "en") {
        KitTheme(platform = platform, appearance = KitAppearance(motion = MotionLevel.Off), localeTag = localeTag, stillFrame = true) {
            KitChatEnvironment(Redactor()).Provide {
                val scope = remember { CoroutineScope(Dispatchers.Unconfined) }
                val flow = remember { header.states(scope, SharingStarted.Eagerly) }
                val ui by flow.collectAsState()
                ChatScreen(
                    ChatUi.loading(ref).copy(title = world.title.value, phase = ChatPhase.Live),
                    chat,
                    composer,
                    header = ChatHeaderSlot(ui, header),
                )
            }
        }
    }

    @Test
    fun `B-CHAT-018 the bar shows the title and the server with the project folder`() =
        runComposeUiTest {
            setContent { Screen() }
            onNodeWithText("Fix the build").assertIsDisplayed()
            onNodeWithText("Home PC · app").assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-020 the conversation menu offers Continue with and only the acts this server supports`() =
        runComposeUiTest {
            world.capabilities.value = ServerCapabilities(sessionShare = false, sessionFork = false, sessionArchive = false, sessionDelete = true)
            setContent { Screen() }
            onNodeWithContentDescription("More actions").performClick()
            onNodeWithText("Continue with…").assertIsDisplayed()
            onNodeWithText("Copy transcript").assertIsDisplayed()
            onNodeWithText("Rename conversation").assertIsDisplayed()
            onNodeWithText("Delete conversation").assertIsDisplayed()
            assertEquals(0, onAllNodesWithText("Share conversation").fetchSemanticsNodes().size, "gated off by the server")
        }

    @Test
    fun `B-CHAT-020 Continue with moves the conversation to another model on this server`() =
        runComposeUiTest {
            setContent { Screen() }
            onNodeWithContentDescription("More actions").performClick()
            onNodeWithText("Continue with…").performClick()
            onNodeWithText("Continue this work").assertIsDisplayed()
            onNodeWithText("Models on this server").assertIsDisplayed()
            onNodeWithText("In use").assertIsDisplayed()
            onNodeWithText("Sonnet").performClick()
            waitForIdle()
            assertEquals(listOf("sonnet"), world.picked)
        }

    @Test
    fun `tapping the title opens Rename conversation`() =
        runComposeUiTest {
            setContent { Screen() }
            onNodeWithText("Fix the build").performClick()
            onNodeWithText("Title").assertIsDisplayed()
        }

    @Test
    fun `delete asks first naming the conversation then leaves`() =
        runComposeUiTest {
            world.capabilities.value = ServerCapabilities(sessionDelete = true)
            setContent { Screen() }
            onNodeWithContentDescription("More actions").performClick()
            onNodeWithText("Delete conversation").performClick()
            onNodeWithText("Delete “Fix the build”?").assertIsDisplayed()
            assertEquals(emptyList(), port.calls, "nothing runs before the answer")
            onNodeWithText("Delete conversation").performClick()
            waitForIdle()
            assertEquals(listOf("delete"), port.calls)
            assertEquals(listOf("back"), nav.calls)
        }

    @Test
    fun `a failed rename is one plain line with Details`() =
        runComposeUiTest {
            port.renameResult = Outcome.Failed(Failure.Network(NetworkWhy.Offline))
            header.renameDone("Something else")
            setContent { Screen() }
            onNodeWithText("Couldn't rename the conversation.").assertIsDisplayed()
            onNodeWithText("Details").assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-057 details lists the server and the context use in words`() =
        runComposeUiTest {
            port.facts.value = port.facts.value.copy(contextTokens = 300, contextLimit = 1000)
            setContent { Screen() }
            onNodeWithContentDescription("More actions").performClick()
            onNodeWithText("Details").performClick()
            onNodeWithText("Server").assertIsDisplayed()
            onNodeWithText("Home PC").assertIsDisplayed()
            onNodeWithText("30% used · plenty left").assertIsDisplayed()
        }

    @Test
    fun `the header menu reads in Arabic`() =
        runComposeUiTest {
            val before = java.util.Locale.getDefault()
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            try {
                setContent { Screen("ar") }
                onNodeWithText("Fix the build").assertIsDisplayed()
                onNodeWithContentDescription("المزيد من الإجراءات").performClick()
                onNodeWithText("المتابعة مع…").assertIsDisplayed()
            } finally {
                java.util.Locale.setDefault(before)
            }
        }
}
