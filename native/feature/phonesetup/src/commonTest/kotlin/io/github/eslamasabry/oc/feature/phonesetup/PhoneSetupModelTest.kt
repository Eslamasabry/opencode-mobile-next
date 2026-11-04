package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.engine.linuxsetup.HostReply
import io.github.eslamasabry.oc.engine.linuxsetup.SetupDevice
import io.github.eslamasabry.oc.engine.linuxsetup.SetupEngine
import io.github.eslamasabry.oc.engine.linuxsetup.SetupLinuxHost
import io.github.eslamasabry.oc.engine.linuxsetup.SetupLinuxStatus
import io.github.eslamasabry.oc.engine.linuxsetup.SetupNotificationTexts
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.linuxsetup.SetupRunResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupRunnerSpec
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupToolsChanged
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupDevice
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupJobs
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupModel
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** The Set up page over the real SetupEngine and a scripted setup.json (phone_setup_*_screen.dart rules). */
class PhoneSetupModelTest {
    private class FixedClock(var millis: Long = 1_000_000) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    /** Linux not installed; setup.json is whatever the test plays next. */
    private class ScriptedHost : SetupLinuxHost {
        var statusJson: String? = null
        val started = mutableListOf<List<SetupRunnerSpec>>()
        var nextAfterStart: String? = null

        override suspend fun status(): HostReply<SetupLinuxStatus> = HostReply.Ok(SetupLinuxStatus(installed = false))

        override suspend fun run(script: String): HostReply<SetupRunResult> = HostReply.Ok(SetupRunResult(0, ""))

        override suspend fun setupStatus(): HostReply<String?> = HostReply.Ok(statusJson)

        override suspend fun startSetup(
            jobId: String,
            components: List<SetupRunnerSpec>,
            params: Map<String, Map<String, String>>,
            texts: SetupNotificationTexts,
        ): HostReply<Unit> {
            started += components
            statusJson = nextAfterStart?.replace("JOB", jobId)
            return HostReply.Ok(Unit)
        }
    }

    private class Device(var facts: SetupDevice) : PhoneSetupDevice {
        var opened = 0

        override suspend fun facts(): SetupDevice = facts

        override fun openStorageSettings(): Boolean {
            opened++
            return true
        }
    }

    private class Jobs(val host: ScriptedHost) : PhoneSetupJobs {
        val steps = mutableListOf<Pair<String, Boolean>>()
        var cancels = 0
        val plays = ArrayDeque<String>()

        override suspend fun finishStep(jobId: String, openCodeChanged: Boolean) {
            steps += jobId to openCodeChanged
        }

        override suspend fun cancel() {
            cancels++
        }

        override suspend fun pause() {
            delay(500)
            plays.removeFirstOrNull()?.let { host.statusJson = it }
        }
    }

    private class Nav : PhoneSetupNavigator {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun thisPhone() {
            calls += "thisPhone"
        }
    }

    /** Background work (the model's scope) only runs when virtual time moves: 10 s covers every scripted play. */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private val roomy = SetupDevice(listOf("arm64-v8a"), totalMemoryMb = 8192, availableStorageBytes = 64_000_000_000)

    /** setup.json as android/linux SetupRunner writes it: components keyed by id, their order alongside. */
    private fun job(state: String, current: String?, components: String, error: String? = null, log: String = ""): String {
        val order = Regex(""""([a-z]+)":\{""").findAll(components).joinToString(",") { "\"${it.groupValues[1]}\"" }
        return """{"jobId":"JOB","state":"$state","current":${current?.let { "\"$it\"" } ?: "null"},"startedAt":1000000,""" +
            """"updatedAt":1000500,"logTail":"$log",${error?.let { "\"error\":\"$it\"," } ?: ""}"order":[$order],""" +
            """"components":{$components}}"""
    }

    private fun c(id: String, state: String, extra: String = ""): String = """"$id":{"state":"$state","weight":1$extra}"""

    private class Rig(scope: TestScope, device: SetupDevice) {
        val host = ScriptedHost()
        val clock = FixedClock()
        val device = Device(device)
        val jobs = Jobs(host)
        val nav = Nav()
        val engine = SetupEngine(host, clock, scope.backgroundScope, SetupToolsChanged())

        fun model(scope: CoroutineScope) = PhoneSetupModel(engine, device, jobs, nav, scope)
    }

    @Test
    fun `B-LINUX-209 the Set up page offers setup on a supported roomy phone and lists what it installs`() = runTest {
        val rig = Rig(this, roomy)
        val model = rig.model(backgroundScope)
        settle()
        val ui = model.state.value
        assertFalse(ui.checking)
        assertEquals(SetupPreflightResult.Ok, ui.preflight)
        assertTrue(ui.canSetUp)
        assertEquals(listOf("linux", "essentials", "python", "node", "opencode"), ui.items.map { it.id })
        assertTrue(ui.downloadBytes > 0)
        assertNull(ui.progress)
    }

