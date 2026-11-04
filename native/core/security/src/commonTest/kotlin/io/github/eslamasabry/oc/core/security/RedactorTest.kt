package io.github.eslamasabry.oc.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Redactor vectors: the KitRedact port (Flutter test/redaction_test.dart G12 fakes), diagnostics sanitising
 * (B-SEC-008) and bounded log excerpts (B-SEC-079). Fakes are built from adjacent literals so the source holds no
 * whole key; failure messages name the kind, never the value.
 */
class RedactorTest {
    private class Fake(val kind: String, val line: String, val tail: String) {
        val fragments: List<String>
            get() {
                val w = 10
                val mid = (tail.length - w) / 2
                return listOf(tail.substring(0, w), tail.substring(mid, mid + w), tail.substring(tail.length - w))
            }
    }

    private val anthropicTail = "api03-" + "G12Qx7AnthSecret" + "0a1b2c3d4e"
    private val projectTail = "G12Wm4ProjSecret" + "5f6g7h8i9j"
    private val legacyTail = "G12Zr2LegacySecret" + "0k1l2m3n4o"
    private val routerTail = "or-v1-" + "G12Yt5RouterSecret" + "5p6q7r8s"
    private val googleTail = "SyG12Hv8GoogleSecret" + "9t0u1v2w3x4y5z6A"
    private val githubTail = "G12Nc3GithubSecret" + "7B8C9D0E1F2G"
    private val bearerTail = "G12Pk6BearerSecret" + "3H4I5J6K7L"
    private val inlineBearerTail = "G12Lf1InlineSecret" + "8M9N0O1P2Q"
    private val headerTail = "G12Jd9HeaderSecret" + "3R4S5T6U7V"
    private val envTail = "G12Bs0EnvSecret" + "8W9X0Y1Z2a"
    private val opaqueTail = "G12Opaque-horse" + "-battery-staple"

    private val fakes =
        listOf(
            Fake("Anthropic key", "key sk-ant-$anthropicTail", anthropicTail),
            Fake("OpenAI project key", "using sk-proj-$projectTail", projectTail),
            Fake("OpenAI key", "OPENAI sk-$legacyTail", legacyTail),
            Fake("OpenRouter key", "router sk-$routerTail", routerTail),
            Fake("Google key", "google AIza$googleTail", googleTail),
            Fake("GitHub token", "gh ghp_$githubTail", githubTail),
            Fake("Authorization header", "Authorization: Bearer $bearerTail", bearerTail),
            Fake("bearer token", "request failed: Bearer $inlineBearerTail", inlineBearerTail),
            Fake("x-api-key header", "x-api-key: $headerTail", headerTail),
            Fake("env key", "ANTHROPIC_API_KEY=$envTail", envTail),
            Fake("registered server password", "password was $opaqueTail", opaqueTail),
        )

    private fun leaks(text: String) = fakes.filter { f -> f.fragments.any { it in text } }.map { it.kind }

    private fun redactor() = Redactor().apply { registerSecret(opaqueTail) }

    @Test
    fun `G12 every fake key and token is masked, alone and all together`() {
        val r = redactor()
        for (fake in fakes) assertEquals(emptyList(), leaks(r.redact(fake.line)), fake.kind)
        val all = fakes.joinToString("\n") { it.line } + "\nG12 visible context"
        val out = r.redact(all)
        assertEquals(emptyList(), leaks(out))
        assertTrue("G12 visible context" in out)
        assertTrue("sk-ant-" in out, "the public prefix stays as a hint")
    }

    @Test
    fun `B-SEC-144 the redactor masks every fake credential in the fixtures and the gate is not vacuous`() {
        val r = redactor()
        for (fake in fakes) {
            assertTrue(r.containsSecret(fake.line), "${fake.kind}: the redactor must recognise this fake")
            assertEquals(emptyList(), leaks(r.redact(fake.line)), "${fake.kind}: redact leaves part of it")
        }
        // Negative control: the raw lines leak every kind, so the leak check itself can see each fake.
        assertEquals(fakes.size, leaks(fakes.joinToString("\n") { it.line }).size)
    }

    @Test
    fun `secrets shorter than six characters are not registered and forget removes one`() {
        val r = Redactor()
        r.registerSecret("abc12")
        assertEquals("abc12 here", r.redact("abc12 here"))
        r.registerSecret("hunter22")
        assertEquals("pw ${Redactor.MASK}", r.redact("pw hunter22"))
        r.forget("hunter22")
        assertEquals("pw hunter22", r.redact("pw hunter22"))
    }

