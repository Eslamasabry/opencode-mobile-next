package io.github.eslamasabry.oc.engine.project.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TerminalSourceLabelsTest {
    @Test
    fun `B-LINUX-226 the built-in shell and a server called This phone read apart`() {
        val labels = terminalSourceLabels("This phone")
        assertEquals("Built-in Linux", labels.phone.english)
        assertEquals("This phone", labels.server.english)
        assertNotEquals(labels.phone.english, labels.server.english)
    }

    @Test
    fun `B-LINUX-226 a server with another name keeps it`() {
        assertEquals(TerminalLabel.Named("Laptop"), terminalSourceLabels("Laptop").server)
        assertEquals("Laptop", terminalSourceLabels("  Laptop ").server.english)
    }

    @Test
    fun `B-LINUX-226 a nameless server is the OpenCode server`() {
        assertEquals(TerminalLabel.OpenCodeServer, terminalSourceLabels(null).server)
        assertEquals("OpenCode server", terminalSourceLabels("  ").server.english)
    }

    @Test
    fun `B-LINUX-226 a collision gets the runtime added never two equal labels`() {
        val labels = terminalSourceLabels("Built-in Linux")
        assertNotEquals(labels.phone.english, labels.server.english)
        assertEquals("Built-in Linux · OpenCode server", labels.server.english)
        assertEquals(TerminalLabel.NamedWithRuntime("built-in linux"), terminalSourceLabels("built-in linux").server)
        // The check uses the words the UI shows for the built-in label.
        val custom = terminalSourceLabels("Linux", phoneWords = "linux")
        assertEquals(TerminalLabel.NamedWithRuntime("Linux"), custom.server)
    }
}
