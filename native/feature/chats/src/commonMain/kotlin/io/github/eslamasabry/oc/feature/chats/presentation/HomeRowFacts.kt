package io.github.eslamasabry.oc.feature.chats.presentation

/** H6: which word a finished conversation's row carries (Flutter `chats_home_screen.dart:905`, `chat_feed.dart:52`). */
public object HomeRowStatusRules {
    /**
     * An idle conversation: "Stopped" when the person pressed Stop on the run that ended it, even when they did it
     * in the open chat (that read mark clears the server watermark, but this app's memory of the Stop stays until
     * the next message, as Flutter keeps its local finished mark until the chat is opened again; OpenCode 2
     * reports a stop as a normal finish, so only this memory knows); "Done" when a run finished after the person
     * last read it; else nothing.
     */
    public fun idle(finishedUnseen: Boolean, stoppedByPerson: Boolean, interrupted: Boolean = false): RowStatus =
        when {
            stoppedByPerson -> RowStatus.Stopped
            interrupted -> RowStatus.Interrupted
            finishedUnseen -> RowStatus.Done
            else -> RowStatus.None
        }
}
