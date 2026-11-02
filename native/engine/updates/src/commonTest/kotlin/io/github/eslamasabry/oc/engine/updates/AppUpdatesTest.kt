package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdatesTest {
    private val feed = FakeFeed()
    private val platform = FakePlatform()
    private val store = FakeStore()
    private val clock = TestClock()

    private fun make(supported: Boolean = true) = AppUpdates(feed, platform, store, clock, supported)

    @Test
    fun `B-MIGR-012 a newer build is offered, an equal or older one is not`() = runTest {
        val c = make()
        c.start()
        assertEquals(UpdatePhase.Available, c.state.value.phase)
        assertEquals("2.2.0", c.state.value.release?.version)
        assertTrue(c.state.value.hasPendingRelease)
        assertTrue(c.state.value.noticeVisible)
        assertEquals(2000L, c.state.value.downloadSize)

        feed.latestBody = releaseJson(tag = "v2.1.0+57").ok()
        c.checkNow()
        assertEquals(UpdatePhase.None, c.state.value.phase)
        assertFalse(c.state.value.hasPendingRelease)
    }

    @Test
    fun `B-MIGR-012 a finding survives a restart without asking again`() = runTest {
        make().start()
        val again = make()
        again.start()
        assertEquals(1, feed.latestCalls, "the second start is within six hours")
        assertEquals(UpdatePhase.Available, again.state.value.phase)
    }

    @Test
    fun `B-MIGR-012 once the new build is installed the finding and its file are dropped`() = runTest {
        make().start()
        platform.build = 58
        val after = make()
        after.start()
        assertNull(store.record.releaseJson)
        assertTrue(platform.cleared > 0)
        assertFalse(after.state.value.hasPendingRelease)
    }

    @Test
    fun `B-MIGR-012 checks happen at most once every six hours and a failure retries after thirty minutes`() = runTest {
        val c = make()
        c.start()
        c.checkIfDue()
        assertEquals(1, feed.latestCalls)
        clock.advance(6.hours)
        c.checkIfDue()
        assertEquals(2, feed.latestCalls)

        feed.latestBody = offline
        clock.advance(6.hours)
        c.checkIfDue()
        assertEquals(3, feed.latestCalls)
        assertEquals(UpdatePhase.Available, c.state.value.phase, "a failed check is not a failed update")
        clock.advance(29.minutes)
        c.checkIfDue()
        assertEquals(3, feed.latestCalls)
        clock.advance(2.minutes)
        c.checkIfDue()
        assertEquals(4, feed.latestCalls)
    }

    @Test
    fun `B-MIGR-012 the switch off stops the clock and Check now still asks`() = runTest {
        val c = make()
        c.start()
        c.setEnabled(false)
        assertFalse(store.record.enabled)
        clock.advance(7.hours)
        c.checkIfDue()
        assertEquals(1, feed.latestCalls)
        c.checkNow()
        assertEquals(2, feed.latestCalls)
    }

    @Test
    fun `B-ANDR-094 an unsupported platform never asks and never claims an update`() = runTest {
        val c = make(supported = false)
        c.start()
        c.checkNow()
        c.updateNow()
        assertEquals(0, feed.latestCalls)
        assertFalse(c.state.value.hasPendingRelease)
        assertFalse(c.state.value.noticeVisible)
    }

    @Test
    fun `B-MIGR-012 drafts, pre-releases and odd tags are never offered`() = runTest {
        feed.latestBody = releaseJson(draft = true).ok()
        val c = make()
        c.start()
        assertFalse(c.state.value.hasPendingRelease)
        feed.latestBody = releaseJson(prerelease = true).ok()
        c.checkNow()
        assertFalse(c.state.value.hasPendingRelease)
        feed.latestBody = releaseJson(tag = "nightly").ok()
        c.checkNow()
        assertFalse(c.state.value.hasPendingRelease)
    }

    @Test
    fun `B-MIGR-012 Not now hides the offer but keeps the gear badge, and a newer release brings it back`() = runTest {
        val c = make()
        c.start()
        c.notNow()
        assertFalse(c.state.value.noticeVisible)
        assertTrue(c.state.value.hasPendingRelease)
        feed.latestBody = releaseJson(tag = "v2.3.0+59").ok()
        c.checkNow()
        assertTrue(c.state.value.noticeVisible)
    }

    @Test
    fun `B-MIGR-012 the notice steps aside while the Updates page is open`() = runTest {
        val c = make()
        c.start()
        c.setPageOpen(true)
        assertFalse(c.state.value.noticeVisible)
        c.setPageOpen(false)
        assertTrue(c.state.value.noticeVisible)
    }

    @Test
    fun `B-MIGR-012 downloads the file for this phone, checks it and installs it`() = runTest {
        val c = make()
        c.start()
        c.updateNow()
        assertTrue(feed.downloadedUrl!!.endsWith("arm64-v8a.apk"))
        assertEquals(1, platform.installs.size)
        assertEquals(UpdatePhase.Installing, c.state.value.phase)
    }

    @Test
    fun `B-MIGR-012 an armeabi-v7a phone gets the v7a file and others the universal one`() = runTest {
        platform.abis = listOf("armeabi-v7a")
        val c = make()
        c.start()
        c.updateNow()
        assertTrue(feed.downloadedUrl!!.endsWith("armeabi-v7a.apk"))

        val release = AppRelease.parse(releaseJson())!!
        assertEquals("opencode-mobile-2.2.0.apk", release.apkFor(listOf("x86_64"))?.name)
    }

    @Test
    fun `B-MIGR-012 a checksum mismatch never installs and deletes the file`() = runTest {
        platform.hash = "f".repeat(64)
        val c = make()
        c.start()
        c.updateNow()
        assertTrue(platform.installs.isEmpty())
        assertEquals(UpdatePhase.Failed, c.state.value.phase)
        assertEquals(UpdateFailure.Checksum, c.state.value.failure)
        assertEquals(1, platform.deleted.size)
        assertTrue(c.state.value.retryHelps)
    }

    @Test
    fun `B-MIGR-012 a signer mismatch never installs and names the signer under Details`() = runTest {
        platform.signer = "BB22"
        val c = make()
        c.start()
        c.updateNow()
        assertTrue(platform.installs.isEmpty())
        assertEquals(UpdateFailure.Signer, c.state.value.failure)
        assertTrue(c.state.value.detail!!.contains("BB22"))
        assertFalse(c.state.value.retryHelps, "the same file would be refused again")
    }

    @Test
    fun `B-MIGR-012 another package or a version that is not newer never installs`() = runTest {
        platform.pkg = "com.example.other"
        val c = make()
        c.start()
        c.updateNow()
        assertEquals(UpdateFailure.Package, c.state.value.failure)
        platform.pkg = PKG
        platform.versionCode = 57
        c.updateNow()
        assertEquals(UpdateFailure.Version, c.state.value.failure)
        assertTrue(platform.installs.isEmpty())
    }

    @Test
    fun `B-MIGR-012 without install permission it asks once and continues on return with the same file`() = runTest {
        platform.canInstallNow = false
        val c = make()
        c.start()
        c.updateNow()
        assertEquals(1, platform.permissionOpened)
        assertEquals(UpdatePhase.ReadyToInstall, c.state.value.phase)
        assertTrue(c.state.value.needsPermission)
        platform.canInstallNow = true
        c.onResumed()
        assertEquals(1, platform.installs.size)
        assertEquals(1, feed.downloads, "the checked file is reused")
    }

    @Test
    fun `B-MIGR-012 cancelling a download goes back to available`() = runTest {
        feed.hold = CompletableDeferred()
        val c = make()
        c.start()
        val run = async { c.updateNow() }
        runCurrent()
        assertEquals(UpdatePhase.Downloading, c.state.value.phase)
        assertEquals(0.5f, c.state.value.progress)
        c.cancel()
        run.await()
        assertEquals(UpdatePhase.Available, c.state.value.phase)
        assertTrue(platform.installs.isEmpty())
    }

    @Test
    fun `B-MIGR-012 a dismissed Android dialog returns to ready and Try again reuses the file`() = runTest {
        platform.events = listOf(InstallEvent(InstallState.PendingUser), InstallEvent(InstallState.Aborted))
        val c = make()
        c.start()
        c.updateNow()
        assertEquals(UpdatePhase.ReadyToInstall, c.state.value.phase)
        assertFalse(c.state.value.needsPermission)
        c.updateNow()
        assertEquals(2, platform.installs.size)
        assertEquals(1, feed.downloads)
    }

    @Test
    fun `B-MIGR-012 an installer failure keeps Android's words under Details only`() = runTest {
        platform.events = listOf(InstallEvent(InstallState.Failure, "INSTALL_FAILED_X"))
        val c = make()
        c.start()
        c.updateNow()
        assertEquals(UpdateFailure.Installer, c.state.value.failure)
        assertEquals("INSTALL_FAILED_X", c.state.value.detail)
    }
}
