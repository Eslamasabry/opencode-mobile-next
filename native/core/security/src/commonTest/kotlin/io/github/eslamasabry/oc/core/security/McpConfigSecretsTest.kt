package io.github.eslamasabry.oc.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * w4-mcp-secrets: an MCP server's environment and header values are secrets whatever they are named, so every
 * `/config` read registers them through [Redactor.registerCredentialValues]. The answers copy the recorded shapes
 * (2.2.5 `Config.InfoEncoded.mcp.servers` with `Mcp.LocalConfigEncoded`/`Mcp.RemoteConfigEncoded`, the older
 * `mcp.<name>` layout and another agent's `mcpServers`). Fakes are built from adjacent literals; failure messages name
 * the place, never the value.
 */
class McpConfigSecretsTest {
    private val envToken = "env-" + "McpFakeEnvValue" + "7x8y9z"
    private val githubToken = "ghp_" + "FAKEFAKEFAKEFAKE1234"
    private val notionAuth = "ntn-" + "McpFakeNotion" + "3c4d"
    private val customHeader = "hdr-" + "McpFakeHeader" + "5e6f"
    private val bearer = "bear-" + "McpFakeBearer" + "1a2b"
    private val workspaceHeader = "wsp-" + "McpFakeWorkspace" + "9q"
    private val flagToken = "cmd-" + "McpFakeFlag" + "8g9h"
    private val nextPassword = "nxt-" + "McpFakeNext" + "2k3l"
    private val wordSecret = "sec-" + "McpFakeWord" + "4m5n"
    private val argBearer = "hb-" + "McpFakeArgBearer" + "6p7q"
    private val urlPassword = "pw-" + "McpFakeUrlPass" + "8r9s"
    private val queryKey = "qk-" + "McpFakeQuery" + "1t2u"
    private val pathToken = "Zp" + "McpFakePathToken" + "0123456789ab"
    private val clientSecret = "cs-" + "McpFakeClient" + "3v4w"
    private val legacyValue = "oc1-" + "McpFakeLegacy" + "5x6y"
    private val otherAgentValue = "ext-" + "McpFakeOther" + "7z8a"
    private val numericPassword = "48" + "29" + "13"

    /** A 2.2.5 `/config` answer: one project document with a local and two remote servers. */
    private val configAnswer =
        """
        {"data":[
          {"type":"directory","path":"/root/projects"},
          {"type":"document","path":"/root/projects/opencode.json","info":{
            "model":"anthropic/claude-sonnet",
            "mcp":{"timeout":{"startup":30000},"servers":{
              "github":{"type":"local","cwd":"/root/projects",
                "command":["npx","-y","@modelcontextprotocol/server-github","--api-token=$flagToken",
                           "--password","$nextPassword","--port","8080","API_SECRET=$wordSecret","--verbose"],
                "environment":{"GITHUB_TOKEN":"$githubToken","NOTION_AUTH":"$notionAuth","SESSION_VALUE":"$envToken",
                  "NODE_ENV":"production","DEBUG":"true","LOG_DIR":"/usr/local/var/log","MAX_TOKENS":"100000",
                  "AWS_REGION":"us-east-1","HOME_PATHS":"/usr/bin:/root/.local/bin","GITLAB_TOKEN":"{env:GITLAB_TOKEN}",
                  "SLACK_PIN_PASSWORD":"$numericPassword"}},
              "linear":{"type":"remote",
                "url":"https://mcpuser:$urlPassword@mcp.linear.example/sse?api_key=$queryKey&mode=compact",
                "headers":{"X-Custom-Token":"$customHeader","Authorization":"Bearer $bearer","X-Workspace":"$workspaceHeader",
                  "Accept":"application/json","MCP-Protocol-Version":"2025-06-18"},
                "oauth":{"client_id":"oc-mobile","client_secret":"$clientSecret"}},
              "project-notes":{"type":"local",
                "command":["npx","mcp-remote","https://hooks.notes.example/api/mcp/s/$pathToken/mcp",
                           "--header","Authorization: Bearer $argBearer"]}
            }}}}
        ]}
        """.trimIndent()

    /** Each fake and where it sits. */
    private val secrets =
        mapOf(
            "env GITHUB_TOKEN" to githubToken,
            "env NOTION_AUTH" to notionAuth,
            "env with an ordinary name" to envToken,
            "credential-named numeric env" to numericPassword,
            "header X-Custom-Token" to customHeader,
            "Authorization token" to bearer,
            "header with an ordinary name" to workspaceHeader,
            "command --api-token=" to flagToken,
            "command --password <value>" to nextPassword,
            "command API_SECRET=" to wordSecret,
            "command --header value" to argBearer,
            "url password" to urlPassword,
            "url api_key query" to queryKey,
            "url path token" to pathToken,
            "oauth client_secret" to clientSecret,
        )

    /** Words around the secrets that must stay readable everywhere. */
    private val ordinary =
        listOf(
            "GITHUB_TOKEN", "NOTION_AUTH", "SESSION_VALUE", "X-Custom-Token", "X-Workspace", "Authorization",
            "github", "linear", "project-notes", "project", "projects", "server", "Check the server and try again.",
            "/root/projects/bg5.txt", "production", "true", "/usr/local/var/log", "/usr/bin:/root/.local/bin", "100000",
            "us-east-1", "application/json", "2025-06-18", "{env:GITLAB_TOKEN}", "mcpuser", "oc-mobile", "compact",
            "npx -y @modelcontextprotocol/server-github --port 8080 --verbose", "mcp-remote", "mcp.linear.example",
            "https://mcp.linear.example/sse", "https://hooks.notes.example/api/mcp/s/", "Bearer", "anthropic/claude-sonnet",
        )

    private fun read(answer: String): Redactor = Redactor().apply { registerCredentialValues(Json.parseToJsonElement(answer)) }

    private fun assertMaskedEverywhere(redactor: Redactor, kind: String, secret: String) {
        assertTrue(redactor.containsSecret(secret), "$kind is not registered")
        val preview = "Created /root/projects/bg5.txt with $secret"
        val toolRow = "echo $secret > /root/projects/out.txt"
        val log = "request failed\nsent $secret to /root/projects\n"
        for ((where, out) in
            listOf(
                "preview" to redactor.redact(preview),
                "tool row" to redactor.redact(toolRow),
                "log line" to redactor.redact(log),
                "diagnostics" to redactor.diagnostics(log),
                "log excerpt" to redactor.excerpt(log),
            )) {
            assertFalse(secret in out, "$kind leaks in the $where")
            assertTrue("/root/projects" in out, "the $where lost the folder name next to the $kind")
        }
    }

    @Test
    fun `W4-MCP every env and header value and the command and URL credentials are masked everywhere`() {
        val redactor = read(configAnswer)
        for ((kind, secret) in secrets) assertMaskedEverywhere(redactor, kind, secret)
    }

    @Test
    fun `W4-MCP names, server names, plain URLs and ordinary values stay readable`() {
        val redactor = read(configAnswer)
        for (word in ordinary) assertEquals(word, redactor.redact(word), "an ordinary word of the MCP config is masked")
        // A host and a plain URL keep their letters inside other text too.
        assertEquals(
            "connecting to https://mcp.linear.example/sse for project github",
            redactor.redact("connecting to https://mcp.linear.example/sse for project github"),
        )
    }

    @Test
    fun `W4-MCP the older mcp layout and another agent's mcpServers are read the same way`() {
        val redactor =
            read(
                """
                {"mcp":{"files":{"type":"local","command":["node","/root/projects/server.js"],
                                 "environment":{"FS_ROOT":"$legacyValue","FS_MODE":"readonly"}}},
                 "mcpServers":{"tracker":{"command":"npx","args":["-y","tracker-mcp"],"env":{"TEAM":"$otherAgentValue"}}}}
                """.trimIndent()
            )
        assertMaskedEverywhere(redactor, "older layout env", legacyValue)
        assertMaskedEverywhere(redactor, "mcpServers env", otherAgentValue)
        for (word in listOf("FS_ROOT", "readonly", "files", "tracker", "tracker-mcp", "/root/projects/server.js"))
            assertEquals(word, redactor.redact(word), "an ordinary word of the MCP config is masked")
    }

    @Test
    fun `W4-MCP an mcp member that is not a server registers nothing beyond the walker`() {
        val redactor =
            read("""{"permission":{"mcp":"ask"},"experimental":{"mcp":{"timeout":{"startup":30000},"label":"workspace-tools"}}}""")
        assertEquals("ask workspace-tools 30000", redactor.redact("ask workspace-tools 30000"))
    }

    @Test
    fun `W4-MCP the plainly-not-secret rule`() {
        val plain =
            listOf(
                "production", "Development", "default", "localhost", "enabled", "true", "8080", "300000", "1.2.3",
                "127.0.0.1", "us-east-1", "us-central1", "europe-west4", "application/json", "/usr/bin/node",
                "~/.config/app", "./bin/server", "/usr/bin:/bin", "C:\\Tools\\node", "{env:GITHUB_TOKEN}",
                "{file:~/.secrets/token}", "\${GITHUB_TOKEN}", "Bearer {env:API_TOKEN}",
            )
        for (value in plain) assertTrue(ConfigValues.isPlainlyNotSecret(value), "\"$value\" should count as ordinary")
        val possibleSecrets =
            listOf(
                "my-gcp-" + "project42", "\$up3r" + "Secret", "hunter2" + "hunter2", "/9j/" + "4AAQSkZJRgABAQ" + "AAAQ",
                "/tmp/Ab3" + "Cd4Ef5Gh6Ij7Kl8", "Basic dXNl" + "cjpwYXNz", "eu-" + "wizard-7x", "with space/x",
            )
        for (value in possibleSecrets) assertFalse(ConfigValues.isPlainlyNotSecret(value), "a possible secret counts as ordinary")
    }

    @Test
    fun `W4-MCP credential names`() {
        for (name in listOf("GITHUB_TOKEN", "X-Custom-Token", "api_key", "X-API-Key", "--password", "client_secret", "auth", "SSH_KEY"))
            assertTrue(ConfigValues.isCredentialName(name), "$name is not a credential name")
        for (name in listOf("MAX_TOKENS", "NODE_ENV", "project", "server", "--port", "author", "X-Workspace"))
            assertFalse(ConfigValues.isCredentialName(name), "$name counts as a credential name")
    }
}
