package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ByoHostControllerTest {
    private val store = MemoryStore()
    private var runner = FakeRunner()
    private val random = CountingRandom()

    private fun controller(
        runner: FakeRunner = this.runner,
        bundle: ByoHostBundle? = testBundle,
        enabled: Boolean = true,
        id: String = "phone-a",
        clearLocalData: (suspend (String) -> Unit)? = null,
    ) = ByoHostController(id, runner, store, random, bundle, clearLocalData, enabled)

    private var controller = controller()

    private suspend fun add() {
        val candidate = controller.inspect(ByoHostTarget.parse("alice@server.example")!!).value()
        val login = ByoHostLogin(password = "one-use-password")
        controller.adopt(candidate.fingerprint, login).value()
        assertNull(login.password)
    }

    private fun assertCode(outcome: Outcome<*>, code: ByoHostFailureCode, owner: ByoHostController = controller) {
        assertEquals(code.asFailure(), assertIs<Outcome.Failed>(outcome).failure)
        if (code != ByoHostFailureCode.Busy && code != ByoHostFailureCode.Disabled) {
            assertEquals(code, owner.snapshot.value.failure)
        }
    }

    @Test
    fun `B-AGENT-045 key-only enrollment survives restart and pairs the reserved phone`() = runTest {
        val pin = controller.inspect(ByoHostTarget.parse("ubuntu@100.80.1.2")!!).value()
        val identity = controller.prepareSshIdentity(pin.fingerprint).value()
        assertEquals("oc.byoHostSsh.phone-a", identity.keyAlias)
        assertEquals(ByoHostPhase.NeedsTrust, store.record!!.phase)
        assertNull(store.secrets)
        controller.dispose().value()
        controller = controller()
        controller.resume().value()
        assertEquals(ByoHostPhase.NeedsTrust, controller.snapshot.value.phase)
        controller.adopt(pin.fingerprint, ByoHostLogin()).value()
        assertEquals(ByoHostPhase.Ready, controller.snapshot.value.phase)
        assertEquals(1, runner.installs)
    }

    @Test
    fun `B-AGENT-045 default off and an unknown host never change the remote`() = runTest {
        val disabled = ByoHostController("a", runner, store, random, testBundle)
        assertCode(disabled.inspect(ByoHostTarget.parse("alice@host")!!), ByoHostFailureCode.Disabled, disabled)
        assertEquals(ByoHostPhase.Idle, disabled.snapshot.value.phase)
        controller.inspect(ByoHostTarget.parse("alice@host")!!).value()
        val login = ByoHostLogin(password = "once")
        assertCode(controller.adopt(key(2).fingerprint, login), ByoHostFailureCode.NeedsTrust)
        assertNull(login.password)
        assertEquals(0, runner.installs)
        assertNull(store.secrets)
    }

    @Test
    fun `B-AGENT-046 durable adoption returns only an authenticated ephemeral gateway profile`() = runTest {
        add()
        val profile = controller.connectedProfile("My machine").value()
        assertEquals("http://127.0.0.1:19001", profile.baseUrl)
        assertEquals(BackendKind.OpenCode2, profile.backend)
        assertTrue(profile.transientTransport)
        assertEquals(store.secrets!!.deviceToken, profile.password)
        assertFalse(profile.password in ByoHostCodec.encodeRecord(store.record!!))
        assertFalse(profile.password in profile.toString())
        assertEquals(ByoHostPhase.Disconnected, store.record!!.phase)
        controller.dispose().value()
        assertTrue(runner.tunnel!!.closed)
        assertNotNull(store.record)
        assertEquals(0, runner.revokes)
        controller = controller(runner = FakeRunner(), bundle = null)
        controller.resume().value()
        assertEquals(ByoHostPhase.Ready, controller.snapshot.value.phase)
    }

    @Test
    fun `B-AGENT-046 an OpenCode 2 bundle selects OpenCode 2 and keeps per-phone proxy credentials`() = runTest {
        runner.bundleVersion = "2.0.0"
        runner.openCodeVersion = "2.1.1"
        controller = controller(bundle = ByoHostBundle.of("2.0.0", "2.1.1", testBundle.artifacts)!!)
        add()
        val profile = controller.connectedProfile("OC2 host").value()
        assertEquals(BackendKind.OpenCode2, profile.backend)
        assertEquals("2.1.1", profile.serverVersion)
        assertEquals("phone-a", profile.username)
        assertEquals(store.secrets!!.deviceToken, profile.password)
        assertEquals("phone-a", profile.toServerProfile().username)
        controller.dispose().value()
        assertIs<Outcome.Failed>(controller.connectedProfile("gone"))
    }

    @Test
    fun `B-AGENT-047 a killed installation retries the exact device tuple after a fresh admin login`() = runTest {
        runner.dieAfterInstall = true
        val candidate = controller.inspect(ByoHostTarget.parse("alice@host")!!).value()
        val login = ByoHostLogin(password = "one-use")
        assertCode(controller.adopt(candidate.fingerprint, login), ByoHostFailureCode.Transport)
        assertNull(login.password)
        assertNull(store.record!!.remotePort)
        assertNotNull(store.secrets)
        val previous = store.secrets!!.deviceToken
        runner.dieAfterInstall = false
        controller = controller()
        assertCode(controller.resume(), ByoHostFailureCode.Uncertain)
        val fresh = ByoHostLogin(password = "fresh")
        controller.resume(fresh).value()
        assertNull(fresh.password)
        assertEquals(previous, store.secrets!!.deviceToken)
        assertEquals(2, runner.installs)
        assertEquals(listOf(previous, previous), runner.installTokens)
        assertEquals(ByoHostPhase.Ready, controller.snapshot.value.phase)
    }

    @Test
    fun `B-AGENT-048 local removal never revokes or stops the host`() = runTest {
        add()
        controller.remove(revokeAccess = false).value()
        assertEquals(0, runner.revokes)
        assertNull(store.record)
        assertNull(store.secrets)
        assertTrue(runner.tunnel!!.closed)
        assertNull(controller.snapshot.value.record)
        assertEquals(ByoHostPhase.Removed, controller.snapshot.value.phase)
    }

    @Test
    fun `B-AGENT-048 a failed revocation keeps the connection and all durable credentials`() = runTest {
        add()
        runner.rejectRevoke = true
        assertCode(controller.remove(revokeAccess = true), ByoHostFailureCode.RevocationFailed)
        assertEquals(ByoHostPhase.Ready, controller.snapshot.value.phase)
        assertNotNull(store.record)
        assertNotNull(store.secrets)
        assertFalse(runner.tunnel!!.closed)
        assertEquals("phone-a", controller.connectedProfile("kept").value().username)
        runner.rejectRevoke = false
        controller.remove(revokeAccess = true).value()
        assertNull(store.secrets)
    }

    @Test
    fun `B-AGENT-048 a confirmed revoke is not repeated after a local deletion failure`() = runTest {
        add()
        store.failRemove = true
        assertIs<Outcome.Failed>(controller.remove(revokeAccess = true))
        assertNotNull(controller.snapshot.value.failure)
        assertTrue(store.record!!.revoked)
        assertEquals(1, runner.revokes)
        store.failRemove = false
        controller.remove(revokeAccess = true).value()
        assertEquals(1, runner.revokes)
        assertNull(store.secrets)
    }

    @Test
    fun `B-AGENT-048 a refused shared-data sweep keeps a findable revoked cleanup journal`() = runTest {
        add()
        val owner =
            controller(clearLocalData = {
                store.record = null // an optimistic scoped preference sweep
                error("raw shared data error")
            })
        assertCode(owner.remove(revokeAccess = true), ByoHostFailureCode.Storage, owner)
        assertTrue(store.record!!.revoked)
        assertNotNull(store.secrets)
        assertEquals(1, runner.revokes)
        controller(clearLocalData = null).remove(revokeAccess = true).value()
        assertEquals(1, runner.revokes)
        assertNull(store.record)
    }

    @Test
    fun `B-AGENT-048 shared local data is cleared before the secrets envelope is discarded`() = runTest {
        add()
        var calls = 0
        val owner =
            controller(clearLocalData = { id ->
                assertEquals("phone-a", id)
                assertNotNull(store.record)
                assertNotNull(store.secrets)
                calls++
            })
        owner.remove(revokeAccess = false).value()
        assertEquals(1, calls)
        assertNull(store.secrets)
    }

    @Test
    fun `B-AGENT-045 a changed SSH host key is refused before forwarding or installing again`() = runTest {
        add()
        controller.dispose().value()
        runner.hostKey = key(2)
        controller = controller()
        assertCode(controller.resume(), ByoHostFailureCode.HostKeyChanged)
        assertEquals(1, runner.forwards)
        assertEquals(1, runner.installs)
    }

    @Test
    fun `B-AGENT-046 a changed supervisor identity closes the replacement tunnel`() = runTest {
        add()
        controller.dispose().value()
        runner.hostId = "other-host"
        controller = controller()
        assertCode(controller.resume(), ByoHostFailureCode.IdentityChanged)
        assertTrue(runner.tunnel!!.closed)
        assertNotEquals(ByoHostPhase.Ready, controller.snapshot.value.phase)
    }

    @Test
    fun `B-AGENT-045 invalid targets and mismatched fingerprints are refused and logins never print`() {
        for (text in listOf("-oProxyCommand@host", "a@host;id", "a@x@y", "a@..")) {
            assertNull(ByoHostTarget.parse(text), text)
        }
        assertNull(ByoHostTarget.of("a", "host\n"))
        assertNull(ByoHostKey.of(key().publicKey, key(2).fingerprint))
        assertFalse("secret" in ByoHostLogin(password = "secret").toString())
    }

    @Test
    fun `B-AGENT-046 the stored record and secrets round-trip in the Dart shapes without secrets in the record`() =
        runTest {
            add()
            val record = store.record!!
            val text = ByoHostCodec.encodeRecord(record)
            assertTrue("\"phase\":\"disconnected\"" in text)
            assertFalse(store.secrets!!.deviceToken in text)
            assertEquals(record, ByoHostCodec.decodeRecord(text))
            val secrets = ByoHostCodec.encodeSecrets(store.secrets!!)
            assertEquals(store.secrets, ByoHostCodec.decodeSecrets(secrets))
            assertNull(ByoHostCodec.decodeSecrets(secrets.replace("\"schema\":2", "\"schema\":2,\"privateKey\":\"x\"")))
            assertNull(ByoHostCodec.decodeRecord(text.replace("phone-a", "bad id")))
        }
}
