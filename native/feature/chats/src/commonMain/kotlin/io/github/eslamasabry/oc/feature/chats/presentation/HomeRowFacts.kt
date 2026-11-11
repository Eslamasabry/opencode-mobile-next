package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.ui.routes.ChatsRoute

/** H6: which word a finished conversation's row carries (Flutter `chats_home_screen.dart:905`, `chat_feed.dart:52`). */
public object HomeRowStatusRules {
    /**
     * An idle conversation: nothing to say once it was opened; "Stopped" when the person pressed Stop on the run
     * that ended it (OpenCode 2 reports a stop as a normal finish, so only this app's memory knows); else "Done".
     */
    public fun idle(finishedUnseen: Boolean, stoppedByPerson: Boolean): RowStatus =
        when {
            !finishedUnseen -> RowStatus.None
            stoppedByPerson -> RowStatus.Stopped
            else -> RowStatus.Done
        }

    /** H4: a row that opens a new conversation says so ("Can't reopen") unless it is working or failed. */
    public fun saysCantReopen(row: ConversationRowUi): Boolean =
        row.needsNewChat &&
            (row.status == RowStatus.None || row.status == RowStatus.Done || row.status == RowStatus.Stopped)
}

/** The question "Start a new conversation?" for one agent conversation that cannot be reopened. */
public data class StartNewUi(
    val ref: ConversationRef,
    val agentId: String,
    val agentName: String?,
    /** The old conversation's folder: the new one starts there. */
    val directory: String? = null,
)

/** H4: tapping an old agent conversation the helper cannot resume (Flutter `chats_home_screen.dart:186-206`). */
public object AgentResumeRules {
    /** The question to ask for [item], or null when it just opens. */
    public fun ask(item: ChatFeedItem?, agentName: String?): StartNewUi? =
        item?.takeIf { it.needsNewChat }?.let { StartNewUi(it.ref, it.agentId, agentName, it.directory?.takeIf { dir -> dir.isNotBlank() }) }

    /**
     * Where "Start new conversation" goes: the new-conversation form on the same server with the same agent chosen.
     * It starts in the old conversation's folder (the form falls back to its own folder when that is gone). The old
     * conversation is left as it is.
     */
    public fun route(ask: StartNewUi): ChatsRoute.New =
        ChatsRoute.New(
            profile = ask.ref.profileId,
            phoneAgent = agentChoiceId(ask.agentId),
            directory = ask.directory,
        )

    /** The agents list names an agent by its own id; a conversation carries its runtime's provider id. */
    internal fun agentChoiceId(providerId: String): String =
        AgentCatalog.builtIn.agents.firstOrNull { it.providerId == providerId }?.id?.raw ?: providerId
}
