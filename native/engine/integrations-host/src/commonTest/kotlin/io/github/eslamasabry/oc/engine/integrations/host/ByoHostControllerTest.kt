package io.github.eslamasabry.oc.engine.integrations.host

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStore : ByoHostStore {
    var record: ByoHostRecord? = null
    var secrets: ByoHostSecrets? = null

    override suspend fun listRecords(): HostResult<ImmutableList<ByoHostRecord>> = HostResult.Ok(persistentListOf(*listOfNotNull(record).toTypedArray()))

    override suspend fun readRecord(profileId: String): HostResult<ByoHostRecord?> = HostResult.Ok(record)

    override suspend fun readSecrets(profileId: String): HostResult<ByoHostSecrets?> = HostResult.Ok(secrets)

    override suspend fun saveRecord(record: ByoHostRecord): HostResult<Unit> = HostResult.Ok(Unit).also { this.record = record }

    override suspend fun saveSecrets(profileId: String, secrets: ByoHostSecrets): HostResult<Unit> = HostResult.Ok(Unit).also { this.secrets = secrets }

    override suspend fun remove(profileId: String): HostResult<Unit> {
        record = null
        secrets = null
        return HostResult.Ok(Unit)
    }
}

private class Tunnel : ByoHostTunnel {
    var closed = false
    override val localPort: Int = 19_001

    override suspend fun close() {
        closed = true
    }
}

/** The Dart controller test's `FakeRunner`. */
private class FakeRunner : ByoHostSshRunner {
    var hostKey = hostKey(1)
    var hostId = "host-a"
    var installs = 0
    var forwards = 0
    var revokes = 0
    var tunnel: Tunnel? = null

    private val descriptor get() = ByoHostDescriptor(hostId, "1.1.0", "2.1.1", 4096)

    override suspend fun available(): Boolean = true

    override suspend fun inspectKey(target: ByoHostTarget): HostResult<ByoHostKey> = HostResult.Ok(hostKey)

    override suspend fun generateIdentity(profileId: String): HostResult<ByoHostIdentity> =
        HostResult.Ok(ByoHostIdentity("oc.byoHostSsh.phone-a", "fake-public-key"))

    override suspend fun install(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        login: ByoHostLogin,
        identity: ByoHostIdentity,
        deviceToken: String,
        bundle: ByoHostBundle,
    ): HostResult<ByoHostDescriptor> {
        installs++
        return HostResult.Ok(descriptor)
    }

    override suspend fun forward(
        profileId: String,
        target: ByoHostTarget,
        hostKey: ByoHostKey,
        identity: ByoHostIdentity,
        remotePort: Int,
    ): HostResult<ByoHostTunnel> {
        forwards++
        return HostResult.Ok(Tunnel().also { tunnel = it })
    }

    override suspend fun describe(tunnel: ByoHostTunnel, deviceId: String, deviceToken: String): HostResult<ByoHostDescriptor> = HostResult.Ok(descriptor)

    override suspend fun revoke(tunnel: ByoHostTunnel, deviceId: String, deviceToken: String): Boolean {
        revokes++
        return true
    }

    override suspend fun cleanup(profileId: String): HostResult<Unit> = HostResult.Ok(Unit)

    override suspend fun dispose() {}
}

/** Flutter test/byo_host/controller_test.dart (identity pinning rows). */
class ByoHostControllerTest {
    private val store = MemoryStore()
    private val runner = FakeRunner()
    private val bundle = ByoHostBundle.of("1.1.0", "2.1.1", mapOf("x64" to ByoHostArtifact.of("https://example.invalid/bundle.tar.gz", "a".repeat(64))!!))!!
    private var controller = newController(withBundle = true)

    private fun newController(withBundle: Boolean = false) =
        ByoHostController("phone-a", runner, store, if (withBundle) bundle else null, enabled = true)

    private suspend fun TestScope.add() {
        val candidate = controller.inspect(ByoHostTarget.parse("alice@server.example")!!).ok()
        val login = ByoHostLogin(password = "one-use-password")
        controller.adopt(candidate.fingerprint, login).ok()
        assertNull(login.password)
        assertEquals(ByoHostPhase.Ready, controller.snapshot.value.phase)
    }

    @Test
    fun `B-SEC-024 a changed SSH identity is rejected before forwarding or mutations`() =
        runTest {
            add()
            controller.dispose().ok()
            runner.hostKey = hostKey(2)
            controller = newController()
            assertEquals(HostResult.Failed(ByoHostFailureCode.HostKeyChanged), controller.resume())
            assertEquals(1, runner.forwards)
            assertEquals(1, runner.installs)
            assertEquals(ByoHostPhase.Failed, controller.snapshot.value.phase)
            assertEquals(ByoHostFailureCode.HostKeyChanged, controller.snapshot.value.failure)
        }

    @Test
    fun `B-SEC-024 a changed supervisor identity closes the replacement tunnel`() =
        runTest {
            add()
            controller.dispose().ok()
            runner.hostId = "other-host"
            controller = newController()
            assertEquals(HostResult.Failed(ByoHostFailureCode.IdentityChanged), controller.resume())
            assertTrue(runner.tunnel!!.closed)
            assertTrue(controller.connection() is HostResult.Failed)
        }

    @Test
    fun `B-SEC-024 an unchanged identity reconnects with the device credentials only on loopback`() =
        runTest {
            add()
            controller.dispose().ok()
            controller = newController()
            controller.resume().ok()
            val connection = controller.connection().ok()
            assertEquals("http://127.0.0.1:19001", connection.baseUrl)
            assertEquals("phone-a", connection.username)
            assertEquals(store.secrets!!.deviceToken, connection.password)
            assertEquals("ByoHostConnection(<redacted>)", connection.toString())
            assertEquals(2, runner.forwards)
        }
}
