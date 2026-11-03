package io.github.eslamasabry.oc.engine.integrations.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SVG =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"currentColor\" d=\"M2 2h20v20H2z\"/></svg>"

private class MemoryFiles : ProviderLogoFiles {
    val files = HashMap<String, ProviderLogoFile>()
    var clock: () -> Long = { 0 }

    override suspend fun read(name: String): ProviderLogoFile? = files[name]

    override suspend fun write(name: String, text: String) {
        files[name] = ProviderLogoFile(text, clock())
    }

    override suspend fun delete(name: String) {
        files.remove(name)
    }
}

private fun ok(body: String, type: String = "image/svg+xml") = ProviderLogoResponse(200, type, body.encodeToByteArray())

class ProvidersTest {
    // ---- B-PROTO-081 (test/product_repository_projects_test.dart:5, :29, :76)

    @Test
    fun `B-PROTO-081 configured providers retain OpenCode model variant metadata`() {
        val response =
            parseProvidersResponse(
                json(
                    """{"providers":[{"id":"provider","name":"Provider","models":{"model":{"name":"Model","variants":{"fast":{"reasoningEffort":"low"}}}}}],
                    "default":{"provider":"model"}}""",
                ) as JsonObject,
            )
        val variants = response.providers.single().modelData["model"]?.get("variants") as JsonObject
        assertTrue("fast" in variants)
        assertEquals("provider", response.defaultProviderId)
        assertEquals("model", response.defaultModelId)
    }

    @Test
    fun `B-PROTO-081 provider list keeps only connected providers in connection order`() {
        val response =
            parseProvidersResponse(
                json(
                    """{"all":[{"id":"opencode","name":"OpenCode Zen","models":{"big-pickle":{}}},
                    {"id":"zai-coding-plan","name":"Z.AI Coding Plan","models":{"glm-5.2":{"variants":{"max":{"reasoningEffort":"max"}}}}},
                    {"id":"unconnected","name":"Unconnected","models":{"hidden-model":{}}}],
                    "connected":["zai-coding-plan","opencode"],
                    "default":{"unconnected":"hidden-model","opencode":"big-pickle","zai-coding-plan":"glm-5.2"}}""",
                ) as JsonObject,
            )
        assertEquals(listOf("zai-coding-plan", "opencode"), response.providers.map { it.id })
        assertEquals(listOf("glm-5.2"), response.providers.first().modelIds)
        assertEquals(listOf("opencode", "zai-coding-plan", "unconnected"), response.availableProviders.map { it.id })
        assertEquals("zai-coding-plan", response.defaultProviderId)
        assertEquals("glm-5.2", response.defaultModelId)
    }

    @Test
    fun `B-PROTO-081 explicit empty connected list does not expose catalog-only models`() {
        val response =
            parseProvidersResponse(
                json("""{"all":[{"id":"openai","name":"OpenAI","models":{"gpt-5.6-sol":{}}}],"connected":[],"default":{"openai":"gpt-5.6-sol"}}""")
                    as JsonObject,
            )
        assertTrue(response.providers.isEmpty())
        assertEquals("openai", response.availableProviders.single().id)
        assertNull(response.defaultProviderId)
        assertNull(response.defaultModelId)
    }

    // ---- B-SEC-095 (test/library_integrations_test.dart:183, :250, :272)

    @Test
    fun `B-SEC-095 key page links are official https pages for key-only providers`() {
        assertTrue(providerKeyPageUrl("anthropic")!!.startsWith("https://"))
        assertTrue(providerKeyPageUrl("google")!!.startsWith("https://"))
        assertTrue(providerKeyPageUrl("Anthropic")!!.startsWith("https://"))
        assertNull(providerKeyPageUrl("groq"))
    }

    @Test
    fun `B-SEC-095 authorization URL policy accepts only credential-free HTTPS hosts`() {
        assertEquals("https://login.example.com/oauth?state=abc", parseAuthorizationUrl("https://login.example.com/oauth?state=abc"))
        for (value in listOf(
            "http://login.example.com/oauth",
            "javascript:alert(1)",
            "opencode://oauth/callback",
            "https:///oauth",
            "https://user:password@login.example.com/oauth",
            "",
        )) {
            assertNull(parseAuthorizationUrl(value), value)
        }
    }

    @Test
    fun `B-SEC-095 provider OAuth completion accepts a code or callback URL`() {
        assertEquals("returned-code", providerOAuthCompletionCode(" returned-code "))
        assertEquals("returned-code", providerOAuthCompletionCode("https://auth.example.com/callback?code=returned-code&state=state-1"))
    }

