package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.ServerConfigRedaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Flutter `test/coverage/settings_secrets_coverage_test.dart`. */
class ServerConfigRedactionTest {
    private val markers =
        listOf(
            "MARKER_PROVIDER_KEY", "MARKER_ENV_VALUE", "MARKER_HEADER_VALUE", "MARKER_COMMAND_BODY", "MARKER_AGENT_PROMPT",
            "MARKER_TOKEN", "MARKER_NESTED_LIST",
        )

    private val config =
        """
        {
          "model": "anthropic/claude",
          "provider": {
            "anthropic": {
              "options": {"apiKey": "MARKER_PROVIDER_KEY", "baseURL": "https://x"},
              "models": {"claude": {"name": "Claude"}}
            }
          },
          "mcp": {
            "docs": {
              "type": "local",
              "command": ["node", "MARKER_COMMAND_BODY"],
              "environment": {"API": "MARKER_ENV_VALUE"},
              "headers": {"Authorization": "Bearer MARKER_HEADER_VALUE"}
            }
          },
          "agent": {"review": {"prompt": "MARKER_AGENT_PROMPT", "description": "Reviews"}},
          "share": {"accessToken": "MARKER_TOKEN"},
          "plugins": [{"secretList": ["MARKER_NESTED_LIST"]}]
        }
        """.trimIndent()

    @Test
    fun `B-SEC-040 a server configuration shows none of its secrets`() {
        val shown = ServerConfigRedaction().redact(Json.parseToJsonElement(config)).toString()
        for (marker in markers) assertFalse(marker in shown, marker)
        // What is safe to read stays readable.
        assertTrue("anthropic/claude" in shown)
        assertTrue("Reviews" in shown)
        assertTrue(ServerConfigRedaction.MASK in shown)
    }

    @Test
    fun `B-SEC-040 a changed secret is masked by the name it sits under`() {
        val redaction = ServerConfigRedaction()
        for (name in listOf("apiKey", "accessToken", "Authorization-secret")) {
            assertFalse("MARKER" in redaction.redactChanged("MARKER_PROVIDER_KEY", name), name)
            assertFalse("MARKER" in redaction.redact(JsonPrimitive("MARKER_PROVIDER_KEY"), name).toString(), name)
        }
        // A harmless name keeps a harmless value.
        assertTrue(redaction.redactChanged("Reviews", "description") == "Reviews")
    }
}
