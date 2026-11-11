package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * w4-redact gate: what the `/integration` and `/provider` answers register, walked over answers built to the recorded
 * 2.2.5/2.2.6 schema (`Integration.Info`, `Integration.*Method`, `Form.StringField`, `Form.When`,
 * `Connection.CredentialInfo`/`EnvInfo`, `Provider.Info` with open `settings`, `headers` and `body`). Fake credentials
 * are built from adjacent literals; assertion messages name the place, never the value.
 */
class Oc2AnswerCredentialsShapeTest {
    private val hiddenPreset = "preset-" + "GateHidden" + "8x9y"
    private val metadataToken = "meta-" + "GateToken" + "2b3c"
    private val providerKey = "prov-" + "GateApiKey" + "4d5e"
    private val bearer = "bear-" + "GateBearer" + "6f7g"
    private val headerKey = "hdr-" + "GateHeader" + "8h9i"
    private val bodyKey = "body-" + "GateBody" + "1j2k"
    private val nestedFormKey = "form-" + "GateNested" + "3l4m"
    private val otherPublic = "public"

    private val integrationAnswer =
        """
        {"location":{"directory":"/root/projects"},"data":[
          {"id":"google-vertex","name":"Google Vertex AI","methods":[
            {"id":"vertex-adc","type":"external","label":"Application default credentials","form":[
              {"key":"project","title":"Google Cloud project","required":true,"type":"string",
               "placeholder":"Project ID","custom":true,"options":[{"value":"demo-cloud-project","label":"demo-cloud-project"}]},
              {"key":"location","title":"Location","required":true,"type":"string","default":"global","custom":true,
               "options":[{"value":"global","label":"global"},{"value":"us-central1","label":"us-central1"}]}]}],
           "connections":[{"type":"env","name":"GOOGLE_APPLICATION_CREDENTIALS"}]},
          {"id":"amazon-bedrock","name":"Amazon Bedrock","methods":[
            {"id":"aws-profile","type":"external","label":"AWS profile","form":[
              {"key":"profile","title":"AWS profile","required":true,"type":"string","options":[],"custom":true}]}],
           "connections":[]},
          {"id":"github-copilot","name":"GitHub Copilot","methods":[
            {"id":"device","type":"oauth","label":"Sign in with GitHub","form":[
              {"key":"deploymentType","title":"Select GitHub deployment type","required":true,"type":"string",
               "options":[{"value":"github.com","label":"GitHub.com"},{"value":"enterprise","label":"GitHub Enterprise"}]},
              {"key":"enterpriseUrl","title":"Enter your GitHub Enterprise URL or domain","required":true,"type":"string",
               "when":[{"key":"deploymentType","op":"eq","value":"enterprise"}]}]}],
           "connections":[{"type":"credential","id":"cred_01","label":"Work account","method":"oauth",
                           "status":{"status":"needs_auth","message":"Sign in again"}}]},
          {"id":"cloudflare-ai-gateway","name":"Cloudflare AI Gateway","metadata":{"token":"$metadataToken"},"methods":[
            {"type":"key","label":"API token","form":[
              {"key":"accountId","title":"Account ID","required":true,"type":"string"},
              {"key":"gatewayId","title":"Gateway ID","required":true,"type":"string"}]}],"connections":[]},
          {"id":"gitlab","name":"GitLab","methods":[
            {"id":"gitlab-oauth","type":"oauth","label":"Sign in with GitLab","form":[
              {"key":"instanceUrl","title":"GitLab instance","type":"string","format":"uri","default":"https://gitlab.com"}]}],
           "connections":[]},
          {"id":"opencode","name":"OpenCode","methods":[
            {"id":"console","type":"oauth","label":"Sign in","form":[
              {"key":"server","hidden":true,"type":"string","default":"$hiddenPreset"}]},
            {"type":"env","names":["OPENCODE_API_KEY"]}],"connections":[]}
        ]}
        """.trimIndent()

    private val providerAnswer =
        """
        {"location":{"directory":"/root/projects"},"data":[
          {"id":"opencode","integrationID":"opencode","name":"OpenCode","activation":"enabled",
           "package":"aisdk:@ai-sdk/openai-compatible","settings":{"apiKey":"public","baseURL":"https://opencode.ai/zen/v1"}},
          {"id":"anthropic","integrationID":"anthropic","name":"Anthropic","activation":"auto","package":"aisdk:@ai-sdk/anthropic",
           "settings":{"apiKey":"$providerKey"},
           "headers":{"anthropic-beta":"interleaved-thinking-2025-05-14,fine-grained-tool-streaming-2025-05-14"}},
          {"id":"openai","integrationID":"openai","name":"OpenAI","activation":"enabled","package":"aisdk:@ai-sdk/openai",
           "settings":{"baseURL":"https://chatgpt.com/backend-api/codex","reasoningEffort":"medium","include":["reasoning.encrypted_content"],"store":false},
           "headers":{"originator":"opencode","chatgpt-account-id":"00000000-0000-0000-0000-000000000000"}},
          {"id":"zai","integrationID":"zai","name":"Z.ai (GLM)","activation":"enabled","package":"aisdk:@ai-sdk/openai-compatible",
           "settings":{"baseURL":"https://api.z.ai/api/coding/paas/v4","apiKey":""}},
          {"id":"self-hosted","integrationID":"self-hosted","name":"Self hosted","activation":"enabled",
           "package":"aisdk:@ai-sdk/openai-compatible","settings":{"baseURL":"https://llm.example.test/v1"},
           "headers":{"Authorization":"Bearer $bearer","x-api-key":"$headerKey"},"body":{"api_key":"$bodyKey"}}
        ]}
        """.trimIndent()

