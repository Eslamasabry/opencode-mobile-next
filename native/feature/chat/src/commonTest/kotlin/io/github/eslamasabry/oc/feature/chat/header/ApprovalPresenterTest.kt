package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.requests.consent.ApprovalMode
import io.github.eslamasabry.oc.engine.requests.consent.ApprovalModes
import io.github.eslamasabry.oc.engine.requests.consent.SessionApproval
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.Ref
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalCodec
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalPort
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalPresenter
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class MemoryApprovalStore : ApprovalStore {
    val raw = HashMap<String, String>()
    var refuse = false
    override val revision = MutableStateFlow(0L)

    override fun settings(profile: ProfileId): Map<String, SessionApproval> = ApprovalCodec.decode(raw[ApprovalCodec.key(profile)])

    override suspend fun set(profile: ProfileId, session: String, setting: SessionApproval?): Outcome<Unit> {
        if (refuse) return Outcome.Failed(Failure.StorageRefused())
        val next = settings(profile).toMutableMap()
        if (setting == null) next.remove(session) else next[session] = setting
        raw[ApprovalCodec.key(profile)] = ApprovalCodec.encode(next)
        revision.value += 1
        return Outcome.Ok(Unit)
    }
}

class FakeApprovalPort : ApprovalPort {
    override val waiting = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val allowed = mutableListOf<String>()
    val parents = HashMap<SessionId, SessionId>()

    override suspend fun allowOnce(id: RequestId): Outcome<Unit> {
        allowed += id.raw
        waiting.value = waiting.value.filterNot { it.id == id }
        return Outcome.Ok(Unit)
    }

    override fun parentOf(session: SessionId): SessionId? = parents[session]
}

fun ask(id: String, session: String = Ref.sessionId.raw): PermissionRequest =
    PermissionRequest(RequestId(id), SessionId(session), "bash", persistentListOf("ls"), JsonObject(emptyMap()), persistentListOf(), null, false, null)

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalPresenterTest {
    private val store = MemoryApprovalStore()
    private val port = FakeApprovalPort()
    private val caps: StateFlow<ServerCapabilities> = MutableStateFlow(ServerCapabilities(permissionRequests = true))
    private val p = ApprovalPresenter(Ref, store, caps, port, Immediate, TestClock())

    @Test
    fun `B-STATE-099 the setting is stored under the profile-scoped key in the Flutter format and malformed storage reads as none`() {
        p.chooseApproval(ApprovalChoice.Auto)
        assertEquals("""{"s1":{"mode":"autoOnce","inheritToChildren":false}}""", store.raw["oc.autoApprove.p1"])
        assertEquals(ApprovalChoice.Auto, p.current().choice)
        assertTrue(ApprovalCodec.decode("{not json").isEmpty())
        assertTrue(ApprovalCodec.decode("""{"s1":{"mode":"sometimes"}}""").isEmpty())
    }

    @Test
    fun `B-STATE-099 a refused write keeps the last choice and says so`() {
        store.refuse = true
        p.chooseApproval(ApprovalChoice.Auto)
        assertEquals(ApprovalChoice.Ask, p.current().choice)
        assertNotNull(p.current().saveFailure)
        p.dismissApprovalFailure()
        assertEquals(null, p.current().saveFailure)
    }

    @Test
    fun `B-AGENT-221 nothing is answered without a setting`() =
        runTest(UnconfinedTestDispatcher()) {
            p.states(backgroundScope, SharingStarted.Eagerly)
            port.waiting.value = listOf(ask("r1"))
            assertTrue(port.allowed.isEmpty())
            assertEquals(ApprovalChoice.Ask, p.current().choice)
        }

    @Test
    fun `B-AGENT-225 switching to auto answers the asks already waiting once and later ones as they arrive`() =
        runTest(UnconfinedTestDispatcher()) {
            p.states(backgroundScope, SharingStarted.Eagerly)
            port.waiting.value = listOf(ask("r1"), ask("other", session = "s-other"))
            p.chooseApproval(ApprovalChoice.Auto)
            assertEquals(listOf("r1"), port.allowed, "only this conversation's ask")
            port.waiting.value = port.waiting.value + ask("r2")
            assertEquals(listOf("r1", "r2"), port.allowed)
            assertEquals(2, p.current().autoAnswered)
        }

    @Test
    fun `B-AGENT-224 turning approval off makes later asks wait`() =
        runTest(UnconfinedTestDispatcher()) {
            p.states(backgroundScope, SharingStarted.Eagerly)
            p.chooseApproval(ApprovalChoice.Auto)
            p.chooseApproval(ApprovalChoice.Ask)
            port.waiting.value = listOf(ask("r3"))
            assertTrue(port.allowed.isEmpty())
        }

    @Test
    fun `B-AGENT-225 approve everything asks first and then answers every conversation on the server`() =
        runTest(UnconfinedTestDispatcher()) {
            p.states(backgroundScope, SharingStarted.Eagerly)
            p.chooseApproval(ApprovalChoice.Everything)
            assertTrue(p.current().confirmEverything)
            assertTrue(store.raw.isEmpty(), "nothing is written before the answer")
            p.confirmEverything()
            assertEquals(ApprovalChoice.Everything, p.current().choice)
            assertTrue(ApprovalModes.SERVER_WIDE in store.settings(Ref.profileId))
            port.waiting.value = listOf(ask("x", session = "s-other"))
            assertEquals(listOf("x"), port.allowed)
            // Leaving "Approve everything" turns the server-wide switch off.
            p.chooseApproval(ApprovalChoice.Ask)
            assertFalse(ApprovalModes.SERVER_WIDE in store.settings(Ref.profileId))
        }

    @Test
    fun `B-AGENT-223 a sub-agent follows a parent that passes its mode down`() =
        runTest(UnconfinedTestDispatcher()) {
            store.set(Ref.profileId, "parent", SessionApproval(ApprovalMode.AutoOnce, inheritToChildren = true))
            port.parents[Ref.sessionId] = SessionId("parent")
            p.states(backgroundScope, SharingStarted.Eagerly)
            assertEquals(ApprovalChoice.Auto, p.current().choice)
            port.waiting.value = listOf(ask("child-ask"))
            assertEquals(listOf("child-ask"), port.allowed)
        }

    @Test
    fun `the chip is not offered where the server never asks or the host has no store`() {
        val noAsks = ApprovalPresenter(Ref, store, MutableStateFlow(ServerCapabilities(permissionRequests = false)), port, Immediate, TestClock())
        assertFalse(noAsks.current().offered)
        val noStore = ApprovalPresenter(Ref, null, caps, port, Immediate, TestClock())
        assertFalse(noStore.current().offered)
        assertTrue(p.current().offered)
    }
}
