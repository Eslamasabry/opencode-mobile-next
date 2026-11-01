package io.github.eslamasabry.oc.android.agenthost

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.WireTag
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant

/** The Outcome surface over the kept host (plan 17 §1.2): codes, tickets, health and what never leaks. */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAgentHostTest {
    private val dir = Files.createTempDirectory("android-agent-host-").toFile().canonicalFile
    private val ports = FakeRuntimePorts(dir).also { File(dir, "linux/ubuntu/home/oc").mkdirs() }
    private val processes = ports.processes()

    @After fun cleanUp() {
        ports.serviceMap.values.forEach { it.destroyForcibly(); it.waitFor(2, TimeUnit.SECONDS) }
        dir.deleteRecursively()
    }

    private fun TestScope.host(answers: (Int) -> Boolean = { true }): AndroidAgentHost {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : AppDispatchers {
            override val main: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override fun serial(name: String): CoroutineDispatcher = dispatcher
        }
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(currentTime)
        }
        return AndroidAgentHost(processes, dispatchers, clock) { answers(it) }
    }

    private fun request(version: String = "0.11.2", restart: Boolean = false, idle: Long? = null) = HelperStartRequest(
        password = HelperPassword("a".repeat(64)), config = """{"version":1}""", expectedHelperVersion = version,
        restartIfVersionChanged = restart, idleResumeGeneration = idle)

    private fun paseo(version: String) {
        ports.guestScript = { command ->
            if (command.last() == "--version") "printf '%s' '$version'" else "read password; exec sleep 30"
        }
    }

    @Test fun `B-LINUX-129 a helper start answers only after its health check and reports narrow facts`() = runTest {
        paseo("0.11.2")
        var asked = 0
        val host = host { asked++; asked >= 3 }
        val running = host.ensureRunning("qa", request())
        val value = (running as Outcome.Ok).value
        assertEquals(HelperRunning(4099, "0.11.2", "x86_64", startedFresh = true), value)
        assertEquals(3, asked)
        assertEquals(1000L, currentTime) // two 500 ms pauses
        assertEquals(HelperStatus(true, "x86_64", "0.11.2", "qa"), host.status.value)
        assertTrue(File(dir, "linux/ubuntu/home/oc/.oc-profiles/qa/paseo/config.json").isFile)
        assertTrue(host.stop("qa", guardedForUpdate = false) is Outcome.Ok)
        assertEquals(HelperStatus(false, "x86_64", null, null), host.status.value)
    }

    @Test fun `B-LINUX-129 a helper that never answers its health check fails with the exited code after 30 s`() = runTest {
        paseo("0.11.2")
        val failed = host { false }.ensureRunning("qa", request()) as Outcome.Failed
        assertEquals(AgentHostCode.AgentHostExited, AgentHostCode.of(failed.failure))
        assertEquals(30_000L, currentTime)
    }

    @Test fun `B-LINUX-131 an older helper without restart permission names a helper update`() = runTest {
        paseo("0.9.2")
        val host = host()
        assertTrue(host.ensureRunning("qa", request("0.9.2")) is Outcome.Ok)
        paseo("0.11.2")
        val failed = host.ensureRunning("qa", request("0.11.2")) as Outcome.Failed
        assertEquals(Failure.Busy(BusyKind.Starting, WireTag("helper_update_required"), failed.failure.details), failed.failure)
        assertEquals(AgentHostCode.HelperUpdateRequired, AgentHostCode.of(failed.failure))
        assertTrue(host.status.value.running)
        val updated = host.ensureRunning("qa", request("0.11.2", restart = true)) as Outcome.Ok
        assertEquals("0.11.2", updated.value.helperVersion)
    }

    @Test fun `B-LINUX-137 a stale idle resume or a paused screen is refused before anything starts`() = runTest {
        paseo("0.11.2")
        val host = host()
        val stale = host.ensureRunning("qa", request(idle = 7)) as Outcome.Failed
        assertEquals(Failure.Runtime(RuntimeProblem.IdleResumeStale, stale.failure.details), stale.failure)
        ports.state = ports.state.copy(activityResumed = false)
        val paused = host.ensureRunning("qa", request()) as Outcome.Failed
        assertTrue(paused.failure is Failure.Runtime)
        assertTrue(ports.launched.isEmpty())
    }

    @Test fun `B-LINUX-129 another owner's running helper refuses a start with the unowned code`() = runTest {
        paseo("0.11.2")
        val host = host()
        assertTrue(host.ensureRunning("qa", request()) is Outcome.Ok)
        val failed = host.ensureRunning("other", request()) as Outcome.Failed
        assertEquals(Failure.Conflict(WireTag("agent_host_unowned"), details = failed.failure.details), failed.failure)
        val guarded = host.stop("other", guardedForUpdate = true) as Outcome.Failed
        assertEquals(AgentHostCode.AgentHostUnowned, AgentHostCode.of(guarded.failure))
    }

    @Test fun `B-LINUX-129 failures carry only the exception class under Details, never its text`() {
        val secret = "sk-private-provider-output"
        val failures = listOf(
            AndroidAgentHost.classify(IllegalStateException(secret)),
            AndroidAgentHost.classify(PhoneAgentHost.Failure("agent_host_busy", secret)),
            AndroidAgentHost.classify(IllegalStateException("There is not enough free space on this phone. $secret")),
        )
        assertEquals(AgentHostCode.AgentUnavailable, AgentHostCode.of(failures[0]))
        assertEquals(AgentHostCode.AgentHostBusy, AgentHostCode.of(failures[1]))
        assertEquals(RuntimeProblem.StorageUnavailable, (failures[2] as Failure.Runtime).problem)
        for (failure in failures) {
            assertFalse(failure.toString().contains(secret))
            assertFalse(failure.details?.text.orEmpty().contains(secret))
        }
        for (code in AgentHostCode.entries) assertEquals(code, AgentHostCode.of(code.failure()))
    }

    @Test fun `B-LINUX-130 the auth answer is the narrow projection and the label never prints`() = runTest {
        ports.authAnswer = mapOf("state" to "signedIn", "accountDisplayName" to "Synthetic Account")
        val answer = (host().auth("qa", "claude", AuthRequestAction.Probe, "script") as Outcome.Ok).value
        assertEquals("signedIn", answer.state)
        assertEquals("Synthetic Account", answer.account?.value)
        assertFalse(answer.toString().contains("Synthetic"))
        assertEquals(65, ports.authRuns.single()["timeoutSeconds"])
        assertEquals("probe", ports.authRuns.single()["action"])
        ports.authAnswer = mapOf("state" to "error", "error" to "timedOut")
        val timedOut = (host().auth("qa", "codex", AuthRequestAction.SignOut, "script") as Outcome.Ok).value
        assertEquals(AuthAnswer("error", null, "timedOut"), timedOut)
        assertEquals(20, ports.authRuns.last()["timeoutSeconds"])
    }

    @Test fun `B-LINUX-125 the sign-in terminal command comes back with its open-url file`() = runTest {
        val command = (host().signInCommand("qa", listOf("kilo", "auth", "login")) as Outcome.Ok).value
        assertEquals(listOf("kilo", "auth", "login"), command.argv.takeLast(3))
        assertEquals(".oc-open-url", command.openUrlFile.name)
        val refused = host().signInCommand("qa", listOf("bash")) as Outcome.Failed
        assertEquals(AgentHostCode.AgentUnavailable, AgentHostCode.of(refused.failure))
    }

    @Test fun `B-LINUX-117 the password and request never print`() {
        val request = request()
        assertFalse(request.toString().contains("aaaa"))
        assertFalse(request.password.toString().contains("aaaa"))
    }

    @Test fun `B-STATE-056 deleting the agent home drains first and keeps everything when the drain fails`() = runTest {
        paseo("0.11.2")
        val host = host()
        assertTrue(host.ensureRunning("qa", request()) is Outcome.Ok)
        val home = File(dir, "linux/ubuntu/home/oc/.oc-profiles/qa")
        ports.forgetCommits = false
        assertTrue(host.deleteHome("qa") is Outcome.Failed)
        ports.forgetCommits = true
        assertTrue(host.deleteHome("qa") is Outcome.Ok)
        assertFalse(home.exists())
        assertFalse(host.status.value.running)
        assertTrue(host.ensureRunning("qa", request()) is Outcome.Failed)
    }
}
