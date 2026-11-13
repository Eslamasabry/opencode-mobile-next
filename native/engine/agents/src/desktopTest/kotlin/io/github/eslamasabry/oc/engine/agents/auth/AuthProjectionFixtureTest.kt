package io.github.eslamasabry.oc.engine.agents.auth

import io.github.eslamasabry.oc.engine.agents.host.AuthState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The device captures in resources/agent_auth (B-AGENT-004; copied from the Flutter app's test/fixtures/agent_auth):
 * installed agents have final explicit states.
 */
class AuthProjectionFixtureTest {
    private fun capture(name: String): String =
        checkNotNull(javaClass.getResource("/agent_auth/$name-device.json")) { "missing capture $name" }.readText()

    @Test
    fun `B-AGENT-004 captured installed agents have final explicit account states`() {
        for ((name, state) in mapOf("claude-signedIn" to AuthState.SignedIn, "fx-signedOut" to AuthState.SignedOut)) {
            val fixture = Json.parseToJsonElement(capture(name)).jsonObject
            val result = AuthProjectionJson.decode(fixture.getValue("probe").jsonObject)
            assertEquals(state, result.state, name)
            assertTrue(fixture.getValue("capture").jsonObject.getValue("elapsedMs").jsonPrimitive.long < 10_000)
        }
    }
}
