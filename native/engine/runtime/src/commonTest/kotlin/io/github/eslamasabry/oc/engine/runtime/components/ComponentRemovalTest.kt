package io.github.eslamasabry.oc.engine.runtime.components

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Port of test/setup_component_removal_test.dart (the removal engine over fake Linux, setup status and team ports). */
@OptIn(ExperimentalCoroutinesApi::class)
class ComponentRemovalTest {
    private fun component(
        id: String,
        dependsOn: List<String> = emptyList(),
        required: Boolean = false,
        removable: Boolean = true,
        hasProbe: Boolean = true,
    ) = RemovableComponent(
        id = id,
        checkScript = "version:$id",
        presenceScript = if (hasProbe) "presence:$id" else null,
        removeScript = if (removable) "remove:$id" else null,
        dependsOn = dependsOn,
        required = required,
    )

    /** The fake Linux: presence probes answer from [probes]; a remove script flips its probe to absent. */
    private class FakeLinux : LinuxShell, SetupStatusReader {
        val calls = mutableListOf<String>()
        val probes = mutableMapOf<String, Int>()
        val removals = mutableMapOf<String, String>()
        val throwingProbes = mutableSetOf<String>()
        var installed = true
        var statusFails = false
        var setupFails = false
        var setup: String? = null
        var removalExit = 0
        var removalError: String? = null
        var removalFailsAsOutcome = false
        var leaveInstalled = false
        var removalGate: CompletableDeferred<Unit>? = null
        val removalStarted = CompletableDeferred<Unit>()

        fun register(registry: List<RemovableComponent>) {
            for (component in registry) {
                val probe = component.presenceScript ?: continue
                probes[probe] = 0
                component.removeScript?.let { removals[it] = probe }
            }
        }

        override suspend fun linuxState(): Outcome<LinuxInstallState> {
            if (statusFails) throw IllegalStateException("Inventory unavailable")
            return LinuxInstallState(installed = installed).ok()
        }

        override suspend fun setupRecord(): Outcome<String?> {
            if (setupFails) return Failure.Runtime(RuntimeProblem.StorageUnavailable).failed()
            return setup.ok()
        }

        override suspend fun run(script: String, timeout: Duration): Outcome<ShellResult> {
            calls += script
            if (script in throwingProbes) throw IllegalStateException("Presence unavailable")
            probes[script]?.let { return ShellResult(it).ok() }
            val probe = removals[script]
            if (probe != null) {
                removalError?.let { throw IllegalStateException(it) }
                if (removalFailsAsOutcome) return Failure.Runtime(RuntimeProblem.Generic).failed()
                if (!removalStarted.isCompleted) removalStarted.complete(Unit)
                removalGate?.await()
                if (removalExit == 0 && !leaveInstalled) probes[probe] = 1
                return ShellResult(removalExit).ok()
            }
            throw IllegalStateException("Unexpected script")
        }
    }

    private class FakeTeam(val linux: FakeLinux, val probe: String?) : TeamRemover {
        var removeCalls = 0

        override suspend fun remove(): Outcome<Unit> {
            removeCalls++
            probe?.let { linux.probes[it] = 1 }
            return Unit.ok()
        }
    }

    private lateinit var linux: FakeLinux
    private lateinit var team: FakeTeam
    private lateinit var service: ComponentRemoval
    private var changed = 0

    private fun configure(registry: List<RemovableComponent>) {
        linux = FakeLinux().apply { register(registry) }
        team = FakeTeam(linux, registry.firstOrNull { it.id == ComponentIds.AITEAM }?.presenceScript)
        service = ComponentRemoval(linux, linux, registry, team) { changed++ }
    }

    private fun blocked(code: ComponentRemovalBlock) = RemovalResult.Blocked(code)

    private fun removalCalls() = linux.calls.filter { it in linux.removals }

    // ---- B-LINUX-183

