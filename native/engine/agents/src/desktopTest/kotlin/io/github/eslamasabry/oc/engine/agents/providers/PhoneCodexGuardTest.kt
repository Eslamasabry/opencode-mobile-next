package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.scripts.run
import io.github.eslamasabry.oc.engine.agents.scripts.tempDir
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue 147, Galaxy S22 build 10050: under "Ask first" Codex wrote a file and ran a command with no request. The guard
 * between the helper and `codex app-server` makes every thread and turn ask (`untrusted`, reviewed by the user) and
 * declines, by itself, what a read-only (Plan) thread asks for. A fake `codex` records what reaches it.
 */
class PhoneCodexGuardTest {
    /** A fake `codex`: records what reaches it in [log], answers starts, and asks for a command and a file change. */
    private fun fakeCodex(log: File): String =
        """
        #!/usr/bin/env python3
        import json, sys
        if sys.argv[1:2] != ["app-server"]:
            print("codex-cli 0.169.0"); sys.exit(0)
        log = open("LOG", "a")
        for line in sys.stdin:
            log.write(line); log.flush()
            m = json.loads(line)
            method = m.get("method")
            if method == "thread/start":
                print(json.dumps({"id": m["id"], "result": {"thread": {"id": m["params"]["cwd"]}}}), flush=True)
            elif method == "turn/start":
                tid = m["params"]["threadId"]
                print(json.dumps({"id": "ask-" + tid, "method": "item/commandExecution/requestApproval", "params": {"threadId": tid, "itemId": "i", "turnId": "u", "startedAtMs": 1}}), flush=True)
                print(json.dumps({"id": "patch-" + tid, "method": "item/fileChange/requestApproval", "params": {"threadId": tid, "itemId": "j", "turnId": "u", "startedAtMs": 1}}), flush=True)
        """.trimIndent().replace("LOG", log.path) + "\n"

    private fun lines(text: String): List<JsonObject> =
        text.lines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `B-AGENT-CODEX-020 every thread and turn asks the person, and a read-only thread is refused by the guard`() {
        val dir = tempDir("oc-codex-guard")
        try {
            val log = File(dir, "seen.jsonl")
            val codex = File(dir, "codex").apply { writeText(fakeCodex(log)); setExecutable(true) }
            // As the helper sends them: a conversation with no mode, then one in the read-only (Plan) mode.
            val input =
                listOf(
                    """{"id":1,"method":"thread/start","params":{"model":"m","cwd":"rw","config":{}}}""",
                    """{"id":2,"method":"turn/start","params":{"threadId":"rw","input":[]}}""",
                    """{"id":3,"method":"thread/start","params":{"model":"m","cwd":"ro","sandbox":"read-only","approvalPolicy":"on-request","approvalsReviewer":"auto_review"}}""",
                    """{"id":4,"method":"turn/start","params":{"threadId":"ro","input":[],"sandboxPolicy":{"type":"readOnly"},"approvalPolicy":"on-request"}}""",
                ).joinToString("\n", postfix = "\n")
            val inputFile = File(dir, "in.jsonl").apply { writeText(input) }
            // The helper keeps the pipe open while the conversation runs: so does this (the sleep).
            val out =
                run(
                    listOf("sh", "-c", "(cat '${inputFile.path}'; sleep 2) | python3 -c \"\$GUARD\" '${codex.path}' app-server"),
                    environment = mapOf("GUARD" to PhoneCodexSandbox.GUARD),
                )
            assertEquals(0, out.exitCode, out.stderr)

            // What reached Codex: every start and turn asks, reviewed by the person, with the sandbox off.
            val seen = lines(log.readText())
            val starts = seen.filter { it["method"]?.jsonPrimitive?.content in setOf("thread/start", "turn/start") }
            assertEquals(4, starts.size)
            for (m in starts) {
                val p = m["params"]!!.jsonObject
                assertEquals("untrusted", p["approvalPolicy"]!!.jsonPrimitive.content, m.toString())
                assertEquals("user", p["approvalsReviewer"]!!.jsonPrimitive.content, m.toString())
                if (m["method"]!!.jsonPrimitive.content == "turn/start")
                    assertEquals("dangerFullAccess", p["sandboxPolicy"]!!.jsonObject["type"]!!.jsonPrimitive.content)
                else assertEquals("danger-full-access", p["sandbox"]!!.jsonPrimitive.content)
            }
            // The read-only thread's requests were declined by the guard, never shown.
            val answers = seen.filter { it["method"] == null && it["result"] != null }
            assertEquals(setOf("ask-ro", "patch-ro"), answers.map { it["id"]!!.jsonPrimitive.content }.toSet())
            assertTrue(answers.all { it["result"]!!.jsonObject["decision"]!!.jsonPrimitive.content == "decline" })
            // The person's thread's requests reached the helper (and from there the app's cards).
            val shown = lines(out.stdout).filter { it["method"] != null }.map { it["id"]!!.jsonPrimitive.content }.toSet()
            assertEquals(setOf("ask-rw", "patch-rw"), shown)

            // Anything else (a version check, a sign-in) runs the program itself.
            val version = run(listOf("python3", "-c", PhoneCodexSandbox.GUARD, codex.path, "--version"))
            assertEquals("codex-cli 0.169.0", version.stdout.trim())
        } finally {
            dir.deleteRecursively()
        }
    }
}
