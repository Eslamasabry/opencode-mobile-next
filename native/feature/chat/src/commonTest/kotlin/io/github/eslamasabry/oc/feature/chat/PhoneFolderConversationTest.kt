package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ConnectionSession
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.transcript.TranscriptAccessors
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneFolder
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.PHONE_FOLDER_FEATURE
import io.github.eslamasabry.oc.feature.chat.world.phoneFolderRefusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Issue 147: an open conversation whose folder is in the phone's storage says so when the side that works in it can no
 * longer reach it (Android took the access back, the folder is gone, a side started without it), and no message is
 * sent there meanwhile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneFolderConversationTest {
    private val ref = ConversationRef(ProfileId("phone"), SessionId("s"))
    private val folder = "/storage/emulated/0/MyProj"

    private val profile =
        ServerProfile(
            ref.profileId,
            "This phone",
            BackendKind.OpenCode2,
            ServerOrigin("http", "127.0.0.1", 4096),
            ProfileTrust(null, null),
            false,
            null,
        )

    private class Phone(var reach: ChatPhoneFolder?) : ChatPhoneServer {
        val checks = mutableListOf<Pair<String?, Boolean>>()
        val fixes = mutableListOf<ChatPhoneFolder>()

        override fun runsHere(profile: ProfileId): Boolean = true

        override suspend fun restart(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun phoneFolder(ref: ConversationRef, directory: String?, thorough: Boolean): ChatPhoneFolder? {
            checks += directory to thorough
            return reach
        }

        override suspend fun fixPhoneFolder(ref: ConversationRef, reach: ChatPhoneFolder): Outcome<Unit> {
            fixes += reach
            this.reach = ChatPhoneFolder.Ready
            return Outcome.Ok(Unit)
        }
    }

    private fun TestScope.registry(gateway: FakeServerGateway): ConnectionRegistry {
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val app = AppCoroutineScope(backgroundScope.coroutineContext)
        val clock = TestClock()
        return ConnectionRegistry(
            setOf(
                object : GatewayFactory {
                    override val backend = BackendKind.OpenCode2

                    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                        Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                    override suspend fun connect(spec: GatewaySpec) = Outcome.Ok(GatewayPair(gateway, FakeOperationsGateway()))
                }
            ),
            { p, pair, scope ->
                object : ConnectionGraph, ChatGraphAccessors, TranscriptAccessors {
                    override val profile = p
                    override val scope = scope
                    override val session = ConnectionSession(p, pair.live, scope)
                    override val chatDelivery = ConnectionDelivery(pair.live, p, scope, clock, emptySet(), dispatchers)
                    override val chatRequests = ConnectionRequests(pair.live, scope, dispatchers, clock)
                    override val chatGateway = pair.live
                    override val chatOperations = pair.operations
                    override val transcriptEngine = TranscriptEngine(pair.live, dispatchers, clock, scope)
                }
            },
            app,
            dispatchers,
        )
    }

    private suspend fun TestScope.openWorld(phone: Phone): GraphChatWorld {
        val gateway =
            FakeServerGateway(listOf(Session(ref.sessionId, directory = folder, time = SessionTime(idleMs = 20))))
        val registry = registry(gateway)
        gateway.events.connect()
        registry.open(profile)
        val world = GraphChatWorld(ref, registry, phoneServer = phone)
        world.attach()
        runCurrent()
        return world
    }

    @Test
    fun `I147-STORAGE a conversation whose folder Android no longer lets the app open says so as it opens`() = runTest {
        val phone = Phone(ChatPhoneFolder.AppAccess)
        val world = openWorld(phone)
        assertEquals(listOf(folder to true), phone.checks.take(1), "checked thoroughly, in the conversation's own folder")
        assertEquals(ChatPhoneFolder.AppAccess, world.phoneFolder.value)
        // The person allows it again: the notice's action, then the folder is read again and the line goes.
        assertEquals(Outcome.Ok(Unit), world.fixPhoneFolder())
        assertEquals(listOf(ChatPhoneFolder.AppAccess), phone.fixes)
        assertNull(world.phoneFolder.value)
        world.detach()
    }

    @Test
    fun `I147-STORAGE no message is sent into a folder the agent can no longer reach`() = runTest {
        val phone = Phone(ChatPhoneFolder.Ready)
        val world = openWorld(phone)
        assertNull(world.phoneFolder.value)
        // The folder was deleted while the page was open.
        phone.reach = ChatPhoneFolder.Missing
        val sent = world.send("hello")
        val failure = assertIs<Failure.Unavailable>(assertIs<Outcome.Failed>(sent).failure)
        assertEquals(PHONE_FOLDER_FEATURE, failure.feature.name)
        assertEquals(listOf(folder to false), phone.checks.takeLast(1), "the light check before a message starts no Linux")
        assertEquals(ChatPhoneFolder.Missing, world.phoneFolder.value)
        assertTrue(world.pending.value.isEmpty(), "nothing left the box")
        world.detach()
    }

    @Test
    fun `I147-STORAGE a folder that is fine, or not in the phone's storage, never holds a message back`() = runTest {
        assertNull(Phone(ChatPhoneFolder.Ready).phoneFolderRefusal(ref, folder))
        assertNull(Phone(null).phoneFolderRefusal(ref, "/root/projects/app"))
        assertNull((null as ChatPhoneServer?).phoneFolderRefusal(ref, folder))
    }
}