    @Test
    fun `B-LINUX-183 Python removal executes its registered remove script`() = runTest {
        val registry = RemovableComponents.builtin
        configure(registry)
        val python = registry.single { it.id == ComponentIds.PYTHON }
        val before = changed

        assertEquals(RemovalResult.Removed, service.remove("python"))

        assertEquals(1, linux.calls.count { it == python.removeScript })
        assertEquals(1, linux.probes[python.presenceScript])
        assertEquals(0, team.removeCalls)
        assertFalse(service.busy.value)
        assertEquals(before + 1, changed)
    }

    @Test
    fun `B-LINUX-183 AI Team removal uses its lifecycle API`() = runTest {
        val registry = RemovableComponents.builtin
        configure(registry)
        val component = registry.single { it.id == ComponentIds.AITEAM }
        val before = changed

        assertEquals(RemovalResult.Removed, service.remove("aiteam"))

        assertEquals(1, team.removeCalls)
        assertFalse(component.removeScript in linux.calls)
        assertEquals(1, linux.probes[component.presenceScript])
        assertEquals(before + 1, changed)
    }

    @Test
    fun `B-LINUX-183 AI Team refuses a substituted unrecognized remove script`() = runTest {
        configure(listOf(component("aiteam")))
        assertEquals(blocked(ComponentRemovalBlock.Unsupported), service.remove("aiteam"))
        assertEquals(0, team.removeCalls)
        assertFalse("remove:aiteam" in linux.calls)
    }

    @Test
    fun `B-LINUX-183 an unknown component id cannot become a shell command`() = runTest {
        configure(listOf(component("tool")))
        assertEquals(blocked(ComponentRemovalBlock.Unsupported), service.remove("unknown; touch /tmp/not-a-command"))
        assertTrue(removalCalls().isEmpty())
        assertTrue(linux.calls.none { it.contains("not-a-command") })
    }

    @Test
    fun `B-LINUX-183 the registry's AI Team remove script is the team scripts' verbatim one`() {
        val aiteam = RemovableComponents.builtin.single { it.id == ComponentIds.AITEAM }
        assertEquals(AiTeamRemovalScripts.REMOVE_SCRIPT, aiteam.removeScript)
        assertEquals(
            "set -eu\nrm -f /usr/local/bin/gc /usr/local/bin/bd /usr/local/bin/dolt\n" +
                "rm -rf /opt/aiteam /var/cache/oc-setup/aiteam /root/aiteam /root/.gc\n",
            aiteam.removeScript,
        )
    }

    // ---- B-LINUX-184

    @Test
    fun `B-LINUX-184 inventory uses presence probes rather than pinned version checks`() = runTest {
        configure(listOf(component("tool")))
        val entry = service.inventory().single()
        assertEquals(ComponentPresence.Installed, entry.presence)
        assertTrue(entry.canRemove)
        assertFalse("version:tool" in linux.calls)
    }

    @Test
    fun `B-LINUX-184 a component without remove script is unavailable for removal`() = runTest {
        configure(listOf(component("tool", removable = false)))
        assertEquals(blocked(ComponentRemovalBlock.Unsupported), service.remove("tool"))
    }

    @Test
    fun `B-LINUX-184 an absent component cannot run its remove script`() = runTest {
        configure(listOf(component("tool")))
        linux.probes["presence:tool"] = 1
        assertEquals(ComponentPresence.Absent, service.inventory().single().presence)
        assertEquals(blocked(ComponentRemovalBlock.NotInstalled), service.remove("tool"))
        assertFalse("remove:tool" in linux.calls)
    }

    @Test
    fun `B-LINUX-184 presence exits other than 0 and 1 are unknown and fail closed`() = runTest {
        for (code in listOf(-1, 2, 127)) {
            configure(listOf(component("tool")))
            linux.probes["presence:tool"] = code
            assertEquals(ComponentPresence.Unknown, service.inventory().single().presence, "exit $code")
            assertEquals(blocked(ComponentRemovalBlock.InventoryUnavailable), service.remove("tool"), "exit $code")
            assertFalse("remove:tool" in linux.calls, "exit $code")
        }
    }

