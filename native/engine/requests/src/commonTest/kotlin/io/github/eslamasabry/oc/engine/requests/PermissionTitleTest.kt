package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.engine.requests.ask.PermissionTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Flutter `permission_presentation_test.dart` (COPY-15; B-L10N-016). */
class PermissionTitleTest {
    private val known =
        mapOf(
            "bash" to "e7PermissionAction1",
            "edit" to "e7PermissionAction2",
            "read" to "e7PermissionAction3",
            "external_directory" to "e7PermissionAction4",
            "doom_loop" to "e7PermissionAction5",
        )

    @Test
    fun `B-L10N-016 each known id is titled with its own plain-action words, never the generic title`() {
        for ((id, key) in known) {
            val title = PermissionTitle.of(id)
            assertEquals(key, title.wordsKey, id)
            assertNotEquals(PermissionTitle.Generic, title, id)
        }
        assertEquals(known.size, known.keys.map { PermissionTitle.of(it) }.toSet().size, "known ids never share a title")
    }

    @Test
    fun `B-L10N-016 an empty id is titled Permission needed`() {
        for (id in listOf("", " ", "   ", "\t", "\n")) assertEquals(PermissionTitle.Generic, PermissionTitle.of(id), "'$id'")
        assertEquals("e7PermissionAction6", PermissionTitle.Generic.wordsKey)
    }

    @Test
    fun `B-L10N-016 an unknown id is titled Permission needed, with the id kept for Details`() {
        for (id in listOf("unknown_permission", "future.permission", "mcp__some_server__tool", "BASH")) {
            assertEquals(PermissionTitle.Generic, PermissionTitle.of(id), id)
        }
    }
}