    @Test
    fun `B-LINUX-209 a 32-bit-only phone is told plainly and Set up is not offered`() = runTest {
        val rig = Rig(this, roomy.copy(supportedAbis = listOf("armeabi-v7a")))
        val model = rig.model(backgroundScope)
        settle()
        assertIs<SetupPreflightResult.UnsupportedAbi>(model.state.value.preflight)
        assertFalse(model.state.value.canSetUp)
        model.setUp()
        settle()
        assertTrue(rig.host.started.isEmpty(), "a blocked phone never starts a job")
    }

    @Test
    fun `B-LINUX-210 under 2 GB of memory is refused with its number`() = runTest {
        val rig = Rig(this, roomy.copy(totalMemoryMb = 1024))
        val model = rig.model(backgroundScope)
        settle()
        assertEquals(SetupPreflightResult.LowMemory(1024), model.state.value.preflight)
        assertFalse(model.state.value.canSetUp)
    }

    @Test
    fun `B-LINUX-211 low space names how much to free and opens Storage settings`() = runTest {
        val rig = Rig(this, roomy.copy(availableStorageBytes = 10_000_000))
        val model = rig.model(backgroundScope)
        settle()
        val low = assertIs<SetupPreflightResult.LowSpace>(model.state.value.preflight)
        assertTrue(low.bytesToFree > 0)
        model.openStorage()
        assertEquals(1, rig.device.opened)
    }

    @Test
    fun `B-LINUX-201 Set up starts the real job, follows setup-json and runs the start step once`() = runTest {
        val rig = Rig(this, roomy)
        rig.host.nextAfterStart = job("running", "linux", c("linux", "running", ""","done":10,"total":100"""))
        val model = rig.model(backgroundScope)
        settle()
        val running = job("running", "start", c("linux", "done") + "," + c("opencode", "done") + "," + c("start", "running"))
        rig.jobs.plays += running
        rig.jobs.plays += running
        rig.jobs.plays += job("done", null, c("linux", "done") + "," + c("opencode", "done") + "," + c("start", "done"))
        model.setUp()
        settle()
        assertEquals(1, rig.host.started.size, "one job handed to the native runner")
        val ids = rig.host.started.single().map { it.id }
        assertEquals(listOf("linux", "essentials", "python", "node", "opencode", "start"), ids)
        assertEquals(1, rig.jobs.steps.size, "the app-run step starts once per job")
        assertTrue(rig.jobs.steps.single().second, "OpenCode was installed by this job")
        val done = assertNotNull(model.state.value.progress)
        assertEquals(SetupState.Done, done.state)
        assertEquals(1.0, done.overall)
        model.done()
        assertEquals(listOf("thisPhone"), rig.nav.calls)
    }

    @Test
    fun `B-LINUX-201 a job already on the phone is shown and never restarted`() = runTest {
        val rig = Rig(this, roomy)
        rig.host.statusJson = job("running", "node", c("linux", "done") + "," + c("node", "running", ""","percent":40"""))
        rig.jobs.plays += job("done", null, c("linux", "done") + "," + c("node", "done"))
        val model = rig.model(backgroundScope)
        settle()
        assertTrue(rig.host.started.isEmpty())
        assertEquals(SetupState.Done, model.state.value.progress?.state)
    }

    @Test
    fun `B-LINUX-203 a failed job speaks plainly, keeps Details and continues on request`() = runTest {
        val rig = Rig(this, roomy)
        rig.host.statusJson =
            job("failed", "opencode", c("linux", "done") + "," + c("opencode", "failed"), log = "curl: (6) Could not resolve host")
        val model = rig.model(backgroundScope)
        settle()
        val failed = assertNotNull(model.state.value.progress)
        assertEquals(SetupState.Failed, failed.state)
        assertEquals(SetupProblem.NoInternet, failed.error, "a download without internet names the cause")
        assertTrue(model.state.value.details?.text.orEmpty().contains("Could not resolve host"))
        rig.host.nextAfterStart = job("running", "opencode", c("linux", "skipped") + "," + c("opencode", "running"))
        rig.jobs.plays += job("done", null, c("linux", "skipped") + "," + c("opencode", "done"))
        model.continueSetup()
        settle()
        assertEquals(1, rig.host.started.size)
        assertEquals(SetupState.Done, model.state.value.progress?.state)
    }

    @Test
    fun `B-SEC-166 setup Details on the page are redacted`() = runTest {
        val rig = Rig(this, roomy)
        val fake = "sk" + "-abcdefghijklmnopqrstuvwxyz0123456789" // a fake key, split so the push scan does not match it
        rig.host.statusJson = job("failed", "opencode", c("opencode", "failed"), log = "export OPENAI_API_KEY=$fake")
        val model = rig.model(backgroundScope)
        settle()
        val details = model.state.value.details?.text.orEmpty()
        assertFalse(fake in details, details)
    }

    @Test
    fun `B-LINUX-201 Stop asks first, then stops the running job`() = runTest {
        val rig = Rig(this, roomy)
        rig.host.statusJson = job("running", "node", c("node", "running"))
        val model = rig.model(backgroundScope)
        settle()
        model.askStop()
        assertTrue(model.state.value.confirmingStop)
        model.keepGoing()
        assertFalse(model.state.value.confirmingStop)
        assertEquals(0, rig.jobs.cancels)
        model.askStop()
        model.stop()
        settle()
        assertEquals(1, rig.jobs.cancels)
    }
}
