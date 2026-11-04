package io.github.eslamasabry.oc.engine.integrations.quota

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FakeSecrets : SecretStore {
    val values = LinkedHashMap<String, String>()

    override suspend fun read(name: String): Outcome<String?> = values[name].ok()

    override suspend fun write(name: String, value: String): Outcome<Unit> {
        values[name] = value
        return Unit.ok()
    }

    override suspend fun delete(name: String): Outcome<Unit> {
        values.remove(name)
        return Unit.ok()
    }

    override suspend fun names(prefix: String): Outcome<Set<String>> = values.keys.filterTo(LinkedHashSet()) { it.startsWith(prefix) }.ok()
}

/** In-app Ubuntu as the collector host sees it: a call log and canned answers (Flutter `_FakeLinux`). */
internal class FakeLinux : QuotaLinuxHost {
    var installed = true
    var nodeAnswer = "qc node=22"
    var services = listOf<String>()
    val calls = mutableListOf<String>()
    val scripts = mutableListOf<String>()
    var serviceScript: String? = null
    var servicePort: Int? = null

    override suspend fun status(): QuotaLinuxHost.Status {
        calls += "status"
        return QuotaLinuxHost.Status(installed, installed, services)
    }

    override suspend fun run(script: String, timeoutMs: Long): QuotaLinuxHost.RunResult {
        scripts += script
        return when {
            "process.versions.node" in script -> QuotaLinuxHost.RunResult(0, nodeAnswer).also { calls += "node" }
            "qc files=ok" in script -> QuotaLinuxHost.RunResult(0, "qc files=ok").also { calls += "lock" }
            "keys.mjs" in script && "chmod" !in script ->
                QuotaLinuxHost.RunResult(0, "qc keys=ok codex=opencode glm=1 minimax=0").also { calls += "keys" }
            "qc removed" in script -> QuotaLinuxHost.RunResult(0, "qc removed").also { calls += "rm" }
            else -> QuotaLinuxHost.RunResult(0, "").also { calls += "run" }
        }
    }

    override suspend fun startService(name: String, script: String, port: Int?) {
        calls += "start:$name"
        scripts += script
        serviceScript = script
        servicePort = port
        services = services + name
    }

    override suspend fun stopService(name: String) {
        calls += "stop:$name"
        services = services - name
    }

    override suspend fun serviceLog(name: String): String = "QUOTA_FIXTURE_LOG"
}

internal class FakeRootfs : QuotaRootfs {
    val files = LinkedHashMap<String, ByteArray>()
    val writes = mutableListOf<String>()

    override suspend fun read(path: String): ByteArray? = files[path]

    override suspend fun write(path: String, bytes: ByteArray) {
        writes += path
        files[path] = bytes.copyOf()
    }
}

/** Ports the secrets group of test/quota_collector_host_test.dart (row B-SEC-141) plus its set-up order checks. */
class QuotaCollectorHostTest {
    private val builtin = QuotaServerProfile(ProfileId("builtin"), "http://127.0.0.1:4097", isOpenCode = true)

    private class World(scope: TestScope) {
        val backend = InMemoryPrefsBackend()
        val prefs =
            PrefsKeyValueStore(
                backend,
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                StandardTestDispatcher(scope.testScheduler),
            )
        val lane = WriterLane("app")
        val secrets = FakeSecrets()
        val linux = FakeLinux()
        val rootfs = FakeRootfs()
        val random = Random(7)

        fun host(probeOk: Boolean = true, collector: String = "// collector v1\n") =
            InAppQuotaCollector(
                prefs = prefs,
                lane = lane,
                secrets = secrets,
                linux = linux,
                rootfs = rootfs,
                randomBytes = { random.nextBytes(it) },
                loadCollector = { collector },
                loadKeysHelper = { "// keys helper\n" },
                probe = { probeOk },
                supported = true,
                probeAttempts = 2,
                probeDelayMs = 0,
            )

        fun stagedToken(): String = rootfs.files.getValue(InAppQuotaCollector.TOKEN_PATH).decodeToString().trim()
    }

    @Test
    fun `B-SEC-141 set-up checks Node, copies, locks, finds sign-ins, then starts on 4195`() =
        runTest {
            val w = World(this)
            val steps = mutableListOf<QuotaSetupStep>()
            assertNull(w.host().setUp(builtin) { steps += it })
            assertEquals(listOf("status", "node", "lock", "keys", "start:quota"), w.linux.calls)
            assertEquals(listOf(QuotaSetupStep.Checking, QuotaSetupStep.Copying, QuotaSetupStep.SignIns, QuotaSetupStep.Starting), steps)
            assertEquals(4195, w.linux.servicePort)
            assertEquals("// collector v1\n", w.rootfs.files.getValue(InAppQuotaCollector.COLLECTOR_PATH).decodeToString())
            assertEquals(true, w.prefs.bool(InAppQuotaCollector.enabledKey(builtin.id)))
        }

