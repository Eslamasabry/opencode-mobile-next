package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * w4-mcp-secrets, end to end through a real Oc2Gateway: a `/config` answer in the recorded 2.2.5 shape (a bare
 * `Config.Entry` list, servers under `info.mcp.servers`) registers every MCP env and header value, so the Server setup
 * page, previews, tool rows and logs mask them. Fakes are built from adjacent literals; assertion messages never print
 * them.
 */
class Oc2McpConfigSecretsTest {
    private val githubValue = "env-" + "W4McpGithub" + "1h2i"
    private val envValue = "env-" + "W4McpEnvValue" + "3j4k"
    private val customValue = "hdr-" + "W4McpCustom" + "4k5l"
    private val headerValue = "hdr-" + "W4McpHeader" + "5l6m"
    private val bearer = "bear-" + "W4McpBearer" + "7n8p"
    private val queryKey = "qk-" + "W4McpQuery" + "9q0r"

    private val configAnswer =
        """
        [{"type":"directory","path":"/root/projects"},
         {"type":"document","path":"/root/projects/opencode.json","info":{"mcp":{"servers":{
           "github":{"type":"local","command":["npx","-y","@modelcontextprotocol/server-github"],
                     "environment":{"GITHUB_TOKEN":"$githubValue","SESSION_VALUE":"$envValue","NODE_ENV":"production"}},
           "linear":{"type":"remote","url":"https://mcp.linear.example/sse?token=$queryKey",
                     "headers":{"X-Custom-Token":"$customValue","X-Workspace":"$headerValue",
                                "Authorization":"Bearer $bearer"}}}}}}]
        """.trimIndent()

    private val integrationAnswer =
        """
        {"location":{"directory":"/root/projects"},"data":[
          {"id":"amazon-bedrock","name":"Amazon Bedrock","methods":[
            {"id":"aws-profile","type":"oauth","label":"AWS profile","form":[
              {"key":"profile","title":"AWS profile","required":true,"type":"string","options":[],"custom":true}]}],
           "connections":[]},
          {"id":"acme","name":"Acme","methods":[
            {"id":"acme-oauth","type":"oauth","label":"Sign in","form":[
              {"key":"workspace","title":"Workspace","required":true,"type":"string"}]}],"connections":[]}
        ]}
        """.trimIndent()

    private fun server(redactor: Redactor) =
        RowServer(redactor = redactor) { seen ->
            when {
                seen.path.endsWith("/config") -> json(configAnswer)
                seen.path.endsWith("/connect/oauth") ->
                    json(
                        """{"data":{"attemptID":"att_1","url":"https://example.test/device","instructions":"",
                           "mode":"auto","time":{"created":1,"expires":2}}}"""
                    )
                seen.path.endsWith("/integration") -> json(integrationAnswer)
                else -> json("""{"data":[]}""")
            }
        }

    private val secrets =
        mapOf(
            "GITHUB_TOKEN value" to githubValue,
            "env value under an ordinary name" to envValue,
            "X-Custom-Token value" to customValue,
            "header value under an ordinary name" to headerValue,
            "Authorization token" to bearer,
            "url token query" to queryKey,
        )

    @Test
    fun `W4-MCP the Server setup page shows a config without its MCP values, names and ordinary words intact`() = runTest {
        val redactor = Redactor()
        val read = server(redactor).gateway.setupInspection(ServerLocation.Default).readConfig()

        assertTrue(read is Outcome.Ok, "the config answer must parse")
        val shown = read.value.sources.mapNotNull { it.redactedInfo }.joinToString("\n")
        for ((kind, secret) in secrets) assertFalse(secret in shown, "the $kind is shown on the Server setup page")
        val words =
            listOf("GITHUB_TOKEN", "SESSION_VALUE", "X-Custom-Token", "X-Workspace", "github", "linear", "production", "mcp.linear.example")
        for (word in words)
            assertTrue(word in shown, "the Server setup page lost an ordinary word of the MCP config")
    }

    @Test
    fun `W4-MCP after any config read the MCP values are masked in previews, tool rows and logs`() = runTest {
        val redactor = Redactor()
        val gateway = server(redactor).gateway
        assertTrue(gateway.setupConfigSources() is Outcome.Ok, "the config sources must parse")
        val preview = gateway.configurationPreview(ServerLocation.Default)
        assertTrue(preview is Outcome.Ok, "the configuration preview must parse")

        for ((kind, secret) in secrets) {
            assertFalse(secret in preview.value.text, "the $kind is shown in the configuration preview")
            val lines =
                listOf(
                    "preview" to redactor.redact("Created /root/projects/bg5.txt with $secret"),
                    "tool row" to redactor.redact("echo $secret > /root/projects/out.txt"),
                    "log line" to redactor.redact("sent $secret to /root/projects"),
                    "diagnostics" to redactor.diagnostics("sent $secret to /root/projects"),
                    "log excerpt" to redactor.excerpt("sent $secret to /root/projects"),
                )
            for ((where, out) in lines) {
                assertFalse(secret in out, "the $kind leaks in the $where")
                assertTrue("/root/projects" in out, "the $where lost the folder name next to the $kind")
            }
        }
        assertEquals(
            "github and linear at https://mcp.linear.example/sse, GITHUB_TOKEN in production",
            redactor.redact("github and linear at https://mcp.linear.example/sse, GITHUB_TOKEN in production"),
        )
    }
}
