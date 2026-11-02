package io.github.eslamasabry.oc.feature.servers.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from Flutter `test/pairing_payload_test.dart` and `test/session_address_link_test.dart`. */
class PairingTextTest {
    private fun code(raw: String): PairingPayload = assertIs<PairingRead.Code>(PairingText.parse(raw)).payload

    private fun problem(raw: String): PairingProblem = assertIs<PairingRead.Problem>(PairingText.parse(raw)).problem

    @Test
    fun `B-PROTO-064 a real payload parses into addresses password and username in server order`() {
        val p = code("""{"urls":["https://box.tail.ts.net:4096","http://127.0.0.1:4096"],"username":"opencode","password":"s3cret"}""")
        assertEquals(listOf("https://box.tail.ts.net:4096", "http://127.0.0.1:4096"), p.urls)
        assertEquals("opencode", p.username)
        assertEquals("s3cret", p.password())
    }

    @Test
    fun `B-PROTO-064 addresses are trimmed and de-duplicated and clipboard whitespace is tolerated`() {
        val p = code("\n  {\"urls\":[\" https://a.example \",\"https://a.example\",\"\",\"https://b.example\"],\"password\":\"x\"}\t\n")
        assertEquals(listOf("https://a.example", "https://b.example"), p.urls)
    }

    @Test
    fun `B-PROTO-064 a missing or blank username is the default user, a custom one is kept`() {
        assertEquals("opencode", code("""{"urls":["https://a.example"],"password":"x"}""").username)
        assertEquals("opencode", code("""{"urls":["https://a.example"],"username":"  ","password":"x"}""").username)
        assertEquals("opencode", code("""{"urls":["https://a.example"],"username":null,"password":"x"}""").username)
        assertEquals("eslam", code("""{"urls":["https://a.example"],"username":" eslam ","password":"x"}""").username)
    }

    @Test
    fun `B-PROTO-065 every malformed payload is an honest specific failure and never a crash`() {
        assertEquals(PairingProblem.Empty, problem("   "))
        assertEquals(PairingProblem.NotJson, problem("{urls: nope"))
        assertEquals(PairingProblem.NotJson, problem("https://box.example:4096"))
        assertEquals(PairingProblem.Shape, problem("""["https://a.example"]"""))
        assertEquals(PairingProblem.Shape, problem("42"))
        assertEquals(PairingProblem.NoUrls, problem("""{"password":"x"}"""))
        assertEquals(PairingProblem.UrlsType, problem("""{"urls":"https://a.example","password":"x"}"""))
        assertEquals(PairingProblem.AddressMissing, problem("""{"urls":[],"password":"x"}"""))
        assertEquals(PairingProblem.AddressMissing, problem("""{"urls":["  ",""],"password":"x"}"""))
        val many = (1..33).joinToString(",") { "\"https://h$it.example\"" }
        assertEquals(PairingProblem.TooMany, problem("""{"urls":[$many],"password":"x"}"""))
        assertEquals(PairingProblem.AddressType, problem("""{"urls":[42],"password":"x"}"""))
        assertEquals(PairingProblem.AddressType, problem("""{"urls":[["https://a.example"]],"password":"x"}"""))
        assertEquals(PairingProblem.AddressLong, problem("""{"urls":["https://${"a".repeat(2100)}.example"],"password":"x"}"""))
        assertEquals(PairingProblem.UsernameType, problem("""{"urls":["https://a.example"],"username":7,"password":"x"}"""))
        assertEquals(PairingProblem.PasswordMissing, problem("""{"urls":["https://a.example"]}"""))
        assertEquals(PairingProblem.PasswordType, problem("""{"urls":["https://a.example"],"password":123}"""))
        assertEquals(PairingProblem.PasswordType, problem("""{"urls":["https://a.example"],"password":null}"""))
        assertEquals(PairingProblem.TooLong, problem("{\"urls\":[\"" + "a".repeat(9000) + "\"]}"))
        val deep = "[".repeat(4000) + "]".repeat(4000)
        assertEquals(PairingProblem.AddressType, problem("""{"urls":[$deep],"password":"x"}"""))
    }

    @Test
    fun `B-PROTO-065 no failure carries the input`() {
        // Problems are a closed enum: there is no field that could echo what was pasted.
        val read = PairingText.parse("""{"urls":["https://a.example"],"password":7,"secret":"hunter2"}""")
        assertFalse("hunter2" in read.toString())
    }

    @Test
    fun `B-PROTO-066 pairing-shaped text is recognised, broken ones too, ordinary text and huge input are not`() {
        assertTrue(PairingText.looksLikePayload("""  {"urls":["https://a"],"password":"x"}  """))
        assertTrue(PairingText.looksLikePayload("""{"urls": 7}"""))
        assertTrue(PairingText.looksLikePayload("{'urls': []}"))
        assertFalse(PairingText.looksLikePayload("https://box.example:4096"))
        assertFalse(PairingText.looksLikePayload("{\"name\":\"x\"}"))
        assertFalse(PairingText.looksLikePayload("{\"urls\":[\"" + "a".repeat(9000) + "\"]}"))
    }