    @Test
    fun `B-SEC-141 the token is long, random, in secure storage, 0600-staged`() =
        runTest {
            val w = World(this)
            assertNull(w.host().setUp(builtin))
            val token = w.secrets.values.getValue("oc.quotaToken.builtin")
            assertTrue(token.length >= 48)
            assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(token))
            assertEquals(token, w.stagedToken())
            assertTrue(w.linux.scripts.any { "chmod 600" in it && InAppQuotaCollector.TOKEN_PATH in it })
            // A second set-up keeps the token and does not rewrite unchanged files.
            w.rootfs.writes.clear()
            assertNull(w.host().setUp(builtin))
            assertEquals(token, w.stagedToken())
            assertEquals(token, w.secrets.values["oc.quotaToken.builtin"])
            assertFalse(InAppQuotaCollector.COLLECTOR_PATH in w.rootfs.writes)
            // A different random source makes a different token.
            val other = World(this).apply { assertNull(InAppQuotaCollector(prefs, lane, secrets, linux, rootfs, { Random(99).nextBytes(it) }, { "c" }, { "k" }, { true }, true, 1, 0).setUp(builtin)) }
            assertFalse(other.secrets.values.getValue("oc.quotaToken.builtin") == token)
        }

    @Test
    fun `B-SEC-141 no command or service script holds the token or a key`() =
        runTest {
            val w = World(this)
            assertNull(w.host().setUp(builtin))
            val token = w.secrets.values.getValue("oc.quotaToken.builtin")
            for (script in w.linux.scripts) {
                assertFalse(token in script)
                assertFalse("Bearer" in script)
            }
            val service = w.linux.serviceScript!!
            assertTrue("OCMN_QUOTA_READ_TOKEN_FILE=/root/.oc-builtin/quota/read.token" in service)
            assertTrue("OCMN_GLM_KEY_FILE=" in service)
            assertTrue("/root/.local/share/opencode/auth.json" in service)
            assertFalse("KEY=sk" in service)
        }

    @Test
    fun `B-SEC-141 OpenCode 2 looks in its own data folder first`() =
        runTest {
            val script = World(this).host().serviceScript()
            assertTrue(
                script.indexOf("/root/.oc-opencode2/data/opencode/auth.json") < script.indexOf("/root/.local/share/opencode/auth.json"),
            )
        }

    @Test
    fun `B-SEC-141 the secure slot and the switch are swept with the profile`() =
        runTest {
            val w = World(this)
            val scope = DefaultProfileScope(w.prefs, w.secrets, w.lane)
            InAppQuotaCollector.register(scope)
            assertNull(w.host().setUp(builtin))
            assertTrue(InAppQuotaCollector.enabledKey(builtin.id) in scope.scopedKeys(builtin.id))
            val owned = (scope.ownedData(builtin.id) as Outcome.Ok).value
            assertTrue("oc.quotaToken.builtin" in owned.secretNames)
            assertTrue(scope.sweep(builtin.id) is Outcome.Ok)
            assertNull(w.prefs.bool(InAppQuotaCollector.enabledKey(builtin.id)))
            // Turning usage off removes the token and the switch too.
            assertNull(w.host().turnOff(builtin.id))
            assertFalse("oc.quotaToken.builtin" in w.secrets.values)
        }

    @Test
    fun `B-SEC-141 Node 19 and a missing Node stop before anything is copied or stored`() =
        runTest {
            val w = World(this)
            w.linux.nodeAnswer = "qc node=19"
            assertEquals(QuotaSetupFailure.NodeTooOld, w.host().setUp(builtin)?.failure)
            w.linux.nodeAnswer = "qc node="
            assertEquals(QuotaSetupFailure.NoNode, w.host().setUp(builtin)?.failure)
            assertTrue(w.linux.calls.none { it.startsWith("start") })
            assertTrue(w.secrets.values.isEmpty())
            assertNull(w.prefs.bool(InAppQuotaCollector.enabledKey(builtin.id)))
        }

    @Test
    fun `B-SEC-141 Ubuntu not ready and a silent helper are their own failures`() =
        runTest {
            val w = World(this)
            w.linux.installed = false
            assertEquals(QuotaSetupFailure.LinuxNotReady, w.host().setUp(builtin)?.failure)
            w.linux.installed = true
            val silent = w.host(probeOk = false).setUp(builtin)
            assertEquals(QuotaSetupFailure.Start, silent?.failure)
            assertEquals("QUOTA_FIXTURE_LOG", silent?.detail)
            assertNull(w.prefs.bool(InAppQuotaCollector.enabledKey(builtin.id)))
        }
}
