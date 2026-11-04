package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Port of test/phone_process_watch_test.dart groups 'heavy', 'groups', 'stuck' and 'words'. */
class PhoneProcessWatchTest {
    private val step = 5.seconds

    private fun snap(processes: List<RunningProcess>) = RunningSnapshot(processes)

    @Test
    fun `B-LINUX-144 one busy reading is never heavy, however long it ran`() {
        val clock = FakeClock()
        val reading = PhoneProcessWatch().record(snap(listOf(proc(1, 0, "dart", cpu = 99.0, elapsed = 3.hours))), clock.now)
        assertTrue(reading.heavy.isEmpty())
    }

    @Test
    fun `B-LINUX-144 heavy after two minutes over 80 percent across readings`() {
        val clock = FakeClock()
        val watch = PhoneProcessWatch()
        val busy = snap(listOf(proc(1, 0, "dart", cpu = 95.0)))
        repeat(24) { i ->
            assertTrue(watch.record(busy, clock.now).heavy.isEmpty(), "reading $i")
            clock.advance(step)
        }
        val reading = watch.record(busy, clock.now)
        assertEquals(1, reading.heavy.single().pid)
        assertEquals(2.minutes, reading.heavy.single().heavyFor)
    }

    @Test
    fun `B-LINUX-144 a quiet reading or a gap between readings starts over`() {
        val clock = FakeClock()
        val watch = PhoneProcessWatch()
        val busy = snap(listOf(proc(1, 0, "dart", cpu = 95.0)))
        watch.record(busy, clock.now)
        clock.advance(1.minutes)
        // More than 30 s between readings proves nothing about the middle.
        watch.record(busy, clock.now)
        clock.advance(20.seconds)
        watch.record(busy, clock.now)
        clock.advance(20.seconds)
        watch.record(snap(listOf(proc(1, 0, "dart", cpu = 10.0))), clock.now)
        repeat(20) {
            clock.advance(step)
            assertTrue(watch.record(busy, clock.now).heavy.isEmpty())
        }
    }

    @Test
    fun `B-LINUX-144 a reused process id is a new process`() {
        val clock = FakeClock()
        val watch = PhoneProcessWatch()
        repeat(30) {
            watch.record(snap(listOf(proc(1, 0, "dart", cpu = 95.0))), clock.now)
            clock.advance(step)
        }
        val reading = watch.record(snap(listOf(proc(1, 0, "node", cpu = 95.0, elapsed = 3.seconds))), clock.now)
        assertTrue(reading.heavy.isEmpty())
    }

    @Test
    fun `B-LINUX-144 about 100 percent for over 10 minutes with no conversation above it is stuck`() {
        val clock = FakeClock()
        val watch = PhoneProcessWatch()
        fun tree(elapsed: kotlin.time.Duration = 11.minutes) = listOf(
            proc(100, 1, "proot", owner = PhoneProcessOwner.Task),
            proc(101, 100, "make", owner = PhoneProcessOwner.Task, cpu = 99.0, elapsed = elapsed),
        ) + normalProcesses(testerCpu = 99.0).map {
            if (it.pid == 10240) it.copy(cpuPercent = 99.0, elapsed = 20.minutes) else it
        }
        val first = watch.record(snap(tree()), clock.now)
        assertTrue(first.nodes.none { it.stuck }, "one reading")
        clock.advance(step)
        val second = watch.record(snap(tree()), clock.now)
        assertEquals(listOf(101), second.nodes.filter { it.stuck }.map { it.pid })
        clock.advance(step)
        val young = PhoneProcessWatch()
        young.record(snap(tree(elapsed = 9.minutes)), clock.now)
        clock.advance(step)
        assertTrue(young.record(snap(tree(elapsed = 9.minutes)), clock.now).nodes.none { it.stuck })
    }

    @Test
    fun `B-LINUX-146 each process sits under what started it, as a tree`() {
        val groups = groupRunningProcesses(hotProcesses())
        assertEquals(
            listOf("leftBehind", "openCode", "claudeCode:$CHAT_SESSION", "agentHelper", "terminal", "app"),
            groups.map { it.id },
        )
        val chat = groups[2]
        assertEquals(CHAT_SESSION, chat.session)
        // The conversation's tools carry no session; they follow claude's.
        assertEquals(listOf(10220 to 0, 10230 to 1, 10240 to 2), chat.nodes.map { it.pid to it.depth })
        assertEquals(listOf(10200 to 0, 10210 to 1), groups[3].nodes.map { it.pid to it.depth })
        assertEquals(
            listOf(15001 to 0, 15002 to 1, 15003 to 2, 15004 to 2, 15005 to 2, 15006 to 2),
            groups.first().nodes.map { it.pid to it.depth },
        )
    }

