package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.ToolOutputFile
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

// Flutter test/tool_state_test.dart, one test per Flutter case.
class ToolOutputTest {
    @Test
    fun `B-CHAT-112 completed string output preserves leading and trailing whitespace`() {
        val output = "  first line\nsecond line  \n"
        val view = toolOutputView(ToolState(ToolStatus.Completed, output = output), null)
        assertEquals(output, view.text)
        assertNull(view.errorText)
    }

    @Test
    fun `B-CHAT-112 completed JSON object output remains pretty printed`() {
        val value = obj("""{"message":"ok","details":{"count":2}}""")
        val view = toolOutputView(ToolState(ToolStatus.Completed, outputValue = value), null)
        assertEquals("{\n  \"message\": \"ok\",\n  \"details\": {\n    \"count\": 2\n  }\n}", view.text)
    }

    @Test
    fun `B-CHAT-112 pending state exposes raw input unchanged`() {
        val raw = " {\"command\":\"git status\""
        val view =
            toolOutputView(ToolState(ToolStatus.Pending, input = obj("""{"command":"ignored"}"""), inputRaw = raw), null)
        assertEquals(raw, view.rawInput)
        assertTrue(view.text.isEmpty())
    }

    @Test
    fun `B-CHAT-112 error text preserves leading and trailing whitespace`() {
        val error = "\n  permission denied  \n"
        val view = toolOutputView(ToolState(ToolStatus.Error, output = error), null)
        assertEquals(error, view.text)
        assertEquals(error, view.errorText)
    }

    @Test
    fun `B-CHAT-112 a finished call shows its input pretty printed and a string value as it is`() {
        val view =
            toolOutputView(
                ToolState(ToolStatus.Completed, input = obj("""{"a":[1,"x"],"b":{}}"""), outputValue = JsonPrimitive(" ok ")),
                null,
            )
        assertEquals("{\n  \"a\": [\n    1,\n    \"x\"\n  ],\n  \"b\": {}\n}", view.rawInput)
        assertEquals(" ok ", view.text)
        assertEquals("{}", toolOutputView(ToolState(ToolStatus.Completed), null).rawInput)
    }

    @Test
    fun `B-CHAT-113 structured OpenCode input and metadata remain available to renderers`() {
        val view =
            toolOutputView(
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"filePath":"/workspace/lib/main.dart","offset":12,"limit":30}"""),
                    output = "done",
                    metadata = obj("""{"display":{"type":"file","lineStart":12,"lineEnd":41}}"""),
                ),
                "read",
            )
        assertEquals("/workspace/lib/main.dart", (view.input["filePath"] as JsonPrimitive).content)
        assertEquals("12", (view.input["offset"] as JsonPrimitive).content)
        assertIs<JsonObject>(view.metadata?.get("display"))
    }

    @Test
    fun `B-CHAT-114 ordinary URLs and path-like prose are not invented as files`() {
        val view =
            toolOutputView(
                ToolState(
                    ToolStatus.Completed,
                    outputValue =
                        obj(
                            """{"url":"https://opencode.ai/docs/tools",""" +
                                """"message":"See /tmp/opencode/not-an-artifact.png for background."}"""
                        ),
                ),
                "websearch",
            )
        assertTrue(view.artifacts.isEmpty())
    }

    @Test
    fun `B-CHAT-114 explicit filePath output remains a downloadable artifact`() {
        val view =
            toolOutputView(
                ToolState(ToolStatus.Completed, outputValue = obj("""{"filePath":"/tmp/opencode/shots/captcha-r1.png"}""")),
                "browser screenshot",
            )
        assertEquals(1, view.artifacts.size)
        assertEquals("/tmp/opencode/shots/captcha-r1.png", view.artifacts.single().path)
        // The same output as JSON text is decoded and read the same way.
        val fromText =
            toolOutputView(ToolState(ToolStatus.Completed, output = """ {"filePath":"/tmp/a.png"} """), "browser screenshot")
        assertEquals("a.png", fromText.artifacts.single().displayName)
    }

    @Test
    fun `B-CHAT-114 read attachments inherit the requested file name`() {
        val view =
            toolOutputView(
                ToolState(
                    ToolStatus.Completed,
                    input = obj("""{"filePath":"/workspace/assets/diagram.png"}"""),
                    output = "Image read successfully",
                    outputFiles =
                        persistentListOf(
                            ToolOutputFile(path = null, filename = null, mime = "image/png", url = "data:image/png;base64,aGVsbG8=")
                        ),
                ),
                "read",
            )
        assertEquals(1, view.artifacts.size)
        assertEquals("diagram.png", view.artifacts.single().displayName)
    }

    @Test
    fun `B-CHAT-114 a shell output path is an artifact`() {
        val view =
            toolOutputView(
                ToolState(ToolStatus.Completed, output = "done", metadata = obj("""{"outputPath":"/tmp/out/log.txt"}""")),
                "bash",
            )
        assertEquals(listOf(ToolArtifact("/tmp/out/log.txt", null, null, "log.txt")), view.artifacts)
    }
}
