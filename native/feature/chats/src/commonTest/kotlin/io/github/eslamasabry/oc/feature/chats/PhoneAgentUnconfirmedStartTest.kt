package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhone
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneAgent
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneModels
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * SEND-STALE case 2 (owner 2026-11-15, Oh My Pi in a folder where an earlier phone agent's start was left): the helper's
 * first-message record answered "not confirmed" before Oh My Pi was asked, and Check again and Discard read and
 * dropped the server's own record instead of the helper's, so the next Start was refused again, without end. The
 * record a phone agent's start keeps is on the helper's profile: Check again reads it there, Discard drops it there,
 * and a start the helper confirmed meanwhile opens its conversation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneAgentUnconfirmedStartTest {
    private val server = ProfileId("phone")
    private val helper = ProfileId("phone-agents")
    private val directory = "/root/projects/Lottie"
    private val route = ChatsRoute.New(server)
    private val agent = AgentCatalog.builtIn.agents.first { it.runsOnPhoneHost }

    private class Nav(route: AppRoute) : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(route)
        val chats = mutableListOf<ConversationRef>()

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            chats += ref
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            top.value = null
        }
    }

    /** A helper whose first-message record for the folder is still open until [settled]. */
    private inner class Host : NewConversationHost {
        var settled = false
        var made: ConversationRef? = null
        val reviews = mutableListOf<ProfileId>()
        val dismissed = mutableListOf<ProfileId>()
        val starts = mutableListOf<ProfileId>()
        val listed = listOf(NewConversationModel(ModelRef(agent.providerId, "m"), "Model"))
        override val servers = MutableStateFlow(listOf(NewConversationServer(server, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(
                NewConversationSetup(
                    directory = directory,
                    projects = emptyList(),
                    models = listOf(NewConversationModel(ModelRef("server", "m"), "Server model")),
                    agents = emptyList(),
                    phone =
                        NewConversationPhone(
                            helper,
                            listOf(NewConversationPhoneAgent(agent.id.raw, agent.name, agent.id.raw)),
                            emptyList(),
                            selected = agent.id.raw,
                        ),
                )
            )

        override suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> =
            Outcome.Ok(NewConversationPhoneModels(listed, listed.first().model))

        override suspend fun review(profile: ProfileId, directory: String?): Outcome<ConversationRef?> {
            reviews += profile
            val open = profile == helper && !settled
            return Outcome.Ok(if (open) ConversationRef(helper, SessionId("earlier")) else null)
        }

        override suspend fun dismissReview(profile: ProfileId, directory: String?): Outcome<Unit> {
            dismissed += profile
            if (profile == helper) settled = true
            return Outcome.Ok(Unit)
        }

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> =
            Outcome.Ok(Unit)

        /** As the real host: an open record of the folder answers "not confirmed" before anything is sent. */
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
            starts += profile
            made?.let {
                return NewConversationAttempt(
                    directory,
                    ref = it,
                    creationAttempted = true,
                    deliveryUnconfirmed = true,
                    failure = Failure.Refused(RefusalReason.DispatchUncertain),
                )
            }
            if (profile == helper && !settled)
                return NewConversationAttempt(
                    directory,
                    ref = ConversationRef(helper, SessionId("earlier")),
                    deliveryUnconfirmed = true,
                    failure = Failure.Refused(RefusalReason.DispatchUncertain),
                )
            val ref = ConversationRef(profile, SessionId("new"))
            onStarted(ref)
            return NewConversationAttempt(directory, ref = ref, dispatched = true)
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

    private fun vmFor(host: Host, nav: Nav): NewConversationViewModel =
        NewConversationViewModel(route, host, nav, Clock.System, ListenedReports()).also {
            it.choosePhoneAgent(agent.id.raw)
            it.draftChanged("Hi")
        }

    @Test
    fun `SEND-STALE-3a Discard drops the helper's record, so the next Start reaches the agent`() {
        val host = Host()
        val nav = Nav(route)
        val vm = vmFor(host, nav)
        vm.start()
        assertTrue(vm.state.value.deliveryUnconfirmed, "an open record of the folder: not confirmed")
        vm.discardUnconfirmed()
        assertEquals(helper, host.dismissed.lastOrNull(), "Discard drops the record the start was refused by")
        assertFalse(vm.state.value.deliveryUnconfirmed)
        vm.draftChanged("Hi")
        vm.start()
        assertEquals(listOf(helper, helper), host.starts)
        assertEquals(listOf(ConversationRef(helper, SessionId("new"))), nav.chats, "the agent got the message")
    }

    @Test
    fun `SEND-STALE-3b Check again reads the helper's record and a settled one leaves the page ready to start`() {
        val host = Host()
        val nav = Nav(route)
        val vm = vmFor(host, nav)
        vm.start()
        assertTrue(vm.state.value.deliveryUnconfirmed)
        host.reviews.clear()
        vm.recheckUnconfirmed()
        assertEquals(helper, host.reviews.lastOrNull(), "Check again reads the helper's record")
        assertTrue(vm.state.value.deliveryUnconfirmed, "still open: the notice stays")
        host.settled = true
        vm.recheckUnconfirmed()
        assertFalse(vm.state.value.deliveryUnconfirmed, "settled: the page is ready to start again")
        assertEquals("Hi", vm.state.value.text, "the words stay")
        assertTrue(nav.chats.isEmpty(), "an earlier start's conversation is not opened for this one")
    }

    @Test
    fun `SEND-STALE-3c Check again opens a conversation the helper confirmed meanwhile`() {
        val host = Host()
        host.settled = true
        val made = ConversationRef(helper, SessionId("made"))
        host.made = made
        val nav = Nav(route)
        val vm = vmFor(host, nav)
        vm.start()
        assertTrue(vm.state.value.deliveryUnconfirmed, "its answer was not known")
        vm.recheckUnconfirmed()
        assertEquals(listOf(made), nav.chats, "the made conversation opens")
    }
}
