package io.github.eslamasabry.oc.engine.runtime.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of the script-text facts of test/opencode_history_copy_test.dart (the runs on real files are in desktopTest). */
class HistoryCopyScriptsTest {
    private val copy = HistoryCopyScripts.COPY

    @Test
    fun `B-LINUX-107 the in-app OpenCode 2 start copies before the first start, after the isolated folder exists`() {
        val prelude = HistoryCopyScripts.builtinServerPrelude
        val server = prelude + "exec opencode2 serve --hostname 127.0.0.1 --port 4097\n"
        assertTrue(copy in server)
        assertTrue(server.indexOf(copy) < server.indexOf("exec opencode2 serve"))
        assertTrue(server.indexOf("mkdir -p /root/.oc-opencode2/data/opencode") < server.indexOf(copy))
        assertTrue(server.indexOf("export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db") < server.indexOf(copy))
    }

    @Test
    fun `B-LINUX-107 the Termux runner copies inside Ubuntu, piped to the server log`() {
        val step = HistoryCopyScripts.termuxRunnerStep
        val copyAt = step.indexOf(copy)
        assertTrue(copyAt > 0)
        assertTrue(step.lastIndexOf("proot-distro login opencode-ubuntu -- sh -s", copyAt) > 0)
        assertTrue(step.indexOf("mkdir -p /root/.oc-opencode2/data/opencode") < copyAt)
        assertTrue(step.endsWith(copy + "OC_HISTORY_COPY\n"))
    }

    @Test
    fun `B-LINUX-109 copy failures cannot be swallowed by either launcher`() {
        assertTrue("(\n$copy) || exit 74" in HistoryCopyScripts.builtinServerPrelude)
        assertTrue(
            "<<'OC_HISTORY_COPY' | \"\$manager\" write-log server || exit 74" in HistoryCopyScripts.termuxRunnerStep,
        )
        assertEquals(74, HistoryCopyScripts.FAILED_EXIT)
        assertTrue("history_copy_failed() {\n    echo '${HistoryCopyScripts.COPY_FAILED_MARKER}" in copy)
    }

    @Test
    fun `B-LINUX-108 the guard never overwrites and copies all three files, never moving or deleting the original`() {
        assertTrue("[ ! -e \"\$oc2_dir/opencode.db\" ]" in copy)
        for (name in HistoryCopyScripts.HISTORY_FILES) assertTrue("cp \"\$oc1_dir/$name\"" in copy, name)
        assertFalse("mv \"\$oc1_dir" in copy)
        assertFalse("rm -f \"\$oc1_dir" in copy)
        assertEquals(listOf("opencode.db", "opencode.db-wal", "opencode.db-shm"), HistoryCopyScripts.HISTORY_FILES)
    }

    @Test
    fun `B-LINUX-109 provenance is committed before the database, which is placed last`() {
        val receipt = copy.indexOf("mv \"\$stage/.oc1-import-source.sha256\" \"\$oc2_dir/.oc1-import-source.sha256\"")
        val wal = copy.indexOf("mv \"\$stage/opencode.db-wal\" \"\$oc2_dir/opencode.db-wal\"")
        val db = copy.indexOf("mv \"\$stage/opencode.db\" \"\$oc2_dir/opencode.db\"")
        assertTrue(receipt in 0 until wal && wal < db)
        assertTrue(HistoryCopyScripts.RECEIPT_NAME in HistoryCopyScripts.PROVENANCE)
        assertTrue(copy.startsWith(HistoryCopyScripts.PROVENANCE))
        assertTrue(HistoryCopyScripts.SWITCH_CHECK.startsWith(HistoryCopyScripts.PROVENANCE))
    }

    @Test
    fun `B-LINUX-110 the switch check is read-only and refuses symlinked or unreadable locations`() {
        val check = HistoryCopyScripts.SWITCH_CHECK.removePrefix(HistoryCopyScripts.PROVENANCE)
        for (write in listOf("cp ", "mv ", "rm ", "mkdir ", "printf '%s\\n' \"\$copied")) assertFalse(write in check, write)
        assertTrue("[ -L \"\$history_dir/opencode.db\" ]" in check)
        assertTrue(HistoryCopyScripts.SWITCH_BLOCKED_TEXT in check)
        assertTrue("${HistoryCopyScripts.SWITCH_BLOCKED_MARKER} ${HistoryCopyScripts.SWITCH_BLOCKED_TEXT}" in check)
    }
}
