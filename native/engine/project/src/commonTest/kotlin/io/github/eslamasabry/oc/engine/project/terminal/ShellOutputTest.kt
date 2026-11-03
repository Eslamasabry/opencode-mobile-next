package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun sampleShell(id: String = "sh_a"): ManagedShell =
    ManagedShell(id = id, command = "flutter test --concurrency=1", running = true, sessionId = "ses_a")

/** The Flutter `FakeManagedShellRepository`, read side. */
private class FakeShellOutputPort : ManagedShellOutputPort {
    var shells: List<ManagedShell> = listOf(sampleShell())
    var identity: String = "server-1"
    var output: String = "✓ Composer layout\n✓ Draft restoration\nRunning navigation tests…\n"
    var infoReads = 0
    var outputReads = 0
    var failure: Failure? = null
    val cursors = mutableListOf<Long>()
    val pages = mutableMapOf<Long, ManagedShellOutput>()
    var pendingInfo: CompletableDeferred<ManagedShell?>? = null

    override suspend fun serverIdentity(): Outcome<String?> = identity.ok()

    override suspend fun shell(id: String): Outcome<ManagedShell?> {
        infoReads++
        pendingInfo?.let { return it.await().ok() }
        failure?.let { return it.failed() }
        return shells.firstOrNull { it.id == id }.ok()
    }

    override suspend fun readOutput(id: String, cursor: Long, limit: Int): Outcome<ManagedShellOutput> {
        outputReads++
        cursors += cursor
        failure?.let { return it.failed() }
        pages[cursor]?.let { return it.ok() }
        val bytes = output.encodeToByteArray()
        val start = cursor.coerceIn(0, bytes.size.toLong()).toInt()
        return ManagedShellOutput(
            text = bytes.copyOfRange(start, bytes.size).decodeToString(),
            cursor = bytes.size.toLong(),
            size = bytes.size.toLong(),
            truncated = false,
        ).ok()
    }
}

class ShellOutputTest {
    @Test
    fun `B-AGENT-237 a malformed reset page leaves already loaded output intact`() = runTest {
        val repo = FakeShellOutputPort().apply { output = "Original" }
        val output = ShellOutputController(repo, repo.shells.first())
        output.refresh()
        repo.pages[8] = ManagedShellOutput(text = "", cursor = 0, size = 0, truncated = false)
        repo.pages[0] = ManagedShellOutput(text = "invalid", cursor = 9, size = 2, truncated = false)
        output.refresh()
        assertEquals(ShellOutputError.MalformedPage, output.state.value.error)
        assertEquals("Original", output.state.value.text)
        assertEquals(8, output.state.value.cursor)
    }

    @Test
    fun `B-AGENT-237 uses server byte cursors and never duplicates an unchanged page`() = runTest {
        val repo = FakeShellOutputPort()
        repo.pages[0] = ManagedShellOutput(text = "é", cursor = 2, size = 6, truncated = true)
        repo.pages[2] = ManagedShellOutput(text = "🙂", cursor = 6, size = 6, truncated = false)
        repo.pages[6] = ManagedShellOutput(text = "", cursor = 6, size = 6, truncated = false)
        val output = ShellOutputController(repo, repo.shells.first())
        output.refresh()
        assertTrue(output.state.value.hasMore)
        output.refresh()
        output.refresh()
        assertEquals(listOf(0L, 2L, 6L), repo.cursors)
        assertEquals("é🙂", output.state.value.text)
        assertFalse(output.state.value.hasMore)
    }

    @Test
    fun `B-AGENT-237 restarts a reset capture from zero instead of duplicating stale output`() = runTest {
        val repo = FakeShellOutputPort().apply { output = "Old long output" }
        val output = ShellOutputController(repo, repo.shells.first())
        output.refresh()
        repo.output = "New"
        output.refresh(reconcileServer = true)
        assertEquals(listOf(0L, 15L, 0L), repo.cursors)
        assertEquals("New", output.state.value.text)
        assertEquals(3, output.state.value.cursor)
    }

