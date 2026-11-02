package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.APK_SIZE
import io.github.eslamasabry.oc.feature.updates.INSTALLED
import io.github.eslamasabry.oc.feature.updates.RELEASE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The pure page, notice, offer and badge mappings, read from the Flutter Updates page and notice. */
class UpdatesUiTest {
    private val now = Instant.fromEpochMilliseconds(1_767_225_600_000)
    private val available =
        AppUpdateState(phase = UpdatePhase.Available, release = RELEASE, downloadSize = APK_SIZE, installed = INSTALLED)

    @Test
    fun `B-MIGR-012 up to date names the version, when it looked, and offers no update`() {
        val base = AppUpdateState(installed = INSTALLED)
        val justNow = updatesUi(base.copy(checkedAt = now - 30.seconds), now)
        assertEquals("2.1.0", justNow.version)
        assertEquals("2.1.0+57", justNow.build)
        assertEquals(UpdatesLine.CheckedJustNow, justNow.line)
        assertNull(justNow.action)
        assertNull(justNow.failure)
        val minutes = updatesUi(base.copy(checkedAt = now - 5.minutes), now)
        assertEquals(UpdatesLine.CheckedMinutesAgo, minutes.line)
        assertEquals(5, minutes.agoCount)
        val hours = updatesUi(base.copy(checkedAt = now - 3.hours), now)
        assertEquals(UpdatesLine.CheckedHoursAgo to 3, hours.line to hours.agoCount)
        val days = updatesUi(base.copy(checkedAt = now - 2.days), now)
        assertEquals(UpdatesLine.CheckedDaysAgo to 2, days.line to days.agoCount)
    }

    @Test
    fun `B-MIGR-012 never checked and checking off are told apart`() {
        assertEquals(UpdatesLine.NeverChecked, updatesUi(AppUpdateState(), now).line)
        val off = updatesUi(AppUpdateState(enabled = false), now)
        assertEquals(UpdatesLine.CheckingOff, off.line)
        assertFalse(off.checksOn)
        assertNull(off.version, "an unknown installed version names the app instead")
    }

    @Test
    fun `B-MIGR-012 available shows the release version, its size rounded up and Update`() {
        val ui = updatesUi(available, now)
        assertEquals(UpdatesLine.Available, ui.line)
        assertEquals("2.2.0", ui.releaseVersion)
        assertEquals(39L, ui.sizeMb)
        assertEquals(UpdatesAction.Update, ui.action)
        assertEquals(RELEASE.notes, ui.notes)
        assertTrue(ui.checkNowEnabled)
        assertEquals(UpdatesLine.AvailableNoSize, updatesUi(available.copy(downloadSize = null), now).line)
    }

    @Test
    fun `B-MIGR-012 downloading shows progress, the floor percent, Cancel, and Check now waits`() {
        val ui = updatesUi(available.copy(phase = UpdatePhase.Downloading, progress = 0.429f), now)
        assertEquals(UpdatesLine.Downloading, ui.line)
        assertEquals(UpdatesAction.Cancel, ui.action)
        assertTrue(ui.showProgress)
        assertEquals(42, ui.percent)
        assertFalse(ui.checkNowEnabled)
        val unknown = updatesUi(available.copy(phase = UpdatePhase.Downloading, progress = null), now)
        assertNull(unknown.percent, "an unknown size waits rather than inventing a percent")
        assertTrue(unknown.showProgress)
    }

