package io.github.eslamasabry.oc.engine.providerlogos

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SVG =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"currentColor\" d=\"M2 2h20v20H2z\"/></svg>"
private const val DAY = 24L * 60 * 60 * 1000
private const val T0 = 1_791_849_600_000L // 2026-10-13

private fun ok(body: String, type: String = "image/svg+xml") =
    ProviderLogoResponse(200, type, body.encodeToByteArray())

/** In-memory folder; a write stamps the fake clock, like a real file's modified time. */
private class FakeDisk(val clock: () -> Long) : ProviderLogoDisk {
    val files = mutableMapOf<String, CachedLogoFile>()

    override suspend fun read(name: String): Outcome<CachedLogoFile?> = files[name].ok()

    override suspend fun write(name: String, text: String): Outcome<Unit> {
        files[name] = CachedLogoFile(text, clock())
        return Unit.ok()
    }

    override suspend fun delete(name: String): Outcome<Unit> {
        files.remove(name)
        return Unit.ok()
    }
}

private class Harness {
    var clock = T0
    val disk = FakeDisk { clock }
    val requests = mutableListOf<String>()
    var respond: (String) -> ProviderLogoResponse? = { ok(SVG) }
    var gate: CompletableDeferred<Unit>? = null
    val fetcher = ProviderLogoFetcher { url ->
        requests += url
        gate?.await()
        respond(url)?.ok() ?: Failure.Network().failed()
    }

    // The fallback cases run with no bundled mark available.
    val noBundle = BundledMarkReader { Failure.NotFound().failed() }

    fun store(defaultMd5: String = ModelsDevDefaultLogoMd5, bundled: BundledMarkReader = noBundle) =
        ProviderLogoStore(fetcher, disk, bundled, { clock }, defaultMd5)
}

class ProviderLogoStoreTest {
    private val h = Harness()

    @Test
    fun `B-AGENT-205 slugs accept provider ids and lower-case them`() {
        assertEquals("openai", providerLogoSlug("OpenAI"))
        assertEquals("zai-coding-plan", providerLogoSlug("zai-coding-plan"))
        assertEquals("302ai", providerLogoSlug("302ai"))
        assertEquals("a.b_c-d", providerLogoSlug("a.b_c-d"))
    }

    @Test
    fun `B-AGENT-205 slugs reject anything that could change the URL path or host`() {
        val bad = listOf("", "../etc", "a/b", "a b", "a?x=1", "a#x", ".hidden", "a..b", "é", "a".repeat(65), "a\n")
        for (id in bad.dropLast(1)) {
            assertNull(providerLogoSlug(id), id)
            assertNull(providerLogoUrl(id), id)
        }
        // Surrounding whitespace is trimmed (Dart trim), an inner newline is not a slug character.
        assertEquals("a", providerLogoSlug("a\n"))
        assertNull(providerLogoSlug("a\nb"))
        assertEquals("a".repeat(64), providerLogoSlug("a".repeat(64)))
    }

    @Test
    fun `B-AGENT-205 the URL is the fixed models dev logo path`() {
        assertEquals("https://models.dev/logos/anthropic.svg", providerLogoUrl("Anthropic"))
    }

    @Test
    fun `B-AGENT-205 an unsafe id never reaches the network`() = runTest {
        assertNull(h.store().svgFor("../x"))
        assertTrue(h.requests.isEmpty())
    }

    @Test
    fun `B-AGENT-206 fetches once, then serves memory and disk without the network`() = runTest {
        val first = h.store()
        assertEquals(SVG, first.svgFor("openai"))
        assertEquals(SVG, first.svgFor("openai"))
        assertEquals(listOf("https://models.dev/logos/openai.svg"), h.requests)
        // A fresh store (a new launch) reads the disk.
        assertEquals(SVG, h.store().svgFor("openai"))
        assertEquals(1, h.requests.size)
    }

    @Test
    fun `B-AGENT-206 concurrent callers share one fetch`() = runTest {
        val s = h.store()
        h.gate = CompletableDeferred()
        val both = listOf(async { s.svgFor("groq") }, async { s.svgFor("groq") })
        testScheduler.runCurrent()
        h.gate!!.complete(Unit)
        assertEquals(listOf(SVG, SVG), both.awaitAll())
        assertEquals(1, h.requests.size)
    }

    @Test
    fun `B-AGENT-206 refreshes after a week`() = runTest {
        h.store().svgFor("openai")
        h.clock += 8 * DAY
        h.respond = { ok(SVG.replace("M2", "M3")) }
        assertTrue(h.store().svgFor("openai")!!.contains("M3"))
        assertEquals(2, h.requests.size)
        // Six days later the refreshed file is still fresh.
        h.clock += 6 * DAY
        h.store().svgFor("openai")
        assertEquals(2, h.requests.size)
    }

    @Test
    fun `B-AGENT-206 offline keeps the stale cached logo, and nothing means initials`() = runTest {
        h.store().svgFor("openai")
        h.clock += 8 * DAY
        h.respond = { null }
        assertEquals(SVG, h.store().svgFor("openai"))
        assertNull(h.store().svgFor("anthropic"))
    }

    @Test
    fun `B-AGENT-206 a failed fetch is not retried straight away`() = runTest {
        h.respond = { null }
        val s = h.store()
        assertNull(s.svgFor("groq"))
        assertNull(s.svgFor("groq"))
        assertEquals(1, h.requests.size)
        // After the ten-minute backoff the same store tries again.
        h.clock += ProviderLogoFailureBackoffMs
        h.respond = { ok(SVG) }
        assertEquals(SVG, s.svgFor("groq"))
        assertEquals(2, h.requests.size)
    }

