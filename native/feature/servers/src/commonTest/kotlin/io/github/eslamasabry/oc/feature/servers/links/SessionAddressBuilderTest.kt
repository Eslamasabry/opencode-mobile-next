package io.github.eslamasabry.oc.feature.servers.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** K1: the link the sender builds is the link the receiver reads (F: session_address_link.dart build/encode/parse). */
class SessionAddressBuilderTest {
    private val noSecret: (String) -> Boolean = { false }
    private val instance = "123E4567-E89B-12D3-A456-426614174000"

    private fun built(address: String = "https://Laptop.Tail1234.ts.net", session: String = "ses_abc-123") =
        SessionAddressBuilder.build(address, instance, session, includeServerAddress = true, containsSecret = noSecret)

    @Test
    fun `a built link is read by the receiving side`() {
        val link = (built() as SessionAddressBuilder.Built.Link).link
        assertEquals(
            "opencode-mobile://session/v2?server=https%3A%2F%2Flaptop.tail1234.ts.net" +
                "&instance=123e4567-e89b-12d3-a456-426614174000&session=ses_abc-123",
            link,
        )
        assertTrue(SessionAddressLinks.isAddressLink(link))
        assertNull(SessionAddressLinks.validate(link, noSecret))
    }

    @Test
    fun `a non-default port stays and the default port goes`() {
        val withPort = (built("https://a.b.ts.net:8443") as SessionAddressBuilder.Built.Link).link
        assertTrue("https%3A%2F%2Fa.b.ts.net%3A8443" in withPort)
        assertNull(SessionAddressLinks.validate(withPort, noSecret))
        val plain = (built("https://a.b.ts.net:443") as SessionAddressBuilder.Built.Link).link
        assertTrue("server=https%3A%2F%2Fa.b.ts.net&" in plain)
    }

    @Test
    fun `the switch off builds nothing`() {
        val off = SessionAddressBuilder.build("https://a.b.ts.net", instance, "s1", false, noSecret)
        assertTrue(off is SessionAddressBuilder.Built.Refused)
    }

    @Test
    fun `an address that is not a private ts net name is refused`() {
        for (bad in listOf("http://a.b.ts.net", "https://example.com", "https://a.b.c.ts.net", "https://192.168.1.2")) {
            assertEquals(SessionAddressFailure.InvalidLink, (built(bad) as SessionAddressBuilder.Built.Refused).failure, bad)
            assertNull(SessionAddressBuilder.hostPort(bad, noSecret), bad)
        }
    }

    @Test
    fun `an address with a sign-in in it is refused as credentials`() {
        val r = built("https://user:pw@a.b.ts.net") as SessionAddressBuilder.Built.Refused
        assertEquals(SessionAddressFailure.Credentials, r.failure)
    }

    @Test
    fun `a value the redactor calls secret is refused and never put in a link`() {
        val r =
            SessionAddressBuilder.build("https://a.b.ts.net", instance, "ses_secretvalue", true) { "secretvalue" in it }
        assertEquals(SessionAddressFailure.Credentials, (r as SessionAddressBuilder.Built.Refused).failure)
    }

    @Test
    fun `a bad instance or conversation id is refused`() {
        val r = SessionAddressBuilder.build("https://a.b.ts.net", "not-a-uuid", "s1", true, noSecret)
        assertEquals(SessionAddressFailure.InvalidLink, (r as SessionAddressBuilder.Built.Refused).failure)
        val s = SessionAddressBuilder.build("https://a.b.ts.net", instance, "has space", true, noSecret)
        assertTrue(s is SessionAddressBuilder.Built.Refused)
    }

    @Test
    fun `host and port for the switch row`() {
        assertEquals("laptop.t.ts.net:443", SessionAddressBuilder.hostPort("https://Laptop.T.ts.net", noSecret))
        assertEquals("laptop.t.ts.net:8443", SessionAddressBuilder.hostPort("https://laptop.t.ts.net:8443", noSecret))
    }
}