    @Test
    fun `B-PROTO-067 a phone prefers routable addresses, a desktop loopback, order kept, unreadable counts as routable`() {
        val urls = listOf("http://127.0.0.1:4096", "https://a.example", "not a url", "http://localhost:4096", "https://b.example")
        assertEquals(
            listOf("https://a.example", "not a url", "https://b.example", "http://127.0.0.1:4096", "http://localhost:4096"),
            PairingText.orderCandidates(urls, preferLoopback = false),
        )
        assertEquals(
            listOf("http://127.0.0.1:4096", "http://localhost:4096", "https://a.example", "not a url", "https://b.example"),
            PairingText.orderCandidates(urls, preferLoopback = true),
        )
    }

    @Test
    fun `B-PROTO-067 the form gets the address a phone should try first`() {
        val read = PairingText.parse("""{"urls":["http://127.0.0.1:4096","https://box.tail.ts.net:4096"],"password":"pw"}""")
        val fill = PairingFill.of(read)!!
        assertEquals("https://box.tail.ts.net:4096", fill.address)
        assertEquals(2, fill.addresses)
        assertEquals("pw", fill.takePassword())
        assertNull(fill.takePassword(), "the password is handed over once")
    }

    @Test
    fun `B-SEC-107 the payload prints no password, no untrusted username and no URL secrets`() {
        val p = code("""{"urls":["https://user:pw@box.example:4096/path?token=abc"],"username":"mallory<script>","password":"hunter2"}""")
        val printed = p.toString()
        assertFalse("hunter2" in printed)
        assertFalse("mallory" in printed)
        assertFalse("token" in printed)
        assertFalse("user:pw" in printed)
        assertTrue("https://box.example:4096" in printed)
        assertTrue("<redacted>" in printed)
    }

    @Test
    fun `B-SEC-107 consuming drops the password and the address list cannot be changed`() {
        val p = code("""{"urls":["https://a.example"],"password":"hunter2"}""")
        p.consume()
        assertTrue(p.isConsumed)
        assertNull(p.password())
        assertTrue("<consumed>" in p.toString())
        assertFails { (p.urls as MutableList<String>).add("https://evil.example") }
        assertEquals(listOf("https://a.example"), p.urls)
    }

    @Test
    fun `B-SEC-107 the form fill consumes the payload and prints no secret`() {
        val read = PairingText.parse("""{"urls":["https://a.example/x?k=v"],"password":"hunter2"}""")
        val payload = (read as PairingRead.Code).payload
        val fill = PairingFill.of(read)!!
        assertTrue(payload.isConsumed)
        assertFalse("hunter2" in fill.toString())
        assertFalse("k=v" in fill.toString())
    }

    private val instance = "0f8fad5b-d9cb-469f-a165-70867728950e"

    @Test
    fun `B-SEC-157 a strict share link yields its private https origin and no credential`() {
        val link = "opencode-mobile://session/v2?server=https%3A%2F%2Fbox.tail1234.ts.net%3A8443&instance=$instance&session=ses_abc123"
        assertEquals("https://box.tail1234.ts.net:8443", ShareLink.origin(link))
        val default = "opencode-mobile://session/v2?server=https://box.tail1234.ts.net&instance=$instance&session=ses_1"
        assertEquals("https://box.tail1234.ts.net", ShareLink.origin(default))
        assertEquals(PairingRead.Link("https://box.tail1234.ts.net"), PairingText.read(default))
        val fill = PairingFill.of(PairingRead.Link("https://box.tail1234.ts.net"))!!
        assertTrue(fill.fromLink)
        assertNull(fill.takePassword())
    }

    @Test
    fun `B-SEC-157 malformed, credential-bearing, public or doubly-encoded links are refused, not repaired`() {
        val ok = "server=https://box.tail1234.ts.net&instance=$instance&session=ses_1"
        listOf(
            "opencode-mobile://session/v1?$ok",
            "opencode-mobile://session/v2?$ok#frag",
            "opencode-mobile://session/v2?$ok&extra=1",
            "opencode-mobile://session/v2?server=https://box.tail1234.ts.net&session=ses_1",
            "opencode-mobile://session/v2?server=https://box.tail1234.ts.net&server=https://x.y.ts.net&instance=$instance&session=ses_1",
            "opencode-mobile://session/v2?server=https://user:pw@box.tail1234.ts.net&instance=$instance&session=ses_1",
            "opencode-mobile://session/v2?server=https%3A%2F%2Fuser%40box.tail1234.ts.net&instance=$instance&session=ses_1",
            "opencode-mobile://session/v2?server=http://box.tail1234.ts.net&instance=$instance&session=ses_1",
            "opencode-mobile://session/v2?server=https://box.example.com&instance=$instance&session=ses_1",
            "opencode-mobile://session/v2?server=https%253A%252F%252Fbox.tail1234.ts.net&instance=$instance&session=ses_1",
            "opencode-mobile://session/v2?server=https://box.tail1234.ts.net&instance=not-a-uuid&session=ses_1",
            "opencode-mobile://session/v2?server=https://box.tail1234.ts.net&instance=$instance&session=../x",
            "opencode-mobile://session/v2?server=https://box.tail1234.ts.net&instance=$instance&session=a%zz",
            "opencode-mobile://session/v2?server=https://box.tail1234.ts.net:99999&instance=$instance&session=ses_1",
            "opencode-mobile://session/v2?" + "a".repeat(1100),
            "opencode-mobile://session/v2?server=https://bóx.tail1234.ts.net&instance=$instance&session=ses_1",
        ).forEach { assertNull(ShareLink.origin(it), it) }
    }
}