    // ---- B-SEC-137 (test/provider_logo_store_test.dart:129, :150)

    @Test
    fun `B-SEC-137 rejects oversize, wrong type, non-200 and active content`() =
        runTest {
            val big = "x".repeat(PROVIDER_LOGO_MAX_BYTES + 1)
            val cases =
                listOf(
                    ok("$SVG<!--$big-->"),
                    ok(SVG, type = "text/html"),
                    ok("<html></html>"),
                    ProviderLogoResponse(404, "image/svg+xml", ByteArray(0)),
                    ok("<svg><script>alert(1)</script></svg>"),
                    ok("<svg><image href=\"https://x/y.png\"/></svg>"),
                )
            for (bad in cases) {
                val files = MemoryFiles()
                val store = ProviderLogoStore(fetch = { bad }, files = files, nowMs = { 0 })
                assertNull(store.svgFor("rogue"))
                assertFalse(files.files.containsKey("rogue.svg"))
            }
        }

    @Test
    fun `B-SEC-137 models_dev default sparkle means no logo and is remembered`() =
        runTest {
            val sparkle = "<svg viewBox=\"0 0 1 1\"><path d=\"M0 0\"/></svg>"
            val files = MemoryFiles()
            val requests = mutableListOf<String>()
            fun sparkleStore() =
                ProviderLogoStore(
                    fetch = { url ->
                        requests += url
                        ok(sparkle)
                    },
                    files = files,
                    nowMs = { 0 },
                    defaultLogoMd5 = Md5.hex(sparkle.encodeToByteArray()),
                )
            assertNull(sparkleStore().svgFor("nobody"))
            assertFalse(files.files.containsKey("nobody.svg"))
            // A new launch reads the marker instead of fetching again.
            assertNull(sparkleStore().svgFor("nobody"))
            assertEquals(1, requests.size)
            assertEquals(32, MODELS_DEV_DEFAULT_LOGO_MD5.length)
        }

    @Test
    fun `B-SEC-137 the MD5 used for the sparkle matches RFC 1321 vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5.hex(ByteArray(0)))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.hex("abc".encodeToByteArray()))
        assertEquals(
            "9e107d9d372bb6826bd81d3542a419d6",
            Md5.hex("The quick brown fox jumps over the lazy dog".encodeToByteArray()),
        )
    }

    @Test
    fun `B-SEC-137 slugs are safe and an unsafe id never reaches the network`() =
        runTest {
            assertEquals("openai", providerLogoSlug("OpenAI"))
            assertEquals("a.b_c-d", providerLogoSlug("a.b_c-d"))
            for (bad in listOf("", "../etc", "a/b", "a b", "a?x=1", "a#x", ".hidden", "a..b", "é", "a".repeat(65))) {
                assertNull(providerLogoSlug(bad), bad)
                assertNull(providerLogoUrl(bad), bad)
            }
            assertEquals("https://models.dev/logos/anthropic.svg", providerLogoUrl("Anthropic"))
            val requests = mutableListOf<String>()
            val store = ProviderLogoStore(fetch = { requests += it; ok(SVG) }, files = MemoryFiles(), nowMs = { 0 })
            assertNull(store.svgFor("../x"))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `B-SEC-137 fetches once, shares concurrent callers, keeps a stale logo offline`() =
        runTest {
            var now = 0L
            val files = MemoryFiles().apply { clock = { now } }
            val requests = mutableListOf<String>()
            var respond: () -> ProviderLogoResponse? = { ok(SVG) }
            fun store() = ProviderLogoStore(fetch = { requests += it; respond() }, files = files, nowMs = { now })
            val s = store()
            val a = async { s.svgFor("groq") }
            val b = async { s.svgFor("groq") }
            assertEquals(listOf(SVG, SVG), listOf(a.await(), b.await()))
            assertEquals(1, requests.size)
            assertEquals(SVG, store().svgFor("groq"))
            assertEquals(1, requests.size)
            now += 8L * 24 * 60 * 60 * 1000
            respond = { null }
            assertEquals(SVG, store().svgFor("groq"))
            assertNull(store().svgFor("anthropic"))
            val bundled = ProviderLogoStore(fetch = { requests += it; null }, files = files, nowMs = { now }, loadBundled = { "<svg/>" })
            val before = requests.size
            assertEquals("<svg/>", bundled.svgFor("anthropic"))
            assertEquals(before, requests.size)
        }
}
