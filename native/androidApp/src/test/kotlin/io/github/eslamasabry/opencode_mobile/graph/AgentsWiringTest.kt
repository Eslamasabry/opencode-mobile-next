package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.protocol.paseo.PaseoEndpoint
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class AgentsWiringTest {
    @Test
    fun `B-SEC-113 the socket refuses an endpoint OkHttp would read as another host before sending the password`() {
        // Built directly (the parser refuses it): a host OkHttp ends at `\`, so it would connect to attacker.example.
        val forged = PaseoEndpoint("wss", "attacker.example\\.tail1234.ts.net", null)
        val failure =
            assertFailsWith<PaseoException> { runBlocking { OkHttpPaseoSockets().open(forged, "s3cret") } }
        assertEquals(PaseoFailureKind.InvalidEndpoint, failure.kind)
    }
}
