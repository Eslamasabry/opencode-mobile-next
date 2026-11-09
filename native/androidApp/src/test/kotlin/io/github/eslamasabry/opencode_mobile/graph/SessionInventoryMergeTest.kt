package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cache-first (CF): three writers share `oc.sessionInventory.<profile>`. A plain list save (the runtime's refresh,
 * All conversations) must keep the home's richer fields of the rows it rewrites, or the home's first frame loses its
 * agent marks, projects and last-message lines after any list refresh.
 */
class SessionInventoryMergeTest {
    private val home =
        DartJson.encode(
            mapOf(
                "v" to 1,
                "scope" to "here",
                "at" to 1L,
                "rows" to
                    listOf(
                        mapOf(
                            "id" to "s1",
                            "title" to "Old title",
                            "updated" to 1L,
                            "project" to "api",
                            "agent" to "claude",
                            "preview" to "Done: tests pass",
                        )
                    ),
            )
        )

    @Test
    fun `CF-19 a plain save keeps the home's fields of the same row and takes its own fresh ones`() {
        val rich = NativeSessionInventory.richRows(home, "here")
        val row = NativeSessionInventory.withRich(rich, mapOf("id" to "s1", "title" to "New title", "updated" to 9L))
        assertEquals("New title", row["title"])
        assertEquals(9L, row["updated"])
        assertEquals("api", row["project"])
        assertEquals("claude", row["agent"])
        assertEquals("Done: tests pass", row["preview"])

        val fresh = mapOf<String, Any>("id" to "s2", "title" to "New chat", "updated" to 10L)
        assertEquals(fresh, NativeSessionInventory.withRich(rich, fresh), "a row the home never saw stays plain")
        assertEquals(emptyMap(), NativeSessionInventory.richRows(home, "another server"), "another server's rows never carry over")
    }
}
