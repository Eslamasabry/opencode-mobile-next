package io.github.eslamasabry.oc.protocol.paseo.mappers

import kotlin.test.Test
import kotlin.test.assertTrue

/** B-AGENT-189: the helper's own failure line for an expired Claude Code sign-in is recognised as a sign-in. */
class ExpiredSignInPaseoTest {
    @Test
    fun `B-AGENT-189 the refresh token notice is the agent's sign-in failing`() {
        assertTrue(
            paseoSignInFailure("Unable to validate model: OAuth refresh token is no longer valid; run /login to re-authenticate")
        )
    }
}
