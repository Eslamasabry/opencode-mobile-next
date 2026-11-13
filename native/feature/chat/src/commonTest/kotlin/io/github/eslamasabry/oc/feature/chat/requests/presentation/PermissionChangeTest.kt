package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Review 2026-11-13 #1: the change a file ask carries, read from what each server really sends. */
class PermissionChangeTest {
    private fun ask(permission: String, patterns: List<String>, metadata: Map<String, JsonElement>) =
        PermissionRequest(
            RequestId("p"), SessionId("s"), permission, patterns.toImmutableList(), JsonObject(metadata),
            listOf("*").toImmutableList(), null, true, null,
        )

    private fun file(vararg pairs: Pair<String, Any>) =
        JsonObject(pairs.associate { (k, v) -> k to if (v is Int) JsonPrimitive(v) else JsonPrimitive(v.toString()) })

    private val created = "--- e9.txt\n+++ e9.txt\n@@ -0,0 +1,1 @@\n+hello\n"
    private val edited = "--- a.kt\n+++ a.kt\n@@ -1,2 +1,2 @@\n one\n-two\n+three\n"

    @Test
    fun `B-CHAT-REV1-010 OpenCode 2 write of a new file is a creation with its patch, and the question says Create`() {
        val request =
            ask(
                "edit", listOf("e9.txt"),
                mapOf("files" to JsonArray(listOf(file("file" to "e9.txt", "patch" to created, "status" to "added", "additions" to 1, "deletions" to 0)))),
            )
        val change = PermissionChange.of(request)!!
        assertEquals(listOf(FileChange("e9.txt", ChangeKind.Create, created)), change.files)
        assertEquals(created, change.first)
        assertFalse(change.missing)
        val asked = PermissionAsk.of(request)
        assertEquals(AskVerb.Create, asked.verb)
        assertEquals(1, asked.targets.single().added)
    }

    @Test
    fun `B-CHAT-REV1-011 OpenCode 2 edit is an edit, never a creation, whatever its diff looks like`() {
        val change =
            PermissionChange.of(
                ask("edit", listOf("a.kt"), mapOf("files" to JsonArray(listOf(file("file" to "a.kt", "patch" to created, "status" to "modified"))))),
            )!!
        assertEquals(ChangeKind.Edit, change.files.single().kind)
    }

    @Test
    fun `B-CHAT-REV1-012 OpenCode 1 and Paseo send one diff with the path, and a diff from nothing is a creation`() {
        val oc1 = PermissionChange.of(ask("edit", listOf("a.kt"), mapOf("filepath" to JsonPrimitive("/w/a.kt"), "diff" to JsonPrimitive(edited))))!!
        assertEquals(listOf(FileChange("a.kt", ChangeKind.Edit, edited)), oc1.files)
        val paseo = PermissionChange.of(ask("edit", listOf("e9.txt"), mapOf("filePath" to JsonPrimitive("e9.txt"), "diff" to JsonPrimitive(created))))!!
        assertEquals(ChangeKind.Create, paseo.files.single().kind)
    }

    @Test
    fun `B-CHAT-REV1-013 a path with no content is missing, a deletion needs none, and other asks have no change`() {
        assertTrue(PermissionChange.of(ask("edit", listOf("x.txt"), mapOf("filePath" to JsonPrimitive("x.txt"))))!!.missing)
        val deleted = ask("edit", listOf("x.txt"), mapOf("files" to JsonArray(listOf(file("file" to "x.txt", "status" to "deleted")))))
        assertFalse(PermissionChange.of(deleted)!!.missing)
        assertNull(PermissionChange.of(ask("bash", listOf("npm test"), emptyMap())))
        assertNull(PermissionChange.of(ask("read", listOf("a.kt"), emptyMap())))
    }
}
