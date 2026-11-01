package io.github.eslamasabry.oc.protocol.oc2.fixtures

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2EventRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plan 04 §7.5: every type documented in docs/opencode2-protocol-notes.md §3.2 is in exactly one registry list. */
class Oc2EventRegistryDocTest {
    @Test
    fun `B-PROTO-014 every documented OpenCode 2 event type is typed passed through or ignored exactly once`() {
        val notes = File(Fixtures.repoRoot, "docs/opencode2-protocol-notes.md").readText()
        val section = notes.substring(notes.indexOf("### 3.2"), notes.indexOf("### 3.3"))
        val documented = Regex("`([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)`").findAll(section).map { it.groupValues[1] }.toSet()
        assertTrue(documented.size >= 91, "found ${documented.size} documented types")
        val lists = listOf(Oc2EventRegistry.typed, Oc2EventRegistry.passThrough, Oc2EventRegistry.ignored)
        val placement = documented.associateWith { type -> lists.count { type in it } }
        assertEquals(emptyMap(), placement.filterValues { it != 1 }, "types in no list (0) or several lists (>1)")
    }
}
