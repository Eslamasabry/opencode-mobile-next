package io.github.eslamasabry.oc.core.domain.capabilities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilitiesTest {
    @Test
    fun `withGenUi keeps every protocol flag and sets only the managed-host pair`() {
        val base = ServerCapabilities(forms = true, terminal = false, agentHandoffFiles = false)
        val on = base.withGenUi(true, agentHandoffFiles = true)
        assertEquals(base.copy(genUi = true, agentHandoffFiles = true), on)
        assertEquals(on.copy(genUi = false), on.withGenUi(false))
    }

    @Test
    fun `withClaudeAgent changes only claudeAgent`() {
        val base = ServerCapabilities(inbox = true)
        assertEquals(base.copy(claudeAgent = true), base.withClaudeAgent(true))
    }

    @Test
    fun `a conversation shell counts only with a terminal`() {
        assertTrue(ServerCapabilities().conversationShellOn)
        assertFalse(ServerCapabilities(terminal = false).conversationShellOn)
        assertFalse(ServerCapabilities(conversationShell = false).conversationShellOn)
    }

    @Test
    fun `a flag looks up by its FeatureId name`() {
        assertEquals(true, ServerCapabilities.Unknown.flag("sessionFork"))
        assertEquals(false, ServerCapabilities.Unknown.flag("forms"))
        assertEquals(null, ServerCapabilities.Unknown.flag("noSuchFlag"))
    }
}
