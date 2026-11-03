package io.github.eslamasabry.oc.feature.gate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S2-GATEUI: the gate's states and ways out (plan 05 §8, 01 §9.7), on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class GatePresenterTest {
    private class RecordingActions : GateActions {
        val calls = mutableListOf<String>()
        var retryHold: CompletableDeferred<Unit>? = null

        override suspend fun retry() {
            calls += "retry"
            retryHold?.await()
        }

        override suspend fun restorePreUpdateData() { calls += "restore" }
        override suspend fun startFresh() { calls += "startFresh" }
        override suspend fun reportProblem() { calls += "report" }
        override suspend fun openStorageSettings() { calls += "storage" }
        override suspend fun acknowledgeNotices() { calls += "ack" }
        override suspend fun openReentry(item: GateReentry) { calls += "open:${item.targetName}:${item.kind}" }
        override suspend fun acknowledgeDeferredNotice() { calls += "ackDeferred" }

        var saveResult = true
        var saveHold: CompletableDeferred<Unit>? = null

        override suspend fun saveReentry(item: GateReentry, secret: String): Boolean {
            // The secret itself is never recorded: only its length, to prove the typed value reached the store.
            calls += "save:${item.targetName}:${secret.length}"
            saveHold?.await()
            return saveResult
        }
    }

    private class Harness(scope: TestScope, initial: GateInput) {
        val input = MutableStateFlow(initial)
        val actions = RecordingActions()
        val presenter = GatePresenter(input, actions, scope.backgroundScope)
        val ui: GateUi get() = presenter.ui.value
    }

    @Test
    fun `B-MIGR-010 Updating offers Try again at 8 s and not before`() = runTest {
        val h = Harness(this, GateInput.Running("M1"))
        runCurrent()
        assertEquals(GateUi.Updating(offerRetry = false), h.ui)
        advanceTimeBy(GatePresenter.SLOW_MS - 1)
        runCurrent()
        assertEquals(GateUi.Updating(offerRetry = false), h.ui)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(GateUi.Updating(offerRetry = true), h.ui)
    }

    @Test
    fun `B-MIGR-010 a step change inside one run keeps the clock, a new run restarts it`() = runTest {
        val h = Harness(this, GateInput.Closed(localeKnown = true))
        runCurrent()
        advanceTimeBy(5_000)
        h.input.value = GateInput.Running("M2") // Opening → Updating is still the same wait
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(GateUi.Updating(offerRetry = true), h.ui)
        h.input.value = GateInput.Blocked(GateBlockReason.StepCrashed)
        runCurrent()
        h.input.value = GateInput.Running("M2") // retried from Blocked: a new wait
        runCurrent()
        assertEquals(GateUi.Updating(offerRetry = false), h.ui)
    }

    @Test
    fun `B-MIGR-010 Try again is single-flight, a second press while it runs starts nothing`() = runTest {
        val h = Harness(this, GateInput.Running("M3"))
        h.actions.retryHold = CompletableDeferred()
        advanceTimeBy(GatePresenter.SLOW_MS)
        runCurrent()
        h.presenter.onRow(GateRow.TryAgain)
        runCurrent()
        h.presenter.onRow(GateRow.TryAgain)
        runCurrent()
        assertEquals(listOf("retry"), h.actions.calls)
        h.actions.retryHold!!.complete(Unit)
        runCurrent()
        h.presenter.onRow(GateRow.TryAgain)
        runCurrent()
        assertEquals(listOf("retry", "retry"), h.actions.calls)
    }

    @Test
    fun `B-MIGR-010 Opening is wordless until the locale is known, and gets words and Try again at 8 s`() = runTest {
        val h = Harness(this, GateInput.Closed(localeKnown = false))
        runCurrent()
        assertEquals(GateUi.Opening(wordless = true, offerRetry = false), h.ui)
        h.input.value = GateInput.Closed(localeKnown = true)
        runCurrent()
        assertEquals(GateUi.Opening(wordless = false, offerRetry = false), h.ui)
        h.input.value = GateInput.Closed(localeKnown = false)
        advanceTimeBy(GatePresenter.SLOW_MS)
        runCurrent()
        // Never a dead end: a stuck fence still gets a way out, in the system language.
        assertEquals(GateUi.Opening(wordless = false, offerRetry = true), h.ui)
    }

    @Test
    fun `B-MIGR-010 every failure class keeps a way out, Restore only while it is allowed`() {
        val all = GateBlockReason.entries
        for (reason in all) {
            for (restore in listOf(true, false)) {
                val rows = GatePresenter.rowsFor(reason, restore)
                assertTrue(rows.isNotEmpty() && rows.any { it != GateRow.Report }, "$reason/$restore: $rows")
                assertEquals(restore && GateRow.Restore in GatePresenter.rowsFor(reason, true), GateRow.Restore in rows)
                assertEquals(GateRow.entries.filter { it in rows }, rows, "drawing order for $reason")
            }
        }
        assertEquals(
            listOf(GateRow.TryAgain, GateRow.OpenStorage, GateRow.Report),
            GatePresenter.rowsFor(GateBlockReason.LowSpace, restoreAvailable = true),
        )
        assertEquals(
            listOf(GateRow.TryAgain, GateRow.Restore, GateRow.StartFresh, GateRow.Report),
            GatePresenter.rowsFor(GateBlockReason.StepCrashed, restoreAvailable = true),
        )
        assertEquals(GateFailureScreen.LowSpace, GatePresenter.screenFor(GateBlockReason.LowSpace))
        assertEquals(GateFailureScreen.SaveFailed, GatePresenter.screenFor(GateBlockReason.WriteRefused))
        assertEquals(GateFailureScreen.Failed, GatePresenter.screenFor(GateBlockReason.AuthorityChanged))
    }

    @Test
    fun `B-MIGR-010 Restore and Start fresh ask first, and only the confirmation acts`() = runTest {
        val h = Harness(this, GateInput.Blocked(GateBlockReason.StepCrashed, restoreAvailable = true))
        runCurrent()
        h.presenter.onRow(GateRow.StartFresh)
        runCurrent()
        assertEquals(GateRow.StartFresh, assertIs<GateUi.Failed>(h.ui).confirming)
        assertEquals(emptyList(), h.actions.calls)
        h.presenter.onCancelConfirm()
        runCurrent()
        assertNull(assertIs<GateUi.Failed>(h.ui).confirming)
        h.presenter.onRow(GateRow.Restore)
        h.presenter.onConfirm()
        runCurrent()
        assertEquals(listOf("restore"), h.actions.calls)
        h.presenter.onRow(GateRow.Report)
        h.presenter.onRow(GateRow.OpenStorage)
        runCurrent()
        assertEquals(listOf("restore", "report", "storage"), h.actions.calls)
    }

    @Test
    fun `B-MIGR-011 the details line carries the step and class only, and space is said in plain units`() = runTest {
        val blocked =
            GateInput.Blocked(GateBlockReason.LowSpace, neededBytes = 120L * 1024 * 1024, detail = "S0 · LowSpace")
        val h = Harness(this, blocked)
        runCurrent()
        val failed = assertIs<GateUi.Failed>(h.ui)
        assertEquals("S0 · LowSpace", failed.detail)
        assertEquals("120 MB", GatePresenter.formatBytes(failed.neededBytes!!))
        assertEquals("1 MB", GatePresenter.formatBytes(10))
        assertEquals("1.5 GB", GatePresenter.formatBytes(1536L * 1024 * 1024))
    }

    @Test
    fun `B-MIGR-006 the re-enter sheet is asked once, then each item keeps its own state`() = runTest {
        val home = GateReentry("p1", "Home server", GateReentryKind.Password)
        val gh = GateReentry("p1", "Home server", GateReentryKind.GitHubSignIn)
        val h = Harness(this, GateInput.Open(firstNativeLaunch = true, reenter = listOf(home, gh)))
        runCurrent()
        assertEquals(listOf(home, gh), assertIs<GateUi.Open>(h.ui).reenter)
        h.presenter.onReentryLater()
        runCurrent()
        assertEquals(emptyList(), assertIs<GateUi.Open>(h.ui).reenter)
        h.presenter.onReentryLater() // already closed: no second acknowledgement
        runCurrent()
        assertEquals(listOf("ack"), h.actions.calls)
    }

    /**
     * Codex review p12/migration #2: the GitHub row closed the sheet for good and opened the Servers page, which has
     * no GitHub sign-in. GitHub is signed in from a chat's Git page, so the row now says where, in the sheet, and
     * "Open chats" goes there without recording the sheet as answered (it comes back until the sign-in is done or the
     * person picks Later).
     */
    @Test
    fun `B-MIGR-006 a GitHub row says where to sign in again and opens the chats without answering the sheet`() = runTest {
        val gh = GateReentry("p1", "Home server", GateReentryKind.GitHubSignIn)
        val h = Harness(this, GateInput.Open(firstNativeLaunch = true, reenter = listOf(gh)))
        runCurrent()
        h.presenter.onReentry(gh)
        runCurrent()
        val guide = assertIs<GateUi.Open>(h.ui)
        assertEquals(GateEntry(gh), guide.entry)
        assertEquals(emptyList(), h.actions.calls)

        h.presenter.onReentryOpen()
        runCurrent()
        assertEquals(GateUi.Open(emptyList(), deferredNotice = false), h.ui)
        assertEquals(listOf("open:Home server:GitHubSignIn"), h.actions.calls) // no "ack": asked again next start
    }

    /**
     * Codex review p12/migration #2: the BYO set-up row opened the Servers page, which cannot set a BYO host up. This
     * version has no BYO set-up page, so the row says so plainly and the person goes back to the list.
     */
    @Test
    fun `B-MIGR-007 a BYO set-up row says plainly it is not in this version and opens nothing`() = runTest {
        val unnamed = GateReentry(null, null, GateReentryKind.Password)
        val byo = GateReentry("p3", "Laptop", GateReentryKind.ByoSetup)
        val h = Harness(this, GateInput.Open(firstNativeLaunch = true, reenter = listOf(unnamed, byo)))
        runCurrent()
        h.presenter.onReentry(byo)
        runCurrent()
        assertEquals(GateEntry(byo), assertIs<GateUi.Open>(h.ui).entry)
        h.presenter.onReentryOpen() // nothing to open for a BYO row
        h.presenter.onReentryBack()
        runCurrent()
        assertEquals(GateUi.Open(listOf(unnamed, byo), deferredNotice = false), h.ui)
        assertEquals(emptyList(), h.actions.calls)
    }

    /**
     * Migration proof 2026-11-03: a password row used to close the sheet and open the Servers page, which has no
     * password field (the only way out was remove and re-add, which gives the server a new id and orphans its
     * schedules and drafts). The row now asks for the password right in the sheet and saves it on the same server.
     */
    @Test
    fun `B-MIGR-006 a password row asks for the password in the sheet and saves it on the same server`() = runTest {
        val home = GateReentry("p1", "Home server", GateReentryKind.Password)
        val work = GateReentry("p2", "Work", GateReentryKind.Token)
        val h = Harness(this, GateInput.Open(firstNativeLaunch = true, reenter = listOf(home, work)))
        runCurrent()
        h.presenter.onReentry(home)
        runCurrent()
        val asking = assertIs<GateUi.Open>(h.ui)
        assertEquals(GateEntry(home), asking.entry)
        assertEquals(listOf(home, work), asking.reenter) // the sheet stays: nothing is acknowledged yet
        assertEquals(emptyList(), h.actions.calls)

        h.presenter.onReentrySubmit("") // an empty password is never saved
        runCurrent()
        assertEquals(emptyList(), h.actions.calls)

        h.presenter.onReentrySubmit("s3cret")
        runCurrent()
        val saved = assertIs<GateUi.Open>(h.ui)
        assertNull(saved.entry)
        assertEquals(listOf(work), saved.reenter) // the saved row leaves; the other one is still asked
        assertEquals(listOf("save:Home server:6"), h.actions.calls)

        h.presenter.onReentry(work)
        h.presenter.onReentrySubmit("tok")
        runCurrent()
        // The last row saved closes the sheet and records it, once.
        assertEquals(GateUi.Open(emptyList(), deferredNotice = false), h.ui)
        assertEquals(listOf("save:Home server:6", "save:Work:3", "ack"), h.actions.calls)
    }

    @Test
    fun `B-MIGR-006 a refused save keeps the field open with a plain error, and Save is single-flight`() = runTest {
        val home = GateReentry("p1", "Home server", GateReentryKind.Password)
        val h = Harness(this, GateInput.Open(firstNativeLaunch = true, reenter = listOf(home)))
        runCurrent()
        h.presenter.onReentry(home)
        h.actions.saveResult = false
        h.actions.saveHold = CompletableDeferred()
        h.presenter.onReentrySubmit("one")
        runCurrent()
        assertEquals(GateEntry(home, saving = true), assertIs<GateUi.Open>(h.ui).entry)
        h.presenter.onReentrySubmit("two") // while the first save runs: nothing
        runCurrent()
        h.actions.saveHold!!.complete(Unit)
        runCurrent()
        assertEquals(GateEntry(home, failed = true), assertIs<GateUi.Open>(h.ui).entry)
        assertEquals(listOf("save:Home server:3"), h.actions.calls)

        h.presenter.onReentryBack() // back to the list; the row is still there
        runCurrent()
        assertEquals(GateUi.Open(listOf(home), deferredNotice = false), h.ui)
        assertTrue("ack" !in h.actions.calls)
    }

    @Test
    fun `B-MIGR-006 Later closes the sheet and records it, with no item opened`() = runTest {
        val h = Harness(this, GateInput.Open(true, listOf(GateReentry("p2", "Work", GateReentryKind.Token))))
        runCurrent()
        h.presenter.onReentryLater()
        runCurrent()
        assertEquals(listOf("ack"), h.actions.calls)
        assertEquals(GateUi.Open(emptyList(), deferredNotice = false), h.ui)
    }

    /** No catalog row yet (02 §3.7 item 4; S2-GATEUI report, catalog requests). */
    @Test
    fun `02-3_7-4 the deferred-features notice shows while it is unacknowledged and is recorded once`() = runTest {
        val h = Harness(this, GateInput.Running("M6"))
        h.input.value = GateInput.Open(firstNativeLaunch = true, deferredDataFound = true)
        runCurrent()
        assertTrue(assertIs<GateUi.Open>(h.ui).deferredNotice)
        h.input.value = GateInput.Open(firstNativeLaunch = true, deferredDataFound = true, reenter = emptyList())
        runCurrent()
        assertEquals(1, h.actions.calls.count { it == "ackDeferred" })

        val none = Harness(this, GateInput.Open(firstNativeLaunch = true, deferredDataFound = false))
        runCurrent()
        assertFalse(assertIs<GateUi.Open>(none.ui).deferredNotice)
        assertEquals(emptyList(), none.actions.calls)
    }

    /**
     * Codex review p12/migration #5: the migration can finish in a background process (a widget or a scheduled
     * prompt started the app), which records the first open. The notice used to need `firstNativeLaunch`, so it never
     * showed. The runner keeps the deferred set only until it is acknowledged, so that set alone decides.
     */
    @Test
    fun `B-MIGR-009 the deferred-features notice still shows when the migration finished in a background start`() = runTest {
        val later = Harness(this, GateInput.Open(firstNativeLaunch = false, deferredDataFound = true))
        runCurrent()
        assertTrue(assertIs<GateUi.Open>(later.ui).deferredNotice)
        assertEquals(listOf("ackDeferred"), later.actions.calls)
    }
}
