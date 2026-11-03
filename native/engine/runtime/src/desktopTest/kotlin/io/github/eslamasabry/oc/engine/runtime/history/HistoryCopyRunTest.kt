package io.github.eslamasabry.oc.engine.runtime.history

import io.github.eslamasabry.oc.engine.runtime.install.Sh
import io.github.eslamasabry.oc.engine.runtime.install.ShRun
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Port of test/opencode_history_copy_test.dart "the snippet itself, run in a scratch home": the engine's scripts run in
 * real `sh` on real files, with OpenCode 2's folder moved into the scratch home.
 */
class HistoryCopyRunTest {
    private lateinit var home: File
    private lateinit var oc1: File
    private lateinit var oc2: File

    @BeforeTest
    fun setUp() {
        home = Files.createTempDirectory("oc1copy").toFile()
        oc1 = File(home, ".local/share/opencode").apply { mkdirs() }
        oc2 = File(home, "oc2/data/opencode")
    }

    @AfterTest
    fun tearDown() {
        home.deleteRecursively()
    }

    private fun run(source: String = HistoryCopyScripts.COPY): ShRun =
        Sh.run(
            listOf("sh", "-c", source.replace(HistoryCopyScripts.OC2_DATA_DIR, oc2.path)),
            mapOf("HOME" to home.path, "PATH" to "/usr/local/bin:/usr/bin:/bin"),
        )

    private fun f(dir: File, name: String) = File(dir, name)

    @Test
    fun `B-LINUX-107 copies db, wal and shm while OpenCode 1 stays whole`() {
        f(oc1, "opencode.db").writeText("db")
        f(oc1, "opencode.db-wal").writeText("wal")
        f(oc1, "opencode.db-shm").writeText("shm")
        assertEquals(0, run().exitCode)
        assertEquals("db", f(oc2, "opencode.db").readText())
        assertEquals("wal", f(oc2, "opencode.db-wal").readText())
        assertEquals("shm", f(oc2, "opencode.db-shm").readText())
        assertEquals("db", f(oc1, "opencode.db").readText())
        assertTrue(f(oc1, "opencode.db-wal").exists())
        assertTrue(f(oc1, "opencode.db-shm").exists())
        assertEquals(
            setOf("opencode.db", "opencode.db-wal", "opencode.db-shm", HistoryCopyScripts.RECEIPT_NAME),
            oc2.list()!!.toSet(),
            "no scratch folder left behind",
        )
    }

    @Test
    fun `B-LINUX-107 a database alone is enough (no wal or shm yet)`() {
        f(oc1, "opencode.db").writeText("db")
        assertEquals(0, run().exitCode)
        assertEquals("db", f(oc2, "opencode.db").readText())
        assertFalse(f(oc2, "opencode.db-wal").exists())
    }

    @Test
    fun `B-LINUX-108 never overwrites an OpenCode 2 database that exists`() {
        oc2.mkdirs()
        f(oc2, "opencode.db").writeText("mine")
        f(oc1, "opencode.db").writeText("old")
        f(oc1, "opencode.db-wal").writeText("oldwal")
        assertEquals(0, run().exitCode)
        assertEquals("mine", f(oc2, "opencode.db").readText())
        assertFalse(f(oc2, "opencode.db-wal").exists())
    }

    @Test
    fun `B-LINUX-108 explicit move refuses two histories without changing either`() {
        oc2.mkdirs()
        for (suffix in listOf("", "-wal", "-shm")) {
            f(oc1, "opencode.db$suffix").writeText("old$suffix")
            f(oc2, "opencode.db$suffix").writeText("new$suffix")
        }
        val result = run(HistoryCopyScripts.SWITCH_CHECK)
        assertEquals(74, result.exitCode)
        assertTrue(HistoryCopyScripts.SWITCH_BLOCKED_TEXT in result.stderr)
        assertEquals(HistorySwitchAdmission.SeparateHistory, HistorySwitchAdmission.fromResult(result.exitCode, result.stderr))
        for (suffix in listOf("", "-wal", "-shm")) {
            assertEquals("old$suffix", f(oc1, "opencode.db$suffix").readText())
            assertEquals("new$suffix", f(oc2, "opencode.db$suffix").readText())
        }
    }

    @Test
    fun `B-LINUX-108 ordinary OpenCode 2 start stays available with both histories`() {
        oc2.mkdirs()
        f(oc1, "opencode.db").writeText("old")
        f(oc2, "opencode.db").writeText("new")
        assertEquals(0, run().exitCode)
        assertEquals("new", f(oc2, "opencode.db").readText())
    }

    @Test
    fun `B-LINUX-109 a completed copy can resume even after OpenCode 2 converted its target`() {
        f(oc1, "opencode.db").writeText("original")
        assertEquals(0, run().exitCode)
        f(oc2, "opencode.db").writeText("converted")
        val check = run(HistoryCopyScripts.SWITCH_CHECK)
        assertEquals(0, check.exitCode)
        assertEquals(HistorySwitchAdmission.Allowed, HistorySwitchAdmission.fromResult(check.exitCode, check.stderr))
        assertEquals("original", f(oc1, "opencode.db").readText())
        assertEquals("converted", f(oc2, "opencode.db").readText())
    }

