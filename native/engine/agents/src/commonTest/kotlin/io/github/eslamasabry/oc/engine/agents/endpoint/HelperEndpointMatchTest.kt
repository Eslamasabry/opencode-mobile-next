package io.github.eslamasabry.oc.engine.agents.endpoint

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of test/local_agent_profile_match_test.dart (B-AGENT-119), on the helper's port. */
class HelperEndpointMatchTest {
    private val match = HelperEndpointMatch(port = 4099)

    private fun paseo(id: String, url: String) = EndpointCandidate(ProfileId(id), isPaseo = true, baseUrl = url)

    @Test
    fun `B-AGENT-119 every loopback name of the helper endpoint is the same server`() {
        for (url in listOf("ws://127.0.0.1:4099", "ws://localhost:4099", "ws://[::1]:4099", " ws://LOCALHOST:4099/ ")) {
            assertTrue(match.matches(paseo("a", url)), url)
        }
    }

    @Test
    fun `B-AGENT-119 another port, scheme, host or backend is a different server`() {
        assertFalse(match.matches(paseo("a", "ws://localhost:4100")))
        assertFalse(match.matches(paseo("a", "wss://localhost:4099")))
        assertFalse(match.matches(paseo("a", "ws://203.0.113.2:4099")))
        assertFalse(match.matches(paseo("a", "ws://localhost")), "no port is port 80")
        assertFalse(match.matches(paseo("a", "ws://user@localhost:4099")))
        assertFalse(match.matches(EndpointCandidate(ProfileId("a"), isPaseo = false, baseUrl = "ws://localhost:4099")))
    }

    @Test
    fun `B-AGENT-119 the name never decides, the endpoint does`() {
        // EndpointCandidate has no name at all: a server called "Claude Code on this phone" elsewhere is not it.
        assertFalse(match.matches(paseo("claude-code-on-this-phone", "ws://203.0.113.2:4099")))
    }

    @Test
    fun `B-AGENT-119 with two saved for the endpoint, the connected one is the row`() {
        val profiles = listOf(paseo("stale", "ws://127.0.0.1:4099"), paseo("live", "ws://localhost:4099"))
        assertEquals(ProfileId("stale"), match.saved(profiles)?.id)
        assertEquals(ProfileId("live"), match.saved(profiles, ProfileId("live"))?.id)
        assertEquals(ProfileId("stale"), match.saved(profiles, ProfileId("other"))?.id)
    }

    @Test
    fun `B-AGENT-119 only one profile per endpoint folds into the phone row`() {
        val first = paseo("first", "ws://127.0.0.1:4099")
        val second = paseo("second", "ws://localhost:4099")
        val all = listOf(first, second)
        assertTrue(match.foldsIntoPhoneRow(first, all))
        assertFalse(match.foldsIntoPhoneRow(second, all))
        assertTrue(match.foldsIntoPhoneRow(second, all, ProfileId("second")))
        assertFalse(match.foldsIntoPhoneRow(first, all, ProfileId("second")))
    }
}
