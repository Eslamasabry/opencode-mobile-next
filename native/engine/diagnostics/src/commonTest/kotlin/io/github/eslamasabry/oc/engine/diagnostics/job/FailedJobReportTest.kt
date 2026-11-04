package io.github.eslamasabry.oc.engine.diagnostics.job

import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FailedJobReportTest {
    private val redactor = Redactor()

    private fun setup(state: SetupJobState = SetupJobState.Failed, log: String = "download failed\nexit 1") =
        SetupJobSnapshot(
            state = state,
            components =
                listOf(
                    SetupComponent("linux", SetupComponentState.Done),
                    SetupComponent("opencode", SetupComponentState.Failed, error = "Could not install"),
                ),
            jobId = "setup-1",
            current = "opencode",
            logTail = log,
        )

    @Test
    fun `B-AGENT-108 a failed v2 row carries the same job log and the component reason`() {
        val report = assertNotNull(FailedJobReport.setup(redactor, setup(), componentId = "opencode"))
        assertEquals(FailedJobKind.Setup, report.kind)
        assertEquals("setup-1", report.jobId)
        assertEquals("opencode", report.title)
        assertEquals("Could not install", report.detail)
        assertEquals("download failed\nexit 1", report.logExcerpt)
        assertTrue(report.logExcerpt in report.preview)
        assertNull(FailedJobReport.setup(redactor, setup(), componentId = "linux"))
        assertNull(FailedJobReport.setup(redactor, setup(), componentId = "missing"))
        val attachment = report.toAttachment()
        assertEquals("failed job · setup · setup-1", attachment.source)
        assertEquals(report.logExcerpt, attachment.log)
        assertEquals("Could not install", attachment.details)
    }

    @Test
    fun `B-AGENT-108 v1 whole-job failure uses the job's current step and its error`() {
        val report = assertNotNull(FailedJobReport.setup(redactor, setup().copy(error = "Job failed")))
        assertEquals("opencode", report.title)
        assertEquals("Job failed", report.detail)
        assertEquals("download failed\nexit 1", report.logExcerpt)
    }

    @Test
    fun `B-AGENT-108 OCTRACE timing lines never land in a failed job's log excerpt`() {
        val report =
            assertNotNull(
                FailedJobReport.setup(
                    redactor,
                    setup(
                        log =
                            "OCTRACE 2.9ms linux.setupStatus\n" +
                                "[oc] What OpenCode said:\n" +
                                "  Error: Failed to start server\n" +
                                "[oc] OpenCode was installed but did not start\n" +
                                "[2026-09-28 10:00:00 UTC] timing · OCTRACE\n" +
                                "OCTRACE 3.1ms linux.setupStatus\n",
                    ),
                ),
            )
        assertFalse("OCTRACE" in report.logExcerpt)
        assertEquals("[oc] OpenCode was installed but did not start", report.logExcerpt.split('\n').last())
    }

    @Test
    fun `B-AGENT-108 successful, active and interrupted setup do not claim failure`() {
        for (state in SetupJobState.entries.filter { it != SetupJobState.Failed }) {
            assertNull(FailedJobReport.setup(redactor, setup(state = state)), state.name)
        }
        val empty = assertNotNull(FailedJobReport.setup(redactor, setup(log = "")))
        assertFalse(empty.hasLog)
        assertTrue("Log excerpt: unavailable" in empty.preview)
    }

    @Test
    fun `B-AGENT-108 redacts the whole multiline input before line or character truncation`() {
        redactor.registerSecret("fake-loaded-credential")
        val report =
            assertNotNull(
                FailedJobReport.setup(
                    redactor,
                    setup(
                        log =
                            listOf(
                                "password=\"${List(140) { "synthetic secret" }.joinToString("\n")}\"",
                                "-----BEGIN PRIVATE KEY-----",
                                "synthetic-private-key-body",
                                "-----END PRIVATE KEY-----",
                                "\u001B[31mBearer synthetic-bearer\u001B[0m",
                                "fake-loaded-credential",
                                "error at /work/project/main.dart",
                            ).joinToString("\n"),
                    ),
                ),
            )
        assertFalse("synthetic" in report.logExcerpt)
        assertFalse("fake-loaded-credential" in report.preview)
        assertTrue(Redactor.MASK in report.logExcerpt)
        assertTrue("/work/project/main.dart" in report.logExcerpt)
        assertFalse("\u001B" in report.logExcerpt)
    }

    @Test
    fun `B-AGENT-108 the excerpt keeps a bounded tail, including a single huge line`() {
        val lines = List(200) { "line $it" }
        val report = assertNotNull(FailedJobReport.setup(redactor, setup(log = lines.joinToString("\n"))))
        assertEquals(120, report.logExcerpt.split('\n').size)
        assertTrue(report.logExcerpt.startsWith("line 80\n"))
        assertTrue(report.logExcerpt.endsWith("line 199"))
        val large = assertNotNull(FailedJobReport.setup(redactor, setup(log = "x".repeat(20_000) + "END")))
        assertEquals(FailedJobReport.MAX_LOG_CHARACTERS, large.logExcerpt.length)
        assertTrue(large.logExcerpt.endsWith("END"))
    }
}
