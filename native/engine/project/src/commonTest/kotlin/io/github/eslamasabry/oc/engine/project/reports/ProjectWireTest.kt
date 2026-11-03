package io.github.eslamasabry.oc.engine.project.reports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

class ProjectWireTest {
    @Test
    fun `B-PROTO-082 a project reference uses the upstream directory file-part contract`() {
        val part = PromptFilePart.reference(name = "docs", path = "/workspace/../shared-docs")
        assertTrue(part.isDirectoryReference)
        assertEquals(
            buildJsonObject {
                put("type", "file")
                put("mime", "application/x-directory")
                put("filename", "docs")
                put("url", "file:///shared-docs")
            },
            part.toJson(),
        )
    }

    @Test
    fun `B-PROTO-082 a project reference preserves a remote Windows server path`() {
        val part = PromptFilePart.reference(name = "platform-docs", path = "C:\\Shared Docs\\platform")
        assertEquals("file:///C:/Shared%20Docs/platform", part.url)
        assertTrue(part.isDirectoryReference)
        assertEquals("file://server/share/x", PromptFilePart.fileUrl("\\\\server\\share\\x"))
        assertEquals("file:///work/caf%C3%A9/a%23b", PromptFilePart.fileUrl("/work/café/a#b"))
        assertFalse(PromptFilePart("text/plain", "a", "file:///a").isDirectoryReference)
    }

    @Test
    fun `B-PROTO-082 binary file content preserves metadata and decodes bytes`() {
        val content = parseFileContent(json("""{"type":"binary","content":"AAEC/w==","encoding":"base64","mimeType":"application/octet-stream"}"""))
        assertTrue(content.isBinary)
        assertEquals("base64", content.encoding)
        assertEquals("application/octet-stream", content.mimeType)
        assertContentEquals(byteArrayOf(0, 1, 2, -1), content.bytes())
        val text = parseFileContent(json("""{"type":"text","content":"hi"}"""))
        assertFalse(text.isBinary)
        assertContentEquals("hi".encodeToByteArray(), text.bytes())
    }

    @Test
    fun `B-PROTO-082 session parsing retains project and workspace ownership`() {
        val session =
            parseSessionOwnership(
                json(
                    """{"id":"session-1","title":"Mobile work","projectID":"project-1","workspaceID":"workspace-1",
                    "directory":"/work/acme/packages/app","path":"packages/app","time":{"created":1,"updated":2}}""",
                ),
            )
        assertEquals("project-1", session.projectId?.raw)
        assertEquals("workspace-1", session.workspaceId?.raw)
        assertEquals("/work/acme/packages/app", session.directory)
        assertEquals("packages/app", session.path)
        assertEquals(2L, session.time?.updatedMs)
    }

    @Test
    fun `B-PROTO-082 the current OpenCode diff shape preserves patch and server counts`() {
        val diff = parseFileDiff(json("""{"file":"lib/main.dart","patch":"@@ -1 +1 @@\n-old\n+new","additions":7,"deletions":3,"status":"modified"}"""))
        assertTrue("+new" in diff.patch!!)
        assertEquals(LineCounts(added = 7, removed = 3), diff.counts)
        assertEquals("modified", diff.status)
        // Without server counts the lines are counted from before and after.
        assertEquals(LineCounts(1, 1), parseFileDiff(json("""{"file":"a","before":"x\ny\nz","after":"x\nY\nz"}""")).counts)
    }
}
