package io.github.eslamasabry.oc.engine.agents.auth

import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Port of the projection cases of test/agent_auth_probe_test.dart (B-AGENT-004). */
class AuthProjectionJsonTest {
    private fun decode(text: String) = AuthProjectionJson.decode(Json.parseToJsonElement(text).jsonObject)

    @Test
    fun `B-AGENT-004 malformed and contradictory account replies remain errors`() {
        for (
            data in listOf(
                "{}",
                """{"state":"loading"}""",
                """{"state":"signedOut","accountDisplayName":"account"}""",
                """{"state":"signedIn","accountDisplayName":"bad\naccount"}""",
                """{"state":"signedIn","error":"synthetic-secret"}""",
                """{"state":"error","error":"synthetic-secret"}""",
                """{"state":"signedIn","accountDisplayName":"   "}""",
                """{"state":"signedIn","accountDisplayName":42}""",
                """{"state":true}""",
                """{"state":"signedIn","accountDisplayName":"${"a".repeat(161)}"}""",
            )
        ) {
            val result = decode(data)
            assertEquals(AuthState.Error, result.state, data)
            assertEquals(AuthProbeError.InvalidResponse, result.error, data)
            assertNull(result.account, data)
        }
    }

    @Test
    fun `B-AGENT-004 explicit states and closed error reasons pass, the label only for signed in`() {
        assertEquals(AuthState.SignedOut, decode("""{"state":"signedOut"}""").state)
        val signedIn = decode("""{"state":"signedIn","accountDisplayName":"Example account"}""")
        assertEquals(AuthState.SignedIn, signedIn.state)
        assertEquals("Example account", signedIn.account?.value)
        assertEquals("•••", signedIn.account.toString())
        assertEquals(AuthProbeError.TimedOut, decode("""{"state":"error","error":"timedOut"}""").error)
        assertEquals(AuthProbeError.NotInstalled, decode("""{"state":"error","error":"notInstalled"}""").error)
        assertEquals(AuthState.SignedIn, decode("""{"state":"signedIn","error":null,"accountDisplayName":null}""").state)
    }
}
