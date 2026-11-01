package io.github.eslamasabry.oc.engine.delivery.receipts

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

/** What one dispatch proved. Never "completed". */
public enum class DispatchProof { Confirmed, Uncertain }

/**
 * At most one dispatch per durable command id; a retry only reads the server (port of `CommandReceiptController`,
 * rows B-STATE-008 and B-STATE-009).
 */
public class CommandReceiptController(private val journal: PendingCommandJournal) {
    /**
     * Claims [receipt] and only then calls [dispatch] with the wire id. A command already in the journal is never
     * dispatched again: the stored receipt comes back (if it belongs to the same scope, session and tab; another tab or
     * scope cannot borrow it). A dispatch that did not prove admission records `uncertain`: no transport status proves a
     * mutation did not happen.
     */
    public suspend fun send(receipt: CommandReceipt, dispatch: suspend (receiptId: String) -> DispatchProof): Outcome<CommandReceipt> {
        if (receipt.state != CommandReceiptState.Sent) return Failure.Refused(RefusalReason.DispatchUncertain).failed()
        val claimed = when (val c = journal.claim(receipt)) {
            is Outcome.Ok -> c.value
            is Outcome.Failed -> return c
        }
        if (!claimed) {
            val old = when (val r = journal.read()) {
                is Outcome.Ok -> r.value.single { it.commandId == receipt.commandId }
                is Outcome.Failed -> return r
            }
            if (old.scope != receipt.scope || old.sessionId != receipt.sessionId || old.tabId != receipt.tabId) {
                return Failure.Refused(RefusalReason.DispatchUncertain).failed()
            }
            return old.ok()
        }
        val outcome = when (dispatch(receipt.receiptId)) {
            DispatchProof.Confirmed -> receipt.withState(CommandReceiptState.Confirmed)
            DispatchProof.Uncertain -> receipt.withState(CommandReceiptState.Uncertain)
        }
        // A refused update leaves `sent` on disk, which reads as unconfirmed: never a resend.
        journal.update(outcome)
        return outcome.ok()
    }

    /**
     * Looks the command up; never sends. Confirmed and failed receipts come back unchanged. [lookup] answers true only
     * when the server shows the admission; false, a failure or an absent capability keeps it `uncertain`.
     */
    public suspend fun retry(commandId: String, lookup: suspend (CommandReceipt) -> Boolean): Outcome<CommandReceipt> {
        val receipt = when (val r = journal.read()) {
            is Outcome.Ok -> r.value.singleOrNull { it.commandId == commandId } ?: return Failure.NotFound().failed()
            is Outcome.Failed -> return r
        }
        if (receipt.state.terminal) return receipt.ok()
        val found = lookup(receipt)
        val outcome = receipt.withState(if (found) CommandReceiptState.Confirmed else CommandReceiptState.Uncertain)
        return when (val u = journal.update(outcome)) {
            is Outcome.Ok -> outcome.ok()
            is Outcome.Failed -> u
        }
    }
}
