package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
 * P25 (device QA sweep A, 2026-11-11): closing New conversation with words typed threw them away, while the model and
 * folder were kept. Flutter keeps the unsent words as the project's draft (`KitDraft`, new_chat_screen.dart:119-175):
 * Back, a swipe or Close never loses them, and the next New conversation on that project opens with them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationDraftKeptTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/kept"

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        var backs = 0

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            backs++
        }
    }

    /** The phone's saved drafts, by project, as the app keeps them (`oc.draft.new-chat.<project>.<profile>`). */
    private inner class Host : NewConversationHost {
        val drafts = mutableMapOf<String?, String>()

        /** When set, reading a draft waits for it (storage is slow on a phone). */
        var hold: CompletableDeferred<Unit>? = null
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> {
            hold?.await()
            return Outcome.Ok(drafts[directory].orEmpty())
        }

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
        ): NewConversationAttempt = NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))

        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        override suspend fun import(
            profile: ProfileId,
            directory: String,
            conversation: ImportableConversation,
        ): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun open(host: Host, nav: Nav = Nav(), route: ChatsRoute.New = ChatsRoute.New(phone)) =
        NewConversationViewModel(route, host, nav, Clock.System, ListenedReports())

    @Test
    fun `P25 words typed and closed come back when New conversation opens again on the project`() {
        val host = Host()
        val nav = Nav()
        val first = open(host, nav)
        assertEquals(dir, first.state.value.directory)
        first.draftChanged("half way draft")
        first.back()
        assertEquals(1, nav.backs, "Close leaves")
        assertEquals("half way draft", host.drafts[dir], "the words are kept as the project's draft")
        val again = open(host)
        assertEquals("half way draft", again.state.value.text, "reopened, the field holds what was typed")
    }

    @Test
    fun `P25 a cleared field leaves no draft behind, and offered words are never replaced by one`() {
        val host = Host()
        val first = open(host)
        first.draftChanged("typo")
        first.draftChanged("")
        first.back()
        assertEquals("", open(host).state.value.text, "nothing typed, nothing restored")
        host.drafts[dir] = "old words"
        val offered = open(host, route = ChatsRoute.New(phone, initialText = "Talk about this"))
        assertEquals("Talk about this", offered.state.value.text)
    }

    @Test
    fun `P25 words typed while the stored draft is still being read are never replaced by it`() {
        val reading = CompletableDeferred<Unit>()
        val host = Host().apply {
            drafts[dir] = "old words"
            hold = reading
        }
        val vm = open(host)
        assertEquals("", vm.state.value.text, "the stored draft is still being read")
        vm.draftChanged("new words")
        reading.complete(Unit)
        assertEquals("new words", vm.state.value.text, "what the person typed stays in the field")
        assertEquals("new words", host.drafts[dir], "and is the project's draft from now on")
    }
}
