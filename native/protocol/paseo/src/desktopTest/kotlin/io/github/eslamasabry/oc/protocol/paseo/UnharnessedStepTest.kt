package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoItemMessage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class UnharnessedStepTest {
    @Test
    fun `B-AGENT-UH-021 captured Python code and output become a readable completed step`() {
        val root =
            generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
                .first {
                    File(
                            it,
                            "native/engine/agents/src/desktopTest/resources/unharnessed/turn-frames.json",
                        )
                        .isFile
                }
        val frames =
            Json.parseToJsonElement(
                    File(
                            root,
                            "native/engine/agents/src/desktopTest/resources/unharnessed/turn-frames.json",
                        )
                        .readText()
                )
                .jsonArray
        val updates = frames.mapNotNull {
            it.jsonObject["received"]
                ?.jsonObject
                ?.get("params")
                ?.jsonObject
                ?.get("update")
                ?.jsonObject
        }
        val call = updates.first { it["sessionUpdate"]?.jsonPrimitive?.content == "tool_call" }
        val id = call["toolCallId"]!!.jsonPrimitive.content
        // Replay the captured upstream shape through our presentation bridge,
        // then through the exact shell-detail shape the pinned helper produces.
        val upstream = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "session/update")
            put(
                "params",
                buildJsonObject {
                    put("sessionId", "fixture-session")
                    put(
                        "update",
                        JsonObject(
                            call +
                                ("rawInput" to
                                    JsonObject(
                                        call["rawInput"]!!.jsonObject.filterKeys { it != "command" }
                                    ))
                        ),
                    )
                },
            )
        }
        val program =
            "import importlib.util,json,sys; s=importlib.util.spec_from_file_location(" bridge
                ",sys.argv[1]); m=importlib.util.module_from_spec(s); s.loader.exec_module(m); print(json.dumps(m.present_step(json.load(sys.stdin))))"
        val process =
            ProcessBuilder(
                    "python3",
                    "-B",
                    "-c",
                    program,
                    File(root, "tool/unharnessed/adapter.py").path,
                )
                .start()
        process.outputStream.use { it.write(upstream.toString().toByteArray()) }
        check(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue(), process.errorStream.bufferedReader().readText())
        val projected =
            Json.parseToJsonElement(process.inputStream.bufferedReader().readText()).jsonObject
        val input = projected["params"]!!.jsonObject["update"]!!.jsonObject["rawInput"]!!.jsonObject
        val code = input["code"]!!.jsonPrimitive.content
        assertEquals(code, input["command"]!!.jsonPrimitive.content)
        val end = updates.first {
            it["sessionUpdate"]?.jsonPrimitive?.content == "tool_call_update" &&
                it["toolCallId"]?.jsonPrimitive?.content == id &&
                it["status"]?.jsonPrimitive?.content == "completed"
        }
        val output =
            end["content"]!!.jsonArray.joinToString("\n") {
                it.jsonObject["content"]!!.jsonObject["text"]!!.jsonPrimitive.content
            }
        val detail = buildJsonObject {
            put("type", "shell")
            put("command", code)
            put("output", output)
        }
        val item = buildJsonObject {
            put("type", "tool_call")
            put("name", "execute")
            put("status", "completed")
            put("detail", detail)
        }
        val message = paseoItemMessage("fixture-session", item, id, "unharnessed", 0L, 1L)!!
        val tool = message.parts.single().tool!!
        assertEquals(code, tool.input["command"]!!.jsonPrimitive.content)
        assertTrue(code.contains("hello.py"))
        assertTrue(tool.output.contains("hello from phone"))
    }
}