    @Test
    fun `B-AGENT-206 rejects oversize, wrong type, non-200 and active content`() = runTest {
        val big = "x".repeat(ProviderLogoMaxBytes + 1)
        val cases = listOf(
            ok("$SVG<!--$big-->"),
            ok(SVG, type = "text/html"),
            ok("<html></html>"),
            ProviderLogoResponse(404, "image/svg+xml", ByteArray(0)),
            ProviderLogoResponse(500, "image/svg+xml", SVG.encodeToByteArray()),
            ok("<svg><script>alert(1)</script></svg>"),
            ok("<svg><image href=\"https://x/y.png\"/></svg>"),
            ok("<svg><foreignObject/></svg>"),
            ok("<svg><use href=\"#a\"/></svg>"),
            ok("<svg onload=\"x()\"></svg>"),
            ok("<!ENTITY x><svg/>"),
            ProviderLogoResponse(200, "image/svg+xml", byteArrayOf(0x3c, 0x73, 0x76, 0x67, 0xC3.toByte())),
        )
        for (bad in cases) {
            h.respond = { bad }
            assertNull(h.store().svgFor("rogue"))
            assertFalse(h.disk.files.containsKey("rogue.svg"))
        }
    }

    @Test
    fun `B-AGENT-206 the models dev default sparkle means no logo and is remembered`() = runTest {
        val sparkle = "<svg viewBox=\"0 0 1 1\"><path d=\"M0 0\"/></svg>"
        h.respond = { ok(sparkle) }
        val md5 = Md5.hex(sparkle.encodeToByteArray())
        assertNull(h.store(defaultMd5 = md5).svgFor("nobody"))
        assertFalse(h.disk.files.containsKey("nobody.svg"))
        // A new launch reads the marker instead of fetching again.
        assertNull(h.store(defaultMd5 = md5).svgFor("nobody"))
        assertEquals(1, h.requests.size)
        assertEquals(32, ModelsDevDefaultLogoMd5.length)
    }

    @Test
    fun `B-AGENT-206 the md5 matches Dart crypto for known vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5.hex(ByteArray(0)))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.hex("abc".encodeToByteArray()))
        assertEquals(
            "57edf4a22be3c955ac49da2e2107b67a",
            Md5.hex("1234567890".repeat(8).encodeToByteArray()),
        )
    }

    @Test
    fun `B-AGENT-207 a bundled mark is answered without any request`() = runTest {
        val asked = mutableListOf<String>()
        val bundled = h.store(bundled = { path -> asked += path; "<svg/>".ok() })
        assertEquals("<svg/>", bundled.svgFor("anthropic"))
        assertEquals("<svg/>", bundled.svgFor("anthropic"))
        assertEquals(listOf("assets/marks/anthropic.svg"), asked)
        assertTrue(h.requests.isEmpty())
    }

    @Test
    fun `B-AGENT-207 a provider with no bundled mark falls back to the download`() = runTest {
        val unmarked = h.store(bundled = { error("never asked") })
        assertEquals(SVG, unmarked.svgFor("some-new-lab"))
        assertEquals(1, h.requests.size)
    }

    @Test
    fun `B-AGENT-207 an unreadable bundled mark falls back to the download`() = runTest {
        assertEquals(SVG, h.store().svgFor("openai"))
        assertEquals(1, h.requests.size)
    }

    @Test
    fun `B-AGENT-207 every listed mark is a bundled SVG, and none is spare`() {
        assertEquals(FlutterMarkAssets.svgFiles.map { it.removeSuffix(".svg") }.toSet(), BundledMarkSlugs)
        assertEquals(FlutterMarkAssets.svgFiles.size, BundledMarkSlugs.size)
        for (slug in BundledMarkSlugs) assertEquals("assets/marks/$slug.svg", markAssetPath(slug))
    }

    @Test
    fun `B-AGENT-207 bundled marks pass the same plain-SVG check as downloads`() {
        assertTrue(looksLikePlainSvg(SVG))
        assertFalse(looksLikePlainSvg("<svg><script/></svg>"))
        assertFalse(looksLikePlainSvg("<svg><IMAGE/></svg>"))
    }

    @Test
    fun `B-AGENT-207 provider ids resolve to a bundled mark, unknown ones to none`() {
        val ids = listOf(
            "anthropic", "openai", "google", "xai", "mistral", "deepseek", "groq", "openrouter", "ollama", "zai",
            "zhipuai-coding-plan", "amazon-bedrock", "azure", "github-copilot", "togetherai", "fireworks-ai",
            "cerebras", "moonshotai", "alibaba",
        )
        for (id in ids) assertTrue(providerMarkSlug(id) in BundledMarkSlugs, id)
        assertEquals("anthropic", providerMarkSlug("  Anthropic "))
        assertNull(providerMarkSlug("some-unknown-gateway"))
        assertNull(providerMarkSlug("../etc"))
    }

    @Test
    fun `B-AGENT-207 every catalog agent with a logo has a bundled mark`() {
        val noLogo = setOf("fx")
        for (agentId in FlutterMarkAssets.catalogAgentIds) {
            val slug = agentMarkSlug(agentId)
            if (agentId in noLogo) {
                assertNull(slug, agentId)
            } else {
                assertNotNull(slug, agentId)
                assertTrue(slug in BundledMarkSlugs, agentId)
            }
        }
        assertEquals("pi", agentMarkSlug("omp-acp"))
        assertEquals("kilocode", agentMarkSlug("KILO"))
    }
}
