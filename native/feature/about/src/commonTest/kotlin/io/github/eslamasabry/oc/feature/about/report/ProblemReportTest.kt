package io.github.eslamasabry.oc.feature.about.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The report text and the failed-job attachment (test/failed_job_report_ui_test.dart:205-310, 539). */
class ProblemReportTest {
    private val fakeKey = "sk-ant-" + "api03-FAKEFAKEFAKEFAKEFAKE1234567890"

    @Test
    fun `B-NAV-015 a job log leads the diagnostics, scrubbed, even with recent events`() {
        val report =
            ProblemReport.build(
                description = "Setup stopped",
                version = "1.0.0+1",
                platform = "Android",
                error = ReportAttachment(
                    title = "Couldn't start the team",
                    log = "curl http://desk.example.invalid:4096/health\nauth $fakeKey\nexit 7",
                ),
                events = listOf(ReportEvent(ReportEventKind.Error, "2026-09-27 12:00:00 UTC", "sync", "boom")),
            )
        assertTrue(report.diagnostics.startsWith("Failed job log (last lines)\n"))
        assertTrue("exit 7" in report.diagnostics)
        assertTrue("http://[server]/health" in report.diagnostics)
        assertTrue("boom" in report.diagnostics, "the errors around the job follow its log")
        assertFalse(fakeKey in report.text)
        assertFalse("desk.example.invalid" in report.text)
        assertTrue(report.text.startsWith("Couldn't start the team"))
    }

    @Test
    fun `B-NAV-015 a job that kept no log says so and a plain error adds nothing`() {
        val none = ProblemReport.build("", "1", "Android", ReportAttachment(title = "Run failed", log = ""))
        assertEquals("Failed job log: none was kept", none.diagnostics)
        val plain = ProblemReport.build("x", "1", "Android", ReportAttachment(title = "Couldn't load files"))
        assertEquals("", plain.diagnostics)
        // A failed job's report leaves timings out; a plain report keeps them.
        val timing = ReportEvent(ReportEventKind.Timing, "2026-09-27 12:00:00 UTC", "trace", "poll 12 ms")
        assertFalse("poll" in ProblemReport.build("", "1", "Android", ReportAttachment("Run failed", log = "x"), events = listOf(timing)).diagnostics)
        assertTrue("poll" in ProblemReport.build("", "1", "Android", events = listOf(timing)).diagnostics)
    }

    @Test
    fun `B-NAV-015 a failed job becomes the page's attachment with its log and never another session's`() {
        val work = JobWork(id = "w-1", title = "Write tests", failed = true, sessionId = "ses-1")
        val attached = FailedJob.teamWork(work, sessionId = "ses-1", logTail = "pytest: 3 failed")!!.toAttachment(title = "Write tests stopped")
        assertEquals("Write tests stopped", attached.title)
        assertEquals("pytest: 3 failed", attached.log)
        assertTrue("teamWork" in attached.source!!)
        val other = FailedJob.teamWork(work, sessionId = "ses-2", logTail = "unrelated")!!.toAttachment()
        assertEquals("", other.log)
        assertEquals("Write tests", other.title)
        assertNull(FailedJob.teamWork(work.copy(failed = false), "ses-1", "x"))
    }

    @Test
    fun `B-NAV-015 the attached log is cleaned, bounded and keeps the real error at its end`() {
        val noisy = (1..200).joinToString("\n") { "line $it" } + "\nOCTRACE poll 3ms\n\u001B[31mexit 2\u001B[0m\r\n"
        val job = FailedJob.teamSetup("prepare", noisy)
        assertEquals(FailedJob.MAX_LOG_LINES, job.logExcerpt.split('\n').size)
        assertTrue(job.logExcerpt.endsWith("exit 2"))
        assertFalse("OCTRACE" in job.logExcerpt)
        assertFalse('\u001B' in job.logExcerpt)
        assertFalse("noisy" in job.toString())
    }

    @Test
    fun `B-NAV-015 Report a problem, Run failed and a failed dev service each carry their job`() {
        assertNull(ReportEntry.Settings.attachment())
        assertEquals(ReportLead.None, ReportEntry.Settings.attachment().lead())

        val gate = JobGate(id = "g-1", title = "Run failed", runFailed = true, runId = "oc-loy", prompt = "tests failed")
        val work = JobWork(id = "w-tests", title = "Write tests for calc.py", failed = true, sessionId = "ses-9", runId = "oc-loy")
        val gated = ReportEntry.RunFailedGate(gate, work, sessionId = "ses-9", logTail = "assert 1 == 2").attachment()
        assertNotNull(gated)
        assertEquals("assert 1 == 2", gated.log)
        assertTrue("teamRun · oc-loy" in gated.source!!)
        assertEquals("tests failed", gated.details)
        assertEquals(ReportLead.JobLogAttached, gated.lead())
        // A gate whose host has no output still offers Report, saying no log was kept.
        val bare = ReportEntry.RunFailedGate(gate).attachment()
        assertEquals("", bare!!.log)
        assertEquals(ReportLead.JobKeptNoLog, bare.lead())
        // An unlinked work item's log is never attached.
        assertEquals("", ReportEntry.RunFailedGate(gate, work.copy(runId = "other"), "ses-9", "x").attachment()!!.log)

        val shell = ManagedShell("sh_1", "npm run dev", "/w", "owner-token", 1_000L, ShellStatus.Exited, exitCode = 1)
        val service = DevService("vite", "npm run dev", "/w", ServiceRun("sh_1", "owner-token", 1_000L, stopped = false))
        val dev = ReportEntry.FailedDevService(service, shell, "Error: listen EADDRINUSE :::5173").attachment()!!
        assertEquals("Error: listen EADDRINUSE :::5173", dev.log!!.lines().last())
        assertEquals("exited (exit 1)", dev.details)
        assertFalse("owner-token" in dev.toString() || "npm run dev" in dev.toString())
        assertFalse("owner-token" in ReportEntry.FailedDevService(service, shell).toString())
        // A running service, a deliberate stop or another owner's shell offers no Report.
        assertNull(ReportEntry.FailedDevService(service, shell.copy(status = ShellStatus.Running, exitCode = null)).attachment())
        assertNull(ReportEntry.FailedDevService(service.copy(run = service.run!!.copy(stopped = true)), shell).attachment())
        assertNull(ReportEntry.FailedDevService(service, shell.copy(ownerToken = "someone-else")).attachment())

        val team = ReportEntry.FailedTeamStart("supervisor", "starting\nsupervisor exited 1").attachment()!!
        assertEquals("supervisor exited 1", team.log!!.lines().last())

        // The page's report then leads with that log.
        val report = ProblemReport.build("It failed", "9.9.9+1", "Android", error = gated)
        val lines = report.text.lines()
        val start = lines.indexOf("Failed job log (last lines)")
        assertTrue(start >= 0)
        assertEquals("assert 1 == 2", lines[start + 1])
    }
}
