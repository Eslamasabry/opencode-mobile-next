package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** LocalTerminal.kt as this side sees it: shells that start at once, output pushed by the test, every call recorded. */
private class FakeLocalTerminalPort : LocalTerminalPort {
    private val flow = MutableSharedFlow<LocalTerminalEvent>(extraBufferCapacity = 256)
    override val events: Flow<LocalTerminalEvent> = flow
    val calls = mutableListOf<String>()
    val written = mutableMapOf<Int, MutableList<Byte>>()
    val existing = mutableListOf<LocalShellInfo>()
    val recent = mutableMapOf<Int, ByteArray>()
    var startFailure: Failure? = null
    var processes = 5
    private var nextId = 1
    var acks = 0

    fun output(id: Int, text: String) = check(flow.tryEmit(LocalTerminalEvent.Output(id, text.encodeToByteArray())))

    fun outputBytes(id: Int, bytes: List<Int>) =
        check(flow.tryEmit(LocalTerminalEvent.Output(id, ByteArray(bytes.size) { bytes[it].toByte() })))

    fun exit(id: Int, code: Int) = check(flow.tryEmit(LocalTerminalEvent.Exit(id, code)))

    fun writtenText(id: Int): String = written[id].orEmpty().toByteArray().decodeToString()

    override suspend fun start(rows: Int, cols: Int): Outcome<LocalShellInfo> {
        calls += "start $rows x $cols"
        startFailure?.let { return it.failed() }
        val id = nextId++
        return LocalShellInfo(id = id, pid = 1000 + id).ok()
    }

    override suspend fun list(): Outcome<LocalTerminalListing> {
        calls += "list"
        return LocalTerminalListing(existing.toList(), processes).ok()
    }

    override suspend fun attach(id: Int): Outcome<ByteArray?> {
        calls += "attach $id"
        return recent[id].ok()
    }

    override suspend fun write(id: Int, data: ByteArray): Outcome<Unit> {
        written.getOrPut(id) { mutableListOf() }.addAll(data.toList())
        return Unit.ok()
    }

    override suspend fun ack(id: Int): Outcome<Unit> {
        acks++
        return Unit.ok()
    }

    override suspend fun resize(id: Int, rows: Int, cols: Int): Outcome<Unit> {
        calls += "resize $id $rows x $cols"
        return Unit.ok()
    }

    override suspend fun stop(id: Int): Outcome<Unit> {
        calls += "stop $id"
        exit(id, 129)
        return Unit.ok()
    }

    override suspend fun remove(id: Int): Outcome<Unit> {
        calls += "remove $id"
        return Unit.ok()
    }
}

private fun TestScope.sessions(port: FakeLocalTerminalPort): LocalTerminalSessions =
    LocalTerminalSessions(port, backgroundScope)

class LocalTerminalTest {
    // ---- a new shell

    @Test
    fun `B-LINUX-097 a new shell starts at the terminal size then runs`() = runTest {
        val port = FakeLocalTerminalPort()
        val sessions = sessions(port)
        val shell = sessions.startShell(rows = 30, cols = 100)
        assertEquals(LocalShellState.Starting, shell.state)
        assertEquals(1, shell.number)
        runCurrent()
        assertTrue("start 30 x 100" in port.calls)
        assertEquals(LocalShellState.Running, shell.state)
        assertEquals(1, shell.id)
        assertEquals(1001, shell.pid)
        assertEquals(LocalShellState.Running, shell.status.value.state)
    }

    @Test
    fun `B-LINUX-097 a failed start says why`() = runTest {
        val port = FakeLocalTerminalPort().apply { startFailure = Failure.Runtime(RuntimeProblem.NotInstalled) }
        val shell = sessions(port).startShell()
        runCurrent()
        assertEquals(LocalShellState.Failed, shell.state)
        assertEquals(Failure.Runtime(RuntimeProblem.NotInstalled), shell.failure)
    }

    @Test
    fun `B-LINUX-097 shells are numbered in the order they were opened`() = runTest {
        val sessions = sessions(FakeLocalTerminalPort())
        val first = sessions.startShell()
        val second = sessions.startShell()
        runCurrent()
        assertEquals(listOf(1, 2), listOf(first.number, second.number))
        assertEquals(listOf(first, second), sessions.shells.value)
    }

    // ---- output

