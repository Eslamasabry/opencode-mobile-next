package io.github.eslamasabry.oc.feature.chat.composer.presentation

import io.github.eslamasabry.oc.feature.chat.world.DraftFacts

/**
 * P-003: what the message field shows around a send. The words leave the field at the tap (they are on their bubble
 * now); the draft owner keeps them until the server acknowledges the send and then clears the draft. If the send
 * fails or ends uncertain, the words come back so the person can act on them. The "may already have been sent"
 * error is for an uncertain send only, never for a normal send that is still on its way.
 */
public object SendFieldRules {
    /** What to do with words that left the field at the tap while their send is followed. */
    public enum class AfterSend {
        /** The send is still on its way: the field stays empty. */
        Wait,

        /** The send failed or ended uncertain and the draft still holds the words: put them back. */
        Restore,

        /** The draft was cleared or changed (acknowledged, or the person typed): stop following it. */
        Done,
    }

    /** The "This text may already have been sent" error shows only when the send is uncertain. */
    public fun showsAlreadySent(draft: DraftFacts): Boolean = draft.sendBlocked && !draft.sendInFlight

    /**
     * A send that did not go puts the words back, unless the person typed in the meantime ([serialAtTap] differs from
     * [serialNow]) or the field is no longer empty.
     */
    public fun restoreAfterFailedSend(field: String, serialAtTap: Long, serialNow: Long): Boolean =
        field.isEmpty() && serialAtTap == serialNow

    /**
     * Follows an admitted send. [sent] is what left the field; [seenInFlight] is true once the draft reported the
     * send on its way (its state can lag the tap by a frame, so "not in flight" means nothing before that);
     * [userEdited] is true when the person typed since the tap.
     */
    public fun afterSend(sent: String, seenInFlight: Boolean, draft: DraftFacts, userEdited: Boolean): AfterSend =
        when {
            userEdited -> AfterSend.Done
            draft.text.trim() != sent.trim() -> AfterSend.Done
            draft.sendInFlight -> AfterSend.Wait
            // Uncertain: the draft says so itself, whether or not the in-flight state was seen.
            draft.sendBlocked -> AfterSend.Restore
            seenInFlight -> AfterSend.Restore
            else -> AfterSend.Wait
        }
}