    private val credentials =
        mapOf(
            "hidden preset" to hiddenPreset,
            "integration metadata token" to metadataToken,
            "provider settings.apiKey" to providerKey,
            "Authorization bearer" to bearer,
            "x-api-key header" to headerKey,
            "body api_key" to bodyKey,
        )

    /** Every member name and string value in [value], ordinary words and credentials alike. */
    private fun words(value: JsonElement): Set<String> =
        when (value) {
            is JsonArray -> value.flatMap(::words).toSet()
            is JsonObject -> value.flatMap { (name, field) -> words(field) + name }.toSet()
            is JsonPrimitive -> if (value.isString) setOf(value.content) else emptySet()
        }

    private fun server(redactor: Redactor) =
        RowServer(redactor = redactor) { seen ->
            when {
                seen.path.endsWith("/connect/oauth") ->
                    json(
                        """{"data":{"attemptID":"att_1","url":"https://github.com/login/device","instructions":"",
                           "mode":"auto","time":{"created":1,"expires":2}}}"""
                    )
                seen.path.endsWith("/integration") -> json(integrationAnswer)
                seen.path.endsWith("/provider") -> json(providerAnswer)
                seen.path.endsWith("/model/default") -> json("""{"data":{}}""")
                else -> json("""{"data":[]}""")
            }
        }

    @Test
    fun `W4-REDACT-GATE a full integration and provider answer registers every credential and no ordinary word`() =
        runTest {
            val redactor = Redactor()
            val gateway = server(redactor).gateway
            assertTrue(gateway.integrations() is Outcome.Ok, "the integration answer must parse")
            gateway.providers()

            for ((place, secret) in credentials) {
                assertTrue(redactor.containsSecret(secret), "$place is not registered")
                assertFalse(secret in redactor.redact("in /root/projects: $secret"), "$place leaks")
            }
            // The whole bearer header value is registered too, not only the token.
            assertFalse(bearer in redactor.redact("sent Bearer $bearer"), "the Authorization value leaks")

            val ordinary =
                (words(Json.parseToJsonElement(integrationAnswer)) + words(Json.parseToJsonElement(providerAnswer)))
                    .filter { word -> credentials.values.none { it in word } }
            for (word in ordinary) assertEquals(word, redactor.redact(word), "an ordinary word of the answer is masked")
            assertEquals(
                "/root/projects/github.com/enterprise/public/server/location.txt",
                redactor.redact("/root/projects/github.com/enterprise/public/server/location.txt"),
            )
        }

    @Test
    fun `W4-REDACT-GATE only a sign-in method form and only the free provider public key are left out of the walk`() {
        val redactor = Redactor()
        redactor.registerAnswerCredentials(
            Json.parseToJsonElement(
                """
                {"data":[
                  {"id":"custom","settings":{"apiKey":"$otherPublic","form":[{"apiKey":"$nestedFormKey"}]},
                   "body":{"when":[{"key":"$bodyKey"}]}},
                  {"id":"opencode","settings":{"apiKey":"Public"},"headers":{"x-api-key":"$headerKey"}}
                ]}
                """.trimIndent()
            )
        )
        // A `form` or `when` list anywhere but a sign-in method is walked as before.
        assertTrue(redactor.containsSecret(nestedFormKey), "a form list in provider settings is skipped")
        assertTrue(redactor.containsSecret(bodyKey), "a when list in a provider body is skipped")
        // "public" is exempt only as the built-in provider's settings.apiKey, exactly.
        assertTrue(redactor.containsSecret("public"), "another provider's public key is not registered")
        assertTrue(redactor.containsSecret("Public"), "the exemption is not exact-case")
        assertTrue(redactor.containsSecret(headerKey), "the free provider's other credentials are skipped")
    }

    @Test
    fun `W4-REDACT-GATE a typed answer that is one of the field's published choices is not registered`() = runTest {
        val redactor = Redactor()
        val gateway = server(redactor).gateway
        gateway.integrations()
        val typedUrl = "https://ghe-" + "gate" + ".example.test"
        val launch =
            gateway.startIntegrationOAuth(
                "github-copilot",
                "device",
                persistentMapOf("deploymentType" to "enterprise", "enterpriseUrl" to typedUrl),
                null,
            )

        assertTrue(launch is Outcome.Ok, "the sign-in must start")
        assertEquals("GitHub Enterprise on github.com", redactor.redact("GitHub Enterprise on github.com"))
        assertFalse(redactor.containsSecret("enterprise"), "a published choice is registered")
        // A free-text answer is still registered whole: the form schema has no secret flag to go by.
        assertTrue(redactor.containsSecret(typedUrl), "a typed free-text answer is not registered")

        gateway.startIntegrationOAuth(
            "google-vertex",
            "vertex-adc",
            persistentMapOf("location" to "us-central1", "project" to "my-gcp-" + "project42"),
            null,
        )
        assertFalse(redactor.containsSecret("us-central1"), "a published location is registered")
        assertTrue(redactor.containsSecret("my-gcp-" + "project42"), "a typed value outside the choices is not registered")
    }

    @Test
    fun `W4-REDACT-GATE without a sign-in list read every typed answer is registered`() = runTest {
        val redactor = Redactor()
        val gateway = server(redactor).gateway
        gateway.startIntegrationOAuth("github-copilot", "device", persistentMapOf("deploymentType" to "enterprise"), null)

        assertTrue(redactor.containsSecret("enterprise"), "an answer with no known choices must be registered")
    }
}
