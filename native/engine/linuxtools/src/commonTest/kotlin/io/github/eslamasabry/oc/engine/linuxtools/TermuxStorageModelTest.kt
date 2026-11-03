package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The model half of test/termux_storage_test.dart (lib/termux/storage.dart). */
class TermuxStorageModelTest {
    @Test
    fun `B-LINUX-248 byte and path formatting`() {
        assertEquals(SplitBytes("0", TermuxByteUnit.B), splitBytes(0))
        assertEquals(SplitBytes("2", TermuxByteUnit.KB), splitBytes(2048))
        assertEquals(SplitBytes("812", TermuxByteUnit.MB), splitBytes(812L * 1024 * 1024))
        assertEquals(SplitBytes("11.6", TermuxByteUnit.GB), splitBytes((11.6 * 1024 * 1024 * 1024).roundToLong()))
        assertEquals(SplitBytes("3", TermuxByteUnit.GB), splitBytes(3L * 1024 * 1024 * 1024))
        assertEquals(
            "ubuntu:/root/.gradle/caches",
            displayTermuxPath(
                "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/opencode-ubuntu/rootfs/root/.gradle/caches",
            ),
        )
        assertEquals("~/.npm", displayTermuxPath("/data/data/com.termux/files/home/.npm"))
        assertEquals("termux:/tmp", displayTermuxPath("/data/data/com.termux/files/usr/tmp"))
    }

    @Test
    fun `B-LINUX-248 status parsing tolerates an idle phone and a bare log`() {
        val idle = TermuxStorageScanStatus.parse("state=idle\n__OC_TOOLS_LOG__\n__OC_TOOLS_JSON__\n")
        assertEquals(TermuxStorageScanState.Idle, idle.state)
        assertNull(idle.report)
        val running =
            TermuxStorageScanStatus.parse("state=running\n__OC_TOOLS_LOG__\n[oc] Build caches\n__OC_TOOLS_JSON__\n")
        assertEquals(TermuxStorageScanState.Running, running.state)
        assertEquals("[oc] Build caches", running.log)
        assertEquals(TermuxStorageScanState.Idle, TermuxStorageScanStatus.parse("").state)
    }

    @Test
    fun `B-LINUX-248 the scan stage is the last heading the script printed`() {
        assertNull(TermuxStorageScanStage.reached(""))
        assertNull(TermuxStorageScanStage.reached("[oc] Measuring storage on this phone"))
        assertEquals(
            TermuxStorageScanStage.AgentScratch,
            TermuxStorageScanStage.reached(
                "[oc] Measuring storage on this phone\n[oc] Build caches\n" +
                    "[oc] Other caches and package data (read only)\n[oc] Agent scratch",
            ),
        )
        // A path that happens to read like a heading is not one.
        assertEquals(TermuxStorageScanStage.BuildCaches, TermuxStorageScanStage.reached("[oc] Build caches\n  Toolchains"))
    }

    @Test
    fun `B-LINUX-248 every scan stage heading is a log line of the bundled script`() {
        for (stage in TermuxStorageScanStage.entries) {
            assertTrue(TERMUX_TOOLS_SCRIPT.contains("log '${stage.heading}'"), "${stage.name} heading drifted from the script")
        }
    }

    private val reportJson =
        """{"scanned_at":1700000000,"total_bytes":5000,"cleanup_policy":2,"stale":false,
           "categories":[{"key":"build_caches","label_key":"termuxStorageCat_build_caches","bytes":3000,"deletable":true,
             "paths":[{"path":"/a","bytes":1000},{"path":"/b","bytes":2000},{"path":"","bytes":5}]},
             {"key":"projects","bytes":1500,"deletable":true,"paths":[]},
             {"key":"mystery","bytes":10,"deletable":false},{"bytes":1}],
           "projects":[{"name":"app","path":"/p/app","bytes":900,"build_bytes":400},{"name":""}]}"""

    @Test
    fun `B-LINUX-247 only build caches of a current report can be cleaned and a clean makes the report stale`() {
        val report = TermuxStorageReport.parse(reportJson)!!
        assertFalse(report.isStale)
        assertEquals(1700000000L, report.scannedAtEpochSeconds)
        assertEquals(listOf("build_caches", "projects", "mystery"), report.categories.map { it.key })
        val caches = report.category(TermuxStorageCategoryKey.BuildCaches)!!
        assertTrue(caches.canClean)
        assertEquals(listOf("/a", "/b"), caches.paths.map { it.path })
        // An old or forged report saying projects are deletable still cannot offer them.
        assertFalse(report.category(TermuxStorageCategoryKey.Projects)!!.canClean)
        assertNull(report.categories.last().known, "an unknown key stays visible under its raw key")
        assertEquals(3000L, report.deletableBytes)
        assertEquals(listOf("app"), report.projects.map { it.name })
        assertEquals(400L, report.projects.single().buildBytes)

        val after = report.afterClean("build_caches", listOf("/a"), 1000)
        assertTrue(after.isStale)
        assertEquals(4000L, after.totalBytes)
        assertEquals(2000L, after.category(TermuxStorageCategoryKey.BuildCaches)!!.bytes)
        assertEquals(0L, after.deletableBytes, "a stale report offers nothing")
        assertEquals(0L, report.afterClean("build_caches", emptyList(), 99999).totalBytes)
    }

