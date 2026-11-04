package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId

/** What the person can do on the request slot. Every action names its request (rule 15). */
public interface ChatRequestActions {
    /** The card is on screen (starts its age once). */
    public fun shown(id: RequestId)

    public fun allowOnce(id: RequestId)

    public fun reject(id: RequestId)

    /**
     * The quiet Always allow action: opens the one confirm that names what and where; sends nothing
     * yet.
     */
    public fun askAlwaysAllow(id: RequestId)

    /** Confirmed: answers "always" through the same held path, if the request still waits. */
    public fun confirmAlwaysAllow()

    /** Cancel or dismissal of the confirm: nothing is sent. */
    public fun cancelAlwaysAllow()

    /** Declines only the repeated invitation; the request keeps waiting for its ordinary answer. */
    public fun keepAsking(id: RequestId)

    /** A one-answer question: the tapped option answers it. */
    public fun choose(id: RequestId, option: String)

    /** "Something else": opens the field (the typed text is kept as this request's draft). */
    public fun openOther(id: RequestId)

    public fun editOther(id: RequestId, text: String)

    public fun sendOther(id: RequestId)

    /** A longer question: opens the request sheet (the sheet itself is S3-REQFORMS). */
    public fun openSheet(id: RequestId)

    /** Inside the 3 s hold only. */
    public fun undo(id: RequestId)

    /** After "Not accepted": sends the same answer again. */
    public fun retry(id: RequestId)
}
