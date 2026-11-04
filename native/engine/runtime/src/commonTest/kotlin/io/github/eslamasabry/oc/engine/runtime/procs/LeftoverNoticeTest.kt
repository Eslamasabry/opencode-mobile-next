package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/leftover_process_notice_test.dart group 'the leftover-process line (item 7)' (its logic). */
class LeftoverNoticeTest {
    private fun orphan(pid: Int = 4242, name: String = "node", cwd: String = "/root/projects/", cpuSeconds: Long = 610) =
        LeftoverProcess(pid, name, cwd, cpuSeconds, orphan = true)

    private fun notice(helper: String, project: String? = null) =
        RunawayNotice(RunawayIdentity(1, helper), helper, project, DurationWords.Minutes(10))

    @Test
    fun `B-LINUX-081 names a project by its folder, and never shows a path`() {
        assertNull(runawayProjectName("/root/projects/"))
        assertNull(runawayProjectName("/root/projects"))
        assertNull(runawayProjectName("/root"))
        assertNull(runawayProjectName(""))
        assertNull(runawayProjectName("/root/projects/.cache/x"))
        assertEquals("FinanceHub", runawayProjectName("/root/projects/FinanceHub"))
        assertEquals("FinanceHub", runawayProjectName("/root/projects/FinanceHub/node_modules/.bin"))
        assertEquals("app", runawayProjectName("/data/data/com.termux/files/home/projects/app"))
    }

    @Test
    fun `B-LINUX-081 OpenCode is named only when OpenCode itself is the one busy, and a path never shows`() {
        val tenMin = DurationWords.Minutes(10)
        assertEquals(RunawayMessage.OpenCode(tenMin), notice("opencode").message)
        assertEquals(RunawayMessage.OpenCodeInProject("FinanceHub", tenMin), notice("opencode", "FinanceHub").message)
        // A path is cut to its program: "A leftover java process has been busy for 10 min with nothing to do".
        assertEquals(RunawayMessage.Helper("java", tenMin), notice("/usr/bin/java").message)
    }

    @Test
    fun `B-LINUX-081 the line offers See what's running only when it can open the list`() {
        val n = notice("node")
        assertTrue(n.more(canSeeRunning = false).isEmpty())
        assertEquals(listOf(RunawayMoreAction.SeeRunning), n.more(canSeeRunning = true))
    }

    @Test
    fun `B-LINUX-081 a leftover helper is named as one, and its dismissal lasts until the process changes`() {
        val watch = RunawayWatch()
        val first = assertNotNull(watch.notice(listOf(orphan())))
        // "A leftover node process has been busy for 10 min with nothing to do": no OpenCode, no path.
        assertEquals(RunawayMessage.Helper("node", DurationWords.Minutes(10)), first.message)
        watch.dismiss(first.identity)
        // The same process, scanned again: still dismissed.
        assertNull(watch.notice(listOf(orphan(cpuSeconds = 700))))
        // A different process: back, named by its project.
        val second = assertNotNull(watch.notice(listOf(orphan(pid = 5151, cwd = "/root/projects/FinanceHub", cpuSeconds = 720))))
        assertEquals(RunawayMessage.HelperInProject("node", "FinanceHub", DurationWords.Minutes(12)), second.message)
    }

    @Test
    fun `B-LINUX-081 only an orphan over ten minutes of processor time is named, the worst first`() {
        val watch = RunawayWatch()
        assertNull(watch.notice(listOf(orphan(cpuSeconds = 600))))
        assertNull(watch.notice(listOf(orphan().copy(orphan = false))))
        val worst = watch.notice(listOf(orphan(pid = 11, cpuSeconds = 700), orphan(pid = 12, name = "java", cpuSeconds = 900)))
        assertEquals(RunawayIdentity(12, "java"), worst?.identity)
    }

    @Test
    fun `B-LINUX-081 a stop that did not end it keeps the line in failure words, an ended one hides it`() {
        val watch = RunawayWatch()
        val n = assertNotNull(watch.notice(listOf(orphan())))
        assertEquals("node", n.stopLabelName)
        watch.stopped(n.identity, ended = false)
        assertEquals(RunawayMessage.StopFailed("node"), watch.notice(listOf(orphan()))?.message)
        watch.stopped(n.identity, ended = true)
        assertNull(watch.notice(listOf(orphan())))
    }
}
