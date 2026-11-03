package io.github.eslamasabry.oc.engine.project.reports

import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val WORK = TeamWorkItem(id = "work-1", title = "Build", failed = true, runId = "run-1", sessionId = "session-1")
private val GATE = TeamGate(id = "gate-1", kind = TeamGateKind.RunFailed, title = "Run failed", runId = "run-1", workId = "work-1")

private fun setup(failed: Boolean = true, log: String = "download failed\nexit 1") =
    SetupJobRun(
        failed = failed,
        components = listOf(SetupComponentRun("linux", failed = false), SetupComponentRun("opencode", failed = true, error = "Could not install")),
        jobId = "setup-1",
        current = "opencode",
        logTail = log,
    )

private fun service(stopped: Boolean = false) =
    DevService("dev", "Preview", "npm run dev", "/project", DevServiceRun(ownerToken = "fake-owner", shellId = "sh_1", startedAtMs = 1000, stopped = stopped))

private fun shell(
    status: ManagedShellStatus = ManagedShellStatus.Exited,
    exitCode: Int? = 1,
    owner: String = "fake-owner",
    startedAt: Long = 1000,
    command: String = "npm run dev",
    directory: String = "/project",
    id: String = "sh_1",
) = ManagedShell(id, command, status, startedAt, null, exitCode, null, directory, owner)

class FailedJobReportTest {
    private val redactor = Redactor()
    private val reports = FailedJobReport.Factory(redactor)

    @Test
    fun `B-AGENT-109 a failed setup row carries the job log and the component reason`() {
        val report = reports.setup(setup(), componentId = "opencode")!!
        assertEquals(FailedJobKind.Setup, report.kind)
        assertEquals("setup-1", report.jobId)
        assertEquals("opencode", report.title)
        assertEquals("Could not install", report.detail)
        assertEquals("download failed\nexit 1", report.logExcerpt)
        assertTrue(report.logExcerpt in report.preview)
        assertNull(reports.setup(setup(), componentId = "linux"))
        assertNull(reports.setup(setup(), componentId = "missing"))
        assertNull(reports.setup(setup(failed = false)))
        assertFalse(reports.setup(setup(log = ""))!!.hasLog)
    }

    @Test
    fun `B-AGENT-109 the whole log is redacted before it is bounded`() {
        redactor.registerSecret("fake-loaded-credential")
        val report =
            reports.setup(
                setup(
                    log =
                        listOf(
                            "-----BEGIN " + "PRIVATE KEY-----",
                            "synthetic-private-key-body",
                            "-----END " + "PRIVATE KEY-----",
                            "\u001B[31mBearer synthetic-bearer\u001B[0m",
                            "fake-loaded-credential",
                            "error at /work/project/main.dart",
                        ).joinToString("\n"),
                ),
            )!!
        assertFalse("synthetic" in report.logExcerpt)
        assertFalse("fake-loaded-credential" in report.preview)
        assertTrue(Redactor.MASK in report.logExcerpt)
        assertTrue("/work/project/main.dart" in report.logExcerpt)
        assertFalse('\u001B' in report.logExcerpt)
        val lines = (0 until 200).joinToString("\n") { "line $it" }
        val bounded = reports.setup(setup(log = lines))!!
        assertEquals(120, bounded.logExcerpt.split('\n').size)
        assertTrue(bounded.logExcerpt.startsWith("line 80\n"))
        val large = reports.setup(setup(log = "x".repeat(20_000) + "END"))!!
        assertEquals(FailedJobReport.MAX_LOG_CHARACTERS, large.logExcerpt.length)
        assertTrue(large.logExcerpt.endsWith("END"))
    }

