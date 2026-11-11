package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** W4-26: a sign-in method's form reaches the app whole (Flutter ops_integrations.dart `_formPrompt`). */
class Oc2SignInFormRowsTest {
    private val body =
        """{"data":[{"id":"github-copilot","name":"GitHub Copilot","methods":[{"type":"oauth","id":"device","label":"Login with GitHub","form":[
          {"key":"deploymentType","type":"string","title":"Deployment type","required":true,"default":"github.com",
           "options":[{"value":"github.com","label":"GitHub.com"},{"value":"enterprise","label":"GitHub Enterprise"}]},
          {"key":"enterpriseUrl","type":"string","title":"Enterprise URL","format":"uri","placeholder":"company.ghe.com",
           "description":"Your company's GitHub address","when":[{"key":"deploymentType","op":"eq","value":"enterprise"}]},
          {"key":"note","type":"string","title":"Note","custom":true,"required":false,
           "options":[{"value":"a","label":"A"}],"when":{"key":"deploymentType","op":"neq","value":"enterprise"}},
          {"key":"client","type":"string","hidden":true,"default":"abc"}]}],"connections":[]}]}"""

    private suspend fun prompts(): Pair<List<JsonObject>, Map<String, String>> {
        val server = RowServer { json(body) }
        val rows = assertIs<Outcome.Ok<List<IntegrationInfo>>>(server.gateway.integrations()).value
        server.gateway.close()
        val method = rows.single().methods.single()
        return method.prompts.toList() to method.presetAnswers
    }

    @Test
    fun `W4-26 a choice keeps its options, default and required mark`() = runTest {
        val (prompts, _) = prompts()
        val type = prompts.first { it["key"]?.jsonPrimitive?.content == "deploymentType" }
        assertEquals("select", type["type"]?.jsonPrimitive?.content)
        assertEquals("Deployment type", type["message"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(true), type["required"])
        assertEquals("github.com", type["default"]?.jsonPrimitive?.content)
        val options = type["options"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("github.com", "enterprise"), options.map { it["value"]!!.jsonPrimitive.content })
        assertEquals(listOf("GitHub.com", "GitHub Enterprise"), options.map { it["label"]!!.jsonPrimitive.content })
    }

    @Test
    fun `W4-26 a text question keeps its placeholder, description, address format and condition`() = runTest {
        val (prompts, presets) = prompts()
        val url = prompts.first { it["key"]?.jsonPrimitive?.content == "enterpriseUrl" }
        assertEquals("text", url["type"]?.jsonPrimitive?.content)
        assertEquals("uri", url["format"]?.jsonPrimitive?.content)
        assertEquals("company.ghe.com", url["placeholder"]?.jsonPrimitive?.content)
        assertEquals("Your company's GitHub address", url["description"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(false), url["required"])
        val condition = url["when"] as JsonArray
        assertEquals("enterprise", condition.single().jsonObject["value"]?.jsonPrimitive?.content)
        // A field the server lets people type into stays text, and hidden fields are presets, never asked.
        assertEquals("text", prompts.first { it["key"]?.jsonPrimitive?.content == "note" }["type"]?.jsonPrimitive?.content)
        assertEquals(listOf("deploymentType", "enterpriseUrl", "note"), prompts.map { it["key"]!!.jsonPrimitive.content })
        assertEquals(mapOf("client" to "abc"), presets)
    }
}
