package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

/**
 * w4-redact: the redactor masks a registered value everywhere, so what the `/integration` and `/provider` answers
 * register decides what every preview, tool row and log line can show. The answers below copy a 2.2.6 server's shapes
 * (field names, a hidden preset, the free provider's "public" key). Fake credentials are built from adjacent literals
 * and assertion messages never print them.
 */
class Oc2AnswerCredentialsTest {
    private val hiddenPreset = "preset-" + "W4HiddenSecret" + "4k5l6m"
    private val connectionKey = "conn-" + "W4ConnSecret" + "7q8r9s"
    private val providerKey = "prov-" + "W4ApiKeySecret" + "1t2u3v"

    private val integrationAnswer =
        """
        {"location":{"directory":"/root/projects"},"data":[
          {"id":"google-vertex","name":"Google Vertex AI","methods":[{"type":"external","form":[
            {"key":"project","title":"Google Cloud project","required":true,"type":"string",
             "placeholder":"Project ID","default":"demo-cloud-project","custom":true,
             "options":[{"value":"demo-cloud-project","label":"demo-cloud-project"}]},
            {"key":"location","title":"Location","required":true,"type":"string","default":"global",
             "options":[{"value":"global","label":"global"}],"custom":true}]}],"connections":[]},
          {"id":"amazon-bedrock","name":"Amazon Bedrock","methods":[{"type":"external","form":[
            {"key":"profile","title":"AWS profile","required":true,"type":"string","options":[],"custom":true}]}],
           "connections":[]},
          {"id":"github-copilot","name":"GitHub Copilot","methods":[{"type":"oauth","form":[
            {"key":"deploymentType","title":"Select GitHub deployment type","required":true,"type":"string",
             "options":[{"value":"github.com","label":"GitHub.com"},
                        {"value":"enterprise","label":"GitHub Enterprise"}]},
            {"key":"enterpriseUrl","title":"Enter your GitHub Enterprise URL or domain","required":true,
             "when":[{"key":"deploymentType","op":"eq","value":"enterprise"}],"type":"string"}]}],"connections":[]},
          {"id":"snowflake-cortex","name":"Snowflake","methods":[{"type":"key","form":[
            {"key":"account","title":"Snowflake account","required":true,"type":"string"}]}],"connections":[]},
          {"id":"opencode","name":"OpenCode","methods":[{"type":"oauth","form":[
            {"key":"server","hidden":true,"type":"string","default":"$hiddenPreset"}]}],
           "connections":[{"type":"api","id":"conn_1","label":"Work key","key":"$connectionKey"}]}
        ]}
        """.trimIndent()

    private val providerAnswer =
        """
        {"location":{"directory":"/root/projects"},"data":[
          {"id":"opencode","integrationID":"opencode","name":"OpenCode","activation":"enabled",
           "settings":{"apiKey":"public","baseURL":"https://opencode.ai/zen/v1"}},
          {"id":"anthropic","integrationID":"anthropic","name":"Anthropic","activation":"enabled",
           "settings":{"apiKey":"$providerKey"}}
        ]}
        """.trimIndent()

    private fun server(redactor: Redactor) =
        RowServer(redactor = redactor) { seen ->
            when {
                seen.path.endsWith("/integration") -> json(integrationAnswer)
                seen.path.endsWith("/provider") -> json(providerAnswer)
                seen.path.endsWith("/model/default") -> json("""{"data":{}}""")
                seen.path.endsWith("/model") -> json("""{"data":[]}""")
                else -> json("""{"data":[]}""")
            }
        }

    /** What the sweep showed: home previews, a request row and a tool row, all naming the folder /root/projects. */
    private val shown =
        listOf(
            "Created /root/projects/bg5.txt",
            "Here's what's in /root/projects: Folder: second/",
            "Asks to run: wc -c -w -l /root/projects/bg5.txt",
            "/root/projects/bg5.txt && file /root/projects/proof.txt",
            "That did not work. Check the server and try again.",
            "Pick a profile, an account and a location",
            "deploymentType enterpriseUrl",
            "/root/projects/site/public/index.html",
        )

    @Test
    fun `W4-REDACT the sign-in form field names are not registered, so a projects path keeps its letters`() = runTest {
        val redactor = Redactor()
        val read = server(redactor).gateway.integrations()

        assertTrue(read is Outcome.Ok, "the integration answer must still parse")
        val rows = read.value
        for (line in shown) assertEquals(line, redactor.redact(line))
        // The form's own words reach the person unmasked too (they went through the same redactor).
        val vertex = rows.single { it.id == "google-vertex" }.methods.single().prompts.map { it["message"] }
        assertEquals(listOf(JsonPrimitive("Google Cloud project"), JsonPrimitive("Location")), vertex)
        // A visible default is shown to the person in the form; it is no secret either.
        assertEquals("region global, demo-cloud-project", redactor.redact("region global, demo-cloud-project"))
    }

    @Test
    fun `W4-REDACT the free provider public key is not registered, so a public folder keeps its name`() = runTest {
        val redactor = Redactor()
        val gateway = server(redactor).gateway
        gateway.providers()
        gateway.catalog()

        for (line in shown) assertEquals(line, redactor.redact(line))
    }

    @Test
    fun `W4-REDACT a hidden preset, a connection key and a provider key stay masked in previews, tool rows and logs`() =
        runTest {
            val redactor = Redactor()
            val gateway = server(redactor).gateway
            gateway.integrations()
            gateway.providers()

            val secrets =
                mapOf(
                    "hidden preset" to hiddenPreset,
                    "connection key" to connectionKey,
                    "provider key" to providerKey,
                )
            for ((kind, secret) in secrets) {
                val preview = "Created /root/projects/bg5.txt with $secret"
                val toolRow = "curl -H 'x-test: $secret' https://example.test/root/projects"
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
                    assertTrue(Redactor.MASK in out, "$kind is not masked in the $where")
                    assertTrue("/root/projects" in out, "the $where lost the folder name next to the $kind")
                }
                assertTrue(redactor.containsSecret(secret), "$kind is not registered")
            }
        }
}
