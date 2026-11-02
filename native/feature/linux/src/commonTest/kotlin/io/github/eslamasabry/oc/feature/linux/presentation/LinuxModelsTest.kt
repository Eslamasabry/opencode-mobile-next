package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost
import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost.Companion.out
import io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobStatus
import io.github.eslamasabry.oc.feature.linux.manager.LinuxManager
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProblem
import io.github.eslamasabry.oc.feature.linux.manager.VirtualRetryWait
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object Quiet : Pacer {
    override fun ticks(spec: TimerSpec): Flow<Unit> = flow { awaitCancellation() }
}

private object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
}

private class Nav : LinuxNavigator {
    val opened = mutableListOf<LinuxPage>()
    var backs = 0

    override fun open(page: LinuxPage) {
        opened += page
    }

    override fun back() {
        backs++
    }
}

private class Lifecycle(
    var installed: Boolean? = true,
    var erase: Outcome<Unit> = Outcome.Ok(Unit),
    var setup: Outcome<Boolean> = Outcome.Ok(true),
    var repair: Outcome<Unit> = Outcome.Ok(Unit),
) : LinuxLifecycle {
    val calls = mutableListOf<String>()

    override suspend fun installed(): Boolean? = installed

    override suspend fun erase(): Outcome<Unit> {
        calls += "erase"
        return erase
    }

    override suspend fun startSetup(): Outcome<Boolean> {
        calls += "setup"
        return setup
    }

    override suspend fun repair(): Outcome<Unit> {
        calls += "repair"
        return repair
    }
}

private class Marker : LinuxResetMarker {
    var value: String? = null
    val history = mutableListOf<String?>()
    var replySpeedForgotten = false

    override suspend fun read(): String? = value

    override suspend fun write(value: String) {
        this.value = value
        history += value
    }

    override suspend fun clear() {
        value = null
        history += null
    }

    override suspend fun forgetReplySpeed() {
        replySpeedForgotten = true
    }
}

/** Runs model work in backgroundScope (advanceUntilIdle skips background tasks), across the manager's retry pauses. */
private fun TestScope.settle() {
    repeat(8) {
        runCurrent()
        advanceTimeBy(1_000)
    }
    runCurrent()
}

private val notices =
    object : LinuxJobNotices {
        override suspend fun notice(kind: LinuxJobKind): String = "notice ${kind.wire}"
    }

class LinuxResetFlowTest {
    @Test
    fun `B-LINUX-093 a reset saves the marker before erasing and clears it once setup runs`() = runTest {
        val life = Lifecycle()
        val marker = Marker()
        var erasedWithMarker: String? = null
        val flow = LinuxResetFlow(life, marker, FixedClock, beforeErase = { erasedWithMarker = marker.value })
        assertEquals(LinuxResetResult.Reinstalling(setupStarted = true), flow.run())
        assertTrue(erasedWithMarker!!.startsWith("erasing "))
        assertEquals(listOf("erase", "setup"), life.calls)
        assertTrue(marker.replySpeedForgotten, "the old Reply speed reading goes with the erased Linux")
        assertNull(marker.value)
        assertTrue(marker.history.any { it?.startsWith("installing ") == true })
    }

    @Test
    fun `B-LINUX-093 a refusal before erasing changes nothing and drops the marker`() = runTest {
        val life = Lifecycle(installed = true, erase = Outcome.Failed(Failure.Runtime(RuntimeProblem.RemovalFailed)))
        val marker = Marker()
        assertIs<LinuxResetResult.NotErased>(LinuxResetFlow(life, marker, FixedClock).run())
        assertEquals(listOf("erase"), life.calls)
        assertNull(marker.value)
        assertFalse(marker.replySpeedForgotten)
    }

    @Test
    fun `B-LINUX-093 erasing that stopped part way still sets up and a setup that cannot start keeps the marker`() = runTest {
        val life = Lifecycle(installed = false, erase = Outcome.Failed(Failure.Runtime(RuntimeProblem.RemovalFailed)), setup = Outcome.Ok(false))
        val marker = Marker()
        assertEquals(LinuxResetResult.Reinstalling(setupStarted = false), LinuxResetFlow(life, marker, FixedClock).run())
        assertEquals(listOf("erase", "setup"), life.calls)
        assertTrue(marker.value!!.startsWith("installing "))
    }

    @Test
    fun `B-LINUX-093 the next start finishes a reset from its marker`() = runTest {
        // Killed while erasing: erase and set up.
        Lifecycle().let { life ->
            val marker = Marker().apply { value = "erasing 2026-11-02T00:00:00Z" }
            assertTrue(LinuxResetFlow(life, marker, FixedClock).resumeIfPending())
            assertEquals(listOf("erase", "setup"), life.calls)
        }
        // Killed after erasing: only set up.
        Lifecycle(installed = false).let { life ->
            val marker = Marker().apply { value = "installing 2026-11-02T00:00:00Z" }
            assertTrue(LinuxResetFlow(life, marker, FixedClock).resumeIfPending())
            assertEquals(listOf("setup"), life.calls)
        }
        // Installed again since: ends quietly.
        Lifecycle(installed = true).let { life ->
            val marker = Marker().apply { value = "installing 2026-11-02T00:00:00Z" }
            assertFalse(LinuxResetFlow(life, marker, FixedClock).resumeIfPending())
            assertEquals(emptyList(), life.calls)
            assertNull(marker.value)
        }
        // No marker: nothing to do.
        Lifecycle().let { life ->
            assertFalse(LinuxResetFlow(life, Marker(), FixedClock).resumeIfPending())
            assertEquals(emptyList(), life.calls)
        }
    }
}

