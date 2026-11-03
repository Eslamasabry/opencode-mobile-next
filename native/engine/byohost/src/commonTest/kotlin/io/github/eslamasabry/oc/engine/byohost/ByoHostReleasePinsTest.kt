package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ByoHostReleasePinsTest {
    private val ledger = ByoHostReleasePins.Ledger

    @Test
    fun `B-AGENT-044 the shipped ledger pins OpenCode 2_1_1 and publishes no app version yet`() {
        assertEquals(1, ledger.schemaVersion)
        assertEquals("2.1.1", ledger.openCodeVersion)
        assertEquals("2.0.0", ledger.bundleVersion)
        assertTrue(ledger.appVersions.isEmpty()) // Publication requires owner review.
        for (version in listOf("1.1.0+51", "1.1.0+52", "1.1.0", "", "latest")) {
            assertNull(ByoHostReleasePins.bundleForAppVersion(version), version)
        }
        assertEquals(setOf("amd64", "arm64"), ledger.artifacts.keys)
        for (artifact in ledger.artifacts.values) {
            assertTrue(Regex("[a-f0-9]{64}").matches(artifact.sha256))
            assertTrue(artifact.url.startsWith("https://registry.npmjs.org/@opencode/"))
            assertTrue(artifact.url.endsWith("-2.1.1.tgz"))
        }
    }

    @Test
    fun `B-AGENT-044 the verifier accepts only the exact reviewed bundle for the exact app version`() {
        val good = ByoHostArtifact.of("https://example.invalid/oc2-x64.tgz", "b".repeat(64))!!
        val reviewed = ByoHostBundle.of("2.0.0", "2.1.1", mapOf("x64" to good))!!
        val withEntry = ledger.copy(appVersions = mapOf("2.1.0+57" to reviewed))
        assertEquals(reviewed, withEntry.bundleForAppVersion("2.1.0+57"))
        assertNull(withEntry.bundleForAppVersion("2.1.0"))
        assertEquals(ByoHostPinCheck.Match, withEntry.verifyBundle("2.1.0+57", reviewed))
        assertEquals(ByoHostPinCheck.NotReviewed, withEntry.verifyBundle("2.1.0+58", reviewed))
        assertEquals(ByoHostPinCheck.NotReviewed, ledger.verifyBundle("2.1.0+57", reviewed))
        val otherDigest = ByoHostArtifact.of(good.url, "c".repeat(64))!!
        assertEquals(
            ByoHostPinCheck.DigestMismatch,
            withEntry.verifyBundle("2.1.0+57", ByoHostBundle.of("2.0.0", "2.1.1", mapOf("x64" to otherDigest))),
        )
        val otherUrl = ByoHostArtifact.of("https://example.invalid/other.tgz", good.sha256)!!
        assertEquals(
            ByoHostPinCheck.UrlMismatch,
            withEntry.verifyBundle("2.1.0+57", ByoHostBundle.of("2.0.0", "2.1.1", mapOf("x64" to otherUrl))),
        )
        assertEquals(
            ByoHostPinCheck.VersionMismatch,
            withEntry.verifyBundle("2.1.0+57", ByoHostBundle.of("2.0.1", "2.1.1", mapOf("x64" to good))),
        )
        assertEquals(
            ByoHostPinCheck.UnknownArchitecture,
            withEntry.verifyBundle(
                "2.1.0+57",
                ByoHostBundle.of("2.0.0", "2.1.1", mapOf("x64" to good, "arm64" to good)),
            ),
        )
    }

    @Test
    fun `B-AGENT-044 downloaded upstream bytes must hash to the ledger digest`() {
        val bytes = "pretend tarball".encodeToByteArray()
        val url = "https://example.invalid/cli.tgz"
        val pinnedLedger =
            ledger.copy(artifacts = mapOf("amd64" to ByoHostPinnedArtifact(url, DartJson.sha256Hex(bytes))))
        assertEquals(ByoHostPinCheck.Match, pinnedLedger.verifyUpstream("x64", url, bytes))
        assertEquals(ByoHostPinCheck.DigestMismatch, pinnedLedger.verifyUpstream("x64", url, bytes + 0))
        assertEquals(ByoHostPinCheck.UrlMismatch, pinnedLedger.verifyUpstream("x64", "$url?x=1", bytes))
        assertEquals(ByoHostPinCheck.UnknownArchitecture, pinnedLedger.verifyUpstream("arm64", url, bytes))
        assertEquals(ByoHostPinCheck.UnknownArchitecture, pinnedLedger.verifyUpstream("amd64", url, bytes))
        val arm = ledger.artifacts.getValue("arm64")
        assertEquals(ByoHostPinCheck.DigestMismatch, ledger.verifyUpstream("arm64", arm.url, bytes))
    }

    @Test
    fun `B-AGENT-044 bundles and artifacts refuse unreviewable shapes`() {
        val sha = "a".repeat(64)
        for (url in listOf("http://h/x", "https:///x", "https://u@h/x", "https://h/x?q", "https://h/x#f")) {
            assertNull(ByoHostArtifact.of(url, sha), url)
        }
        assertNull(ByoHostArtifact.of("https://h/x", "A".repeat(64)))
        val artifact = ByoHostArtifact.of("https://h/x", sha)!!
        assertNull(ByoHostBundle.of("latest", "2.1.1", mapOf("x64" to artifact)))
        assertNull(ByoHostBundle.of("2.0.0", "2.1.1", emptyMap()))
        assertNull(ByoHostBundle.of("2.0.0", "2.1.1", mapOf("amd64" to artifact)))
    }
}
