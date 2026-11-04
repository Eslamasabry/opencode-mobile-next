@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.PaseoCapabilities
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.plus
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from test/paseo_files_test.dart (Files, Changes, Worktrees, Terminal). */
@OptIn(ExperimentalEncodingApi::class)
class PaseoWorkspaceTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private fun Outcome<*>.failure(): Failure = assertIs<Outcome.Failed>(this, "expected a failure, got $this").failure

    private fun TestScope.harness() = GatewayHarness(this)

    private fun entry(name: String, kind: String, path: String = name) =
        mapOf("name" to name, "path" to path, "kind" to kind, "size" to 12, "modifiedAt" to "2026-10-09T10:00:00.000Z")

    private fun listing(path: String, entries: List<Any> = emptyList()) = jsonOf(
        "cwd" to DIR, "path" to path, "mode" to "list", "directory" to mapOf("path" to path, "entries" to entries), "file" to null, "error" to null,
    )

    // ---- B-AGENT-156 Files

    @Test
    fun `B-AGENT-156 Paseo turns Files, Changes, Worktrees and Terminal on, but not Reset or chat shell`() = runTest {
        val h = harness()
        for (caps in listOf(PaseoCapabilities.Base, h.gateway.capabilities.value)) {
            assertTrue(caps.fileBrowsing)
            assertTrue(caps.sessionDiff)
            assertTrue(caps.worktreeCreate)
            assertTrue(caps.terminal)
            assertFalse(caps.worktreeReset)
            assertFalse(caps.conversationShell)
        }
    }

    @Test
    fun `B-AGENT-156 listing a folder asks the daemon for it and puts folders first`() = runTest {
        val h = harness()
        h.daemon.handlers["file_explorer_request"] = {
            "file_explorer_response" to listing(
                "lib",
                listOf(entry("main.dart", "file", "lib/main.dart"), entry("src", "directory", "lib/src"), entry("App.dart", "file", "lib/App.dart")),
            )
        }
        val nodes = h.gateway.listFiles("lib").ok()
        assertEquals(listOf("src", "App.dart", "main.dart"), nodes.map { it.name })
        assertTrue(nodes.first().isDir)
        assertEquals("lib/src", nodes.first().path)
        val sent = h.daemon.of("file_explorer_request").single()
        assertEquals(DIR, sent.str("cwd"))
        assertEquals("lib", sent.str("path"))
        assertEquals("list", sent.str("mode"))
    }

    @Test
    fun `B-AGENT-156 the project root and absolute paths become relative ones`() = runTest {
        val h = harness()
        h.daemon.handlers["file_explorer_request"] = { "file_explorer_response" to listing(".") }
        h.gateway.listFiles("").ok()
        h.gateway.listFiles("$DIR/lib/").ok()
        h.gateway.listFiles(DIR).ok()
        assertEquals(listOf(".", "lib/", "."), h.daemon.of("file_explorer_request").map { it.str("path") })
    }

    private fun file(path: String, kind: String, encoding: String, content: String?, mime: String) = jsonOf(
        "path" to path, "kind" to kind, "encoding" to encoding, "content" to content, "mimeType" to mime, "size" to 3,
        "modifiedAt" to "2026-10-09T10:00:00.000Z",
    )

    @Test
    fun `B-AGENT-156 reading a text file returns its text, with a size limit sent`() = runTest {
        val h = harness()
        h.daemon.handlers["file_explorer_request"] = {
            "file_explorer_response" to jsonOf(
                "cwd" to DIR, "path" to "a.txt", "mode" to "file", "directory" to null,
                "file" to file("a.txt", "text", "utf-8", "hello\nworld", "text/plain"), "error" to null,
            )
        }
        val content = h.gateway.fileContent("$DIR/a.txt").ok()
        assertEquals("hello\nworld", content.content)
        assertEquals("text", content.type)
        val sent = h.daemon.of("file_explorer_request").single()
        assertEquals("a.txt", sent.str("path"))
        assertEquals("file", sent.str("mode"))
        assertTrue((sent["maxBytes"] as kotlinx.serialization.json.JsonPrimitive).content.toLong() > 0)
    }

    @Test
    fun `B-AGENT-156 a picture comes back as base64 and a binary has no text`() = runTest {
        val h = harness()
        h.daemon.handlers["file_explorer_request"] = { m ->
            val png = m.str("path") == "p.png"
            "file_explorer_response" to jsonOf(
                "cwd" to DIR, "path" to m.str("path"), "mode" to "file", "directory" to null, "error" to null,
                "file" to if (png) file("p.png", "image", "base64", "AAEC", "image/png") else file("x.bin", "binary", "none", null, "application/octet-stream"),
            )
        }
        val image = h.gateway.fileContent("p.png").ok()
        assertEquals("base64", image.encoding)
        assertContentEquals(byteArrayOf(0, 1, 2), Base64.decode(image.content))
        val binary = h.gateway.fileContent("x.bin").ok()
        assertEquals("binary", binary.type)
        assertEquals("", binary.content)
    }

    @Test
    fun `B-AGENT-156 a picture the agent saved outside the project is read from the root`() = runTest {
        val h = harness()
        val name = "ab12".repeat(16) + ".png"
        val shot = "/tmp/paseo-attachments-x1/$name"
        h.daemon.handlers["file_explorer_request"] = { m ->
            val list = m.str("mode") == "list"
            "file_explorer_response" to jsonOf(
                "cwd" to m.str("cwd"), "path" to m.str("path"), "mode" to m.str("mode"), "error" to null,
                "directory" to if (list) mapOf("path" to "/tmp/paseo-attachments-x1", "entries" to listOf(entry(name, "file", shot))) else null,
                "file" to if (!list) file(shot, "image", "base64", "AAEC", "image/png") else null,
            )
        }
        assertEquals(name, h.gateway.listFiles("/tmp/paseo-attachments-x1").ok().single().name)
        assertContentEquals(byteArrayOf(0, 1, 2), Base64.decode(h.gateway.fileContent(shot).ok().content))
        h.gateway.fileContent("$DIR/lib/a.png").ok()
        assertEquals(
            listOf("/" to "/tmp/paseo-attachments-x1", "/" to shot, DIR to "lib/a.png"),
            h.daemon.of("file_explorer_request").map { it.str("cwd") to it.str("path") },
        )
    }

    @Test
    fun `B-AGENT-156 a refused read says it plainly, never the daemon text`() = runTest {
        val h = harness()
        h.daemon.handlers["file_explorer_request"] = {
            "file_explorer_response" to jsonOf(
                "cwd" to DIR, "path" to "big.log", "mode" to "file", "directory" to null, "file" to null,
                "error" to "File is too large to display /secret/path",
            )
        }
        val failure = h.gateway.fileContent("big.log").failure()
        assertNull(failure.details)
        assertFalse("/secret" in failure.toString())
    }

    @Test
    fun `B-AGENT-156 finding by name lists files only`() = runTest {
        val h = harness()
        h.daemon.handlers["directory_suggestions_request"] = {
            "directory_suggestions_response" to jsonOf(
                "directories" to emptyList<String>(),
                "entries" to listOf(mapOf("path" to "lib/main.dart", "kind" to "file"), mapOf("path" to "lib", "kind" to "directory")),
                "error" to null,
            )
        }
        assertEquals(listOf("lib/main.dart"), h.gateway.findFile("main").ok())
        val sent = h.daemon.of("directory_suggestions_request").single()
        assertEquals(DIR, sent.str("cwd"))
        assertEquals("true", sent["includeFiles"].toString())
        assertEquals("main", sent.str("query"))
        assertEquals(emptyList(), h.gateway.findFile("  ").ok())
        assertEquals(1, h.daemon.of("directory_suggestions_request").size)
    }

    @Test
    fun `B-AGENT-156 a folder change while a listing runs drops the answer`() = runTest {
        val h = harness()
        val pending = async { h.gateway.listFiles("") }
        h.pump()
        val request = h.daemon.of("file_explorer_request").single()
        h.gateway.setLocation(ServerLocation(directory = "/work/other"))
        h.pump()
        h.daemon.push("file_explorer_response", listing(".") + mapOf("requestId" to request["requestId"]!!))
        val failure = pending.await().failure()
        assertEquals(Failure.Refused(RefusalReason.StaleScope), failure)
    }

    // ---- B-AGENT-157 Changes

    private fun diffFile(path: String, isNew: Boolean = false, isDeleted: Boolean = false, status: String? = null): Map<String, Any?> = buildMap {
        put("path", path)
        put("isNew", isNew)
        put("isDeleted", isDeleted)
        put("additions", 2)
        put("deletions", 1)
        if (status != null) put("status", status)
        put(
            "hunks",
            listOf(
                mapOf(
                    "oldStart" to 1, "oldCount" to 2, "newStart" to 1, "newCount" to 3,
                    "lines" to listOf(
                        mapOf("type" to "header", "content" to "@@ -1,2 +1,3 @@"),
                        mapOf("type" to "context", "content" to "keep"),
                        mapOf("type" to "remove", "content" to "old"),
                        mapOf("type" to "add", "content" to "new"),
                        mapOf("type" to "add", "content" to "newer"),
                    ),
                ),
            ),
        )
    }

    private fun GatewayHarness.answerDiff(files: List<Map<String, Any?>>) {
        daemon.handlers["checkout.diff.get.request"] = { "checkout.diff.get.response" to jsonOf("cwd" to DIR, "files" to files, "error" to null) }
    }

    @Test
    fun `B-AGENT-157 the working tree diff becomes a unified patch per file`() = runTest {
        val h = harness()
        h.answerDiff(listOf(diffFile("lib/a.dart")))
        val diffs = PaseoOperations(h.gateway).listVcsDiffs(VcsDiffMode.WorkingTree).ok()
        assertEquals("lib/a.dart", diffs.single().file)
        assertEquals(2, diffs.single().additions)
        assertEquals(1, diffs.single().deletions)
        assertEquals("--- a/lib/a.dart\n+++ b/lib/a.dart\n@@ -1,2 +1,3 @@\n keep\n-old\n+new\n+newer\n", diffs.single().patch)
        val sent = h.daemon.of("checkout.diff.get.request").single()
        assertEquals(DIR, sent.str("cwd"))
        assertEquals("uncommitted", sent.obj("compare")?.str("mode"))
    }

    @Test
    fun `B-AGENT-157 the branch view compares with the base branch`() = runTest {
        val h = harness()
        h.answerDiff(listOf(diffFile("b.txt", isNew = true)))
        val diffs = PaseoOperations(h.gateway).listVcsDiffs(VcsDiffMode.Branch).ok()
        assertEquals("added", diffs.single().status)
        assertTrue(diffs.single().patch!!.startsWith("--- /dev/null\n+++ b/b.txt\n"))
        assertEquals("base", h.daemon.of("checkout.diff.get.request").single().obj("compare")?.str("mode"))
    }

    @Test
    fun `B-AGENT-157 file marks come from the same diff`() = runTest {
        val h = harness()
        h.answerDiff(listOf(diffFile("a.dart"), diffFile("new.dart", isNew = true), diffFile("gone.dart", isDeleted = true)))
        val marks = PaseoOperations(h.gateway).listFileStatuses().ok()
        assertEquals(listOf("modified", "added", "deleted"), marks.map { it.status })
        assertEquals(2, marks.first().additions)
    }

    @Test
    fun `B-AGENT-157 a binary file says it differs, without hunks`() = runTest {
        val h = harness()
        h.answerDiff(listOf(mapOf("path" to "logo.png", "isNew" to false, "isDeleted" to false, "additions" to 0, "deletions" to 0, "hunks" to emptyList<Any>(), "status" to "binary")))
        val diffs = PaseoOperations(h.gateway).listVcsDiffs(VcsDiffMode.WorkingTree).ok()
        assertTrue("Binary files" in diffs.single().patch!!)
    }

    @Test
    fun `B-AGENT-157 an agent shows what changed in the folder it runs in`() = runTest {
        val h = harness()
        h.answerDiff(listOf(diffFile("x.dart")))
        h.gateway.allSessions().ok()
        assertEquals("x.dart", h.gateway.diff(SessionId("a1")).ok().single().file)
        assertEquals(DIR, h.daemon.of("checkout.diff.get.request").single().str("cwd"))
    }

    @Test
    fun `B-AGENT-157 a worktree is read in its own folder`() = runTest {
        val h = harness()
        h.answerDiff(listOf(diffFile("w.dart")))
        assertEquals("w.dart", PaseoOperations(h.gateway).listWorktreeFileStatuses("/work/app-wt").ok().single().path)
        assertEquals("/work/app-wt", h.daemon.of("checkout.diff.get.request").single().str("cwd"))
    }

    @Test
    fun `B-AGENT-157 a diff the daemon refuses is an error, not an empty list`() = runTest {
        val h = harness()
        h.daemon.handlers["checkout.diff.get.request"] = {
            "checkout.diff.get.response" to jsonOf("cwd" to DIR, "files" to emptyList<Any>(), "error" to mapOf("code" to "NOT_GIT_REPO", "message" to "not a git repo /x"))
        }
        val failure = PaseoOperations(h.gateway).listVcsDiffs(VcsDiffMode.WorkingTree).failure()
        assertNull(failure.details)
    }

    @Test
    fun `B-AGENT-157 branch and checkout health come from the checkout status`() = runTest {
        val h = harness()
        h.daemon.handlers["checkout_status_request"] = {
            "checkout_status_response" to jsonOf(
                "cwd" to DIR, "isGit" to true, "isPaseoOwnedWorktree" to false, "repoRoot" to DIR,
                "currentBranch" to "feat/x", "isDirty" to true, "baseRef" to "main", "error" to null,
            )
        }
        h.answerDiff(listOf(diffFile("a.dart")))
        val health = PaseoOperations(h.gateway).versionControlHealth().ok()
        assertEquals("feat/x", health.branch)
        assertEquals("main", health.defaultBranch)
        assertEquals(VersionControlSetupState.Git, health.setupState)
        assertEquals(1, health.changes.size)
    }

    @Test
    fun `B-AGENT-157 a folder that is not a repository reads as no git`() = runTest {
        val h = harness()
        h.daemon.handlers["checkout_status_request"] = {
            "checkout_status_response" to jsonOf(
                "cwd" to DIR, "isGit" to false, "isPaseoOwnedWorktree" to false, "repoRoot" to null,
                "currentBranch" to null, "isDirty" to null, "baseRef" to null, "error" to null,
            )
        }
        assertEquals(VersionControlSetupState.Absent, PaseoOperations(h.gateway).versionControlHealth().ok().setupState)
    }

    // ---- B-AGENT-158 Worktrees

    @Test
    fun `B-AGENT-158 the list reads the project's worktrees`() = runTest {
        val h = harness()
        h.daemon.handlers["paseo_worktree_list_request"] = {
            "paseo_worktree_list_response" to jsonOf(
                "worktrees" to listOf(
                    mapOf("worktreePath" to "/home/u/.paseo/worktrees/app/fix-login", "createdAt" to "2026-10-01T10:00:00.000Z", "branchName" to "fix-login", "head" to "abc123"),
                    mapOf("worktreePath" to "/home/u/.paseo/worktrees/app/detached", "createdAt" to "2026-10-01T10:00:00.000Z", "branchName" to null),
                ),
                "error" to null,
            )
        }
        val list = PaseoOperations(h.gateway).listWorktrees(DIR, null).ok()
        assertEquals(listOf("fix-login", "detached"), list.map { it.name })
        assertEquals("fix-login", list.first().branch)
        assertNull(list.last().branch)
        assertEquals("/home/u/.paseo/worktrees/app/fix-login", list.first().directory)
        assertEquals(DIR, h.daemon.of("paseo_worktree_list_request").single().str("cwd"))
    }

    private fun created(dir: String, branch: String) = jsonOf(
        "workspace" to mapOf(
            "id" to "ws1", "projectId" to "p1", "projectDisplayName" to "app", "projectRootPath" to DIR,
            "workspaceDirectory" to dir, "gitRuntime" to mapOf("currentBranch" to branch),
        ),
        "error" to null,
        "setupTerminalId" to null,
    )

    @Test
    fun `B-AGENT-158 creating one sends the name and comes back ready`() = runTest {
        val h = harness()
        h.daemon.handlers["create_paseo_worktree_request"] = {
            "create_paseo_worktree_response" to created("/home/u/.paseo/worktrees/app/mobile-review", "mobile-review")
        }
        val made = PaseoOperations(h.gateway).createWorktree(DIR, " mobile-review ").ok()
        assertEquals("mobile-review", made.name)
        assertEquals("mobile-review", made.branch)
        assertTrue(made.ready)
        val sent = h.daemon.of("create_paseo_worktree_request").single()
        assertEquals(DIR, sent.str("cwd"))
        assertEquals("mobile-review", sent.str("worktreeSlug"))
    }

    @Test
    fun `B-AGENT-158 a nameless worktree sends no slug`() = runTest {
        val h = harness()
        h.daemon.handlers["create_paseo_worktree_request"] = {
            "create_paseo_worktree_response" to created("/home/u/.paseo/worktrees/app/calm-otter", "calm-otter")
        }
        PaseoOperations(h.gateway).createWorktree(DIR, "").ok()
        assertFalse("worktreeSlug" in h.daemon.of("create_paseo_worktree_request").single())
    }

    @Test
    fun `B-AGENT-158 a refused creation is a plain failure`() = runTest {
        val h = harness()
        h.daemon.handlers["create_paseo_worktree_request"] = {
            "create_paseo_worktree_response" to jsonOf("workspace" to null, "error" to "git said no /x", "setupTerminalId" to null)
        }
        val failure = PaseoOperations(h.gateway).createWorktree(DIR, "x").failure()
        assertNull(failure.details)
    }

    @Test
    fun `B-AGENT-158 removing one archives the worktree and its folder`() = runTest {
        val h = harness()
        h.daemon.handlers["paseo_worktree_archive_request"] = {
            "paseo_worktree_archive_response" to jsonOf("success" to true, "removedAgents" to emptyList<String>(), "error" to null)
        }
        PaseoOperations(h.gateway).removeWorktree(DIR, "/home/u/.paseo/worktrees/app/fix-login").ok()
        val sent = h.daemon.of("paseo_worktree_archive_request").single()
        assertEquals("/home/u/.paseo/worktrees/app/fix-login", sent.str("worktreePath"))
        assertEquals(DIR, sent.str("repoRoot"))
        assertEquals("worktree", sent.str("scope"))
    }

    @Test
    fun `B-AGENT-158 a removal the daemon did not do is a failure`() = runTest {
        val h = harness()
        h.daemon.handlers["paseo_worktree_archive_request"] = {
            "paseo_worktree_archive_response" to jsonOf("success" to false, "error" to mapOf("code" to "UNKNOWN", "message" to "busy"))
        }
        PaseoOperations(h.gateway).removeWorktree(DIR, "/x/y").failure()
    }

    // ---- B-AGENT-159 Terminal

    private fun info(id: String, title: String? = null): Map<String, Any?> =
        buildMap {
            put("id", id)
            put("name", "Shell $id")
            put("cwd", DIR)
            if (title != null) put("title", title)
        }

    @Test
    fun `B-AGENT-159 the list shows this folder's terminals by title or name`() = runTest {
        val h = harness()
        h.daemon.handlers["list_terminals_request"] = {
            "list_terminals_response" to jsonOf("cwd" to DIR, "terminals" to listOf(info("t1", "npm run dev"), info("t2")))
        }
        val list = PaseoOperations(h.gateway).listTerminals().ok()
        assertEquals(listOf("npm run dev", "Shell t2"), list.map { it.title })
        assertTrue(list.all { it.running && it.directory == DIR })
        assertEquals(DIR, h.daemon.of("list_terminals_request").single().str("cwd"))
    }

    @Test
    fun `B-AGENT-159 a new terminal opens in the folder under the given name`() = runTest {
        val h = harness()
        h.daemon.handlers["create_terminal_request"] = { "create_terminal_response" to jsonOf("terminal" to info("t9", "Terminal 1"), "error" to null) }
        assertEquals("t9", PaseoOperations(h.gateway).createTerminal("Terminal 1").ok().id)
        val sent = h.daemon.of("create_terminal_request").single()
        assertEquals(DIR, sent.str("cwd"))
        assertEquals("Terminal 1", sent.str("name"))
    }

    @Test
    fun `B-AGENT-159 renaming and closing send the terminal id`() = runTest {
        val h = harness()
        h.daemon.handlers["terminal.rename.request"] = { "terminal.rename.response" to jsonOf("success" to true, "error" to null) }
        h.daemon.handlers["kill_terminal_request"] = { m -> "kill_terminal_response" to jsonOf("terminalId" to m.str("terminalId"), "success" to true) }
        val ops = PaseoOperations(h.gateway)
        ops.renameTerminal("t1", " logs ").ok()
        ops.removeTerminal("t1").ok()
        assertEquals("logs", h.daemon.of("terminal.rename.request").single().str("title"))
        assertEquals("t1", h.daemon.of("kill_terminal_request").single().str("terminalId"))
    }

    @Test
    fun `B-AGENT-159 closing a terminal that already ended is not a failure, a stuck one is`() = runTest {
        val h = harness()
        h.daemon.handlers["kill_terminal_request"] = { m -> "kill_terminal_response" to jsonOf("terminalId" to m.str("terminalId"), "success" to false) }
        h.daemon.handlers["list_terminals_request"] = { "list_terminals_response" to jsonOf("cwd" to DIR, "terminals" to emptyList<Any>()) }
        val ops = PaseoOperations(h.gateway)
        ops.removeTerminal("gone").ok()
        h.daemon.handlers["list_terminals_request"] = { "list_terminals_response" to jsonOf("cwd" to DIR, "terminals" to listOf(info("stuck"))) }
        ops.removeTerminal("stuck").failure()
    }

    private fun GatewayHarness.subscribing(slot: Int) {
        daemon.handlers["subscribe_terminal_request"] = { m ->
            "subscribe_terminal_response" to jsonOf("terminalId" to m.str("terminalId"), "slot" to slot, "error" to null)
        }
    }

    @Test
    fun `B-AGENT-159 opening one subscribes, replays the screen, then streams output`() = runTest {
        val h = harness()
        h.subscribing(3)
        val channel = PaseoOperations(h.gateway).connectTerminal("t1", null).ok()
        val text = StringBuilder()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { channel.output.collect { text.append(it) } }
        val sent = h.daemon.of("subscribe_terminal_request").single()
        assertEquals("t1", sent.str("terminalId"))
        assertEquals("visible-snapshot", sent.obj("restore")?.str("mode"))
        h.daemon.pushBinary(0x05, 3, "\$ ls\r\n".encodeToByteArray())
        h.daemon.pushBinary(0x01, 9, "OTHER SLOT".encodeToByteArray())
        h.daemon.pushBinary(0x01, 3, "file.txt\r\n".encodeToByteArray())
        h.pump()
        assertEquals("\u001bc\$ ls\r\nfile.txt\r\n", text.toString())
        channel.close()
    }

    @Test
    fun `B-AGENT-159 a character split across two frames arrives whole`() = runTest {
        val h = harness()
        h.subscribing(1)
        val channel = PaseoOperations(h.gateway).connectTerminal("t1", null).ok()
        val text = StringBuilder()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { channel.output.collect { text.append(it) } }
        val bytes = "é".encodeToByteArray()
        h.daemon.pushBinary(0x01, 1, byteArrayOf(bytes[0]))
        h.daemon.pushBinary(0x01, 1, byteArrayOf(bytes[1]))
        h.pump()
        assertEquals("é", text.toString())
        channel.close()
    }

    @Test
    fun `B-AGENT-159 typing and resizing go to the terminal`() = runTest {
        val h = harness()
        h.subscribing(2)
        val ops = PaseoOperations(h.gateway)
        val channel = ops.connectTerminal("t1", null).ok()
        channel.write("ls\r")
        ops.resizeTerminal("t1", 30, 90).ok()
        h.pump()
        val inputs = h.daemon.of("terminal_input")
        assertEquals("t1", inputs.first().str("terminalId"))
        assertEquals(jsonOf("type" to "input", "data" to "ls\r"), inputs.first().obj("message"))
        assertEquals(jsonOf("type" to "resize", "rows" to 30, "cols" to 90), inputs.last().obj("message"))
        channel.close()
        h.pump()
        assertEquals("t1", h.daemon.of("unsubscribe_terminal_request").single().str("terminalId"))
    }

    @Test
    fun `B-AGENT-159 a shell that exits ends the stream`() = runTest {
        val h = harness()
        h.subscribing(2)
        val channel = PaseoOperations(h.gateway).connectTerminal("t1", null).ok()
        val all = async { channel.output.toList() }
        h.daemon.push("terminal_stream_exit", jsonOf("terminalId" to "t1"))
        h.pump()
        assertEquals(emptyList(), all.await())
    }

    @Test
    fun `B-AGENT-159 a terminal the daemon cannot open is a plain failure`() = runTest {
        val h = harness()
        h.daemon.handlers["subscribe_terminal_request"] = { m ->
            "subscribe_terminal_response" to jsonOf("terminalId" to m.str("terminalId"), "error" to "Terminal not found")
        }
        val failure = PaseoOperations(h.gateway).connectTerminal("nope", null).failure()
        assertNull(failure.details)
    }
}