    @Test
    fun `B-MIGR-012 a checksum failure says not installed, offers Try again, and keeps the detail for Details only`() {
        val ui =
            updatesUi(
                available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Checksum, detail = "sha256 bb, expected aa"),
                now,
            )
        assertEquals(UpdatesLine.NotInstalled, ui.line)
        assertEquals(UpdateFailure.Checksum, ui.failure)
        assertEquals(UpdatesAction.TryAgain, ui.action)
        assertEquals("sha256 bb, expected aa", ui.detail)
        assertNull(updatesUi(available.copy(detail = "old"), now).detail, "detail only after a failure")
    }

    @Test
    fun `B-MIGR-012 a refusal the same file would repeat offers the release page, not a retry`() {
        for (why in listOf(UpdateFailure.Signer, UpdateFailure.Package, UpdateFailure.Version)) {
            val failed = available.copy(phase = UpdatePhase.Failed, failure = why)
            assertEquals(UpdatesAction.OpenRelease, updatesUi(failed, now).action, "$why")
            val noPage = failed.copy(release = RELEASE.copy(pageUrl = ""))
            assertNull(updatesUi(noPage, now).action, "$why without a page offers nothing")
        }
        for (why in listOf(UpdateFailure.Network, UpdateFailure.Storage, UpdateFailure.Installer, UpdateFailure.NoFile)) {
            assertEquals(
                UpdatesAction.TryAgain,
                updatesUi(available.copy(phase = UpdatePhase.Failed, failure = why), now).action,
                "$why",
            )
        }
    }

    @Test
    fun `B-MIGR-012 ready to install offers Install and the permission note only while Android must allow it`() {
        val ready = available.copy(phase = UpdatePhase.ReadyToInstall)
        assertEquals(UpdatesAction.Install, updatesUi(ready, now).action)
        assertFalse(updatesUi(ready, now).needsPermission)
        assertTrue(updatesUi(ready.copy(needsPermission = true), now).needsPermission)
        assertFalse(updatesUi(available.copy(needsPermission = true), now).needsPermission)
    }

    @Test
    fun `B-ANDR-094 where the app cannot install itself nothing is offered`() {
        val ui = updatesUi(available.copy(supported = false), now)
        assertNull(ui.action)
        assertFalse(ui.supported)
        assertFalse(ui.checkNowEnabled)
        assertNull(noticeUi(available.copy(supported = false)))
        assertNull(offerUi(available.copy(supported = false)))
        assertFalse(hasUpdateBadge(available.copy(supported = false)))
    }

    @Test
    fun `B-MIGR-012 the notice offers, tracks and steps aside`() {
        assertEquals(NoticeUi.Ready("2.2.0"), noticeUi(available))
        assertEquals(
            NoticeUi.Downloading("2.2.0", 42),
            noticeUi(available.copy(phase = UpdatePhase.Downloading, progress = 0.425f)),
        )
        assertEquals(NoticeUi.Verifying, noticeUi(available.copy(phase = UpdatePhase.Verifying)))
        assertEquals(NoticeUi.Installing, noticeUi(available.copy(phase = UpdatePhase.Installing)))
        assertEquals(
            NoticeUi.ReadyToInstall("2.2.0", needsPermission = true),
            noticeUi(available.copy(phase = UpdatePhase.ReadyToInstall, needsPermission = true)),
        )
        assertNull(noticeUi(available.copy(pageOpen = true)), "the Updates page is the one place it shows")
        assertNull(noticeUi(available.copy(notNow = true)), "Not now hides the offer")
        assertEquals(
            NoticeUi.Downloading("2.2.0", null),
            noticeUi(available.copy(phase = UpdatePhase.Downloading, notNow = true)),
            "work the person started keeps its line",
        )
        assertNull(noticeUi(available.copy(phase = UpdatePhase.Failed, failure = UpdateFailure.Network)))
        assertNull(noticeUi(AppUpdateState()))
    }

    @Test
    fun `B-MIGR-012 the gear badge and offer stay after Not now and vanish with nothing waiting`() {
        val silenced = available.copy(notNow = true)
        assertTrue(hasUpdateBadge(silenced))
        assertEquals(OfferUi("2.2.0", "This release is faster."), offerUi(silenced))
        assertFalse(hasUpdateBadge(AppUpdateState()))
        assertNull(offerUi(AppUpdateState()))
    }
}
