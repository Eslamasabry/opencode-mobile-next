package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.usagereaders.collector.InAppLinux
import io.github.eslamasabry.oc.engine.usagereaders.collector.InAppQuotaCollector
import io.github.eslamasabry.oc.engine.usagereaders.collector.LinuxRun
import io.github.eslamasabry.oc.engine.usagereaders.collector.LinuxStatus
import io.github.eslamasabry.oc.engine.usagereaders.collector.QuotaCollectorDemand
import io.github.eslamasabry.oc.engine.usagereaders.collector.QuotaSetupFailure
import io.github.eslamasabry.oc.engine.usagereaders.collector.QuotaSetupResult
import io.github.eslamasabry.oc.engine.usagereaders.collector.QuotaSetupStep
import io.github.eslamasabry.oc.engine.usagereaders.collector.RootfsFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-app Ubuntu as the collector host sees it: a call log and canned answers. */
internal class FakeLinux : InAppLinux {
    var installed = true
    var nodeAnswer = "qc node=22"
    var services = listOf<String>()
    val calls = mutableListOf<String>()
    val scripts = mutableListOf<String>()
    var serviceScript: String? = null
    var servicePort: Int? = null

    override suspend fun status(): Outcome<LinuxStatus> {
        calls += "status"
        return LinuxStatus(installed, installed, services).ok()
    }

    override suspend fun run(script: String, timeoutMs: Long): Outcome<LinuxRun> {
        scripts += script
        val (call, out) = when {
            "process.versions.node" in script -> "node" to nodeAnswer
            "qc files=ok" in script -> "lock" to "qc files=ok"
            "keys.mjs" in script && "chmod" !in script -> "keys" to "qc keys=ok codex=opencode glm=1 minimax=0"
            "qc removed" in script -> "rm" to "qc removed"
            else -> "run" to ""
        }
        calls += call
        return LinuxRun(0, out).ok()
    }

    override suspend fun startService(name: String, script: String, port: Int?): Outcome<Unit> {
        calls += "start:$name"
        scripts += script
        serviceScript = script
        servicePort = port
        services = services + name
        return Unit.ok()
    }

    override suspend fun stopService(name: String): Outcome<Unit> {
        calls += "stop:$name"
        services = services - name
        return Unit.ok()
    }

    override suspend fun serviceLog(name: String): Outcome<String> = "QUOTA_FIXTURE_LOG".ok()
}

internal class FakeFiles : RootfsFiles {
    val files = HashMap<String, ByteArray>()
    val writes = mutableListOf<String>()

    override suspend fun read(path: String): ByteArray? = files[path]

    override suspend fun write(path: String, bytes: ByteArray): Boolean {
        writes += path
        files[path] = bytes
        return true
    }

    fun text(path: String): String? = files[path]?.decodeToString()
}

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

    override suspend fun names(prefix: String): Outcome<Set<String>> = values.keys.filter { it.startsWith(prefix) }.toSet().ok()
}

class QuotaCollectorHostTest {
    private val linux = FakeLinux()
    private val files = FakeFiles()
    private val secrets = FakeSecrets()
    private val switches = MemoryUsageSwitches()
    private var collectorText = "// collector v1\n"
    private val random = Random(7)
    private val inApp = UsageServer("builtin", BackendKind.OpenCode2, inApp = true)

    private fun host(probeOk: Boolean = true, demand: QuotaCollectorDemand? = null, supported: Boolean = true) =
        InAppQuotaCollector(
            switches = switches,
            linux = linux,
            files = files,
            secrets = secrets,
            loadCollector = { collectorText },
            randomBytes = { random.nextBytes(it) },
            probe = { probeOk },
            supported = supported,
            demand = demand,
            probeAttempts = 2,
            probeDelayMs = 0,
        )

    private fun stagedToken() = files.text(InAppQuotaCollector.TOKEN_PATH)!!.trim()

    private val enabled get() = switches.bool(InAppQuotaCollector.enabledKey("builtin"))

    // ---- B-LINUX-176 set-up order