    @Test
    fun `B-LINUX-146 server roots and the app are never stoppable, the rest are`() {
        val nodes = groupRunningProcesses(normalProcesses()).flatMap { it.nodes }.associateBy { it.pid }
        for (root in listOf(10100, 10200, 10300)) {
            assertTrue(nodes.getValue(root).root, "$root")
            assertFalse(nodes.getValue(root).stoppable, "$root")
        }
        assertFalse(nodes.getValue(9000).stoppable)
        for (child in listOf(10110, 10121, 10220, 10240, 10310)) assertTrue(nodes.getValue(child).stoppable, "$child")
    }

    @Test
    fun `B-LINUX-146 a parent loop in a bad reading still shows every process`() {
        val groups = groupRunningProcesses(
            listOf(proc(1, 2, "a", owner = PhoneProcessOwner.Other), proc(2, 1, "b", owner = PhoneProcessOwner.Other)),
        )
        assertEquals(setOf(1, 2), groups.single().nodes.map { it.pid }.toSet())
    }

    @Test
    fun `B-LINUX-146 left behind is stuck at once, and Android counts all but the app with the warning at 28`() {
        val reading = PhoneProcessWatch().record(snap(leftBehindProcesses()), Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(listOf(15001, 15002, 15003, 15004, 15005, 15006), reading.leftBehind.map { it.pid })
        assertTrue(reading.nodes.all { it.stuck })
        val near = PhoneProcessWatch().record(snap(nearLimitProcesses()), Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(29, near.childCount)
        assertTrue(near.nearLimit)
        val below = PhoneProcessWatch().record(snap(nearLimitProcesses(workers = 16)), Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(27, below.childCount)
        assertFalse(below.nearLimit)
    }

    @Test
    fun `B-LINUX-146 a plain name, never the command line`() {
        fun name(kernel: String, command: String = "", owner: PhoneProcessOwner = PhoneProcessOwner.OpenCode) =
            runningProcessName(proc(1, 0, kernel, command = command, owner = owner))
        fun plain(kind: RunningNameKind) = RunningProcessName.Plain(kind)
        assertEquals(plain(RunningNameKind.FlutterTest), name("flutter_tester"))
        assertEquals(plain(RunningNameKind.Command), name("bash", command = "bash -c \"rm -rf build\""))
        assertEquals(plain(RunningNameKind.Linux), name("proot"))
        assertEquals(plain(RunningNameKind.ClaudeCode), name("claude"))
        assertEquals(plain(RunningNameKind.OpenCode), name("node", command = "node /x/opencode serve"))
        assertEquals(plain(RunningNameKind.AgentHelper), name("node", command = "node /root/.paseo/daemon.js"))
        assertEquals(plain(RunningNameKind.Gradle), name("java", command = "java org.gradle.launcher.GradleDaemon"))
        assertEquals(plain(RunningNameKind.Python), name("python3.12"))
        assertEquals(plain(RunningNameKind.App), name("anything", owner = PhoneProcessOwner.App))
        // A program with no plain name keeps the kernel's short name.
        assertEquals(RunningProcessName.Kernel("make"), name("make", command = "make -j8 all"))
        // The l10n select keys (runningPhoneName) the app maps these to.
        assertEquals("flutterTest", RunningNameKind.FlutterTest.key)
        assertEquals("agentHelper", RunningNameKind.AgentHelper.key)
    }

    @Test
    fun `B-LINUX-146 processor use in a word and a percent`() {
        assertEquals(CpuWords(CpuLevel.Idle, "0.4"), runningCpuWords(0.4))
        assertEquals(CpuWords(CpuLevel.Light, "12"), runningCpuWords(12.0))
        assertEquals(CpuWords(CpuLevel.Busy, "45"), runningCpuWords(45.0))
        assertEquals(CpuWords(CpuLevel.VeryBusy, "100"), runningCpuWords(99.6))
        // Nothing measured is "0%", never "0.0%".
        assertEquals(CpuWords(CpuLevel.Idle, "0"), runningCpuWords(0.0))
    }

    @Test
    fun `B-LINUX-146 This phone's line orders left behind, then heavy, then the limit`() {
        val clock = FakeClock()
        val hot = watchWithHistory(snap(hotProcesses()), clock, 3.minutes).record(snap(hotProcesses()), clock.now)
        assertEquals(RunningRowStatus.LeftBehind(6), runningRowStatus(hot))
        val heavyScene = snap(normalProcesses(testerCpu = 96.0))
        val heavyOnly = watchWithHistory(heavyScene, clock, 3.minutes).record(heavyScene, clock.now)
        assertEquals(
            RunningRowStatus.Heavy(1, RunningProcessName.Plain(RunningNameKind.FlutterTest), "96"),
            runningRowStatus(heavyOnly),
        )
        val near = PhoneProcessWatch().record(snap(nearLimitProcesses()), clock.now)
        assertEquals(RunningRowStatus.NearLimit(29, 32), runningRowStatus(near))
        val calm = PhoneProcessWatch().record(snap(normalProcesses()), clock.now)
        val normal = runningRowStatus(calm)
        assertEquals(RunningRowStatus.Normal(12, normalProcesses().sumOf { it.rssBytes }), normal)
        assertNull(runningRowStatus(PhoneProcessWatch().record(snap(emptyList()), clock.now)))
    }
}
