package io.github.eslamasabry.oc.engine.agents.auth

import io.github.eslamasabry.oc.engine.agents.host.AuthState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The device captures of test/fixtures/agent_auth (B-AGENT-004): installed agents have final explicit states. */
class AuthProjectionFixtureTest {
    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "test/fixtures/agent_auth").isDirectory }

    @Test
    fun `B-AGENT-004 captured installed agents have final explicit account states`() {
        for ((name, state) in mapOf("claude-signedIn" to AuthState.SignedIn, "fx-signedOut" to AuthState.SignedOut)) {
            val fixture = Json.parseToJsonElement(File(repo, "test/fixtures/agent_auth/$name-device.json").readText()).jsonObject
            val result = AuthProjectionJson.decode(fixture.getValue("probe").jsonObject)
            assertEquals(state, result.state, name)
            assertTrue(fixture.getValue("capture").jsonObject.getValue("elapsedMs").jsonPrimitive.long < 10_000)
        }
    }
}
