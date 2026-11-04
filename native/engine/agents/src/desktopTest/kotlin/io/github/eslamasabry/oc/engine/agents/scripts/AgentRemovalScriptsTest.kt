package io.github.eslamasabry.oc.engine.agents.scripts

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentInstallRecipe
import io.github.eslamasabry.oc.engine.agents.catalog.AgentLimitation
import io.github.eslamasabry.oc.engine.agents.catalog.AgentResumeReason
import io.github.eslamasabry.oc.engine.agents.catalog.AgentRoute
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val IDS = listOf("codex", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx")

/**
 * The guest's files are owned by uid 1000; the files this test creates are owned by whoever runs it. Point the
 * script's ownership checks at the real owner and leave every other check as authored.
 */
private val hostUid: String = run(listOf("id", "-u")).stdout.trim()

private fun asHostOwner(script: String) = script
    .replace("entry.st_uid != 1000", "entry.st_uid != $hostUid")
    .replace("entry.st_uid not in (0, 1000)", "entry.st_uid not in (0, $hostUid)")

internal fun exists(path: String): Boolean = Files.exists(Paths.get(path), LinkOption.NOFOLLOW_LINKS)
internal fun isLink(path: String): Boolean = Files.isSymbolicLink(Paths.get(path))
internal fun link(path: String, target: String): Path = Files.createSymbolicLink(Paths.get(path), Paths.get(target))
internal fun linkTarget(path: String): String = Files.readSymbolicLink(Paths.get(path)).toString()
internal fun write(path: String, text: String) = File(path).also { it.parentFile.mkdirs() }.writeText(text)
internal fun read(path: String) = File(path).readText()
internal fun mkdirs(path: String) = File(path).mkdirs()

/** Isolated authored-script fixture: rewrite only fixed guest paths and proc in test input. */
private class Guest(val temp: File) {
    val agent: AgentDescriptor = AgentCatalog.builtIn.byId(AgentId("codex"))!!
    val home get() = "${temp.path}/home"
    val bin get() = "$home/.local/bin"
    val parent get() = "$home/.local/share/oc-agents"
    val base get() = "$parent/codex"
    val version get() = agent.recipe!!.version
    val launch get() = "$base/$version/launch"
    val link get() = "$bin/codex"
    val lock get() = "$parent/.lock-codex"
    val proc get() = "${temp.path}/proc"
    val receiptId = "a".repeat(32)
    val receiptPath get() = "$home/.local/share/oc-agent-removal-$receiptId"
    var freedBytes: Long? = null
    var alreadyAbsent: Boolean? = null

    private val kept get() = listOf(
        "$home/.oc-profiles/owner/claude/account",
        "$home/.oc-profiles/owner/codex/history",
        "$parent/claude/payload",
        "$home/.local/node/bin/node",
        "$home/.local/share/paseo/daemon",
    )

    fun prepare() {
        mkdirs("$base/$version/payload")
        mkdirs(bin)
        mkdirs(proc)
        write(launch, "authored launcher")
        write("$base/$version/payload/codex", "payload")
        link(link, launch)
        for (path in kept) write(path, "keep")
    }

    fun run(
        descriptor: AgentDescriptor? = null,
        uid: String? = null,
        environmentHome: String? = null,
        drainReceipt: Boolean = true,
    ): ShResult {
        var script = AgentRemovalScripts.remove(descriptor ?: agent, receiptId = receiptId)
            .replace("/home/oc", home)
            .replace("pathlib.Path('/proc')", "pathlib.Path('$proc')")
        script = asHostOwner(script).replace("os.getuid()", uid ?: "1000")
        val result = sh(
            listOf("-c", script),
            environment = mapOf("HOME" to (environmentHome ?: home), "PATH" to TEST_PATH),
            workingDirectory = temp,
            timeoutSeconds = 10,
        )
        if (result.exitCode == 0 && drainReceipt) {
            val read = readReceipt()
            assertEquals(0, read.exitCode)
            assertEquals("", read.stderr)
            val values = read.stdout.trim().split(' ')
            assertEquals(2, values.size)
            freedBytes = values[0].toLong()
            alreadyAbsent = values[1] == "1"
            assertFalse(exists(receiptPath))
        }
        return result
    }

    fun readReceipt(uid: String = "0"): ShResult = sh(
        listOf(
            "-c",
            asHostOwner(AgentRemovalScripts.readReceipt(receiptId)).replace("/home/oc", home).replace("os.getuid()", uid),
        ),
        environment = mapOf("HOME" to "/root", "PATH" to TEST_PATH),
        workingDirectory = temp,
        timeoutSeconds = 10,
    )

    fun expectKept() {
        for (path in kept) assertEquals("keep", read(path), path)
    }

    fun allocated(paths: List<String>): Long {
        val measured = run(listOf("du", "-s", "-B1") + paths)
        assertEquals(0, measured.exitCode)
        return measured.stdout.trim().split('\n').sumOf { it.split(Regex("\\s+")).first().toLong() }
    }
}

private fun receipt(result: ShResult, code: Int) {
    assertEquals(code, result.exitCode)
    assertEquals("::oc-check-begin agent-codex\n::oc-check-end agent-codex $code\n", result.stdout)
    assertEquals("", result.stderr)
}

/** Port of test/agent_removal_scripts_test.dart against [AgentRemovalScripts]. */
class AgentRemovalScriptsTest {
    private lateinit var temp: File
    private lateinit var guest: Guest

    @BeforeTest
    fun setUp() {
        temp = tempDir("oc-agent-removal-")
        guest = Guest(temp)
        guest.prepare()
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    @Test
    fun `B-LINUX-002 all six authored recipes are valid shell and Claude and OpenCode are excluded`() {
        for (id in IDS) {
            val script = AgentRemovalScripts.remove(AgentCatalog.builtIn.byId(AgentId(id))!!, receiptId = guest.receiptId)
            val file = File("${temp.path}/remove.sh").also { it.writeText(script) }
            assertEquals(0, sh(listOf("-n", file.path)).exitCode, id)
            assertFalse("kill(" in script)
            assertFalse("signOut" in script)
        }
        assertFailsWith<IllegalArgumentException> {
            AgentRemovalScripts.remove(AgentCatalog.builtIn.byId(AgentId("claude"))!!, receiptId = guest.receiptId)
        }
        assertFailsWith<IllegalArgumentException> {
            AgentRemovalScripts.remove(AgentCatalog.builtIn.byId(AgentId("opencode2"))!!, receiptId = guest.receiptId)
        }
    }

    @Test
    fun `B-LINUX-002 removes payload and partial staging, preserves accounts and shared tools`() {
        write("${guest.base}/${guest.version}.new/payload/partial", "download")
        write("${guest.base}/.downloads/pinned-partial", "cached bytes")
        mkdirs("${guest.base}/${guest.version}.old.123")
        link("${guest.link}.new.123", guest.launch)
        receipt(guest.run(), 0)
        assertFalse(exists(guest.base))
        assertFalse(exists(guest.link))
        assertFalse(exists("${guest.link}.new.123"))
        assertFalse(exists(guest.lock))
        guest.expectKept()
        receipt(guest.run(), 0)
    }

    @Test
    fun `B-LINUX-002 absent payload is idempotent and leaves shared directories alone`() {
        File(guest.base).deleteRecursively()
        Files.delete(Paths.get(guest.link))
        receipt(guest.run(), 0)
        assertFalse(exists(guest.lock))
        guest.expectKept()
    }

    @Test
    fun `B-LINUX-003 existing install lock is busy and never removed`() {
        write("${guest.lock}/foreign-owner", "keep")
        receipt(guest.run(), 16)
        assertEquals("keep", read("${guest.lock}/foreign-owner"))
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-LINUX-003 absent payload never clears a foreign install lock`() {
        File(guest.base).deleteRecursively()
        Files.delete(Paths.get(guest.link))
        write("${guest.lock}/foreign-owner", "keep")
        receipt(guest.run(), 16)
        assertTrue(File(guest.lock).isDirectory)
    }

    @Test
    fun `B-LINUX-003 empty abandoned install lock is removed with partial staging`() {
        mkdirs(guest.lock)
        write("${guest.base}/${guest.version}.new/partial", "partial")
        val expected = guest.allocated(listOf(guest.base, guest.link, guest.lock))
        receipt(guest.run(), 0)
        assertEquals(expected, guest.freedBytes)
        assertEquals(false, guest.alreadyAbsent)
        assertFalse(exists(guest.base))
        assertFalse(exists(guest.lock))
        guest.expectKept()
    }

    @Test
    fun `B-LINUX-003 absent empty abandoned lock returns its allocated bytes and not absent`() {
        File(guest.base).deleteRecursively()
        Files.delete(Paths.get(guest.link))
        mkdirs(guest.lock)
        val expected = guest.allocated(listOf(guest.lock))
        receipt(guest.run(), 0)
        assertEquals(expected, guest.freedBytes)
        assertEquals(false, guest.alreadyAbsent)
        assertFalse(exists(guest.lock))
        receipt(guest.run(), 0)
        assertEquals(0L, guest.freedBytes)
        assertEquals(true, guest.alreadyAbsent)
    }

    @Test
    fun `B-LINUX-003 live authored bash installer preserves empty lock and target`() {
        mkdirs(guest.lock)
        write("${guest.proc}/126/cmdline", "/bin/bash\u0000-c\u0000set -eu\noc_lock=${guest.lock}\nmkdir \"\$oc_lock\"\n\u0000")
        receipt(guest.run(), 16)
        assertTrue(exists(guest.launch))
        assertTrue(File(guest.lock).isDirectory)
        assertFalse(exists(guest.receiptPath))
    }

    @Test
    fun `B-SEC-005 unsafe lock links and non-directories refuse before deleting target`() {
        link(guest.lock, "${guest.home}/.oc-profiles/owner")
        receipt(guest.run(), 17)
        Files.delete(Paths.get(guest.lock))
        write(guest.lock, "foreign lock")
        receipt(guest.run(), 17)
        assertTrue(exists(guest.launch))
        guest.expectKept()
    }

    @Test
    fun `B-LINUX-004 allocated bytes deduplicate payload hardlinks and exclude retained inode`() {
        val payload = "${guest.base}/${guest.version}/payload"
        val outside = File("${temp.path}/retained").also { it.writeBytes(ByteArray(65536) { 7 }) }
        assertEquals(0, run(listOf("ln", "$payload/codex", "$payload/internal-copy")).exitCode)
        assertEquals(0, run(listOf("ln", outside.path, "$payload/retained-link")).exitCode)
        val retainedBytes = guest.allocated(listOf(outside.path))
        val expected = guest.allocated(listOf(guest.base, guest.link)) - retainedBytes
        receipt(guest.run(), 0)
        assertEquals(expected, guest.freedBytes)
        assertEquals(false, guest.alreadyAbsent)
        assertEquals(65536L, outside.length())
        guest.expectKept()
    }

    @Test
    fun `B-LINUX-004 nonce receipt is private, numeric only, and reader deletes it`() {
        val expected = guest.allocated(listOf(guest.base, guest.link))
        receipt(guest.run(drainReceipt = false), 0)
        val file = File(guest.receiptPath)
        assertEquals("{\"bytes\":$expected,\"absent\":0}", file.readText())
        assertEquals("600\n", run(listOf("stat", "-c", "%a", file.path)).stdout)
        val read = guest.readReceipt()
        assertEquals(0, read.exitCode)
        assertEquals("$expected 0\n", read.stdout)
        assertEquals("", read.stderr)
        assertFalse(file.exists())
        val repeat = guest.readReceipt()
        assertEquals(18, repeat.exitCode)
        assertEquals("", repeat.stdout)
    }

    @Test
    fun `B-LINUX-004 existing nonce file prevents removal rather than overwriting a receipt`() {
        write(guest.receiptPath, "keep existing")
        receipt(guest.run(), 17)
        assertEquals("keep existing", read(guest.receiptPath))
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-LINUX-004 reader rejects and cleans malformed bounded private receipts silently`() {
        for (value in listOf(
            "",
            "x".repeat(97),
            "{\"bytes\":true,\"absent\":0}",
            "{\"bytes\":-1,\"absent\":0}",
            "{\"bytes\":9223372036854775808,\"absent\":0}",
            "{\"bytes\":1,\"absent\":1}",
            "{\"bytes\":0,\"absent\":2}",
            "{\"bytes\":0,\"bytes\":1,\"absent\":0}",
            "{\"bytes\":0,\"absent\":0,\"extra\":\"synthetic-private\"}",
        )) {
            write(guest.receiptPath, value)
            run(listOf("chmod", "600", guest.receiptPath))
            val read = guest.readReceipt()
            assertEquals(18, read.exitCode, value)
            assertEquals("", read.stdout)
            assertEquals("", read.stderr)
            assertFalse(exists(guest.receiptPath))
        }
    }

    @Test
    fun `B-LINUX-004 reader never follows receipt links or deletes their retained target`() {
        val outside = File("${temp.path}/retained-receipt").also { it.writeText("{\"bytes\":0,\"absent\":1}") }
        link(guest.receiptPath, outside.path)
        val read = guest.readReceipt()
        assertEquals(18, read.exitCode)
        assertEquals("", read.stdout)
        assertEquals("{\"bytes\":0,\"absent\":1}", outside.readText())
        assertTrue(isLink(guest.receiptPath))
    }

    @Test
    fun `B-LINUX-004 reader requires root view and bounded signed integer projection`() {
        write(guest.receiptPath, "{\"bytes\":9223372036854775807,\"absent\":0}")
        run(listOf("chmod", "600", guest.receiptPath))
        val denied = guest.readReceipt(uid = "1000")
        assertEquals(18, denied.exitCode)
        assertEquals("", denied.stdout)
        assertTrue(exists(guest.receiptPath))
        val read = guest.readReceipt()
        assertEquals(0, read.exitCode)
        assertEquals("9223372036854775807 0\n", read.stdout)
        assertFalse(exists(guest.receiptPath))
    }

    @Test
    fun `B-LINUX-004 unsafe nonce values are rejected before becoming authored shell`() {
        for (id in listOf("a".repeat(31), "A".repeat(32), "../escape", "'; touch /tmp/escaped")) {
            assertFailsWith<IllegalArgumentException> { AgentRemovalScripts.remove(guest.agent, receiptId = id) }
            assertFailsWith<IllegalArgumentException> { AgentRemovalScripts.readReceipt(id) }
        }
    }

    @Test
    fun `B-LINUX-002 prior pinned-version authored launcher is removed within the same target`() {
        val old = "${guest.base}/0.159.0/launch"
        write(old, "older authored launcher")
        Files.delete(Paths.get(guest.link))
        link(guest.link, old)
        link("${guest.link}.new.123", old)
        receipt(guest.run(), 0)
        assertFalse(exists(guest.base))
        assertFalse(exists(guest.link))
        assertFalse(exists("${guest.link}.new.123"))
        guest.expectKept()
    }

    @Test
    fun `B-LINUX-003 absent payload with a surviving target PID remains busy`() {
        File(guest.base).deleteRecursively()
        Files.delete(Paths.get(guest.link))
        write("${guest.proc}/123/cmdline", "${guest.base}/${guest.version}/payload/codex\u0000")
        receipt(guest.run(), 16)
        assertTrue(exists("${guest.proc}/123/cmdline"))
        assertFalse(exists(guest.lock))
    }

    @Test
    fun `B-SEC-005 unrelated executable link refuses before any mutation`() {
        Files.delete(Paths.get(guest.link))
        link(guest.link, "${guest.home}/.local/node/bin/node")
        receipt(guest.run(), 17)
        assertEquals("${guest.home}/.local/node/bin/node", linkTarget(guest.link))
        assertTrue(exists(guest.launch))
        assertFalse(exists(guest.lock))
    }

    @Test
    fun `B-SEC-005 regular executable refuses before any mutation`() {
        Files.delete(Paths.get(guest.link))
        write(guest.link, "foreign executable")
        receipt(guest.run(), 17)
        assertEquals("foreign executable", read(guest.link))
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-SEC-005 staging symlink is unlinked without following its account target`() {
        mkdirs("${guest.base}/${guest.version}.new")
        link("${guest.base}/${guest.version}.new/escaped", "${guest.home}/.oc-profiles/owner")
        receipt(guest.run(), 0)
        assertFalse(exists(guest.base))
        assertFalse(exists(guest.link))
        guest.expectKept()
    }

    @Test
    fun `B-SEC-005 payload links are removed without following internal or external targets`() {
        val payload = "${guest.base}/${guest.version}/payload"
        link("$payload/internal", "codex")
        link("$payload/external", "${guest.home}/.oc-profiles/owner")
        val outside = File("${temp.path}/outside").also { it.writeText("keep outside") }
        link("$payload/external-file", outside.path)
        receipt(guest.run(), 0)
        assertFalse(exists(guest.base))
        assertEquals("keep outside", outside.readText())
        guest.expectKept()
    }

    @Test
    fun `B-SEC-005 symlinked target root refuses before any mutation`() {
        File(guest.base).deleteRecursively()
        link(guest.base, "${guest.home}/.oc-profiles/owner")
        receipt(guest.run(), 17)
        assertEquals("${guest.home}/.oc-profiles/owner", linkTarget(guest.base))
        guest.expectKept()
    }

    @Test
    fun `B-SEC-005 symlinked bin ancestor refuses before any mutation`() {
        assertTrue(File(guest.bin).renameTo(File("${guest.bin}-saved")))
        link(guest.bin, "${guest.bin}-saved")
        receipt(guest.run(), 17)
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-SEC-005 foreign staging launcher refuses all cleanup`() {
        link("${guest.link}.new.123", "${guest.home}/.local/node/bin/node")
        receipt(guest.run(), 17)
        assertTrue(isLink(guest.link))
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-SEC-005 non-PID staging launcher refuses all cleanup`() {
        link("${guest.link}.new.foreign", guest.launch)
        receipt(guest.run(), 17)
        assertTrue(isLink(guest.link))
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-LINUX-003 exact target PID is busy without signalling or deleting anything`() {
        mkdirs("${guest.proc}/123")
        File("${guest.proc}/123/cmdline").writeBytes(
            "/usr/bin/node".toByteArray() + 0 + "${guest.base}/${guest.version}/payload/codex".toByteArray() + 0,
        )
        receipt(guest.run(), 16)
        assertTrue(exists(guest.launch))
        assertTrue(exists("${guest.proc}/123/cmdline"))
        assertFalse(exists(guest.lock))
    }

    @Test
    fun `B-LINUX-003 host-rootfs executable identity also blocks removal`() {
        write("${guest.proc}/124/cmdline", "codex\u0000")
        link("${guest.proc}/124/exe", "/data/rootfs${guest.base}/${guest.version}/payload/codex")
        receipt(guest.run(), 16)
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-LINUX-003 other agent processes never block or get removed`() {
        write("${guest.proc}/125/cmdline", "${guest.parent}/claude/payload\u0000")
        receipt(guest.run(), 0)
        assertTrue(exists("${guest.proc}/125/cmdline"))
        guest.expectKept()
    }

    @Test
    fun `B-SEC-005 wrong UID or HOME refuses before any mutation`() {
        receipt(guest.run(uid = "0"), 17)
        receipt(guest.run(environmentHome = "/root"), 17)
        assertTrue(exists(guest.launch))
    }

    @Test
    fun `B-SEC-005 unsafe catalog version and executable mapping cannot become shell input`() {
        fun descriptor(executable: String, version: String) = AgentDescriptor(
            id = AgentId("codex"),
            name = "Codex",
            iconKey = "codex",
            route = AgentRoute.PaseoNative,
            providerId = "codex",
            signInMethod = AgentSignInMethod.None,
            resumeReason = AgentResumeReason.ResumeUnverified,
            availability = AgentAvailability.Limited,
            limitation = AgentLimitation.NeedsPhoneCheck,
            recipe = AgentInstallRecipe(version = version, executable = executable, artifacts = guest.agent.recipe!!.artifacts),
        )
        assertFailsWith<IllegalArgumentException> {
            AgentRemovalScripts.remove(descriptor("claude", guest.version), receiptId = guest.receiptId)
        }
        assertFailsWith<IllegalArgumentException> {
            AgentRemovalScripts.remove(descriptor("codex", "v'; touch /tmp/escaped"), receiptId = guest.receiptId)
        }
        assertFailsWith<IllegalArgumentException> {
            AgentRemovalScripts.remove(descriptor("codex", "../owner"), receiptId = guest.receiptId)
        }
    }

    @Test
    fun `B-LINUX-002 the removal script text equals the Flutter source byte for byte`() {
        val dart = dartRawStrings("lib/builtin/agents/agent_removal_scripts.dart")
        assertEquals(dart.getValue("_remove"), AgentRemovalScripts.REMOVE_PY)
        assertEquals(dart.getValue("_readReceipt"), AgentRemovalScripts.READ_RECEIPT_PY)
    }
}
