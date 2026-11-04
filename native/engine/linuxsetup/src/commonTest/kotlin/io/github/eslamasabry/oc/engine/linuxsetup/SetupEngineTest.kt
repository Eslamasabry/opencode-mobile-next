package io.github.eslamasabry.oc.engine.linuxsetup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports of setup_engine_test.dart (trace, start errors), setup_inventory_test.dart and setup_termux_host_test.dart. */
class SetupEngineTest {
    private val host = FakeSetupHost()
    private val tools = SetupToolsChanged()
    private val trace = RecordingTrace()

    private val inventoryComponents =
        listOf(
            SetupComponent(id = "python", checkScript = "python3 --version"),
            SetupComponent(id = "aiteam", checkScript = "test -f /opt/ai-team/installed"),
            SetupComponent(id = "opencode", checkScript = "opencode --version", required = true),
        )

    private fun TestScope.inventoryEngine() =
        SetupEngine(host, FixedClock(), backgroundScope, tools, components = { inventoryComponents })

    private fun TestScope.engine(
        kind: SetupHostKind = SetupHostKind.Builtin,
        components: (Map<String, Map<String, String>>) -> List<SetupComponent> = { setupComponentRegistry(params = it) },
        log: (() -> String) -> Unit = {},
    ) = SetupEngine(host, FixedClock(), backgroundScope, tools, kind, components, trace, log)

    // ---- B-LINUX-206 / B-LINUX-207: the shared inventory -----------------------------------------------------------

    @Test
    fun `B-LINUX-206 concurrent inventory consumers share one Linux check`() = runTest {
        val engine = inventoryEngine()
        host.holdFirstProbe = CompletableDeferred()
        val reads = List(3) { async { engine.installedOptional() } }
        runCurrent()
        assertEquals(1, host.scripts.size)
        host.holdFirstProbe!!.complete(Unit)
        for (inventory in reads.awaitAll()) assertEquals(SetupInventory.Known(setOf("python", "aiteam")), inventory)
    }

    @Test
    fun `B-LINUX-206 a later inventory read checks the phone again`() = runTest {
        val engine = inventoryEngine()
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
        host.tools = setOf("python")
        assertEquals(SetupInventory.Known(setOf("python")), engine.installedOptional())
        assertEquals(2, host.scripts.size)
    }

    @Test
    fun `B-LINUX-206 removal notification refreshes every inventory consumer`() = runTest {
        val engine = inventoryEngine()
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
        host.tools = setOf("python")
        tools.bump()
        val reads = List(3) { async { engine.installedOptional() } }.awaitAll()
        for (inventory in reads) assertEquals(SetupInventory.Known(setOf("python")), inventory)
        assertEquals(2, host.scripts.size)
    }