    @Test
    fun `B-LINUX-184 a missing presence probe does not treat a healthy version as proof`() = runTest {
        configure(listOf(component("tool", hasProbe = false)))
        assertEquals(ComponentPresence.Unknown, service.inventory().single().presence)
        assertEquals(blocked(ComponentRemovalBlock.InventoryUnavailable), service.remove("tool"))
        assertTrue(linux.calls.isEmpty())
    }

    // ---- B-LINUX-185

    @Test
    fun `B-LINUX-185 Node removal reports OpenCode and transitive AI Team dependents`() = runTest {
        configure(RemovableComponents.builtin)
        val entries = service.inventory()
        val node = entries.single { it.component.id == ComponentIds.NODE }

        assertEquals(ComponentPresence.Installed, node.presence)
        assertTrue(node.blockingDependents.containsAll(listOf("opencode", "aiteam")))
        assertFalse(node.canRemove)
        assertFalse("start" in entries.map { it.component.id })
        val before = changed
        assertEquals(blocked(ComponentRemovalBlock.DependentInstalled), service.remove("node"))
        assertTrue(removalCalls().isEmpty())
        assertEquals(before, changed)
    }

    @Test
    fun `B-LINUX-185 a required component cannot be removed without dependents`() = runTest {
        configure(listOf(component("base", required = true)))
        assertEquals(blocked(ComponentRemovalBlock.RequiredComponent), service.remove("base"))
        assertTrue(removalCalls().isEmpty())
    }

    @Test
    fun `B-LINUX-185 an unknown dependent blocks removal with its identity`() = runTest {
        configure(listOf(component("base"), component("child", dependsOn = listOf("base"))))
        linux.probes["presence:child"] = 2
        val base = service.inventory().first()
        assertTrue("child" in base.blockingDependents)
        assertEquals(blocked(ComponentRemovalBlock.DependencyUnknown), service.remove("base"))
        assertFalse("remove:base" in linux.calls)
    }

    @Test
    fun `B-LINUX-185 removal refreshes dependencies after the confirmation inventory`() = runTest {
        configure(listOf(component("base"), component("child", dependsOn = listOf("base"))))
        linux.probes["presence:child"] = 1
        assertTrue(service.inventory().first().canRemove)

        linux.probes["presence:child"] = 0

        assertEquals(blocked(ComponentRemovalBlock.DependentInstalled), service.remove("base"))
        assertFalse("remove:base" in linux.calls)
    }

    // ---- B-LINUX-186

    @Test
    fun `B-LINUX-186 a failed native inventory never runs a remove script`() = runTest {
        configure(listOf(component("tool")))
        linux.statusFails = true
        assertEquals(blocked(ComponentRemovalBlock.InventoryUnavailable), service.remove("tool"))
        assertTrue(removalCalls().isEmpty())
        assertFalse(service.busy.value)
    }

    @Test
    fun `B-LINUX-186 a failed presence probe never runs a remove script`() = runTest {
        configure(listOf(component("tool")))
        linux.throwingProbes += "presence:tool"
        assertEquals(blocked(ComponentRemovalBlock.InventoryUnavailable), service.remove("tool"))
        assertFalse("remove:tool" in linux.calls)
    }

    @Test
    fun `B-LINUX-186 a running setup blocks component removal`() = runTest {
        configure(listOf(component("tool")))
        linux.setup = """{"state":"running"}"""
        val before = changed
        assertEquals(blocked(ComponentRemovalBlock.SetupRunning), service.remove("tool"))
        assertFalse("remove:tool" in linux.calls)
        assertFalse(service.busy.value)
        assertEquals(before, changed)
    }

    @Test
    fun `B-LINUX-186 unavailable setup status fails closed`() = runTest {
        configure(listOf(component("tool")))
        linux.setupFails = true
        assertEquals(blocked(ComponentRemovalBlock.InventoryUnavailable), service.remove("tool"))
        assertFalse("remove:tool" in linux.calls)
    }

