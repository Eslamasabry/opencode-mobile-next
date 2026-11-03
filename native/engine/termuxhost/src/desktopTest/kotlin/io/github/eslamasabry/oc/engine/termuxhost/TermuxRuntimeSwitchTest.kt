package io.github.eslamasabry.oc.engine.termuxhost

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** test/termux_runtime_switch_test.dart: the manager's switch_runtime run for real against a fixture home. */
class TermuxRuntimeSwitchTest {
    @AfterTest
    fun cleanUp() = Shell.cleanUp()

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun home(legacyTwo: Boolean = false): File {
        val home = Shell.temp("oc-switch-test-")
        val oc = File(home, ".oc").apply { mkdirs() }
        File(oc, "runtime").writeText(if (legacyTwo) "opencode2" else "opencode1")
        File(oc, "server.password").writeText(if (legacyTwo) "two-fixture" else "one-fixture")
        File(oc, "binary-opencode1").writeText("1.18.29")
        if (legacyTwo) File(oc, "binary-opencode2").writeText("0.0.0-beta-18600")
        File(oc, "original-data").writeText("original history fixture")
        return home
    }

    private fun run(home: File, body: String): Shell.Result {
        val manager = TermuxScripts.managerScript
        File(home, ".oc/manager.sh").apply {
            writeText(manager.replaceFirst("#!/data/data/com.termux/files/usr/bin/bash", "#!/usr/bin/env bash"))
            setExecutable(true)
        }
        val probe = File(home, "probe.sh")
        probe.writeText("${Shell.managerFunctions()}\n${Shell.resource("switch-mocks.sh")}\n$body\n")
        return Shell.bashFile(probe, env = mapOf("HOME" to home.path))
    }

    private fun switch(target: String, password: String, before: String = "") =
        Shell.fill(
            Shell.resource("switch-template.sh"),
            "before" to before,
            "_quote(password)" to quote(password),
            "_quote(target)" to quote(target),
        )

    private fun read(home: File, name: String) = File(home, ".oc/$name").readText()

    @Test
    fun `B-LINUX-241 switch dispatch acknowledges while its detached manager is still running`() {
        val home = home()
        val release = File(home, ".oc/release-dispatch-fixture")
        val finished = File(home, ".oc/dispatch-fixture-finished")
        val source =
            TermuxScripts.restartScript(
                operationId = "dispatch-fixture",
                switchTarget = TermuxRuntime.OpenCode2,
                switchPassword = "fixture-only",
            ).replaceFirst(TermuxScripts.managerScript, Shell.resource("held-manager.sh"))
                .replace(TermuxScripts.HOME, home.path)
        val script = File(home, "dispatch.sh").apply { writeText(source) }
        val started = System.nanoTime()
        try {
            val result = Shell.bashFile(script, env = mapOf("HOME" to home.path))
            assertEquals(0, result.exitCode, result.toString())
            assertTrue((System.nanoTime() - started) / 1_000_000 < 4_000, "the dispatch waited for the manager")
            assertTrue(TermuxScripts.isLaunchAcknowledged(result.stdout))
            assertFalse(finished.exists(), "The manager has not been released yet")
        } finally {
            release.writeText("release")
            var attempt = 0
            while (attempt < 200 && !finished.exists()) {
                Thread.sleep(20)
                attempt++
            }
            assertTrue(finished.exists(), "The exact fixture manager must finish before cleanup")
        }
    }