    @Test
    fun `B-LINUX-206 a tools change discards an earlier in-flight inventory`() = runTest {
        val engine = inventoryEngine()
        host.tools = setOf("python")
        host.holdFirstProbe = CompletableDeferred()
        val beforeInstall = async { engine.installedOptional() }
        runCurrent()
        assertEquals(1, host.scripts.size)
        host.tools = setOf("python", "aiteam")
        tools.bump()
        val afterInstall = async { engine.installedOptional() }
        runCurrent()
        host.holdFirstProbe!!.complete(Unit)
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), beforeInstall.await())
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), afterInstall.await())
        assertEquals(2, host.scripts.size)
    }

    @Test
    fun `B-LINUX-207 a busy Linux probe is unknown, not an empty inventory`() = runTest {
        val engine = inventoryEngine()
        host.runRefusal = HostReply.Refused("Another phone task is running. Wait a moment and try again.")
        assertEquals(SetupInventory.Unknown, engine.installedOptional())
        host.runRefusal = null
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
        assertEquals(2, host.scripts.size)
    }

    @Test
    fun `B-LINUX-207 a failed refresh cannot report prior tools as removed`() = runTest {
        val engine = inventoryEngine()
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
        tools.bump()
        host.runRefusal = HostReply.Refused("The check could not finish.")
        assertEquals(SetupInventory.Unknown, engine.installedOptional())
        host.runRefusal = null
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
    }

    @Test
    fun `B-LINUX-207 an incomplete check is unknown, not absence`() = runTest {
        for (result in listOf(
            SetupRunResult(-1, ""),
            SetupRunResult(0, "::oc-check-begin python\n1.0\n::oc-check-end python 0\n"),
        )) {
            val engine = inventoryEngine()
            host.runResult = result
            assertEquals(SetupInventory.Unknown, engine.installedOptional())
            host.runResult = null
            assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
        }
    }

    @Test
    fun `B-LINUX-207 a missing Linux rootfs is an authoritative empty inventory`() = runTest {
        val engine = inventoryEngine()
        host.installed = false
        assertEquals(SetupInventory.Known(emptySet()), engine.installedOptional())
        assertTrue(host.scripts.isEmpty())
    }

    @Test
    fun `B-LINUX-207 a runtime switch job does not erase Python or AI Team`() = runTest {
        val engine = inventoryEngine()
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
        host.job = """{"jobId":"switch-to-older","state":"running","current":"opencode","order":["opencode"],
            "components":{"opencode":{"state":"running"}}}"""
        engine.restore()
        assertEquals(SetupState.Running, engine.progress.value.state)
        assertEquals(listOf("opencode"), engine.progress.value.components.map { it.id })
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
        host.job = """{"jobId":"switch-to-older","state":"done","current":"opencode","order":["opencode"],
            "components":{"opencode":{"state":"done"}}}"""
        engine.restore()
        assertEquals(SetupInventory.Known(setOf("python", "aiteam")), engine.installedOptional())
    }

    // ---- B-LINUX-204: trace ---------------------------------------------------------------------------------------

    @Test
    fun `B-LINUX-204 each finished component is a span timed by the runner's clock, recorded once`() = runTest {
        val engine = engine()
        host.job = RECORDED_INTERRUPTED
        engine.restore()
        engine.restore()
        val components = trace.spans.filter { it.name == "setup.component" }
        assertEquals(
            mapOf("linux" to 8451L, "essentials" to 28963L, "python" to 16683L),
            components.associate { it.attrs.getValue("id") to it.millis },
        )
        val job = trace.spans.single { it.name == "setup.job" }
        assertEquals(55783L, job.millis)
        assertEquals("interrupted", job.attrs["state"])
        assertTrue(job.failed)
    }

    @Test
    fun `B-LINUX-204 the checks are one span`() = runTest {
        val engine = engine()
        engine.run(setOf("python"))
        val check = trace.spans.single { it.name == "setup.check" }
        assertEquals("6", check.attrs["components"])
        assertEquals(1, host.started.size)
    }

    // ---- B-SEC-166: start diagnostics -----------------------------------------------------------------------------

    @Test
    fun `B-SEC-166 unexpected setup start diagnostics redact credentials`() = runTest {
        val printed = mutableListOf<String>()
        val engine =
            engine(
                components = { error("setup failed password=synthetic-private-value") },
                log = { printed.add(it()) },
            )
        assertNull(engine.run(emptySet()))
        assertTrue(printed.joinToString("\n").contains("setup: start failed"))
        assertFalse(printed.joinToString("\n").contains("synthetic-private-value"))
        assertEquals(SetupState.Failed, engine.progress.value.state)
        val error = assertIs<SetupProblem.StartFailed>(engine.progress.value.error)
        assertFalse(error.details.contains("synthetic-private-value"))
        assertTrue(host.started.isEmpty())
    }

    // ---- B-SEC-170: Termux params -------------------------------------------------------------------------------

    @Test
    fun `B-SEC-170 unsafe or unknown raw setup params are rejected by the validator`() {
        for (params in listOf(
            mapOf("opencode" to mapOf("password" to "fake-value")),
            mapOf("opencode" to mapOf("runtime" to "unsupported")),
            mapOf("opencode" to mapOf("version" to "1.2.3; echo injected")),
            mapOf("_job" to mapOf("adding" to "token=fake-value")),
        )) {
            assertFalse(TermuxSetupParams.isValid(params), params.toString())
        }
    }

    @Test
    fun `B-SEC-170 legacy runtime and version requests normalize to supported OC2`() {
        val params = effectiveJobParams(mapOf("opencode" to mapOf("runtime" to "opencode1", "version" to "1.19.0")), null)
        assertEquals(mapOf("runtime" to "opencode2"), params["opencode"])
        assertTrue(TermuxSetupParams.isValid(params))
    }

    @Test
    fun `B-SEC-170 unsafe effective setup params never generate install scripts`() = runTest {
        var generated = false
        val engine =
            engine(
                kind = SetupHostKind.Termux,
                components = {
                    generated = true
                    emptyList()
                },
            )
        for (params in listOf(
            mapOf("opencode" to mapOf("password" to "fake-value")),
            mapOf("opencode" to mapOf("version" to "2.2.5; echo injected")),
            mapOf("_job" to mapOf("adding" to "token=fake-value")),
        )) {
            assertEquals(SetupRunRefusal(TermuxSetupParams.INVALID_CODE), engine.run(emptySet(), params))
        }
        assertFalse(generated)
        assertTrue(host.statusReads.all { it == "setupStatus" })
        assertTrue(host.started.isEmpty())
    }
}
