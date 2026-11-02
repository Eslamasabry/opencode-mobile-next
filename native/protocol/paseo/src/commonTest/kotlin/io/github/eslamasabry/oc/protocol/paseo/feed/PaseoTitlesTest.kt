package io.github.eslamasabry.oc.protocol.paseo.feed

import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The gateway-side title rules of test/paseo_title_agree_test.dart, as the pure helpers the gateway calls. */
class PaseoTitlesTest {
    private fun agent(title: String?) =
        jsonOf(
            "id" to "new",
            "provider" to "kilo",
            "title" to title,
            "persistence" to mapOf("provider" to "kilo", "sessionId" to "ses_1"),
        )

    @Test
    fun `B-AGENT-181 a lone record without a title takes the title saved for its conversation`() {
        val saved = mapOf("kilo:ses_1" to "Say pong")
        val titled = PaseoTitles.withTitle(agent(null), known = null, savedTitle = saved::get)
        assertEquals("Say pong", titled.str("title"))
        assertTrue(PaseoTitles.hasOwnTitle(titled))
        assertEquals("kilo:ses_1", PaseoTitles.sessionKey(titled))
    }

    @Test
    fun `B-AGENT-181 without a saved title the stand-in stays`() {
        val bare = agent(null)
        assertSame(bare, PaseoTitles.withTitle(bare, known = null, savedTitle = { null }))
        assertSame(bare, PaseoTitles.withTitle(bare, known = "  ", savedTitle = null))
        assertFalse(PaseoTitles.hasOwnTitle(bare))
        assertFalse(PaseoTitles.hasOwnTitle(agent("   ")))
    }

    @Test
    fun `B-AGENT-180 a reloaded record without its title keeps the one already known, and an own title wins`() {
        assertEquals("Known", PaseoTitles.withTitle(agent(""), known = "Known", savedTitle = mapOf("kilo:ses_1" to "Saved")::get).str("title"))
        val own = agent("Own")
        assertSame(own, PaseoTitles.withTitle(own, known = "Known", savedTitle = { "Saved" }))
    }

    @Test
    fun `B-AGENT-180 a record without a runtime session handle has no conversation key`() {
        assertNull(PaseoTitles.sessionKey(jsonOf("id" to "x")))
        assertNull(PaseoTitles.sessionKey(jsonOf("persistence" to mapOf("provider" to "kilo", "sessionId" to ""))))
        assertNull(PaseoTitles.sessionKey(null))
    }
}
