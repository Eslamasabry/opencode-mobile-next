package io.github.eslamasabry.oc.feature.linux.manager

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.host.ShellRun
import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost.Companion.out
import io.github.eslamasabry.oc.feature.linux.manager.FakeLinuxHost.Companion.ownership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * The reads of Storage and Packages end: with the answer (late if the phone is slow), or after a
 * stated limit with "not answering" words. A set-up Linux that is merely busy or slow never gets
 * the "not set up" words.
 */
class LinuxReadsBoundedTest {
    private val list = "::pkg bash\t5.2\t1000\tno\toptional\tii \n"
    private val timedOut = Outcome.Ok(ShellRun(-1, ""))

    @Test
    fun `B-LINUX-401 a slow channel gives the list late and it is shown`() = runTest {
        val host = FakeLinuxHost(answer = { out(list) })
        host.latencyMs = 80_000
        val answer = LinuxManager(host, VirtualRetryWait).installedPackages()
        assertEquals(listOf("bash"), assertIs<LinuxAnswer.Ok<List<LinuxPackage>>>(answer).value.map { it.name })
    }

    @Test
    fun `B-LINUX-401 a read that ran out of time is tried once more with a longer limit`() = runTest {
        var calls = 0
        val host = FakeLinuxHost(answer = { if (calls++ == 0) timedOut else out(list) })
        val answer = LinuxManager(host, VirtualRetryWait).installedPackages()
        assertEquals(1, assertIs<LinuxAnswer.Ok<List<LinuxPackage>>>(answer).value.size)
        assertEquals(listOf(90L, 180L), host.timeouts)
    }

    @Test
    fun `B-LINUX-401 a read that never answers ends as not answering after two tries`() = runTest {
        val host = FakeLinuxHost(answer = { timedOut })
        val answer = LinuxManager(host, VirtualRetryWait).installedPackages()
        assertEquals(LinuxProblem.NotAnswering, assertIs<LinuxAnswer.Problem>(answer).problem)
        assertEquals(2, host.scripts.size)
    }

    @Test
    fun `B-LINUX-401 an unnamed failure is tried once more and then is not answering, never not set up`() = runTest {
        val generic = Failure.Runtime(RuntimeProblem.Generic)
        val first = FakeLinuxHost(answer = { out(list) })
        first.failures += generic
        assertIs<LinuxAnswer.Ok<List<LinuxPackage>>>(LinuxManager(first, VirtualRetryWait).installedPackages())

        val stays = FakeLinuxHost()
        stays.failures += listOf(generic, generic)
        val answer = LinuxManager(stays, VirtualRetryWait).installedPackages()
        assertEquals(LinuxProblem.NotAnswering, assertIs<LinuxAnswer.Problem>(answer).problem)
    }

    @Test
    fun `B-LINUX-401 a Linux that is really gone still says it is not set up`() = runTest {
        val generic = Failure.Runtime(RuntimeProblem.Generic)
        val host = FakeLinuxHost(presence = LinuxPresence.NotInstalled)
        host.failures += listOf(generic, generic)
        val answer = LinuxManager(host, VirtualRetryWait).installedPackages()
        assertEquals(LinuxProblem.Unavailable, assertIs<LinuxAnswer.Problem>(answer).problem)
    }

    @Test
    fun `B-LINUX-401 a busy channel that stays busy says busy, not not set up`() = runTest {
        val host = FakeLinuxHost()
        host.failures += listOf(ownership, ownership, ownership)
        val answer = LinuxManager(host, VirtualRetryWait).installedPackages()
        assertEquals(LinuxProblem.Busy, assertIs<LinuxAnswer.Problem>(answer).problem)
    }

    @Test
    fun `B-LINUX-402 a storage stage that runs out of time ends the scan, and Try again continues with a longer limit`() = runTest {
        var du = 0
        val host =
            FakeLinuxHost(
                answer = { script ->
                    // The third stage's first try runs out of time; everything else answers.
                    if (script.contains("/opt/node") && du++ == 0) timedOut else out("::du 10 /x\n")
                },
            )
        val manager = LinuxManager(host, VirtualRetryWait)
        val first = manager.measureStorage()
        assertEquals(LinuxProblem.NotAnswering, assertIs<LinuxAnswer.Problem>(first).problem)
        assertEquals(LinuxStorageScanState.Failed, manager.storageScan.value.state)
        val before = host.scripts.size
        assertEquals(3, before, "the scan stopped at the slow stage")

        val second = manager.measureStorage()
        assertIs<LinuxAnswer.Ok<LinuxStorageReport>>(second)
        // Only the stages not yet measured ran again: the slow one and the three after it.
        assertEquals(before + 4, host.scripts.size)
        assertEquals(180L, host.timeouts[before], "the slow stage got a longer limit")
    }

    @Test
    fun `B-LINUX-402 storage that never answers ends in a problem each time instead of measuring forever`() = runTest {
        val host = FakeLinuxHost(answer = { timedOut })
        val manager = LinuxManager(host, VirtualRetryWait)
        repeat(3) { assertEquals(LinuxProblem.NotAnswering, assertIs<LinuxAnswer.Problem>(manager.measureStorage()).problem) }
        assertEquals(3, host.scripts.size, "one stage, one try, per reading")
    }
}
