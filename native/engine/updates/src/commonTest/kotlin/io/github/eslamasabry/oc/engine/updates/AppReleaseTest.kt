package io.github.eslamasabry.oc.engine.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppReleaseTest {
    @Test
    fun `B-MIGR-012 parses sums, picks files by ABI and reads the first sentence`() {
        val release = AppRelease.parse(releaseJson())!!
        assertEquals(58, release.build)
        assertEquals("2.2.0", release.version)
        assertEquals("SHA256SUMS", release.checksums?.name)
        assertEquals("opencode-mobile-2.2.0-arm64-v8a.apk", release.apkFor(listOf("arm64-v8a"))?.name)
        assertEquals("Faster chats.", release.summary)

        val sums = parseSha256Sums("${"A".repeat(64)}  a.apk\n${"b".repeat(64)} *b.apk\nnoise\n")
        assertEquals("a".repeat(64), sums["a.apk"])
        assertEquals("b".repeat(64), sums["b.apk"])
        assertEquals(2, sums.size)
    }

    @Test
    fun `B-MIGR-012 tags carry the versionCode and anything else is not a release`() {
        assertEquals(57, AppRelease.buildOf("v2.1.0+57"))
        assertEquals(57, AppRelease.buildOf("v2.1.0+57-rc.1"))
        assertNull(AppRelease.buildOf("2.1.0+57"))
        assertNull(AppRelease.parse("not json"))
        assertNull(AppRelease.parse("[]"))
    }

    @Test
    fun `B-MIGR-012 only GitHub release downloads of this project are trusted`() {
        val ok = "https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.2.0%2B58/a.apk"
        assertTrue(isTrustedReleaseFileUrl(ok))
        assertFalse(isTrustedReleaseFileUrl(ok.replace("https", "http")))
        assertFalse(isTrustedReleaseFileUrl("https://github.com/someone/else/releases/download/a.apk"))
        assertFalse(isTrustedReleaseFileUrl("https://user@github.com/Eslamasabry/opencode-mobile-next/releases/a.apk"))
        assertFalse(isTrustedReleaseFileUrl("https://github.com.evil.example/Eslamasabry/opencode-mobile-next/releases/a"))
        assertFalse(isTrustedReleaseFileUrl("not a url"))
        // Proof builds: only the feed's own origin.
        assertTrue(isTrustedReleaseFileUrl("http://localhost:8080/a.apk", feedOverride = "http://localhost:8080/feed.json"))
        assertFalse(isTrustedReleaseFileUrl("http://localhost:9090/a.apk", feedOverride = "http://localhost:8080/feed.json"))
    }
}
