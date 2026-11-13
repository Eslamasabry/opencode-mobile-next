package io.github.eslamasabry.oc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

class TerminalNamesTest {
    private fun process(id: String, title: String) =
        TerminalProcess(id, title, "/bin/bash", persistentListOf(), "/p", true, 1, null)

    @Test
    fun `B-PROJ-TERM-001 a server default title is not a name and the one terminal is plain`() {
        val list = listOf(process("pty_x1BYbZ", "Terminal BYbZ"))
        assertEquals(TerminalName.Plain(1), terminalNameOf(list, "pty_x1BYbZ"))
    }

    @Test
    fun `B-PROJ-TERM-002 a renamed terminal keeps the persons name`() {
        val list = listOf(process("pty_a0Ome", "Terminal 0Ome"), process("b", "Build watcher"))
        assertEquals(TerminalName.Given("Build watcher"), terminalNameOf(list, "b"))
        assertEquals(TerminalName.Plain(1), terminalNameOf(list, "pty_a0Ome"))
    }

    @Test
    fun `B-PROJ-TERM-003 several unnamed terminals are numbered in the order they were opened`() {
        val list =
            listOf(process("pty_a0Ome", "Terminal 0Ome"), process("pty_bBYbZ", "Terminal BYbZ"), process("c", ""))
        assertEquals(
            listOf(TerminalName.Plain(1), TerminalName.Plain(2), TerminalName.Plain(3)),
            list.map { terminalNameOf(list, it) },
        )
    }

    @Test
    fun `B-PROJ-TERM-004 neither a numbered default nor a server id starts the rename field`() {
        val list = listOf(process("a", "Terminal 7"), process("pty_bBYbZ", "Terminal BYbZ"))
        assertEquals(null, list[0].givenName())
        assertEquals(null, list[1].givenName())
    }

    @Test
    fun `B-PROJ-TERM-005 the app's old Terminal N default is not a name`() {
        val one = listOf(process("a", "Terminal 3"))
        assertEquals(TerminalName.Plain(1), terminalNameOf(one, "a"))
        val two = listOf(process("a", "Terminal 2"), process("b", "Terminal 2"))
        assertEquals(TerminalName.Plain(1), terminalNameOf(two, "a"))
        assertEquals(TerminalName.Plain(2), terminalNameOf(two, "b"))
        assertEquals(null, process("c", "Terminal").givenName())
        assertEquals("Build watcher", process("d", "Build watcher").givenName())
    }

    @Test
    fun `B-PROJ-TERM-006 only the server default for this terminal's own id is hidden`() {
        assertEquals(null, process("pty_9zQ0BYbZ", "Terminal BYbZ").givenName())
        assertEquals("Terminal build", process("pty_9zQ0BYbZ", "Terminal build").givenName())
        assertEquals("Terminal Main", process("pty_9zQ0BYbZ", "Terminal Main").givenName())
        // Looks like a server default but belongs to another id: the person typed it.
        assertEquals("Terminal 0Ome", process("pty_9zQ0BYbZ", "Terminal 0Ome").givenName())
    }
}