class LinuxHomeModelTest {
    private fun TestScope.home(host: FakeLinuxHost, life: Lifecycle = Lifecycle(), nav: Nav = Nav()): LinuxHomeModel {
        val manager = LinuxManager(host, VirtualRetryWait)
        return LinuxHomeModel(manager, LinuxJobWatch(manager, notices), life, LinuxResetFlow(life, Marker(), FixedClock), nav, backgroundScope, Quiet)
    }

    @Test
    fun `B-LINUX-092 the manager says when Linux is not on this phone and offers no act`() = runTest {
        val model = home(FakeLinuxHost(presence = LinuxPresence.NotOnThisBuild))
        model.shown(true)
        settle()
        assertEquals(LinuxPresence.NotOnThisBuild, model.state.value.presence)
        assertFalse(model.state.value.ready)
    }

    @Test
    fun `B-LINUX-093 repair and reset wait while setup runs and say so plainly`() = runTest {
        val host = FakeLinuxHost().apply { setup = true }
        val model = home(host)
        model.askRepair()
        settle()
        assertEquals(LinuxHomeNotice.Problem(LinuxProblem.SetupRunning), model.state.value.notice)
        assertNull(model.state.value.confirm)
        host.setup = false
        host.running += LinuxManager.JOB_SERVICE
        model.askReset()
        settle()
        assertEquals(LinuxHomeNotice.Problem(LinuxProblem.JobRunning), model.state.value.notice)
    }

    @Test
    fun `B-LINUX-093 reset asks first then erases and hands off to This phone`() = runTest {
        val life = Lifecycle()
        val nav = Nav()
        val model = home(FakeLinuxHost(), life, nav)
        model.askReset()
        settle()
        assertEquals(LinuxConfirm.Reset, model.state.value.confirm)
        assertEquals(emptyList(), life.calls, "nothing is erased before the confirm")
        model.confirm()
        settle()
        assertEquals(listOf("erase", "setup"), life.calls)
        assertEquals(listOf(LinuxPage.ThisPhone), nav.opened)
    }

    @Test
    fun `B-LINUX-093 a reset the phone refused says nothing was erased`() = runTest {
        val life = Lifecycle(erase = Outcome.Failed(Failure.Runtime(RuntimeProblem.RemovalFailed)))
        val model = home(FakeLinuxHost(), life)
        model.askReset()
        settle()
        model.confirm()
        settle()
        assertEquals(LinuxHomeNotice.ResetNotErased, model.state.value.notice)
    }

    @Test
    fun `B-LINUX-093 repair that is not available is said in place`() = runTest {
        val life = Lifecycle(repair = Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))
        val model = home(FakeLinuxHost(), life)
        model.askRepair()
        settle()
        model.confirm()
        settle()
        assertEquals(LinuxHomeNotice.SetupUnavailable, model.state.value.notice)
    }
}

class LinuxPagesModelsTest {
    @Test
    fun `B-LINUX-085 only a previewable port opens and a failed open is said`() = runTest {
        val opened = mutableListOf<String>()
        var opens = true
        val host = FakeLinuxHost(answer = { out("::open 4097\n::open 5173\n") })
        val model = LinuxPortsModel(LinuxManager(host, VirtualRetryWait), { url -> opened += url; opens }, Nav(), backgroundScope)
        model.shown(true)
        settle()
        assertEquals(listOf(4097, 5173), model.state.value.ports.map { it.port })
        model.preview(4097)
        assertEquals(emptyList(), opened, "the OpenCode server is not a web page")
        model.preview(5173)
        assertEquals(listOf("http://127.0.0.1:5173/"), opened)
        opens = false
        model.preview(5173)
        assertTrue(model.state.value.previewFailed)
    }

    @Test
    fun `B-LINUX-092 a page whose first read failed says busy with try again`() = runTest {
        val host = FakeLinuxHost()
        repeat(3) { host.failures += FakeLinuxHost.ownership }
        val model = LinuxPortsModel(LinuxManager(host, VirtualRetryWait), { true }, Nav(), backgroundScope)
        model.shown(true)
        settle()
        assertEquals(LinuxProblem.Busy, model.state.value.problem)
    }

