package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** How a saved server answers, as the switcher's row says it (F: lib/ui/widgets/server_switcher_sheet.dart:73-95). */
public enum class SwitcherServerState {
    /** The server in use answers. */
    Connected,

    /** The server in use is still opening. */
    Connecting,

    /** It was tried and does not answer now. */
    NotAnswering,

    /** Saved, not in use: tapping it switches to it. */
    Saved,
}

/** One row of the server switcher: a saved server and what the app knows of it now. */
public data class SwitcherServerUi(
    val profile: ProfileId,
    val name: String,
    /** New conversations start here (the server behind the home now). */
    val current: Boolean,
    val onThisPhone: Boolean,
    val state: SwitcherServerState,
    /** Conversations working there now (sub-agents count as their parent's work). */
    val working: Int = 0,
    /** Conversations there that wait on the person. */
    val needsYou: Int = 0,
)

/** The one-tap switcher the home's server pill opens. */
public data class ServerSwitcherUi(
    val servers: ImmutableList<SwitcherServerUi> = persistentListOf(),
    /** The server being switched to; the rows wait while it opens. */
    val switching: ProfileId? = null,
    /** Why the last switch did not happen; the sheet stays open to say it. */
    val failure: Failure? = null,
)

/** Plain rules for the switcher's rows; `ServerSwitcherRulesTest` drives them. */
public object ServerSwitcherRules {
    /**
     * F: server_switcher_sheet.dart:145-160,312-350: the server in use first, then the other saved servers most
     * urgent first (waiting on the person, then working, then not answering, then the rest, each as saved), and this
     * phone's own server last unless it is the one in use.
     */
    public fun rows(extra: ChatsHomeExtraState, items: List<ChatFeedItem>): ImmutableList<SwitcherServerUi> {
        val top = items.filter { !it.isSubagent }
        val working = top.filter { it.status == ChatStatus.Running }.groupingBy { it.profileId }.eachCount()
        val waiting =
            top.filter { it.status == ChatStatus.NeedsYou || it.permission != null }.groupingBy { it.profileId }.eachCount()
        val opening = extra.opening
        val rows =
            extra.sources.map { source ->
                val state =
                    when {
                        source.main && opening != null && (opening.profile == null || opening.profile == source.profile) ->
                            if (opening.phase == ChatsOpeningPhase.Failed) SwitcherServerState.NotAnswering
                            else SwitcherServerState.Connecting
                        source.unreachable -> SwitcherServerState.NotAnswering
                        source.main -> SwitcherServerState.Connected
                        else -> SwitcherServerState.Saved
                    }
                SwitcherServerUi(
                    profile = source.profile,
                    name = source.name,
                    current = source.main,
                    onThisPhone = source.onThisPhone,
                    state = state,
                    working = if (state == SwitcherServerState.NotAnswering) 0 else working[source.profile] ?: 0,
                    needsYou = if (state == SwitcherServerState.NotAnswering) 0 else waiting[source.profile] ?: 0,
                )
            }
        val current = rows.filter { it.current }
        val others = rows.filter { !it.current }
        // sortedBy is stable: equal ranks keep the saved order.
        val ranked = others.filterNot { it.onThisPhone }.sortedBy(::rank)
        val phone = others.filter { it.onThisPhone }
        return (current + ranked + phone).toImmutableList()
    }

    private fun rank(row: SwitcherServerUi): Int =
        when {
            row.needsYou > 0 -> 0
            row.working > 0 -> 1
            row.state == SwitcherServerState.NotAnswering -> 2
            else -> 3
        }
}
