package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

/**
 * G6 (F: lib/ui/screens/agents/agent_notices.dart): what the home says about the phone's agents. Only an agent
 * New conversation offers is named here, so a sign-in line never leads to an agent the person cannot start.
 */
public enum class HomeAgentNoticeKind {
    /** The agent lost its sign-in: "{agent} signed out", with "Sign in to {agent}". */
    SignedOut,

    /** The agent stopped in the background: "{agent} stopped in the background", with "Resume {agent}". */
    Stopped,

    /** The plan's limit is reached: one quiet line. */
    LimitReached,
}

public data class HomeAgentNotice(
    val agentId: String,
    /** The agent's own name ("Claude Code"), a proper noun in every language. */
    val agentName: String,
    val kind: HomeAgentNoticeKind,
)

public data class HomeAgentNotices(
    /** Clearing saved sign-ins needs the app closed and opened again (F: `agentsRestartLine`). */
    val restart: Boolean = false,
    val lines: ImmutableList<HomeAgentNotice> = persistentListOf(),
) {
    val any: Boolean
        get() = restart || lines.isNotEmpty()
}

/** Where the home reads its agent notices from; bound by the app over the phone's agents. */
public interface HomeAgentNoticeSource {
    public val notices: StateFlow<HomeAgentNotices>
}

@ContributesTo(AppScope::class)
public interface HomeAgentNoticeBindings {
    @Multibinds(allowEmpty = true)
    public val homeAgentNoticeSources: Set<HomeAgentNoticeSource>
}

/** What a waiting permission's row says while its answer waits out the undo window (F: `DelayedAnswers.heldLabel`). */
public enum class HomeHeldReply { Allowed, Rejected, AlwaysAllowed }