    @Test
    fun `B-LINUX-089 storage measures each cleanup and a confirmed cleanup says what it freed`() = runTest {
        var cleaned = false
        val host =
            FakeLinuxHost(
                answer = { script ->
                    when {
                        script.contains("apt-get clean") -> { cleaned = true; out("") }
                        script.contains("/var/cache/apt") && script.contains("oc_du /var/cache/apt\n") ->
                            out(if (cleaned) "::du 0 /var/cache/apt\n" else "::du 2048 /var/cache/apt\n")
                        else -> out("::du 10 /usr\n")
                    }
                },
            )
        val model = LinuxStorageModel(LinuxManager(host, VirtualRetryWait), Nav(), backgroundScope)
        model.shown(true)
        settle()
        assertEquals(2048L * 1024, model.state.value.cleanable[LinuxCleanupId.AptCache])
        model.askCleanup(LinuxCleanupId.AptCache)
        assertEquals(CleanupAsk(LinuxCleanupId.AptCache, 2048L * 1024), model.state.value.confirm)
        model.confirm()
        settle()
        assertEquals(CleanupResult.Freed(2048L * 1024), model.state.value.result)
    }

    private fun TestScope.packages(host: FakeLinuxHost): LinuxPackagesModel {
        val manager = LinuxManager(host, VirtualRetryWait)
        return LinuxPackagesModel(manager, LinuxJobWatch(manager, notices), Nav(), backgroundScope, Quiet)
    }

    private val list = "::pkg bash\t5.2\t1400\tyes\trequired\tii \n::pkg htop\t3.3\t300\t\toptional\tii \n::pkg libx\t1\t1\t\toptional\tii \n"

    @Test
    fun `B-LINUX-090 typed text that is not a package name never reaches Linux`() = runTest {
        val host = FakeLinuxHost(answer = { out(list) })
        val model = packages(host)
        model.shown(true)
        settle()
        val before = host.scripts.size
        model.checkInstall("git; rm -rf /")
        settle()
        assertEquals(PackagesNotice.InvalidName, model.state.value.notice)
        assertEquals(before, host.scripts.size)
    }

    @Test
    fun `B-LINUX-090 an unknown name is said and a known one asks before installing as one job`() = runTest {
        val host =
            FakeLinuxHost(
                answer = { script ->
                    when {
                        script.startsWith("# oc-linux-package-check") ->
                            out(if (script.contains("nope")) "::cand nope\t\t\n" else "::cand tmux\t3.4\t\n")
                        else -> out(list)
                    }
                },
            )
        val model = packages(host)
        model.shown(true)
        settle()
        model.checkInstall("nope")
        settle()
        assertEquals(PackagesNotice.Unknown("nope"), model.state.value.notice)
        model.checkInstall("tmux")
        settle()
        assertEquals(PackagesConfirm.Install(listOf("tmux")), model.state.value.confirm)
        model.confirm()
        settle()
        assertEquals(listOf(LinuxManager.JOB_SERVICE to "notice install"), host.started)
        assertEquals(LinuxJobStatus.Running, model.state.value.job.status)
        assertFalse(model.state.value.canStartJob)
    }

    @Test
    fun `B-LINUX-090 a package Linux needs is never offered for removal and a plan that takes one is refused`() = runTest {
        val host =
            FakeLinuxHost(
                answer = { script ->
                    when {
                        script.startsWith("# oc-linux-remove-plan") -> out("::remv libx\n::remv bash\n")
                        else -> out(list)
                    }
                },
            )
        val model = packages(host)
        model.shown(true)
        settle()
        val before = host.scripts.size
        model.askRemove("bash")
        settle()
        assertEquals(before, host.scripts.size, "a locked package asks nothing")
        model.askRemove("libx")
        settle()
        assertEquals(PackagesNotice.BlockedPlan("bash"), model.state.value.notice)
        assertNull(model.state.value.confirm)
    }

    @Test
    fun `B-LINUX-091 stopping the job asks first and names what it stops`() = runTest {
        val host = FakeLinuxHost(answer = { out(list) })
        val model = packages(host)
        model.shown(true)
        settle()
        model.askUpdate()
        model.confirm()
        settle()
        host.logs[LinuxManager.JOB_SERVICE] = "::oc job update\n"
        model.askStopJob()
        assertEquals(PackagesConfirm.StopJob(LinuxJobKind.Update, emptyList()), model.state.value.confirm)
        model.confirm()
        settle()
        assertFalse(LinuxManager.JOB_SERVICE in host.running)
        assertEquals(LinuxJobStatus.Interrupted, model.state.value.job.status)
    }
}

class LinuxSizeTest {
    @Test
    fun `B-LINUX-089 sizes read in short plain units`() {
        assertEquals("0 B", LinuxSize.format(0))
        assertEquals("512 B", LinuxSize.format(512))
        assertEquals("1 KB", LinuxSize.format(1024))
        assertEquals("1.5 KB", LinuxSize.format(1536))
        assertEquals("320 MB", LinuxSize.format(320L * 1024 * 1024))
        assertEquals("1.4 GB", LinuxSize.format((1.4 * 1024 * 1024 * 1024).toLong()))
    }
}
