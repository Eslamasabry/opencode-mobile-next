package io.github.eslamasabry.oc.ui.shell.gate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Turns the gate input into what the gate draws, and the person's taps into [GateActions] calls (plan 05 §8, 01 §9.7).
 *
 * - The 8 s clock runs while the app waits (Opening or Updating) and restarts only when a new wait begins, so a
 *   step change inside one run does not hide Try again.
 * - Every action is single-flight on this side too: a second tap while its call is running does nothing. In the
 *   runner, Try again during a run keeps waiting on the same run (§8.2).
 * - The re-enter sheet is asked once: closing it, choosing Later or opening a row acknowledges it, and it does not
 *   come back. The deferred-features notice is acknowledged the first time it shows.
 *
 * [scope] is the owner's (the shell's ViewModel scope, or a test scope); time comes from its dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class GatePresenter(
    private val input: StateFlow<GateInput>,
    private val actions: GateActions,
    private val scope: CoroutineScope,
) {
    private val confirming = MutableStateFlow<GateRow?>(null)
    private val sheetClosed = MutableStateFlow(false)
    private val running = mutableMapOf<Any, Job>()
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
                    flow {
                        emit(false)
                        delay(SLOW_MS)
                        emit(true)
                    }
                }
            }.stateIn(scope, SharingStarted.Eagerly, false)

    public val ui: StateFlow<GateUi> =
        combine(input, slow, confirming, sheetClosed) { i, s, c, closed -> reduce(i, s, c, closed) }
            .stateIn(scope, SharingStarted.Eagerly, reduce(input.value, false, null, false))

    private fun reduce(input: GateInput, slow: Boolean, confirming: GateRow?, sheetClosed: Boolean): GateUi =
        when (input) {
            is GateInput.Closed -> GateUi.Opening(wordless = !input.localeKnown && !slow, offerRetry = slow)
            is GateInput.Running -> GateUi.Updating(offerRetry = slow)
            is GateInput.Blocked -> {
                val rows = rowsFor(input.reason, input.restoreAvailable)
                GateUi.Failed(
                    screen = screenFor(input.reason),
                    rows = rows,
                    neededBytes = input.neededBytes,
                    detail = input.detail,
                    confirming = confirming?.takeIf { it in rows },
                )
            }
            is GateInput.Open -> {
                if (input.firstNativeLaunch && input.deferredDataFound) latchNotice()
                GateUi.Open(reenter = if (sheetClosed) emptyList() else input.reenter, deferredNotice = noticeLatched)
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

    /** A re-enter row: closes the sheet (asked once) and opens what the row names. */
    public fun onReentry(item: GateReentry) {
        closeSheet()
        once("reentry") { actions.openReentry(item) }
    }

    /** "Sign in to these later", Close or the scrim. */
    public fun onReentryLater() {
        closeSheet()
    }

    private fun closeSheet() {
        if (sheetClosed.value) return
        sheetClosed.value = true
        once("ack") { actions.acknowledgeNotices() }
    }

    private fun once(key: Any, block: suspend () -> Unit) {
        if (running[key]?.isActive == true) return
        running[key] = scope.launch { block() }
    }

    public companion object {
        /** 01 §9.7: after 8 s the waiting screens offer Try again. */
        public const val SLOW_MS: Long = 8_000

        /** Plan 05 §8.1 "Way out", in drawing order; Restore only while it is allowed (§9.4). */
        public fun rowsFor(reason: GateBlockReason, restoreAvailable: Boolean): List<GateRow> {
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