    @Test
    fun `url user info, private keys, named values and JWTs are masked`() {
        val r = Redactor()
        assertEquals("http://${Redactor.MASK}@10.0.0.2:4096/api", r.redact("http://opencode:pw@10.0.0.2:4096/api"))
        val key = "-----BEGIN PRIVATE KEY-----\nMIIsecretbody\n-----END PRIVATE KEY-----"
        assertFalse("MIIsecretbody" in r.redact("k=$key"))
        assertEquals("""{"password": "${Redactor.MASK}"}""", r.redact("""{"password": "hunter2!"}"""))
        assertEquals("token=${Redactor.MASK}&x=1", r.redact("token=abc123&x=1"))
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"
        assertEquals("jwt ${Redactor.MASK}", r.redact("jwt $jwt"))
        // A path that merely contains a credential-like name is not a credential.
        assertEquals("open /etc/token=file.txt", r.redact("open /etc/token=file.txt"))
        // A declaration's invocation is code, not a secret.
        assertEquals("final token = readToken();", r.redact("final token = readToken();"))
    }

    @Test
    fun `B-SEC-008 diagnostics redact secrets, URLs and long token-like values`() {
        val bearer = "abcdefghijklmnopqrstuvwxyz0123456789TOKEN"
        val apiKey = "super-secret-api-key-value-123456789"
        val text =
            "Authorization: Bearer $bearer\n" +
                "password=hunter2 api_key=\"$apiKey\" " +
                "https://user:pass@example.com/private?q=chat&token=hidden#frag\n" +
                "at safe_function (lib/main.dart:10:2)"
        val report = Redactor().diagnostics(text)
        for (secret in listOf(bearer, apiKey, "hunter2", "user:pass", "q=chat", "hidden", "frag")) {
            assertFalse(secret in report, "leaked a ${secret.length}-char value")
        }
        assertTrue("example.com/private" in report)
        assertTrue("safe_function" in report)
    }

    @Test
    fun `B-SEC-008 diagnostics keep long code names but redact long tokens`() {
        val name = "ForegroundServiceDidNotStartInTimeException"
        val token = "q8Zx1kP0vR7mN2sL5tY9wE3uI6oA4dFgHjK"
        val out = Redactor().diagnostics("RemoteServiceException\$$name $token")
        assertTrue(name in out)
        assertFalse(token in out)
    }

    @Test
    fun `B-SEC-008 diagnostics are bounded`() {
        val out = Redactor().diagnostics("a".repeat(13_000))
        assertEquals(12_000 + "\n… [truncated]".length, out.length)
        assertTrue(out.endsWith("… [truncated]"))
    }

    @Test
    fun `B-SEC-079 log excerpts are redacted over the whole multiline input before truncation`() {
        val r = Redactor().apply { registerSecret("fake-loaded-credential") }
        val log =
            listOf(
                "password=\"" + List(140) { "synthetic secret" }.joinToString("\n") + "\"",
                "-----BEGIN PRIVATE KEY-----",
                "synthetic-private-key-body",
                "-----END PRIVATE KEY-----",
                "\u001b[31mBearer synthetic-bearer\u001b[0m",
                "fake-loaded-credential",
                "error at /work/project/main.dart",
            ).joinToString("\n")
        val excerpt = r.excerpt(log)
        assertFalse("synthetic" in excerpt)
        assertFalse("fake-loaded-credential" in excerpt)
        assertTrue(Redactor.MASK in excerpt)
        assertTrue("/work/project/main.dart" in excerpt)
        assertFalse("\u001b" in excerpt)
    }

    @Test
    fun `B-SEC-079 an excerpt keeps a bounded tail, including a single huge line`() {
        val r = Redactor()
        val excerpt = r.excerpt((0 until 200).joinToString("\n") { "line $it" })
        assertEquals(120, excerpt.split('\n').size)
        assertTrue(excerpt.startsWith("line 80\n"))
        assertTrue(excerpt.endsWith("line 199"))
        val large = r.excerpt("x".repeat(20_000) + "END")
        assertEquals(Redactor.EXCERPT_MAX_CHARACTERS, large.length)
        assertTrue(large.endsWith("END"))
    }

    @Test
    fun `details are redacted and capped, and an error is shape plus redacted message`() {
        val r = Redactor().apply { registerSecret("hunter22secret") }
        assertEquals(12_000, r.details("y".repeat(13_000)).text.length)
        val d = r.details(IllegalStateException("login with hunter22secret failed"), 401)
        assertEquals("IllegalStateException · HTTP 401 · login with ${Redactor.MASK} failed", d.text)
    }

    @Test
    fun `credential values in config JSON are registered by field name`() {
        val r = Redactor()
        r.registerCredentialValues(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"provider":{"anthropic":{"options":{"apiKey":"cfg-secret-value-1","baseURL":"https://x"}}},
                   "headers":[{"Authorization":"Bearer hdr-secret-value-2"}]}""",
            ),
        )
        val out = r.redact("saw cfg-secret-value-1 and hdr-secret-value-2 at https://x")
        assertFalse("cfg-secret-value-1" in out)
        assertFalse("hdr-secret-value-2" in out)
        assertTrue("https://x" in out)
    }
}