    @Test
    fun `B-AGENT-238 bounded output preserves complete surrogate pairs and strips terminal escapes`() = runTest {
        val repo =
            FakeShellOutputPort().apply {
                output = "a".repeat(ShellOutputController.MAX_CHARACTERS) + "🙂\u001b[32mOK\u001b[0m"
            }
        val output = ShellOutputController(repo, repo.shells.first())
        output.refresh()
        val state = output.state.value
        assertTrue(state.trimmed)
        assertTrue(state.text.length <= ShellOutputController.MAX_CHARACTERS)
        assertTrue(state.displayText.endsWith("🙂OK"))
        assertFalse(state.displayText.contains('\u001b'))
        assertFalse(state.text.first().isLowSurrogate())
    }

    @Test
    fun `B-AGENT-238 display text drops OSC titles and normalises line breaks`() {
        assertEquals("a\nb\nc", stripTerminalEscapes("\u001b]0;title\u0007a\r\nb\rc"))
        assertEquals("x", stripTerminalEscapes("\u001b]2;t\u001b\\x"))
    }

    @Test
    fun `B-AGENT-239 a missing shell after reconnect is not called a restart without evidence`() = runTest {
        val repo = FakeShellOutputPort()
        val output = ShellOutputController(repo, repo.shells.first())
        output.refresh()
        val text = output.state.value.text
        repo.shells = emptyList()
        output.refresh(reconcileServer = true)
        assertFalse(output.state.value.available)
        assertFalse(output.state.value.serverRestarted)
        assertEquals(text, output.state.value.text)
        repo.identity = "server-2"
        output.refresh(reconcileServer = true)
        assertTrue(output.state.value.serverRestarted)
    }

    @Test
    fun `B-AGENT-239 a retired repository response cannot replace the new connection state`() = runTest {
        val old = FakeShellOutputPort().apply { pendingInfo = CompletableDeferred() }
        val output = ShellOutputController(old, old.shells.first())
        val oldRead = async { output.refresh() }
        runCurrent()
        val next = FakeShellOutputPort().apply { this.output = "New connection" }
        output.bind(next)
        output.refresh(reconcileServer = true)
        old.pendingInfo!!.complete(null)
        oldRead.await()
        assertEquals("New connection", output.state.value.text)
        assertTrue(output.state.value.available)
    }

    @Test
    fun `B-AGENT-240 concurrent refreshes coalesce and closing a viewer discards late results`() = runTest {
        val repo = FakeShellOutputPort().apply { pendingInfo = CompletableDeferred() }
        val output = ShellOutputController(repo, repo.shells.first())
        var changes = 0
        val watcher = output.state.onEach { changes++ }.launchIn(backgroundScope)
        runCurrent()
        val request = async { output.refresh() }
        runCurrent()
        output.refresh()
        assertEquals(1, repo.infoReads)
        output.dispose()
        runCurrent()
        val changesAtClose = changes
        val stateAtClose = output.state.value
        repo.pendingInfo!!.complete(repo.shells.first())
        request.await()
        runCurrent()
        assertEquals(changesAtClose, changes)
        assertEquals(stateAtClose, output.state.value)
        assertEquals(0, repo.outputReads)
        watcher.cancel()
    }

    @Test
    fun `B-AGENT-240 errors preserve visible output and a retry clears the error`() = runTest {
        val repo = FakeShellOutputPort()
        val output = ShellOutputController(repo, repo.shells.first())
        output.refresh()
        val text = output.state.value.text
        repo.failure = Failure.Network()
        output.refresh()
        assertNotNull(output.state.value.error)
        assertIs<ShellOutputError.Failed>(output.state.value.error)
        assertEquals(text, output.state.value.text)
        repo.failure = null
        output.refresh()
        assertNull(output.state.value.error)
    }
}
