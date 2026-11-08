package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** S28 story 4 (Flutter `showPermissionDetails`): the Details sheet's whole text and technical names. */
class PermissionDetailsTest {
    private fun ask(
        permission: String,
        patterns: List<String> = emptyList(),
        metadata: Map<String, JsonElement> = emptyMap(),
        always: List<String> = emptyList(),
        message: String? = null,
    ) = PermissionRequest(
        RequestId("r1"),
        SessionId("s1"),
        permission,
        persistentListOf(*patterns.toTypedArray()),
        JsonObject(metadata),
        persistentListOf(*always.toTypedArray()),
        null,
        always.isNotEmpty(),
        message,
    )

    @Test
    fun `B-CHAT-S28-01 a shell ask shows its command, where it runs, the tool and its other patterns`() {
        val details = PermissionDetails.of(
            ask(
                "bash",
                patterns = listOf("npm test", "npm *"),
                metadata = mapOf("command" to JsonPrimitive("npm test"), "cwd" to JsonPrimitive("/work/app")),
                always = listOf("npm *"),
            )
        )
        assertEquals("npm test", details.fullText)
        assertTrue(details.command)
        assertEquals(
            listOf(
                PermissionFact.RunsIn("/work/app"),
                PermissionFact.Tool("bash"),
                PermissionFact.Patterns(listOf("npm *")),
                PermissionFact.AlsoCovers(listOf("npm *")),
            ),
            details.facts,
        )
    }

    @Test
    fun `B-CHAT-S28-02 a read of part of a file names the range, and the path is the whole text`() {
        val details = PermissionDetails.of(
            ask(
                "read",
                patterns = listOf("src/app.kt"),
                metadata = mapOf("offset" to JsonPrimitive(20), "limit" to JsonPrimitive(80.0)),
            )
        )
        assertEquals("src/app.kt", details.fullText)
        assertFalse(details.command)
        assertEquals(listOf(PermissionFact.Range(20, 80), PermissionFact.Tool("read")), details.facts)
    }

    @Test
    fun `B-CHAT-S28-03 an edit carries its diff, a task names its helper, and a range is only for reads`() {
        val edit = PermissionDetails.of(
            ask(
                "edit",
                metadata = mapOf(
                    "filePath" to JsonPrimitive("a.txt"),
                    "diff" to JsonPrimitive("-a\n+b"),
                    "offset" to JsonPrimitive(3),
                ),
            )
        )
        assertEquals("a.txt", edit.fullText)
        assertEquals("-a\n+b", edit.diff)
        assertEquals(listOf<PermissionFact>(PermissionFact.Tool("edit")), edit.facts)

        val task = PermissionDetails.of(
            ask("task", metadata = mapOf("subagent_type" to JsonPrimitive("explore"), "prompt" to JsonPrimitive("Find it")))
        )
        assertEquals(
            listOf(PermissionFact.Asks("Find it"), PermissionFact.Helper("explore"), PermissionFact.Tool("task")),
            task.facts,
        )
        assertNull(task.diff)
    }

    @Test
    fun `B-CHAT-S28-04 a pattern list with no command is the whole text, and the agent's words come last`() {
        assertEquals("a/**\nb/**", PermissionDetails.of(ask("glob", patterns = listOf("a/**", "b/**"))).fullText)
        assertEquals("Look around", PermissionDetails.of(ask("webfetch", message = "Look around")).fullText)
        assertNull(PermissionDetails.of(ask("webfetch")).fullText)
    }
}