    @Test
    fun `B-LINUX-241 switch rotates prior output under ownership and preserves bounded history on reopen`() {
        val home = home()
        val oc = File(home, ".oc")
        File(oc, "install.log").writeText("OLD_INSTALL_HEAD\n${"x".repeat(1048576)}\nOLD_INSTALL_TAIL\n")
        File(oc, "install.log.1").writeText("OLDER_HISTORY\n")
        File(oc, "install.log.2").writeText("OLDEST_DROPPED\n")
        val result =
            run(home, switch("opencode2", "two-fixture", before = Shell.resource("switch-rotate-before.sh")) +
                "\nprintf 'CURRENT_SWITCH_COMPLETE\\n'\n")
        assertEquals(0, result.exitCode, result.toString())
        val active = read(home, "install.log")
        assertTrue(active.contains("CURRENT_SWITCH_INSTALL"))
        assertTrue(active.contains("CURRENT_SWITCH_COMPLETE"))
        assertFalse(active.contains("OLD_INSTALL"))
        assertTrue(read(home, "install.log.1").endsWith("OLD_INSTALL_TAIL\n"))
        assertEquals("OLDER_HISTORY\n", read(home, "install.log.2"))
        val logs = oc.listFiles()!!.filter { it.isFile && it.name.contains("install.log") }
        assertEquals(3, logs.size)
        val before = logs.associate { it.path to it.readBytes() }
        for (log in logs) assertTrue(log.length() <= 1048576, log.path)
        assertEquals(0, run(home, "status").exitCode)
        for ((path, bytes) in before) assertContentEquals(bytes, File(path).readBytes())
    }

    @Test
    fun `B-LINUX-241 a switch without lock ownership does not rotate another operation output`() {
        val home = home()
        val active = File(home, ".oc/install.log").apply { writeText("OWNED_BY_OTHER_OPERATION") }
        val result = run(home, switch("opencode2", "two-fixture", before = "claim_direct_lock() { return 75; }"))
        assertEquals(75, result.exitCode)
        assertEquals("OWNED_BY_OTHER_OPERATION", active.readText())
        assertFalse(File(home, ".oc/install.log.1").exists())
    }

    @Test
    fun `B-LINUX-241 explicit switch installs OC2 and preserves original binary, data and credential`() {
        val home = home()
        val result = run(home, switch("opencode2", "two-fixture"))
        assertEquals(0, result.exitCode, result.toString())
        assertEquals("opencode2", read(home, "runtime"))
        assertEquals("two-fixture", read(home, "server.password"))
        assertEquals("one-fixture", read(home, "password-opencode1"))
        assertEquals("1.18.29", read(home, "binary-opencode1"))
        assertEquals("original history fixture", read(home, "original-data"))
        assertEquals("isolated", read(home, "opencode2-data-mode"))
        assertFalse(File(home, ".oc/runtime-switch").exists())
        assertFalse(result.stdout.contains("fixture"))
    }

    @Test
    fun `B-LINUX-241 return to OC1 is refused and the migrated runtime stays intact`() {
        val home = home()
        assertEquals(0, run(home, switch("opencode2", "two-fixture")).exitCode)
        val result = run(home, switch("opencode1", "one-fixture"))
        assertEquals(64, result.exitCode)
        assertEquals("opencode2", read(home, "runtime"))
        assertEquals("two-fixture", read(home, "server.password"))
    }

    @Test
    fun `B-LINUX-241 failures leave durable history and permit retrying the OC2 move`() {
        for (failure in listOf("FAIL_INSTALL=1", "FAIL_START=1", "REFUSE_STOP=1")) {
            val home = home()
            val result = run(home, switch("opencode2", "two-fixture", before = failure))
            assertNotEquals(0, result.exitCode, failure)
            val journal = read(home, "runtime-switch")
            assertTrue(journal.contains("switch_previous=opencode1"), failure)
            assertTrue(journal.contains("switch_target=opencode2"), failure)
            assertEquals("one-fixture", read(home, "password-opencode1"), failure)
            val restored = run(home, switch("opencode2", "two-fixture"))
            assertEquals(0, restored.exitCode, "$failure: $restored")
            assertEquals("opencode2", read(home, "runtime"))
            assertEquals("two-fixture", read(home, "server.password"))
            assertFalse(File(home, ".oc/runtime-switch").exists())
        }
    }

    @Test
    fun `B-LINUX-241 interruption between password and marker writes never overwrites the saved old credential`() {
        val home = home()
        File(home, ".oc/password-opencode1").writeText("one-fixture")
        File(home, ".oc/server.password").writeText("two-fixture")
        File(home, ".oc/runtime-switch")
            .writeText("switch_previous=opencode1\nswitch_target=opencode2\nswitch_phase=stopping\n")
        val result = run(home, switch("opencode2", "two-fixture"))
        assertEquals(0, result.exitCode, result.toString())
        assertEquals("one-fixture", read(home, "password-opencode1"))
        assertEquals("two-fixture", read(home, "server.password"))
    }

