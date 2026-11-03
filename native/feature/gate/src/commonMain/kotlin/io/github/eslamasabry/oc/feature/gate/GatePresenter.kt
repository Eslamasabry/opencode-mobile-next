package io.github.eslamasabry.oc.feature.gate

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.github.eslamasabry.oc.engine.migration.slowAfter
import kotlinx.coroutines.launch

/**
 * Turns the gate input into what the gate draws, and the person's taps into [GateActions] calls
 * (plan 05 §8, 01 §9.7).
 *
 * - The 8 s clock runs while the app waits (Opening or Updating) and restarts only when a new wait begins, so a
 *   step change inside one run does not hide Try again.
 * - Every action is single-flight on this side too: a second tap while its call is running does nothing. In the
 *   runner, Try again during a run keeps waiting on the same run (§8.2).
 * - The re-enter sheet is asked once: closing it, choosing Later or saving its last row acknowledges it, and it does
 *   not come back. A password or token row of a saved server asks for it in the sheet and saves it on that same
 *   server. A GitHub row says where GitHub is signed in again (a chat's Git page) and "Open chats" goes there without
 *   answering the sheet; a BYO set-up row says this version cannot set it up. The deferred-features notice is
 *   acknowledged the first time it shows.
 *
 * [scope] is the owner's (the shell's ViewModel scope, or a test scope); time comes from its dispatcher.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
public class GatePresenter(
    private val input: StateFlow<GateInput>,
    private val actions: GateActions,
    private val scope: CoroutineScope,
) {
    private val confirming = MutableStateFlow<GateRow?>(null)
    private val sheet = MutableStateFlow(SheetState())
    private val running = mutableSetOf<Any>()
    private var noticeLatched = false
    private var noticeAcknowledged = false

    private val slow: StateFlow<Boolean> =
        input
            .map { it is GateInput.Closed || it is GateInput.Running }
            .distinctUntilChanged()
            .flatMapLatest { waiting ->
                if (!waiting) {
                    flowOf(false)
                } else {
                    slowAfter(SLOW_MS)
                }
            }.stateIn(scope, SharingStarted.Eagerly, false)

    public val ui: StateFlow<GateUi> =
        combine(input, slow, confirming, sheet) { i, s, c, sh -> reduce(i, s, c, sh) }
            .stateIn(scope, SharingStarted.Eagerly, reduce(input.value, false, null, SheetState()))

    private fun reduce(input: GateInput, slow: Boolean, confirming: GateRow?, sheet: SheetState): GateUi =
        when (input) {
            is GateInput.Closed -> GateUi.Opening(wordless = !input.localeKnown && !slow, offerRetry = slow)
            is GateInput.Running -> GateUi.Updating(offerRetry = slow)
            is GateInput.Blocked -> {
                val rows = rowsFor(input.reason, input.restoreAvailable, input.startFreshAvailable)
                GateUi.Failed(
                    screen = screenFor(input.reason),
                    rows = rows,
                    neededBytes = input.neededBytes,
                    detail = input.detail,
                    confirming = confirming?.takeIf { it in rows },
                )
            }
            is GateInput.Open -> {
                // The runner keeps the deferred set only until it is acknowledged, so the set alone means "not shown
                // yet". `firstNativeLaunch` is not asked: a migration finished in a background start (a widget, a
                // scheduled prompt) records the first open before any screen (Codex review p12/migration #5).
                if (input.deferredDataFound) latchNotice()
                if (sheet.closed) {
                    GateUi.Open(reenter = emptyList(), deferredNotice = noticeLatched)
                } else {
                    GateUi.Open(input.reenter - sheet.saved, deferredNotice = noticeLatched, entry = sheet.entry)
                }
            }
        }

    private fun latchNotice() {
        noticeLatched = true
        if (!noticeAcknowledged) {
            noticeAcknowledged = true
            once("notice") { actions.acknowledgeDeferredNotice() }
        }
    }

    /** A row on a failure screen, or Try again on a waiting screen. */
    public fun onRow(row: GateRow) {
        when (row) {
            GateRow.TryAgain -> once(row) { actions.retry() }
            GateRow.OpenStorage -> once(row) { actions.openStorageSettings() }
            GateRow.Report -> once(row) { actions.reportProblem() }
            GateRow.Restore, GateRow.StartFresh -> confirming.value = row
        }
    }

    /** The confirmation's action: runs what [GateUi.Failed.confirming] names. */
    public fun onConfirm() {
        when (confirming.value) {
            GateRow.Restore -> once(GateRow.Restore) { actions.restorePreUpdateData() }
            GateRow.StartFresh -> once(GateRow.StartFresh) { actions.startFresh() }
            else -> Unit
        }
        confirming.value = null
    }

    public fun onCancelConfirm() {
        confirming.value = null
    }

    /**
     * A re-enter row. A saved server's password or token is asked for right here; a GitHub or BYO row explains itself
     * here (the sheet stays open). Only a password or token row with no saved server behind it closes the sheet
     * (asked once) and opens the servers.
     */
    public fun onReentry(item: GateReentry) {
        if (sheet.value.closed) return
        if (item.typedHere() || item.kind == GateReentryKind.GitHubSignIn || item.kind == GateReentryKind.ByoSetup) {
            sheet.value = sheet.value.copy(entry = GateEntry(item))
            return
        }
        closeSheet()
        once("reentry") { actions.openReentry(item) }
    }

    /**
     * "Open chats" on a GitHub row: GitHub is signed in again from a chat's Git page. The sheet closes for now but is
     * not recorded as answered, so it comes back at the next start until the sign-in is done or Later is picked.
     */
    public fun onReentryOpen() {
        val item = sheet.value.entry?.item ?: return
        if (item.kind != GateReentryKind.GitHubSignIn) return
        sheet.value = SheetState(closed = true)
        once("reentry") { actions.openReentry(item) }
    }

    /** Save on the asking sheet. Empty input saves nothing; single-flight while a save runs. */
    public fun onReentrySubmit(secret: String) {
        val entry = sheet.value.entry ?: return
        if (secret.isEmpty() || entry.saving) return
        sheet.value = sheet.value.copy(entry = entry.copy(saving = true, failed = false))
        once("save") {
            val ok =
                try {
                    actions.saveReentry(entry.item, secret)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false // a store that throws is a refused save: the field stays with its plain error
                }
            val now = sheet.value
            if (now.entry?.item != entry.item) return@once
            if (!ok) {
                sheet.value = now.copy(entry = entry.copy(saving = false, failed = true))
                return@once
            }
            val saved = now.saved + entry.item
            sheet.value = now.copy(entry = null, saved = saved)
            val left = (input.value as? GateInput.Open)?.reenter.orEmpty() - saved
            if (left.isEmpty()) closeSheet()
        }
    }

    /** "Back to the list" on the asking sheet: the row stays, nothing is recorded. */
    public fun onReentryBack() {
        val now = sheet.value
        if (now.entry?.saving == true) return
        sheet.value = now.copy(entry = null)
    }

    /** "Sign in to these later", Close or the scrim. */
    public fun onReentryLater() {
        closeSheet()
    }

    private fun closeSheet() {
        if (sheet.value.closed) return
        sheet.value = SheetState(closed = true)
        once("ack") { actions.acknowledgeNotices() }
    }

    /** The re-enter sheet: closed for good, the row being typed, the rows already saved. */
    private data class SheetState(
        val closed: Boolean = false,
        val entry: GateEntry? = null,
        val saved: Set<GateReentry> = emptySet(),
    )

    /** A password or token of a saved server is typed in the sheet; GitHub and BYO set-up have their own pages. */
    private fun GateReentry.typedHere(): Boolean =
        profileId != null && (kind == GateReentryKind.Password || kind == GateReentryKind.Token)

    private fun once(key: Any, block: suspend () -> Unit) {
        if (!running.add(key)) return
        scope.launch {
            try {
                block()
            } finally {
                running.remove(key)
            }
        }
    }

    public companion object {
        /** 01 §9.7: after 8 s the waiting screens offer Try again. */
        public const val SLOW_MS: Long = 8_000

        /** Plan 05 §8.1 "Way out", in drawing order; Restore only while it is allowed (§9.4). */
        public fun rowsFor(
            reason: GateBlockReason,
            restoreAvailable: Boolean,
            startFreshAvailable: Boolean = true,
        ): List<GateRow> = rowsWithAllWays(reason, restoreAvailable).filter { startFreshAvailable || it != GateRow.StartFresh }

        private fun rowsWithAllWays(reason: GateBlockReason, restoreAvailable: Boolean): List<GateRow> {
            val rows =
                when (reason) {
                    GateBlockReason.LowSpace -> listOf(GateRow.TryAgain, GateRow.OpenStorage, GateRow.Report)
                    GateBlockReason.PrefsUnreadable -> listOf(GateRow.TryAgain, GateRow.StartFresh, GateRow.Report)
                    GateBlockReason.WriteRefused -> listOf(GateRow.TryAgain, GateRow.OpenStorage, GateRow.Report)
                    GateBlockReason.AuthorityChanged -> listOf(GateRow.Restore, GateRow.Report)
                    GateBlockReason.StepCrashed ->
                        listOf(GateRow.TryAgain, GateRow.Restore, GateRow.StartFresh, GateRow.Report)
                    // The published backup is never touched (§7.4 S0 step 4); Report a problem says so.
                    GateBlockReason.BackupInvalid -> listOf(GateRow.TryAgain, GateRow.Report)
                }
            return when {
                restoreAvailable -> rows
                // After the gate has opened once (or in a later migration) there is no R2: §8.1's last sentence.
                reason == GateBlockReason.AuthorityChanged ->
                    listOf(GateRow.TryAgain, GateRow.StartFresh, GateRow.Report)
                else -> rows - GateRow.Restore
            }
        }

        /** The needed space in the person's terms ("120 MB", "1.5 GB"); never a raw byte count. */
        public fun formatBytes(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb < 1024) {
                "${kotlin.math.ceil(mb).toLong().coerceAtLeast(1)} MB"
            } else {
                val gbTenths = kotlin.math.ceil(mb / 1024.0 * 10).toLong()
                "${gbTenths / 10}.${gbTenths % 10} GB"
            }
        }

        public fun screenFor(reason: GateBlockReason): GateFailureScreen =
            when (reason) {
                GateBlockReason.LowSpace -> GateFailureScreen.LowSpace
                GateBlockReason.WriteRefused -> GateFailureScreen.SaveFailed
                else -> GateFailureScreen.Failed
            }
    }
}