    @Test
    fun `B-LINUX-176 checks Node, copies, locks, finds sign-ins, then starts`() = runTest {
        val steps = mutableListOf<QuotaSetupStep>()
        assertEquals(QuotaSetupResult.Done, host().setUp(inApp) { steps += it })
        assertEquals(listOf("status", "node", "lock", "keys", "start:quota"), linux.calls)
        assertEquals(
            listOf(QuotaSetupStep.Checking, QuotaSetupStep.Copying, QuotaSetupStep.SignIns, QuotaSetupStep.Starting),
            steps,
        )
        assertEquals(4195, linux.servicePort)
        assertEquals(collectorText, files.text(InAppQuotaCollector.COLLECTOR_PATH))
        assertEquals(InAppQuotaCollector.KEYS_HELPER_SOURCE, files.text(InAppQuotaCollector.KEYS_PATH))
        assertEquals(true, enabled)
        // The token is long, URL-safe, in secure storage and staged; no script holds it.
        val token = secrets.values.getValue("oc.quotaToken.builtin")
        assertTrue(token.length >= 48 && Regex("^[A-Za-z0-9_-]+$").matches(token))
        assertEquals(token, stagedToken())
        assertTrue(linux.scripts.none { token in it })
        assertTrue(linux.scripts.any { "chmod 600" in it && InAppQuotaCollector.TOKEN_PATH in it })
    }

    @Test
    fun `B-LINUX-176 running it again keeps the token and the unchanged files`() = runTest {
        host().setUp(inApp)
        val token = stagedToken()
        val copiedBefore = files.writes.count { it == InAppQuotaCollector.COLLECTOR_PATH }
        host().setUp(inApp)
        assertEquals(token, stagedToken())
        assertEquals(token, secrets.values["oc.quotaToken.builtin"])
        assertEquals(copiedBefore, files.writes.count { it == InAppQuotaCollector.COLLECTOR_PATH })
        assertEquals(1, files.writes.count { it == InAppQuotaCollector.KEYS_PATH })
    }

    @Test
    fun `B-LINUX-176 a changed collector is copied again`() = runTest {
        host().setUp(inApp)
        collectorText = "// collector v2\n"
        host().setUp(inApp)
        assertContentEquals(collectorText.encodeToByteArray(), files.files[InAppQuotaCollector.COLLECTOR_PATH])
    }

    @Test
    fun `B-LINUX-176 Node 19 and a missing Node stop before anything is copied`() = runTest {
        linux.nodeAnswer = "qc node=19"
        val old = assertIs<QuotaSetupResult.Stopped>(host().setUp(inApp))
        assertEquals(QuotaSetupFailure.NodeTooOld, old.failure)
        linux.nodeAnswer = "qc node="
        assertEquals(QuotaSetupFailure.NoNode, assertIs<QuotaSetupResult.Stopped>(host().setUp(inApp)).failure)
        assertTrue(linux.calls.none { it.startsWith("start") })
        assertTrue(secrets.values.isEmpty())
        assertTrue(files.writes.isEmpty())
        assertNull(enabled)
    }

    @Test
    fun `B-LINUX-176 Ubuntu not ready is its own failure`() = runTest {
        linux.installed = false
        assertEquals(QuotaSetupFailure.LinuxNotReady, assertIs<QuotaSetupResult.Stopped>(host().setUp(inApp)).failure)
    }

    @Test
    fun `B-LINUX-176 a helper that never answers fails with the log in Details`() = runTest {
        val stopped = assertIs<QuotaSetupResult.Stopped>(host(probeOk = false).setUp(inApp))
        assertEquals(QuotaSetupFailure.Start, stopped.failure)
        assertEquals("QUOTA_FIXTURE_LOG", stopped.detail)
        assertNull(enabled)
    }

    @Test
    fun `B-LINUX-176 the service script holds file paths only, OpenCode 2 first`() {
        val script = host().serviceScript()
        assertTrue("OCMN_QUOTA_READ_TOKEN_FILE=/root/.oc-builtin/quota/read.token" in script)
        assertTrue("OCMN_GLM_KEY_FILE=" in script)
        assertTrue(
            script.indexOf("/root/.oc-opencode2/data/opencode/auth.json") <
                script.indexOf("/root/.local/share/opencode/auth.json"),
        )
        assertFalse("Bearer" in script)
    }

    // ---- B-LINUX-177 running and turning off (amended: on demand)

    @Test
    fun `B-LINUX-177 a set-up helper that is not running comes back`() = runTest {
        host().setUp(inApp)
        linux.services = emptyList()
        linux.calls.clear()
        assertTrue(host().ensureRunning(inApp))
        assertTrue("start:quota" in linux.calls)
    }

    @Test
    fun `B-LINUX-177 a helper that runs is left alone`() = runTest {
        host().setUp(inApp)
        linux.calls.clear()
        assertTrue(host().ensureRunning(inApp))
        assertTrue(linux.calls.none { it.startsWith("start") })
    }