    @Test
    fun `B-AGENT-109 team work and gate reports attach only the linked session excerpt`() {
        val report = reports.teamGate(GATE, work = WORK, sessionId = "session-1", logTail = "test failed")!!
        assertEquals(FailedJobKind.TeamRun, report.kind)
        assertEquals("run-1", report.jobId)
        assertEquals("test failed", report.logExcerpt)
        assertEquals("test failed", reports.teamWork(WORK, sessionId = "session-1", logTail = "test failed")!!.logExcerpt)
        for (session in listOf(null, "", "reused-agent-new-session")) {
            assertFalse(reports.teamGate(GATE, work = WORK, sessionId = session, logTail = "unrelated")!!.hasLog)
            assertFalse(reports.teamWork(WORK, sessionId = session, logTail = "unrelated")!!.hasLog)
        }
        val unavailable = reports.teamGate(GATE)!!
        assertFalse(unavailable.hasLog)
        assertTrue("Log excerpt: unavailable" in unavailable.preview)
    }

    @Test
    fun `B-AGENT-109 a gate rejects unrelated work and never copies the raw provider payload`() {
        val unrelated = TeamWorkItem("other", "Other", failed = true, runId = "other-run", sessionId = "session-1")
        assertFalse(reports.teamGate(GATE, work = unrelated, sessionId = "session-1", logTail = "wrong log")!!.hasLog)
        val runGate =
            TeamGate("g", TeamGateKind.RunFailed, "password=synthetic-title", runId = "run-1", prompt = "token=synthetic-prompt", raw = mapOf("private" to "never-export-this"))
        val report = reports.teamGate(runGate, work = WORK, sessionId = "session-1", logTail = "correct")!!
        assertEquals("correct", report.logExcerpt)
        assertFalse("synthetic" in report.preview)
        assertFalse("never-export-this" in report.preview)
        assertNull(reports.teamGate(TeamGate("g", TeamGateKind.Confirmation, "Confirm")))
        assertNull(reports.teamWork(TeamWorkItem("w", "Done", failed = false)))
    }

    @Test
    fun `B-AGENT-109 a built-in team failed job carries a redacted log until cleared`() {
        assertNull(reports.teamSetup(TeamSetupJobRun(running = false, stage = null, errorLog = null)))
        assertNull(reports.teamSetup(TeamSetupJobRun(running = true, stage = "start", errorLog = "old")))
        val report = reports.teamSetup(TeamSetupJobRun(running = false, stage = "start", errorLog = "start failed\npassword=synthetic-password"))!!
        assertEquals(FailedJobKind.TeamSetup, report.kind)
        assertTrue("start failed" in report.logExcerpt)
        assertFalse("synthetic-password" in report.logExcerpt)
        // Cleared: no new report; the snapshot already taken is unchanged.
        assertNull(reports.teamSetup(TeamSetupJobRun(running = false, stage = "start", errorLog = null)))
        assertTrue("start failed" in report.logExcerpt)
    }

    @Test
    fun `B-AGENT-109 a dev report needs the exact owned run and an observed failure`() {
        val report = reports.developmentService(service(), shell(), logTail = "failed\npassword=synthetic-password")!!
        assertEquals("failed\npassword=${Redactor.MASK}", report.logExcerpt)
        assertEquals("exited (exit 1)", report.detail)
        assertFalse("fake-owner" in report.preview)
        assertFalse("npm run dev" in report.preview)
        for (wrong in listOf(shell(owner = "other"), shell(startedAt = 2000), shell(command = "other"), shell(directory = "/other"), shell(id = "sh_2"))) {
            assertNull(reports.developmentService(service(), wrong))
        }
        assertNull(reports.developmentService(service(stopped = true), shell()))
        for (s in listOf(ManagedShellStatus.Running, ManagedShellStatus.Unknown)) assertNull(reports.developmentService(service(), shell(status = s)))
        for (code in listOf(0, null)) assertNull(reports.developmentService(service(), shell(exitCode = code)))
        for (s in listOf(ManagedShellStatus.Timeout, ManagedShellStatus.Killed)) {
            assertNotNull(reports.developmentService(service(), shell(status = s, exitCode = null))).also { assertFalse(it.hasLog) }
        }
    }
}