    @Test
    fun `B-LINUX-109 a changed original cannot reuse a prior copy receipt`() {
        f(oc1, "opencode.db").writeText("original")
        assertEquals(0, run().exitCode)
        f(oc1, "opencode.db-wal").writeText("later chat")
        assertEquals(74, run(HistoryCopyScripts.SWITCH_CHECK).exitCode)
        assertEquals("later chat", f(oc1, "opencode.db-wal").readText())
    }

    @Test
    fun `B-LINUX-109 an interrupted verified sidecar placement safely resumes`() {
        f(oc1, "opencode.db").writeText("original")
        f(oc1, "opencode.db-wal").writeText("wal")
        assertEquals(0, run().exitCode)
        f(oc2, "opencode.db").delete()
        assertEquals(0, run().exitCode)
        assertEquals("original", f(oc2, "opencode.db").readText())
        assertEquals("wal", f(oc1, "opencode.db-wal").readText())
    }

    @Test
    fun `B-LINUX-109 a copy failure stops startup and preserves the source`() {
        f(oc1, "opencode.db").writeText("old")
        oc2.parentFile.mkdirs()
        oc2.writeText("not a directory")
        val result = run()
        assertEquals(74, result.exitCode)
        assertTrue("Your earlier conversations could not be copied" in result.stderr)
        assertTrue(HistoryCopyScripts.COPY_FAILED_MARKER in result.stderr)
        assertEquals("old", f(oc1, "opencode.db").readText())
        assertEquals("not a directory", oc2.readText())
    }

    @Test
    fun `B-LINUX-109 failed WAL placement never publishes the database or starts`() {
        f(oc1, "opencode.db").writeText("old")
        f(oc1, "opencode.db-wal").writeText("wal")
        val result = run("mv() { return 1; }\n${HistoryCopyScripts.COPY}\necho server-started")
        assertEquals(74, result.exitCode)
        assertFalse("server-started" in result.stdout)
        assertFalse(f(oc2, "opencode.db").exists())
        assertEquals("old", f(oc1, "opencode.db").readText())
        assertEquals("wal", f(oc1, "opencode.db-wal").readText())
    }

    @Test
    fun `B-LINUX-109 an orphaned target WAL refuses a fresh copy`() {
        oc2.mkdirs()
        f(oc1, "opencode.db").writeText("old")
        f(oc2, "opencode.db-wal").writeText("unresolved")
        assertEquals(74, run().exitCode)
        assertFalse(f(oc2, "opencode.db").exists())
        assertEquals("unresolved", f(oc2, "opencode.db-wal").readText())
    }

    @Test
    fun `B-LINUX-109 the in-app launcher prelude stops before the server when the copy fails`() {
        f(oc1, "opencode.db").writeText("old")
        oc2.parentFile.mkdirs()
        val prelude =
            HistoryCopyScripts.builtinServerPrelude
                .lines()
                .filterNot { it.startsWith("mkdir -p ") } // the scratch home has no /root
                .joinToString("\n")
        oc2.writeText("not a directory")
        val result = run("$prelude\necho server-started\n")
        assertEquals(74, result.exitCode)
        assertFalse("server-started" in result.stdout)
        assertEquals("old", f(oc1, "opencode.db").readText())
    }

    @Test
    fun `B-LINUX-110 explicit move permits a fresh target without modifying the source`() {
        f(oc1, "opencode.db").writeText("old")
        assertEquals(0, run(HistoryCopyScripts.SWITCH_CHECK).exitCode)
        assertEquals("old", f(oc1, "opencode.db").readText())
        assertFalse(oc2.exists())
    }

    @Test
    fun `B-LINUX-110 explicit move refuses a symlinked source database`() {
        val outside = File(home, "elsewhere").apply { writeText("old") }
        Files.createSymbolicLink(f(oc1, "opencode.db").toPath(), outside.toPath())
        val result = run(HistoryCopyScripts.SWITCH_CHECK)
        assertEquals(74, result.exitCode)
        assertEquals(HistorySwitchAdmission.Unavailable, HistorySwitchAdmission.fromResult(result.exitCode, result.stderr))
        assertEquals("old", outside.readText())
        assertFalse(oc2.exists())
    }

    @Test
    fun `B-LINUX-110 no OpenCode 1 database is a quiet no-op`() {
        val result = run()
        assertEquals(0, result.exitCode)
        assertEquals("", result.stdout + result.stderr)
        assertFalse(f(oc2, "opencode.db").exists())
    }

    @Test
    fun `B-LINUX-107 the engine's history scripts are lib-domain-opencode_history_copy-dart byte for byte`() {
        val path = "lib/domain/opencode_history_copy.dart"
        val provenance = Sh.dartRaw(path, "_historyCopyProvenance")
        assertEquals(provenance, HistoryCopyScripts.PROVENANCE)
        assertEquals(provenance + Sh.dartRaw(path, "openCode1HistoryCopyScript"), HistoryCopyScripts.COPY)
        assertEquals(provenance + Sh.dartRaw(path, "openCode1HistorySwitchCheckScript"), HistoryCopyScripts.SWITCH_CHECK)
        assertTrue("'${HistoryCopyScripts.SWITCH_BLOCKED_TEXT.take(40)}" in File(Sh.repo, path).readText())
    }
}
