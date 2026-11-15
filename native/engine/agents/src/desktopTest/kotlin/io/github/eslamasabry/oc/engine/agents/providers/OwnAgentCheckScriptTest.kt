package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.scripts.sh
import io.github.eslamasabry.oc.engine.agents.scripts.tempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue 147: "Check" really starts the command and tells an ACP agent from anything else (fake programs). */
class OwnAgentCheckScriptTest {
    private fun program(dir: File, name: String, body: String): File =
        File(dir, name).apply {
            writeText("#!/usr/bin/env python3\nimport json, os, sys\n$body\n")
            setExecutable(true)
        }

    private fun check(argv: List<String>, env: Map<String, String> = emptyMap()): OwnAgentCheckResult {
        val agent = OwnAgent(AgentId("own-test"), "Test", argv, null, env.keys.toList())
        val run = sh(listOf("-c", OwnAgentCheckScript.script(ProfileId("owner-a"))), stdin = OwnAgentCheckScript.stdin(agent, env))
        assertEquals("", run.stdout)
        return OwnAgentCheckResult.of(run.exitCode)
    }

    @Test
    fun `B-AGENT-OWN-060 an ACP agent answers, other programs are told apart, and its env values reach it`() {
        val dir = tempDir("oc-own-agent")
        try {
            val acp = program(
                dir,
                "acp-agent",
                """
                line = sys.stdin.readline()
                req = json.loads(line)
                if os.environ.get("FAKE_KEY") != "fake-key-not-real-0000": sys.exit(5)
                print(json.dumps({"jsonrpc": "2.0", "id": req["id"], "result": {"protocolVersion": 1, "agentCapabilities": {}}}), flush=True)
                sys.stdin.readline()
                """.trimIndent(),
            )
            val chatty = program(dir, "chatty", "sys.stdin.readline()\nprint('hello, I am not ACP', flush=True)\nprint(json.dumps({'id': 1, 'error': {}}), flush=True)\nsys.stdin.readline()")
            val quitter = program(dir, "quitter", "sys.exit(1)")

            assertEquals(OwnAgentCheckResult.Answered, check(listOf(acp.absolutePath), mapOf("FAKE_KEY" to "fake-key-not-real-0000")))
            assertEquals(OwnAgentCheckResult.Stopped, check(listOf(acp.absolutePath), mapOf("FAKE_KEY" to "wrong-value-000")))
            assertEquals(OwnAgentCheckResult.NotAcp, check(listOf(chatty.absolutePath)))
            assertEquals(OwnAgentCheckResult.Stopped, check(listOf(quitter.absolutePath)))
            assertEquals(OwnAgentCheckResult.NotFound, check(listOf(File(dir, "missing").absolutePath)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
