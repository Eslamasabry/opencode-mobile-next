package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Native twin of Flutter PR #126 (lib/update/in_app_update_service.dart, in_app_update_notice.dart and
// test/in_app_update_service_test.dart): what native's updater had to close.

private const val REPO = "https://github.com/Eslamasabry/opencode-mobile-next"

private const val FILES = "$REPO/releases/download/v2.2.0%2B58"
private const val OTHER = "Eslamasabry/opencode-mobile-next/releases/download"

private fun release(apkUrl: String, sumsUrl: String? = "$FILES/SHA256SUMS", apkName: String = "app.apk") =
    """
    {"tag_name":"v2.2.0+58","draft":false,"prerelease":false,"body":"New.","html_url":"$REPO/releases/tag/v2.2.0+58",
     "assets":[{"name":"$apkName","browser_download_url":"$apkUrl","size":10}
     ${if (sumsUrl != null) """,{"name":"SHA256SUMS","browser_download_url":"$sumsUrl","size":1}""" else ""}]}
    """.trimIndent()

@OptIn(ExperimentalCoroutinesApi::class)
class InAppUpdateTwinTest {
    private val feed = FakeFeed()
    private val platform = FakePlatform()
    private val store = FakeStore()
    private val clock = TestClock()

    private fun make() = AppUpdates(feed, platform, store, clock)

    @Test
    fun `B-BUILD-040 only this project's https release downloads are trusted`() {
        assertTrue(isTrustedReleaseFileUrl("$REPO/releases/download/v2.2.0+58/app.apk"))
        // Not a download: the release page, the tag list, another repository, plain http, another host or port.
        assertFalse(isTrustedReleaseFileUrl("$REPO/releases/tag/v2.2.0+58"))
        assertFalse(isTrustedReleaseFileUrl("$REPO/releases/latest/app.apk"))
        assertFalse(isTrustedReleaseFileUrl("https://github.com/Eslamasabry/other/releases/download/v1/app.apk"))
        assertFalse(isTrustedReleaseFileUrl("http://github.com/$OTHER/v1/a.apk"))
        assertFalse(isTrustedReleaseFileUrl("https://objects.githubusercontent.com/$OTHER/a"))
        assertFalse(isTrustedReleaseFileUrl("https://github.com:8443/$OTHER/v1/a.apk"))
        assertFalse(isTrustedReleaseFileUrl("$REPO/releases/download/../../../evil/releases/download/a.apk"))
    }

    @Test
    fun `B-BUILD-041 a release without SHA256SUMS or with a file from elsewhere is never offered`() = runTest {
        val c = make()
        feed.latestBody = release("$REPO/releases/download/v2.2.0%2B58/app.apk", sumsUrl = null).ok()
        c.start()
        assertFalse(c.state.value.hasPendingRelease, "no checksums: fail closed")

        feed.latestBody = release("https://evil.example/app.apk").ok()
        c.checkNow()
        assertFalse(c.state.value.hasPendingRelease)

        feed.latestBody = release("$FILES/app.apk", sumsUrl = "$REPO/releases/tag/SHA256SUMS").ok()
        c.checkNow()
        assertFalse(c.state.value.hasPendingRelease)

        feed.latestBody = release("$REPO/releases/download/v2.2.0%2B58/app.apk").ok()
        c.checkNow()
        assertTrue(c.state.value.hasPendingRelease)
    }

    @Test
    fun `B-BUILD-042 the server's file name never becomes a path`() = runTest {
        feed.latestBody = release("$REPO/releases/download/v2.2.0%2B58/x.apk", apkName = "../../files/evil.apk").ok()
        feed.extraSums = "$GOOD_HASH  ../../files/evil.apk\n"
        val c = make()
        c.start()
        assertTrue(c.state.value.hasPendingRelease)
        c.updateNow()
        val written = feed.written.single()
        assertEquals("/cache/app-update/update-58.apk", written)
        assertFalse(".." in written)
    }

    @Test
    fun `B-BUILD-043 only release builds check for updates`() {
        assertTrue(AppUpdates.supportedOn(debuggable = false))
        assertFalse(AppUpdates.supportedOn(debuggable = true))
    }

    @Test
    fun `B-BUILD-044 start and resume check after a cancellable delay, once for quick resumes, and open the prompt`() =
        runTest {
            val c = make()
            c.opened(backgroundScope)
            runCurrent()
            assertEquals(0, feed.latestCalls, "nothing before the delay")
            advanceTimeBy(AppUpdates.OPEN_DELAY / 2)
            c.opened(backgroundScope) // a quick resume replaces the pending wait
            advanceTimeBy(AppUpdates.OPEN_DELAY / 2 + AppUpdates.OPEN_DELAY / 4)
            runCurrent()
            assertEquals(0, feed.latestCalls, "the first wait was cancelled")
            advanceTimeBy(AppUpdates.OPEN_DELAY)
            runCurrent()
            assertEquals(1, feed.latestCalls)
            assertTrue(c.state.value.promptOpen)

            // A resume within six hours does not ask GitHub again, but the waiting release is offered again.
            c.closePrompt()
            assertFalse(c.state.value.promptOpen)
            c.opened(backgroundScope)
            advanceTimeBy(AppUpdates.OPEN_DELAY * 2)
            runCurrent()
            assertEquals(1, feed.latestCalls, "throttled")
            assertTrue(c.state.value.promptOpen)
        }

    @Test
    fun `B-BUILD-045 Later silences the prompt for the session and Update now closes it and downloads`() = runTest {
        val c = make()
        c.opened(backgroundScope)
        advanceTimeBy(AppUpdates.OPEN_DELAY * 2)
        runCurrent()
        assertTrue(c.state.value.promptOpen)
        c.later()
        assertFalse(c.state.value.promptOpen)
        c.opened(backgroundScope)
        advanceTimeBy(AppUpdates.OPEN_DELAY * 2)
        runCurrent()
        assertFalse(c.state.value.promptOpen, "Later holds for this session")
        assertTrue(c.state.value.hasPendingRelease, "the gear badge stays")

        val next = make()
        next.opened(backgroundScope)
        advanceTimeBy(AppUpdates.OPEN_DELAY * 2)
        runCurrent()
        assertTrue(next.state.value.promptOpen, "a new session offers it again")
        next.updateNow()
        assertFalse(next.state.value.promptOpen)
        assertEquals(1, feed.downloads)
    }

    @Test
    fun `B-BUILD-043 an unsupported build never schedules a check`() = runTest {
        val c = AppUpdates(feed, platform, store, clock, supported = false)
        c.opened(backgroundScope)
        advanceTimeBy(AppUpdates.OPEN_DELAY * 2)
        runCurrent()
        assertEquals(0, feed.latestCalls)
        assertFalse(c.state.value.promptOpen)
    }
}