    @Test
    fun `B-LINUX-186 an unrecognized setup record fails closed`() = runTest {
        for (record in listOf("invalid", "{}", """{"state":"unrecognized"}""", """{"state":3}""", "[]")) {
            configure(listOf(component("tool")))
            linux.setup = record
            assertEquals(blocked(ComponentRemovalBlock.InventoryUnavailable), service.remove("tool"), record)
            assertFalse("remove:tool" in linux.calls, record)
        }
    }

    @Test
    fun `B-LINUX-186 settled setup records and no record at all allow removal`() = runTest {
        for (record in listOf(null, """{"state":"idle"}""", """{"state":"done"}""", """{"state":"failed"}""",
            """{"state":"interrupted"}""", """{"state":"cancelled"}""")) {
            configure(listOf(component("tool")))
            linux.setup = record
            assertEquals(RemovalResult.Removed, service.remove("tool"), record)
        }
    }

    @Test
    fun `B-LINUX-186 an absent native runtime marks components absent without scripts`() = runTest {
        configure(RemovableComponents.builtin)
        linux.installed = false

        val entries = service.inventory()

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.presence == ComponentPresence.Absent })
        assertEquals(blocked(ComponentRemovalBlock.NotInstalled), service.remove("python"))
        assertTrue(linux.calls.isEmpty())
    }

    // ---- B-LINUX-187

    @Test
    fun `B-LINUX-187 concurrent removal is blocked and busy resets after completion`() = runTest {
        configure(listOf(component("tool")))
        linux.removalGate = CompletableDeferred()
        val busy = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.busy.collect { busy += it } }
        val before = changed
        val first = async { service.remove("tool") }
        linux.removalStarted.await()

        assertTrue(service.busy.value)
        assertEquals(blocked(ComponentRemovalBlock.Busy), service.remove("tool"))
        assertEquals(before, changed, "removal is still running")
        linux.removalGate!!.complete(Unit)
        assertEquals(RemovalResult.Removed, first.await())
        runCurrent()

        assertFalse(service.busy.value)
        assertEquals(listOf(false, true, false), busy)
        assertEquals(before + 1, changed)
        assertEquals(1, linux.calls.count { it == "remove:tool" })
    }

    @Test
    fun `B-LINUX-187 a nonzero remove script result is failure and resets busy`() = runTest {
        configure(listOf(component("tool")))
        linux.removalExit = 1
        val before = changed
        assertEquals(blocked(ComponentRemovalBlock.Failed), service.remove("tool"))
        assertEquals(0, linux.probes["presence:tool"])
        assertFalse(service.busy.value)
        assertEquals(before + 1, changed)
    }

    @Test
    fun `B-LINUX-187 thrown script errors expose only a stable failure reason`() = runTest {
        configure(listOf(component("tool")))
        linux.removalError = "Authorization: Bearer fake-component-removal-token"
        val before = changed

        val result = service.remove("tool")

        assertEquals(blocked(ComponentRemovalBlock.Failed), result)
        assertFalse(result.toString().contains("fake-component-removal-token"))
        assertFalse(result.toString().contains("Authorization"))
        val outcome = result.toOutcome() as Outcome.Failed
        assertEquals(Failure.Runtime(RuntimeProblem.RemovalFailed), outcome.failure)
        assertFalse(outcome.toString().contains("fake-component-removal-token"))
        assertFalse(service.busy.value)
        assertEquals(before + 1, changed)
    }

    @Test
    fun `B-LINUX-187 a failed script launch is failure too`() = runTest {
        configure(listOf(component("tool")))
        linux.removalFailsAsOutcome = true
        assertEquals(blocked(ComponentRemovalBlock.Failed), service.remove("tool"))
        assertFalse(service.busy.value)
    }

    @Test
    fun `B-LINUX-187 a successful script with surviving files is still failure`() = runTest {
        configure(listOf(component("tool")))
        linux.leaveInstalled = true
        assertEquals(blocked(ComponentRemovalBlock.Failed), service.remove("tool"))
        assertEquals(0, linux.probes["presence:tool"])
        assertFalse(service.busy.value)
    }

    @Test
    fun `B-LINUX-187 partial removal invalidates inventory after releasing busy`() = runTest {
        val registry = RemovableComponents.builtin
        configure(registry)
        val component = registry.single { it.id == ComponentIds.AITEAM }
        val busyWhenChanged = mutableListOf<Boolean>()
        lateinit var partial: ComponentRemoval
        partial =
            ComponentRemoval(
                linux,
                linux,
                registry,
                team = {
                    linux.probes[component.presenceScript!!] = 1
                    throw IllegalStateException("Removal failed after deleting files")
                },
                toolsChanged = { busyWhenChanged += partial.busy.value },
            )

        assertEquals(blocked(ComponentRemovalBlock.Failed), partial.remove("aiteam"))

        assertEquals(listOf(false), busyWhenChanged)
        val entry = partial.inventory().single { it.component.id == ComponentIds.AITEAM }
        assertEquals(ComponentPresence.Absent, entry.presence)
    }

    @Test
    fun `B-LINUX-187 a team lifecycle that reports failure is failure`() = runTest {
        val registry = RemovableComponents.builtin
        configure(registry)
        val failing =
            ComponentRemoval(linux, linux, registry, team = { Failure.Runtime(RuntimeProblem.RemovalFailed).failed() })
        assertEquals(blocked(ComponentRemovalBlock.Failed), failing.remove("aiteam"))
    }

    @Test
    fun `B-LINUX-183 without a team lifecycle API AI Team is not offered`()= runTest {
        val registry = RemovableComponents.builtin
        configure(registry)
        val noTeam = ComponentRemoval(linux, linux, registry)
        assertEquals(
            ComponentRemovalBlock.Unsupported,
            noTeam.inventory().single { it.component.id == ComponentIds.AITEAM }.block,
        )
    }

    // ---- B-LINUX-188

    private class SizedLinux(val outputs: MutableMap<String, String>) : LinuxShell, SetupStatusReader {
        val ran = mutableListOf<String>()
        var exit = 0

        override suspend fun run(script: String, timeout: Duration): Outcome<ShellResult> {
            ran += script
            return ShellResult(exit, outputs[script] ?: "").ok()
        }

        override suspend fun linuxState(): Outcome<LinuxInstallState> = LinuxInstallState(installed = true).ok()

        override suspend fun setupRecord(): Outcome<String?> = null.ok()
    }

    @Test
    fun `B-LINUX-188 freedBytes - a number is bytes, nothing or zero is no figure`() = runTest {
        val registry =
            listOf(
                RemovableComponent(id = "python", presenceScript = "presence:python", removeScript = "remove:python",
                    sizeScript = "size:python"),
                RemovableComponent(id = "bare", presenceScript = "presence:bare", removeScript = "remove:bare"),
            )
        val fake = SizedLinux(mutableMapOf("size:python" to "12\n25600\n"))
        val removal = ComponentRemoval(fake, fake, registry)
        assertEquals(25600L * 1024, removal.freedBytes("python"))
        assertNull(removal.freedBytes("bare"))
        assertNull(removal.freedBytes("missing"))
        fake.outputs["size:python"] = "0\n"
        assertNull(removal.freedBytes("python"))
        fake.outputs["size:python"] = "not a number\n"
        assertNull(removal.freedBytes("python"))
        fake.exit = 1
        fake.outputs["size:python"] = "25600\n"
        assertNull(removal.freedBytes("python"))
    }

    @Test
    fun `B-LINUX-188 freedBytes runs the registry's own size script`() = runTest {
        val fake = SizedLinux(mutableMapOf(AiTeamRemovalScripts.SIZE_SCRIPT to "2048\n"))
        val removal = ComponentRemoval(fake, fake, RemovableComponents.builtin)
        assertEquals(2048L * 1024, removal.freedBytes(ComponentIds.AITEAM))
        assertEquals(listOf(AiTeamRemovalScripts.SIZE_SCRIPT), fake.ran)
        assertNull(removal.freedBytes(ComponentIds.NODE), "Node has no size script")
    }
}
