package io.github.eslamasabry.oc.engine.linuxsetup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Ports of test/setup_engine_test.dart "errors in plain words" and "combined checks". */
class SetupFailureWordsTest {
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val en = SetupProblemWordsEn::of

    private fun failed(error: String) = SetupJobComponent(id = "node", state = "failed", error = error)

    private fun registryOnly(id: String) = setupComponentRegistry().filter { it.id == id }

    @Test
    fun `B-LINUX-203 native storage admission explains how to resume`() {
        val text =
            describeSetupFailure(
                failed("There is not enough free space on this phone. Free some storage and try again."), "", "Node.js",
            )
        assertEquals(SetupProblem.NoSpace("Node.js"), text.component)
        assertEquals(
            "Not enough free space to install Node.js. Free some storage, then continue setup.",
            en(text.component),
        )
        assertEquals(text.component, text.job)
    }

    @Test
    fun `B-LINUX-203 SIGTERM and SIGKILL mean interrupted without guessing the cause`() {
        for (reason in listOf(
            "proot info: vpid 1: terminated with signal 15",
            "proot info: vpid 1: terminated with signal 9",
            "process stopped: SIGTERM",
        )) {
            val text = describeSetupFailure(failed("The setup step could not finish. Try again."), reason, "Python")
            assertEquals(SetupProblem.Interrupted, text.component)
            assertEquals(text.component, text.job)
        }
    }

    @Test
    fun `B-LINUX-203 old signal output does not mislabel a later failure`() {
        val log = (listOf("proot info: vpid 1: terminated with signal 15") + List(16) { "working" }).joinToString("\n")
        val text = describeSetupFailure(failed("exit 1"), log, "Python")
        assertEquals(SetupProblem.Install("Python"), text.component)
    }

    @Test
    fun `B-LINUX-203 a download without internet names the part and the cause`() {
        for (tail in listOf(
            "curl: (6) Could not resolve host: nodejs.org\n[oc] Download failed (curl exit 6): https://nodejs.org/x",
            "Err:1 http://archive.ubuntu.com noble InRelease\n  Temporary failure resolving 'archive.ubuntu.com'",
            "java.net.UnknownHostException: Unable to resolve host \"cdimage.ubuntu.com\": No address associated with hostname",
        )) {
            val text = describeSetupFailure(failed(tail.split('\n').last()), tail, "Node.js")
            assertEquals("Could not download Node.js: no internet connection", en(text.component))
            assertEquals(SetupProblem.NoInternet, text.job)
            assertEquals("No internet connection. Continue when you're back online.", en(text.job))
        }
    }

    @Test
    fun `B-LINUX-203 checksum, full storage and anything else map to words`() {
        assertEquals(
            SetupProblem.Checksum("Node.js"),
            describeSetupFailure(failed("[oc] The download did not match its checksum and was deleted"), "", "Node.js").component,
        )
        assertEquals(
            SetupProblem.NoSpace("Node.js"),
            describeSetupFailure(failed("tar: node/bin/node: Cannot write: No space left on device"), "", "Node.js").component,
        )
        val other = describeSetupFailure(failed("exit 1"), "npm ERR! code E403", "OpenCode").component
        assertEquals("Could not install OpenCode", en(other))
    }

    @Test
    fun `B-LINUX-203 a failed job maps to failed progress with both errors`() {
        val record =
            assertNotNull(
                SetupJobRecord.parse(
                    """{"jobId":"j","state":"failed","current":"node","error":"exit 6",
                    "logTail":"curl: (6) Could not resolve host: nodejs.org\n","order":["node"],
                    "components":{"node":{"state":"failed","error":"exit 6"}}}""",
                ),
            )
        val progress = progressFromRecord(record, registryOnly("node"), now)
        assertEquals(SetupState.Failed, progress.state)
        assertEquals(SetupProblem.NoInternet, progress.error)
        assertEquals("Could not download Node.js: no internet connection", en(progress.components.single().error!!))
        assertTrue(progress.logTail.contains("Could not resolve host"))
    }

    @Test
    fun `B-LINUX-204 OCTRACE timing lines never reach the visible job log, which ends with the real error`() {
        val log =
            listOf(
                "==> Checking that OpenCode starts",
                "[oc] What OpenCode said:",
                "  Error: Failed to start server",
                OpenCodeInstallFailure.NO_START,
                "[2026-09-28 10:00:00 UTC] timing · OCTRACE",
                "OCTRACE 2.9ms linux.setupStatus",
            ).joinToString("\n")
        val record =
            SetupJobRecord(
                jobId = "j", state = "failed", current = "opencode", error = OpenCodeInstallFailure.NO_START,
                logTail = log,
                components = listOf(SetupJobComponent(id = "opencode", state = "failed", error = OpenCodeInstallFailure.NO_START)),
            )
        val progress = progressFromRecord(record, registryOnly("opencode"), now)
        assertFalse(progress.logTail.contains("OCTRACE"))
        assertEquals(OpenCodeInstallFailure.NO_START, progress.logTail.trimEnd().split('\n').last())
        assertEquals(SetupProblem.OpenCodeNoStart, progress.error)
        assertEquals(SetupProblem.OpenCodeNoStart, progress.components.single().error)
        assertFalse(en(progress.error!!).contains("[oc]"))
    }

    @Test
    fun `B-LINUX-204 combined checks parse passing, failing and missing with versions`() {
        val results =
            parseCombinedChecks(
                "\n::oc-check-begin a\nnoise\n1.2.3\n\n::oc-check-end a 0\n" +
                    "\n::oc-check-begin b\n::oc-check-end b 1\n" +
                    "\n::oc-check-begin c\npartial output",
            )
        assertEquals(SetupCheckResult(true, "1.2.3"), results["a"])
        assertEquals(SetupCheckResult(false, null), results["b"])
        assertFalse("c" in results)
    }

    @Test
    fun `B-SEC-166 pre-output failure Details redact credentials`() {
        val progress =
            progressFromRecord(
                SetupJobRecord(
                    jobId = "private-failure", state = "failed",
                    error = "install failed password=synthetic-private-value", components = emptyList(),
                ),
                emptyList(),
                now,
            )
        assertTrue(progress.logTail.contains("install failed"))
        assertFalse(progress.logTail.contains("synthetic-private-value"))
    }

    @Test
    fun `B-SEC-166 package repair failure keeps its reason in Details without raw output`() {
        val reason = "[oc] Some packages are still unfinished. Retry setup to repair the interrupted installation."
        val record =
            SetupJobRecord(
                jobId = "python-repair", state = "failed", current = "python", error = reason,
                components = listOf(SetupJobComponent(id = "python", state = "failed", error = reason)),
            )
        val progress = progressFromRecord(record, setupComponentRegistry(), now)
        assertEquals(SetupProblem.PackageRepair, progress.components.single().error)
        assertEquals(SetupProblem.PackageRepair, progress.error)
        assertEquals(
            "Some Linux packages need repair. Continue setup to try again. Details show what stopped it.",
            en(progress.error!!),
        )
        assertEquals(reason, progress.logTail)
    }
}
