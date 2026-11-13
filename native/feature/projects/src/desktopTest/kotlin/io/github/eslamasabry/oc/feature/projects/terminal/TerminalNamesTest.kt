package io.github.eslamasabry.oc.feature.projects.terminal

import io.github.eslamasabry.oc.core.model.TerminalProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

class TerminalNamesTest {
    private fun process(id: String, title: String) =
        TerminalProcess(id, title, "/bin/bash", persistentListOf(), "/p", true, 1, null)

    @Test
    fun `B-PROJ-TERM-001 a server default title is not a name and the one terminal is plain`() {
        val list = listOf(process("pty_x", "Terminal BYbZ"))
        assertEquals(TerminalName.Plain(1), terminalNameOf(list, "pty_x"))
    }

    @Test
    fun `B-PROJ-TERM-002 a renamed terminal keeps the persons name`() {
        val list = listOf(process("a", "Terminal 0Ome"), process("b", "Build watcher"))
        assertEquals(TerminalName.Given("Build watcher"), terminalNameOf(list, "b"))
        assertEquals(TerminalName.Plain(1), terminalNameOf(list, "a"))
    }

    @Test
    fun `B-PROJ-TERM-003 several unnamed terminals are numbered in the order they were opened`() {
        val list =
            listOf(process("a", "Terminal 0Ome"), process("b", "Terminal BYbZ"), process("c", ""))
        assertEquals(
            listOf(TerminalName.Plain(1), TerminalName.Plain(2), TerminalName.Plain(3)),
            list.map { terminalNameOf(list, it) },
        )
    }

    @Test
    fun `B-PROJ-TERM-004 a short numbered name is the persons own and a server id never starts the rename field`() {
        val list = listOf(process("a", "Terminal 7"), process("b", "Terminal BYbZ"))
        assertEquals("Terminal 7", list[0].givenName())
        assertEquals(null, list[1].givenName())
    }
}
