package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * w4-provforms gate: sign-in forms in the recorded 2.2.5 shape (`Form.StringField`, `BooleanField`, `NumberField`,
 * `MultiselectField`, `ExternalField`; `when` is always a list of `{key, op: eq|neq, value}`), read by the merged
 * reader, and what the merged reader registers from them. Fake values are built from adjacent literals.
 */
class Oc2SignInFormShapeTest {
    private val hiddenPreset = "preset-" + "FormGate" + "7q8r"

    private val body =
        """{"data":[
          {"id":"gitlab","name":"GitLab","methods":[{"id":"gitlab-oauth","type":"oauth","label":"Sign in with GitLab","form":[
            {"key":"instanceUrl","title":"GitLab instance","type":"string","format":"uri","default":"https://gitlab.com"},
            {"key":"client","type":"string","hidden":true,"default":"$hiddenPreset"}]}],"connections":[]},
          {"id":"shapes","name":"Shapes","methods":[{"id":"all","type":"oauth","label":"Every field type","form":[
            {"key":"mode","title":"Mode","type":"string","required":true,"default":"basic",
             "options":[{"value":"basic","label":"Basic"},{"value":"advanced","label":"Advanced"}]},
            {"key":"verbose","title":"Verbose","type":"boolean","default":true,
             "when":[{"key":"mode","op":"eq","value":"advanced"}]},
            {"key":"port","title":"Port","type":"number","default":8080},
            {"key":"scopes","title":"Scopes","type":"multiselect","options":[{"value":"read","label":"Read"}],"default":["read"]},
            {"key":"docs","title":"Read the guide","type":"external","url":"https://example.test/guide"},
            {"key":"future","title":"Something new","type":"colour"}]}],"connections":[]}
        ]}"""

    private fun server(redactor: Redactor) =
        RowServer(redactor = redactor) { seen ->
            if (seen.path.endsWith("/connect/oauth"))
                json(
                    """{"data":{"attemptID":"att_1","url":"https://gitlab.com/oauth","instructions":"","mode":"auto",
                       "time":{"created":1,"expires":2}}}"""
                )
            else json(body)
        }

    @Test
    fun `W4-PROVFORMS-GATE every recorded field type reaches the form, none is dropped, unknown types are text`() = runTest {
        val gateway = server(Redactor()).gateway
        val rows = assertIs<Outcome.Ok<List<IntegrationInfo>>>(gateway.integrations()).value
        val prompts = rows.first { it.id == "shapes" }.methods.single().prompts
        fun type(key: String) = prompts.first { it["key"]!!.jsonPrimitive.content == key }["type"]!!.jsonPrimitive.content

        assertEquals(listOf("mode", "verbose", "port", "scopes", "docs", "future"), prompts.map { it["key"]!!.jsonPrimitive.content })
        assertEquals("select", type("mode"))
        assertEquals("select", type("scopes"))
        for (key in listOf("verbose", "port", "docs", "future")) assertEquals("text", type(key), key)
        // Primitive defaults keep their text; the condition list survives whole.
        val verbose = prompts.first { it["key"]!!.jsonPrimitive.content == "verbose" }
        assertEquals("true", verbose["default"]!!.jsonPrimitive.content)
        assertEquals("advanced", (verbose["when"] as JsonArray).single().jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("8080", prompts.first { it["key"]!!.jsonPrimitive.content == "port" }["default"]!!.jsonPrimitive.content)
        gateway.close()
    }

    @Test
    fun `W4-PROVFORMS-GATE a visible default left as is is not registered, a hidden preset and a typed value are`() = runTest {
        val redactor = Redactor()
        val gateway = server(redactor).gateway
        assertTrue(gateway.integrations() is Outcome.Ok)
        assertTrue(redactor.containsSecret(hiddenPreset), "a hidden preset is not registered")
        assertFalse(redactor.containsSecret("https://gitlab.com"), "a visible default is registered by the list read")

        assertTrue(
            gateway.startIntegrationOAuth("gitlab", "gitlab-oauth", persistentMapOf("instanceUrl" to "https://gitlab.com"), null)
                is Outcome.Ok
        )
        assertFalse(redactor.containsSecret("https://gitlab.com"), "the prefilled default sent back is registered")
        assertEquals("see https://gitlab.com/help", redactor.redact("see https://gitlab.com/help"))

        val typed = "https://git." + "formgate" + ".example.test"
        gateway.startIntegrationOAuth("gitlab", "gitlab-oauth", persistentMapOf("instanceUrl" to typed), null)
        assertTrue(redactor.containsSecret(typed), "a typed free-text answer is not registered")
        // The default is public for its own field only.
        gateway.startIntegrationOAuth("shapes", "all", persistentMapOf("future" to "https://gitlab.com"), null)
        assertTrue(redactor.containsSecret("https://gitlab.com"), "another field's default is treated as published")
        gateway.close()
    }
}