    @Test
    fun `B-LINUX-098 output is drawn and every chunk is answered`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell()
        runCurrent()
        port.output(1, "root@localhost:~# ")
        runCurrent()
        assertEquals("root@localhost:~#", shell.screen.visibleText())
        assertEquals(1, port.acks)
    }

    @Test
    fun `B-LINUX-098 a character split across two chunks arrives whole`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell()
        runCurrent()
        // "日本" in UTF-8, cut inside the second character.
        port.outputBytes(1, listOf(0xE6, 0x97, 0xA5, 0xE6))
        port.outputBytes(1, listOf(0x9C, 0xAC))
        runCurrent()
        assertTrue(shell.screen.visibleText().contains("日本"))
        assertFalse(shell.screen.visibleText().contains('�'))
        assertEquals(2, port.acks)
    }

    @Test
    fun `B-LINUX-098 the stream decoder holds back only an incomplete tail`() {
        val decoder = Utf8StreamDecoder()
        val emoji = "😀".encodeToByteArray()
        assertEquals("a", decoder.decode(byteArrayOf('a'.code.toByte()) + emoji.copyOfRange(0, 1)))
        assertEquals("", decoder.decode(emoji.copyOfRange(1, 3)))
        assertEquals("😀", decoder.decode(emoji.copyOfRange(3, 4)))
        // Malformed bytes still read as U+FFFD instead of stalling the stream.
        assertEquals("�b", decoder.decode(byteArrayOf(0xFF.toByte(), 'b'.code.toByte())))
        decoder.decode(byteArrayOf(0xE6.toByte()))
        assertEquals("�", decoder.finish())
    }

    @Test
    fun `B-LINUX-098 a wide character at the last column wraps whole`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell(rows = 5, cols = 10)
        runCurrent()
        // Nine columns, then CJK and emoji two columns each.
        port.output(1, "123456789中文😀")
        runCurrent()
        val lines = shell.screen.lines
        assertEquals("123456789", lines[0].text().trimEnd())
        assertTrue(lines[1].text().startsWith("中文😀"))
        assertTrue(lines[1].isWrapped)
        assertEquals(6, shell.screen.cursorX)
    }

    @Test
    fun `B-LINUX-098 after clear a selection copies the words it covers`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell(rows = 5, cols = 20)
        runCurrent()
        port.output(1, (0 until 50).joinToString("") { "line $it\r\n" })
        // What `clear` sends: home, erase the screen, erase the scrollback.
        port.output(1, "\u001b[H\u001b[2J\u001b[3Jpick me")
        runCurrent()
        val screen = shell.screen
        val y = screen.absoluteCursorY
        assertEquals(0, screen.scrollBack)
        assertEquals("pick", screen.text(0, y, 4, y))
        assertEquals("pick me", screen.visibleText())
    }

    @Test
    fun `B-LINUX-098 output for a shell this side does not know is still answered`() = runTest {
        val port = FakeLocalTerminalPort()
        sessions(port).startShell()
        runCurrent()
        port.output(42, "stray")
        runCurrent()
        assertEquals(1, port.acks)
    }

    @Test
    fun `B-LINUX-098 an exit ends the shell with its code`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell()
        runCurrent()
        port.exit(1, 130)
        runCurrent()
        assertEquals(LocalShellState.Exited, shell.state)
        assertEquals(130, shell.exitCode)
        assertFalse(shell.running)
    }

    // ---- input

    @Test
    fun `B-LINUX-099 typed text reaches the shell as UTF-8`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell()
        runCurrent()
        shell.textInput("ls é\r")
        runCurrent()
        assertEquals("ls é\r", port.writtenText(1))
        assertEquals<List<Byte>?>("ls é\r".encodeToByteArray().toList(), port.written[1]?.toList())
    }

    @Test
    fun `B-LINUX-099 a sticky Ctrl turns the next typed letter into its control byte`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell()
        runCurrent()
        val keys = TerminalKeyBarController()
        shell.inputFilter = keys::apply
        keys.toggle(TerminalBarKey.Ctrl)
        // What the phone's keyboard delivers after the Ctrl tap.
        shell.textInput("c")
        shell.textInput("c")
        runCurrent()
        assertEquals<List<Byte>?>(listOf(0x03.toByte(), 0x63.toByte()), port.written[1]?.toList())
        assertFalse(keys.ctrl)
    }

    @Test
    fun `B-LINUX-099 nothing is sent before the shell runs or after it ended`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell()
        shell.textInput("early")
        runCurrent()
        port.exit(1, 0)
        runCurrent()
        shell.textInput("late")
        runCurrent()
        assertTrue(port.written.isEmpty())
    }

    @Test
    fun `B-LINUX-099 a resize reaches the shell once the view settles`() = runTest {
        val port = FakeLocalTerminalPort()
        val shell = sessions(port).startShell(rows = 24, cols = 80)
        runCurrent()
        shell.resize(cols = 60, rows = 20)
        advanceTimeBy(40)
        shell.resize(cols = 61, rows = 21)
        advanceTimeBy(79)
        runCurrent()
        assertTrue(port.calls.none { it.startsWith("resize") }, "not before the view settles")
        advanceTimeBy(41)
        runCurrent()
        assertEquals(listOf("resize 1 21 x 61"), port.calls.filter { it.startsWith("resize") })
        // The same size again is not sent twice.
        shell.resize(cols = 61, rows = 21)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, port.calls.count { it.startsWith("resize") })
    }

    // ---- shells outlive the screen

    @Test
    fun `B-LINUX-100 load adopts shells Android runs with their recent output`() = runTest {
        val port = FakeLocalTerminalPort()
        port.existing += LocalShellInfo(id = 7, pid = 4242)
        port.existing += LocalShellInfo(id = 8, pid = 4243, running = false, exitCode = 2)
        port.existing += LocalShellInfo(id = 9, pid = 4244, signIn = true)
        port.recent[7] = "earlier output\r\n".encodeToByteArray()
        val sessions = sessions(port)
        sessions.load()
        runCurrent()
        val shells = sessions.shells.value
        assertEquals(2, shells.size)
        val adopted = shells.first()
        assertEquals(7, adopted.id)
        assertTrue(adopted.running)
        assertEquals("earlier output", adopted.screen.visibleText())
        assertEquals(LocalShellState.Exited, shells.last().state)
        assertEquals(2, shells.last().exitCode)
        assertEquals(5, sessions.processes.value)
        assertTrue(sessions.loaded)
        // A sign-in left from an earlier run belongs to no screen: removed, not adopted.
        assertTrue("remove 9" in port.calls)
    }

    @Test
    fun `B-LINUX-100 load runs once`() = runTest {
        val port = FakeLocalTerminalPort()
        val sessions = sessions(port)
        sessions.load()
        sessions.load()
        assertEquals(1, port.calls.count { it == "list" })
    }

    @Test
    fun `B-LINUX-100 restart replaces an ended shell`() = runTest {
        val port = FakeLocalTerminalPort()
        val sessions = sessions(port)
        val shell = sessions.startShell()
        runCurrent()
        port.exit(1, 0)
        runCurrent()
        val next = sessions.restart(shell)
        runCurrent()
        assertEquals(listOf(next), sessions.shells.value)
        assertTrue("remove 1" in port.calls)
        assertEquals(LocalShellState.Running, next.state)
    }

    @Test
    fun `B-LINUX-100 stop leaves the shell listed as ended`() = runTest {
        val port = FakeLocalTerminalPort()
        val sessions = sessions(port)
        val shell = sessions.startShell()
        runCurrent()
        sessions.stop(shell)
        runCurrent()
        assertTrue("stop 1" in port.calls)
        assertEquals(LocalShellState.Exited, shell.state)
        assertEquals(listOf(shell), sessions.shells.value)
    }

    // ---- channel

    @Test
    fun `B-LINUX-101 events are parsed from the Android shapes`() {
        val output =
            LocalTerminalChannel.parseEvent(mapOf("type" to "output", "id" to 3, "data" to byteArrayOf(104, 105)))
        assertIs<LocalTerminalEvent.Output>(output)
        assertEquals(3, output.id)
        assertContentEquals(byteArrayOf(104, 105), output.data)
        val exit = LocalTerminalChannel.parseEvent(mapOf("type" to "exit", "id" to 3, "code" to 0))
        assertEquals(LocalTerminalEvent.Exit(3, 0), exit)
        assertEquals(
            LocalTerminalEvent.OpenUrl(3, "https://example.invalid/login"),
            LocalTerminalChannel.parseEvent(
                mapOf("type" to "openUrl", "id" to 3, "url" to "https://example.invalid/login"),
            ),
        )
        assertNull(LocalTerminalChannel.parseEvent(mapOf("type" to "unknown")))
        assertNull(LocalTerminalChannel.parseEvent(mapOf("type" to "unknown", "id" to 3)))
        assertNull(LocalTerminalChannel.parseEvent(mapOf("type" to "output", "id" to 3, "data" to "hi")))
        assertNull(LocalTerminalChannel.parseEvent("output"))
        val noCode = LocalTerminalChannel.parseEvent(mapOf("type" to "exit", "id" to 4))
        assertEquals(LocalTerminalEvent.Exit(4, -1), noCode)
    }

    @Test
    fun `B-LINUX-101 the list reply is parsed from the Android shape`() {
        val listing =
            LocalTerminalChannel.parseListing(
                mapOf(
                    "sessions" to listOf(
                        mapOf("id" to 7, "pid" to 4242),
                        mapOf("id" to 8, "pid" to 4243, "running" to false, "exitCode" to 2, "signIn" to true),
                        "not a shell",
                    ),
                    "processes" to 5,
                ),
            )
        assertEquals(
            listOf(
                LocalShellInfo(id = 7, pid = 4242),
                LocalShellInfo(id = 8, pid = 4243, running = false, exitCode = 2, signIn = true),
            ),
            listing.shells,
        )
        assertEquals(5, listing.processes)
        assertEquals(LocalTerminalListing(emptyList(), null), LocalTerminalChannel.parseListing(null))
    }
}
