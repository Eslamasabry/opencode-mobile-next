package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
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
import io.github.eslamasabry.oc.ui.routes.NewChatOutcome
import io.github.eslamasabry.oc.ui.routes.NewChatReport
import io.github.eslamasabry.oc.ui.routes.NewChatReports
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Keeps every report, for the tests. */
internal class ListenedReports : NewChatReports {
    val seen = mutableListOf<NewChatReport>()
    override val reports: SharedFlow<NewChatReport> = MutableSharedFlow()

    override fun report(key: String, outcome: NewChatOutcome) {
        seen += NewChatReport(key, outcome)
    }
}

/**
 * s26: another page (FactsBook's "Talk about this") opens a new conversation with words already in the field and asks
 * to hear what became of it: the first message was sent, or the person left without sending (F: `showNewChat`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationSeedTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/kept"

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        var backs = 0

        override fun back() {
            backs++
        }
    }

    private inner class Host(private val sends: Boolean) : NewConversationHost {
        val sent = mutableListOf<String>()
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("old draft")

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> =
            Outcome.Ok(Unit)

        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt {
            sent += text
            return if (sends)
                NewConversationAttempt(directory, ref = ConversationRef(phone, SessionId("s1")), dispatched = true)
            else NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))
        }

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

    private val seed = "Talk with me about this.\n\nDentist on Tuesday at 10"

    @Test
    fun `B-CHATS-163 a new conversation opened with words starts with them and says when the first message was sent`() {
        val host = Host(sends = true)
        val reports = ListenedReports()
        val vm =
            NewConversationViewModel(
                ChatsRoute.New(phone, initialText = seed, reportKey = "offer-k"),
                host,
                Nav(),
                Clock.System,
                reports,
            )
        assertEquals(seed, vm.state.value.text, "the field starts with the words, not an old draft")
        vm.start()
        assertEquals(listOf(seed), host.sent)
        assertEquals(listOf(NewChatReport("offer-k", NewChatOutcome.FirstMessageSent)), reports.seen)
        vm.back()
        assertEquals(1, reports.seen.size, "one report per conversation")
    }

    @Test
    fun `B-CHATS-164 leaving a keyed new conversation without sending says so once`() {
        val reports = ListenedReports()
        val nav = Nav()
        val vm =
            NewConversationViewModel(
                ChatsRoute.New(phone, initialText = seed, reportKey = "offer-k"),
                Host(sends = false),
                nav,
                Clock.System,
                reports,
            )
        vm.back()
        assertEquals(1, nav.backs)
        assertEquals(listOf(NewChatReport("offer-k", NewChatOutcome.Left)), reports.seen)
    }

    @Test
    fun `B-CHATS-164 an ordinary new conversation reports nothing`() {
        val reports = ListenedReports()
        val vm = NewConversationViewModel(ChatsRoute.New(phone), Host(sends = true), Nav(), Clock.System, reports)
        vm.draftChanged("Hi")
        vm.start()
        vm.back()
        assertEquals(emptyList(), reports.seen)
    }
}
