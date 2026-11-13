package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * RC1 device pass (2026-11-13): after 90 s of "Still reading this server's projects and models…" the page said "The
 * conversation didn't start. Your message is still here." twice over an empty box, and Try again touched the screen's
 * left edge. The field was locked while the page got ready, and a failed read used the failed-start sentence.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class NewConversationWordsKeptTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/kept"

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host : NewConversationHost {
        val drafts = mutableMapOf<String?, String>()
        var reading = CompletableDeferred<Outcome<NewConversationSetup>>()
        var started: NewConversationAttempt = NewConversationAttempt(dir, failure = Failure.Unavailable(FeatureId("test")))
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> = reading.await()

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> =
            Outcome.Ok(drafts[directory].orEmpty())

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> {
            if (text.isEmpty()) drafts.remove(directory) else drafts[directory] = text
            return Outcome.Ok(Unit)
        }

        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt = started

        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        override suspend fun import(
            profile: ProfileId,
            directory: String,
            conversation: ImportableConversation,
        ): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    private val ready = NewConversationSetup(dir, emptyList(), emptyList(), emptyList())

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun open(host: Host) = NewConversationViewModel(ChatsRoute.New(phone), host, Nav(), Clock.System, ListenedReports())

    @Test
    fun `RC1-NEW-001 words typed while the page gets ready stay when the server never answers`() {
        val host = Host()
        val vm = open(host)
        assertEquals(true, vm.state.value.busy, "the page is getting ready")
        vm.draftChanged("list this folder")
        assertEquals("list this folder", vm.state.value.text, "typing ahead is kept, not dropped")
        host.reading.complete(Outcome.Failed(Failure.Network(NetworkWhy.Unknown)))
        assertNotNull(vm.state.value.failure)
        assertEquals("list this folder", vm.state.value.text, "a failed read never empties the field")
    }

    @Test
    fun `RC1-NEW-002 words typed while the page gets ready become the project draft once it is known`() {
        val host = Host().apply { drafts[dir] = "older words" }
        val vm = open(host)
        vm.draftChanged("list this folder")
        host.reading.complete(Outcome.Ok(ready))
        assertEquals("list this folder", vm.state.value.text, "the stored draft never replaces typed words")
        assertEquals("list this folder", host.drafts[dir])
    }

    @Test
    fun `RC1-NEW-003 a start that fails keeps the words in the field and one that succeeds clears them`() {
        val host = Host().apply { reading.complete(Outcome.Ok(ready)) }
        val vm = open(host)
        vm.draftChanged("list this folder")
        vm.start()
        assertNotNull(vm.state.value.failure, "the start failed")
        assertEquals("list this folder", vm.state.value.text, "the words are still there to send again")
        host.started = NewConversationAttempt(dir, ref = ConversationRef(phone, SessionId("s1")), dispatched = true)
        vm.start()
        assertEquals("", vm.state.value.text, "a delivered first message leaves the field empty")
    }

    private object Actions : NewConversationActions {
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) {}
        override fun start() {}
        override fun import(conversation: ImportableConversation) {}
        override fun retryLoad() {}
    }

    private val server = listOf(NewConversationServer(phone, "OpenCode", connected = true))

    @Test
    fun `RC1-NEW-004 a server that could not be read says so once and never claims a failed start`() = runComposeUiTest {
        val failed =
            NewConversationUi(servers = server, profile = phone, failure = Failure.Network(NetworkWhy.Unknown), text = "hi")
        setContent { Themed { NewConversationScreen(failed, Actions) } }
        onAllNodesWithText("This server's projects and models couldn't be read.").assertCountEquals(1)
        onAllNodesWithText("The conversation didn't start. Your message is still here.").assertCountEquals(0)
        onNodeWithText("Try again").assertIsEnabled()
        val left = onNodeWithText("Try again").getBoundsInRoot().left
        assertTrue(left > 0.dp, "Try again sits on the page gutter, not on the screen edge (left $left)")
    }

    @Test
    fun `RC1-NEW-005 the field takes typing while the page gets ready`() = runComposeUiTest {
        setContent { Themed { NewConversationScreen(NewConversationUi(servers = server, profile = phone, busy = true), Actions) } }
        onNode(hasSetTextAction()).assertIsEnabled()
    }
}