    @Test
    fun `B-LINUX-177 nothing starts when never set up, turned off or without the in-app Linux`() = runTest {
        assertFalse(host().ensureRunning(inApp))
        host().setUp(inApp)
        host().turnOff("builtin")
        linux.calls.clear()
        assertFalse(host().ensureRunning(inApp))
        assertTrue(linux.calls.none { it.startsWith("start") })
        // A runner without In-app Ubuntu, and a server that is not the app's own, never start it.
        host().setUp(inApp)
        linux.services = emptyList()
        linux.calls.clear()
        assertFalse(host(supported = false).ensureRunning(inApp))
        assertFalse(host().ensureRunning(UsageServer("pc", BackendKind.OpenCode2)))
        assertTrue(linux.calls.none { it.startsWith("start") })
    }

    @Test
    fun `B-LINUX-177 turning off stops it and removes files, token and switch`() = runTest {
        host().setUp(inApp)
        assertEquals(QuotaSetupResult.Done, host().turnOff("builtin"))
        val stop = linux.calls.indexOf("stop:quota")
        assertTrue(stop >= 0 && linux.calls.indexOf("rm") > stop)
        assertTrue(secrets.values.isEmpty())
        assertNull(enabled)
        assertNull(host().readToken("builtin"))
    }

    /** The demand over the real host: [ensure] is the host's own ensureRunning, [answers] = the service is up. */
    private fun demandIn(scope: CoroutineScope, enabled: Boolean = true, onStop: () -> Unit = {}): QuotaCollectorDemand {
        lateinit var demand: QuotaCollectorDemand
        val hosted = { host(demand = demand) }
        demand = QuotaCollectorDemand(
            scope = scope,
            enabled = enabled,
            ensure = { hosted().ensureRunning(it) },
            answers = { "quota" in linux.services },
            stop = {
                onStop()
                linux.stopService("quota")
            },
            idleForMs = 200,
            answerEveryMs = 10,
        )
        return demand
    }

    @Test
    fun `B-LINUX-177 setting up or starting the phone's OpenCode starts no collector until something reads`() = runTest {
        val demand = demandIn(backgroundScope)
        host(demand = demand).setUp(inApp)
        // Set-up proves it answers, then it stops once nothing reads.
        advanceTimeBy(201)
        runCurrent()
        assertFalse("quota" in linux.services)
        linux.calls.clear()
        // Nothing but a read starts it again.
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(linux.calls.none { it.startsWith("start") })
        assertFalse(demand.running)
    }

    @Test
    fun `B-LINUX-177 a read wakes it, reads keep it, a quiet spell stops it and the next read restarts it`() = runTest {
        var stops = 0
        val demand = demandIn(backgroundScope) { stops++ }
        host().setUp(inApp)
        linux.stopService("quota")
        linux.calls.clear()
        val token = host(demand = demand).tokenFor(inApp)
        assertEquals(stagedToken(), token)
        assertEquals(1, linux.calls.count { it == "start:quota" })
        assertTrue("quota" in linux.services)
        // Reads close together keep it: no second start, no stop between.
        advanceTimeBy(120)
        host(demand = demand).tokenFor(inApp)
        advanceTimeBy(120)
        runCurrent()
        assertEquals(1, linux.calls.count { it == "start:quota" })
        assertEquals(0, stops)
        // Quiet for longer than the idle time: stopped.
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, stops)
        assertFalse("quota" in linux.services)
        // The next read starts it again.
        host(demand = demand).tokenFor(inApp)
        assertEquals(2, linux.calls.count { it == "start:quota" })
        demand.cancel()
    }

    @Test
    fun `B-LINUX-177 where there is no in-app Linux the demand does nothing`() = runTest {
        var asked = 0
        val demand = QuotaCollectorDemand(
            scope = backgroundScope,
            enabled = false,
            ensure = {
                asked++
                true
            },
            answers = {
                asked++
                false
            },
            stop = { asked++ },
        )
        demand.use(inApp, "token")
        demand.touch()
        advanceTimeBy(10 * 60_000L)
        assertEquals(0, asked)
        assertFalse(demand.running)
    }

    @Test
    fun `B-LINUX-177 turning off cancels the pending stop and a turned-off collector is not woken`() = runTest {
        var stops = 0
        val demand = demandIn(backgroundScope) { stops++ }
        host(demand = demand).setUp(inApp)
        assertTrue(demand.running)
        host(demand = demand).turnOff("builtin")
        assertFalse(demand.running)
        linux.calls.clear()
        assertNull(host(demand = demand).tokenFor(inApp))
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(linux.calls.none { it.startsWith("start") })
        assertEquals(0, stops)
    }
}
