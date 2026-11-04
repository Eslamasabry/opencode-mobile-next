package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.feature.servers.presentation.SetupCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Dart `slice_close_servers_profile_editor_test` › the Codex start command is one line. */
class SetupCommandsTest {
    @Test
    fun `B-AGENT-241 the Codex start command is one line`() {
        assertFalse('\n' in SetupCommands.CODEX_START)
        assertFalse('\\' in SetupCommands.CODEX_START, "no shell continuations")
        assertTrue(SetupCommands.CODEX_START.startsWith("codex app-server --listen"))
        assertEquals(SetupCommands.CODEX_START, SetupCommands.startFor(BackendKind.Codex))
        assertEquals("opencode2 pair", SetupCommands.startFor(BackendKind.OpenCode2))
        assertEquals("paseo start --no-relay", SetupCommands.startFor(BackendKind.Paseo))
    }
}

class SetupCommandsPasswordTest {
    @Test
    fun `B-NAV-016 password commands prompt privately and pass the redactor unchanged`() {
        for ((name, command) in mapOf("PASEO_PASSWORD" to SetupCommands.PASEO_START_PRIVATE_NETWORK)) {
            val redactor = io.github.eslamasabry.oc.core.security.Redactor()
            assertEquals(command, redactor.redact(command))
            assertTrue("IFS= read -rsp " in command)
            assertTrue("test -n \"\$$name\" &&" in command)
            assertTrue("export $name &&" in command)
            assertFalse("$name=" in command)
        }
    }
}
