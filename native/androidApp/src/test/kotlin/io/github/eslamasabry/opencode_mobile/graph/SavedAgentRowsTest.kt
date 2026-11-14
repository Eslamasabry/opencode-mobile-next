package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner's phone, 10038: the agents' saved row came back with the OpenCode mark in the list's root folder ("projects"):
 * a plain list save kept only id, title and time. A saved row keeps its own folder and, on an agents server, its agent.
 */
class SavedAgentRowsTest {
    @Test
    fun `SAVED-AGENT-001 an agents server's saved row keeps its folder and the agent that runs it`() {
        val session = Session(SessionId("a1"), "Fix login", directory = "/root/projects/IPTV_King", model = "claude/sonnet")
        assertEquals(
            mapOf("dir" to "/root/projects/IPTV_King", "agent" to "claude", "agentLabel" to "claude"),
            NativeSessionInventory.described(BackendKind.Paseo, session),
        )
    }

    @Test
    fun `SAVED-AGENT-002 an OpenCode row keeps its folder and is never given an agent from its model`() {
        val session = Session(SessionId("o1"), "Plan", directory = "/root/IPTV_King", model = "anthropic/sonnet")
        assertEquals(mapOf("dir" to "/root/IPTV_King"), NativeSessionInventory.described(BackendKind.OpenCode2, session))
    }

    @Test
    fun `SAVED-AGENT-003 what a save knows itself wins over what an earlier save kept`() {
        val rich = mapOf("a1" to mapOf("id" to "a1", "dir" to "/root/projects", "agent" to "claude", "preview" to "hi"))
        val row = mapOf<String, Any>("id" to "a1", "title" to "t", "updated" to 1L, "dir" to "/root/projects/IPTV_King")
        val merged = NativeSessionInventory.withRich(rich, row)
        assertEquals("/root/projects/IPTV_King", merged["dir"])
        assertEquals("claude", merged["agent"])
        assertEquals("hi", merged["preview"])
    }
}
