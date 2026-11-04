package io.github.eslamasabry.oc.engine.attention.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunIdentityTest {
    private fun message(id: String, created: Long, vararg types: String) = RunMessage(id, "user", created, types.toList())

    @Test
    fun `B-PROTO-127 run identity ignores OC2 timeline records and counts only real prompts`() {
        val prompt = message("user-1", 1, "text")
        val timeline =
            listOf("v2:notice", "v2:switch", "v2:compaction", "v2:future").mapIndexed { index, type ->
                message("notice-$index", index + 2L, type)
            }
        assertEquals("user-1", RunIdentity.marker(listOf(prompt) + timeline))
        assertNull(RunIdentity.marker(timeline))
        // OC1 user parts and attachment-only prompts remain valid identities.
        val next = message("user-2", 10, "file")
        assertEquals("user-2", RunIdentity.marker(listOf(prompt) + timeline + next))
        assertEquals("user-3", RunIdentity.marker(listOf(message("user-3", 11))))
        assertEquals(2, RunIdentity.promptCount(listOf(prompt) + timeline + next))
        // An assistant message is never a prompt; an empty id is no identity.
        assertNull(RunIdentity.marker(listOf(RunMessage("a", "assistant", 5, listOf("text")))))
        assertNull(RunIdentity.marker(listOf(message("", 5, "text"))))
    }
}