    @Test
    fun `B-LINUX-241 pre-existing first-run OC2 cannot launch OC1 against its default data`() {
        val home = home(legacyTwo = true)
        val result = run(home, switch("opencode1", "one-fixture"))
        assertNotEquals(0, result.exitCode)
        assertEquals("opencode2", read(home, "runtime"))
        assertEquals("two-fixture", read(home, "server.password"))
        assertFalse(File(home, ".oc/opencode2-data-mode").exists())
        assertFalse(File(home, ".oc/stops").exists())
    }

    @Test
    fun `B-LINUX-241 unknown data mode refuses switch before touching the managed server`() {
        val home = home()
        File(home, ".oc/opencode2-data-mode").writeText("unreadable")
        assertNotEquals(0, run(home, switch("opencode2", "two-fixture")).exitCode)
        assertFalse(File(home, ".oc/stops").exists())
    }

    @Test
    fun `B-LINUX-241 return refuses changed saved credentials before stopping the target server`() {
        val home = home()
        assertEquals(0, run(home, switch("opencode2", "two-fixture")).exitCode)
        val stops = read(home, "stops")
        assertNotEquals(0, run(home, switch("opencode1", "edited-fixture")).exitCode)
        assertEquals(stops, read(home, "stops"))
        assertEquals("one-fixture", read(home, "password-opencode1"))
        assertEquals("two-fixture", read(home, "server.password"))
    }

    @Test
    fun `B-LINUX-241 a move with both OpenCode databases refuses before stopping and keeps both`() {
        val home = home()
        val one = File(home, ".local/share/opencode/opencode.db").apply { parentFile.mkdirs(); writeText("older history") }
        val two = File(home, "oc2-root/data/opencode/opencode.db").apply { parentFile.mkdirs(); writeText("beta history") }
        val result = run(home, switch("opencode2", "two-fixture"))
        assertNotEquals(0, result.exitCode)
        assertTrue(File(home, ".oc/proot-calls").exists())
        assertFalse(File(home, ".oc/stops").exists())
        assertFalse(File(home, ".oc/installed").exists())
        assertEquals("opencode1", read(home, "runtime"))
        assertEquals("one-fixture", read(home, "server.password"))
        assertEquals("older history", one.readText())
        assertEquals("beta history", two.readText())
    }

    @Test
    fun `B-LINUX-241 persisted ready commits only the matching switch operation`() {
        val result = run(home(), Shell.resource("switch-persisted-ready.sh"))
        assertEquals(0, result.exitCode, result.toString())
        assertTrue(result.stdout.contains("switch_previous=opencode1"))
    }

    @Test
    fun `B-LINUX-241 a pending journal suppresses automatic recovery and restores return metadata`() {
        val status =
            TermuxSetupStatus.parse(
                "phase=failed\nrunner=proot\nport=4096\nfailure_kind=crash\nruntime=opencode2\nswitch_previous=opencode1\n" +
                    "switch_target=opencode2\nswitch_phase=starting\nswitch_return=opencode1\n",
            )!!
        assertTrue(status.switchPending)
        assertFalse(status.canRecover)
        assertTrue(status.switchReturnAvailable)
        assertEquals(TermuxRuntime.OpenCode1, status.switchPrevious)
        assertFalse(TermuxSetupStatus.parse("phase=ready\nruntime=opencode2")!!.switchReturnAvailable)
    }

    @Test
    fun `B-LINUX-241 a switch requires the explicit target credential and validates the operation identity`() {
        assertFailsWith<IllegalArgumentException> {
            TermuxScripts.restartScript(operationId = "fixture", switchTarget = TermuxRuntime.OpenCode2)
        }
        assertFailsWith<IllegalArgumentException> {
            TermuxScripts.restartScript(operationId = "../bad", switchTarget = TermuxRuntime.OpenCode2, switchPassword = "fixture")
        }
        val script =
            TermuxScripts.restartScript(operationId = "fixture", switchTarget = TermuxRuntime.OpenCode2, switchPassword = "fixture'quoted")
        assertTrue(script.contains("manager-started:%s"))
        assertTrue(script.contains("managed-operation-owner-changed"))
    }
}
