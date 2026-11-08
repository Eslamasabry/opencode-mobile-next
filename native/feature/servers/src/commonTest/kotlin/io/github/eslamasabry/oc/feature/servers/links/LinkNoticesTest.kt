package io.github.eslamasabry.oc.feature.servers.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** s28-app stories 5 and 6: what a link that cannot complete says (F: link_routes.dart, session_address_link.dart). */
class LinkNoticesTest {
    private val noSecret: (String) -> Boolean = { false }
    private val good =
        "opencode-mobile://session/v2?server=https%3A%2F%2Flaptop.tail1234.ts.net" +
            "&instance=123e4567-e89b-12d3-a456-426614174000&session=ses_abc123"

    @Test
    fun `B-ENTRY-140 a session link to a server not saved here asks to add it`() {
        assertEquals(LinkNotice.ServerMissing, SessionLinkNotices.before(saved = false, needsSecret = false))
    }

    @Test
    fun `B-ENTRY-141 a saved server that needs its password again says so`() {
        assertEquals(LinkNotice.Reentry, SessionLinkNotices.before(saved = true, needsSecret = true))
        assertNull(SessionLinkNotices.before(saved = true, needsSecret = false))
    }

    @Test
    fun `B-ENTRY-142 a well-formed address link says these links are not available yet, as released Flutter`() {
        assertTrue(SessionAddressLinks.isAddressLink(good))
        assertNull(SessionAddressLinks.validate(good, noSecret))
        assertEquals(SessionAddressFailure.Unavailable, SessionAddressLinks.check(good, noSecret))
    }

    @Test
    fun `B-ENTRY-143 a malformed, oversized or credential-bearing address link says why`() {
        assertEquals(SessionAddressFailure.InvalidLink, SessionAddressLinks.check("opencode-mobile://session/v2?server=x", noSecret))
        assertEquals(
            SessionAddressFailure.InvalidLink,
            SessionAddressLinks.check(good.replace("ts.net", "example.com"), noSecret),
        )
        assertEquals(SessionAddressFailure.TooLarge, SessionAddressLinks.check(good + "a".repeat(1100), noSecret))
        assertEquals(SessionAddressFailure.Credentials, SessionAddressLinks.check(good) { "ses_abc123" in it })
        assertEquals(
            SessionAddressFailure.Credentials,
            SessionAddressLinks.check(good.replace("https%3A%2F%2F", "https%3A%2F%2Fme%40"), noSecret),
        )
    }

    @Test
    fun `B-ENTRY-144 a plain session link is not an address link`() {
        assertFalse(SessionAddressLinks.isAddressLink("opencode-mobile://session?profile=p1&session=s1"))
    }

    @Test
    fun `B-ENTRY-145 the slot keeps the newest notice until dismissed`() {
        val slot = LinkNotices()
        slot.show(LinkNotice.Reentry)
        slot.show(LinkNotice.ConnectionFailed)
        assertEquals(LinkNotice.ConnectionFailed, slot.current.value)
        slot.dismiss()
        assertNull(slot.current.value)
    }
}