    @Test
    fun `B-LINUX-247 legacy reports without the cleanup policy are stale and status keeps non-done reports stale`() {
        val legacy = TermuxStorageReport.parse(reportJson.replace("\"cleanup_policy\":2,\"stale\":false", "\"x\":1"))!!
        assertTrue(legacy.isStale)
        assertEquals(0L, legacy.deletableBytes)
        assertTrue(TermuxStorageReport.parse(reportJson.replace("\"stale\":false", "\"stale\":true"))!!.isStale)
        assertNull(TermuxStorageReport.parse("[1]"))
        val status = TermuxStorageScanStatus.parse("state=stale\n__OC_TOOLS_LOG__\nx\n__OC_TOOLS_JSON__\n$reportJson")
        assertEquals(TermuxStorageScanState.Stale, status.state)
        assertTrue(status.report!!.isStale)
        val done = TermuxStorageScanStatus.parse("state=done\n__OC_TOOLS_LOG__\nx\n__OC_TOOLS_JSON__\n$reportJson")
        assertFalse(done.report!!.isStale)
        assertNull(TermuxStorageScanStatus.parse("state=done\n__OC_TOOLS_LOG__\n__OC_TOOLS_JSON__\n{oops").report)
    }

    @Test
    fun `B-LINUX-247 summary and clean results parse`() {
        val summary = TermuxStorageSummary.parse("state=done\ntotal_bytes=42\nscanned_at=7\n")
        assertEquals(TermuxStorageScanState.Done, summary.state)
        assertEquals(42L, summary.totalBytes)
        assertEquals(7L, summary.scannedAtEpochSeconds)
        val idle = TermuxStorageSummary.parse("state=idle\ntotal_bytes=-1\nscanned_at=0\n")
        assertNull(idle.totalBytes)
        assertNull(idle.scannedAtEpochSeconds)
        val clean =
            TermuxStorageCleanResult.parse(
                "{\"freed_bytes\":10,\"rescan_required\":true,\"removed\":[{\"path\":\"/a\",\"bytes\":10}]," +
                    "\"refused\":[{\"path\":\"/b\",\"reason\":\"in_use\",\"processes\":[\"java\"]}]}",
            )!!
        assertEquals(10L, clean.freedBytes)
        assertTrue(clean.rescanRequired)
        assertEquals(listOf("java"), clean.inUse!!.processes)
        assertNull(TermuxStorageCleanResult.parse("[]"))
    }

    @Test
    fun `B-LINUX-247 the tools wrapper installs beside manager sh and dispatches a scan`() {
        val scan = termuxToolsCommandScript("storage-scan")
        assertTrue(scan.contains("TOOLS=\"$TERMUX_HOME/.oc/tools.sh\""))
        assertTrue(scan.contains("<<'OC_TOOLS_EOF'"))
        assertTrue(scan.contains("mv \"\$tools_tmp\" \"\$TOOLS\""))
        assertTrue(scan.contains("rm -f \"\$OC_DIR/storage-scan.cancel\""))
        assertTrue(scan.contains("nohup \"\$TOOLS\" storage-scan"))
        assertTrue(scan.contains("echo \"tools-started:\$!\""))
        assertTrue(scan.contains("tools_tmp=\"\$TOOLS.tmp.\$\$\""))
        assertTrue(scan.contains(TERMUX_TOOLS_SCRIPT))
        assertTrue(
            termuxToolsCommandScript("storage-clean", "build_caches")
                .endsWith("exec \"\$TOOLS\" storage-clean 'build_caches'\n"),
        )
        assertFailsWith<InvalidToolCommand> { termuxToolsCommandScript("rm") }
        assertFailsWith<InvalidToolCommand> { termuxToolsCommandScript("storage-clean", "../x") }
    }

    @Test
    fun `B-LINUX-247 the storage verbs run through the tools runner and an unacknowledged scan fails`() = runTest {
        var answer = "tools-started:123\n"
        val calls = mutableListOf<String>()
        val storage =
            TermuxStorage { verb, argument, _ ->
                calls += "$verb $argument".trim()
                answer.ok()
            }
        assertIs<Outcome.Ok<Unit>>(storage.startScan())
        answer = "tools-already-running:9"
        assertIs<Outcome.Ok<Unit>>(storage.startScan())
        answer = "nothing"
        assertIs<Outcome.Failed>(storage.startScan())
        answer = "{\"freed_bytes\":1}"
        assertEquals(1L, (storage.clean(TermuxStorageCategoryKey.BuildCaches) as Outcome.Ok).value.freedBytes)
        answer = "state=done\ntotal_bytes=5\n"
        assertEquals(5L, (storage.summary() as Outcome.Ok).value.totalBytes)
        assertEquals(
            listOf("storage-scan", "storage-scan", "storage-scan", "storage-clean build_caches", "storage-summary"),
            calls,
        )
    }
}
