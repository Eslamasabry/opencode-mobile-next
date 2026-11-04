package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** `test/desktop_release_check_test.dart`, case by case. */
class ReleaseCheckTest {
    private class RecordingFeed(private val body: String) : ReleaseListFeed {
        var url: String? = null
        var timeout: Duration? = null

        override suspend fun get(url: String, timeout: Duration): Outcome<String> {
            this.url = url
            this.timeout = timeout
            return body.ok()
        }
    }

    private val page = "https://github.com/Eslamasabry/opencode-mobile-next/releases"

    @Test
    fun `B-BUILD-010 build numbers are read from release tags`() {
        assertEquals(26, ReleaseTags.buildOf("v1.0.25+26-preview.5"))
        assertEquals(20, ReleaseTags.buildOf("v1.0.19+20"))
        assertNull(ReleaseTags.buildOf("v2.0.0"))
        assertNull(ReleaseTags.buildOf("nonsense"))
        assertNull(ReleaseTags.buildOf("v1.0.99+999oops"))
    }

    @Test
    fun `B-BUILD-010 newer means a strictly larger build number`() {
        assertTrue(ReleaseTags.isNewer(currentBuild = 25, tag = "v1.0.25+26-preview.5"))
        assertFalse(ReleaseTags.isNewer(currentBuild = 26, tag = "v1.0.25+26-preview.5"))
        assertFalse(ReleaseTags.isNewer(currentBuild = 27, tag = "v1.0.25+26-preview.5"))
        assertFalse(ReleaseTags.isNewer(currentBuild = 1, tag = "v2.0.0"))
    }

    @Test
    fun `B-BUILD-011 the checker asks the releases API with a bounded wait and skips drafts`() = runTest {
        val feed = RecordingFeed(
            """[{"draft":true,"tag_name":"v9.9.9+99"},""" +
                """{"draft":false,"tag_name":"v1.0.25+26-preview.5","html_url":"$page/tag/v1"}]""",
        )
        val release = assertIs<Outcome.Ok<ReleaseInfo?>>(ReleaseChecker(feed).fetchLatest()).value

        assertTrue(feed.url!!.startsWith("https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases"))
        assertEquals("v1.0.25+26-preview.5", release?.tag)
        assertEquals("$page/tag/v1", release?.pageUrl)
        assertEquals(RELEASE_NETWORK_TIMEOUT, feed.timeout)
        assertEquals(10, RELEASE_NETWORK_TIMEOUT.inWholeSeconds)
    }

    @Test
    fun `B-BUILD-011 the checker chooses the highest valid build from unordered entries`() = runTest {
        val feed = RecordingFeed(
            """[{"draft":false,"tag_name":"latest","html_url":"$page"},""" +
                """{"draft":false,"tag_name":"v1.0.25+26-preview.5","html_url":"$page/tag/v26"},""" +
                """{"draft":false,"tag_name":"v1.0.99+999oops","html_url":"$page/tag/bad"},""" +
                """{"draft":false,"tag_name":"v1.0.27+28","html_url":"$page/tag/v28"}]""",
        )
        val release = assertIs<Outcome.Ok<ReleaseInfo?>>(ReleaseChecker(feed).fetchLatest()).value
        assertEquals("v1.0.27+28", release?.tag)
        assertEquals("$page/tag/v28", release?.pageUrl)
    }

    @Test
    fun `B-BUILD-011 unversioned and malformed entries do not hide a valid update`() = runTest {
        val feed = RecordingFeed(
            """[{"draft":false,"tag_name":"v1.0.28+29oops","html_url":"$page"},""" +
                """{"draft":false,"tag_name":"v1.0.28","html_url":"$page"},""" +
                """{"tag_name":7},"not an object",{"draft":false},""" +
                """{"draft":false,"tag_name":"v1.0.26+27-preview.1","html_url":"$page/tag/v27"}]""",
        )
        val release = assertIs<Outcome.Ok<ReleaseInfo?>>(ReleaseChecker(feed).fetchLatest()).value
        assertEquals("v1.0.26+27-preview.1", release?.tag)
        // Unreadable bodies are "no release", never a crash.
        assertNull(ReleaseChecker.newest("{not json"))
        assertNull(ReleaseChecker.newest("{}"))
    }

    @Test
    fun `B-BUILD-012 the network wait is bounded even when the feed hangs`() = runTest {
        val hanging = ReleaseListFeed { _, _ -> awaitCancellation() }
        val outcome = ReleaseChecker(hanging, timeout = 10.milliseconds).fetchLatest()
        assertIs<Failure.TimedOut>(assertIs<Outcome.Failed>(outcome).failure)
        assertEquals(10L, currentTime)
    }

    @Test
    fun `B-BUILD-012 a failed request is a failure value, not a release`() = runTest {
        val down = ReleaseListFeed { _, _ -> Outcome.Failed(Failure.Network(NetworkWhy.Offline)) }
        assertIs<Failure.Network>(assertIs<Outcome.Failed>(ReleaseChecker(down).fetchLatest()).failure)
    }

    @Test
    fun `B-BUILD-012 a release URL that is not this project's GitHub page over https falls back to the release list`() = runTest {
        val hostile = listOf(
            "javascript:alert(1)",
            "http://github.com/Eslamasabry/opencode-mobile-next/releases",
            "https://user:pass@github.com/releases",
            "https://github.com.evil.test/releases",
            "https://github.com/Eslamasabry/opencode-mobile-next-evil/releases",
            "https://github.com/another-owner/opencode-mobile-next/releases",
            "https://github.com/Eslamasabry/opencode-mobile-next/issues",
            "https://github.com/Eslamasabry/opencode-mobile-next/releases?tab=tags",
            "https://github.com/Eslamasabry/opencode-mobile-next/releases#latest",
            "https://github.com:8443/Eslamasabry/opencode-mobile-next/releases",
            "https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v1/a.apk",
            "https://example.test/rel",
            "",
        )
        for (url in hostile) {
            val feed = RecordingFeed("""[{"draft":false,"tag_name":"v1.0.25+26-preview.5","html_url":"$url"}]""")
            val release = assertIs<Outcome.Ok<ReleaseInfo?>>(ReleaseChecker(feed).fetchLatest()).value
            assertEquals(RELEASES_PAGE_URL, release?.pageUrl, url)
        }
        assertEquals(page, RELEASES_PAGE_URL)
        assertEquals(page, trustedReleasePageUrl(page))
        assertEquals("$page/tag/v1.0.25+26", trustedReleasePageUrl("$page/tag/v1.0.25+26"))
        assertNull(trustedReleasePageUrl(null))
    }

    @Test
    fun `B-BUILD-007 a release tag for a version parses back to its build`() {
        assertEquals("v3.0.0+10003-dev", ReleaseTags.tagFor("3.0.0-dev", 10003))
        assertEquals("v3.0.0+10003", ReleaseTags.tagFor("3.0.0", 10003))
        assertEquals(10003, ReleaseTags.buildOf(ReleaseTags.tagFor("3.0.0-dev", 10003)!!))
        assertEquals(10003, ReleaseTags.buildOf("v3.0.0+10003-preview.9"))
        assertFalse(ReleaseTags.isNewer(10003, ReleaseTags.tagFor("3.0.0", 10003)!!))
        assertTrue(ReleaseTags.isNewer(10002, ReleaseTags.tagFor("3.0.0", 10003)!!))
        assertNull(ReleaseTags.tagFor("3.0", 10003))
    }
}
