package io.github.eslamasabry.oc.feature.chats.presentation

/**
 * The Conversations home shows at most one notice, in the notice place under the header (owner, 2026-11-11: three
 * stacked notices, two of them limits nobody can act on). A notice earns its place only when the person can do
 * something about it or must know it to avoid a mistake. The declaration order of the kinds is the priority order:
 * the first active, actionable kind shows; closing it or resolving it lets the next one show.
 *
 * "Press back again to exit" is not a notice: it is a two-second hint that borrows the status line and hides nothing
 * (a notice that vanished and came back on every Back would make the list jump).
 */
public enum class HomeNoticeKind(public val actionable: Boolean = true) {
    /**
     * The server the home is opening failed (its card: Try again, Start, Restart, Change server), or New conversation
     * was tapped while it still opens. The whole page waits on it, so it leads, and nothing else says it twice (the
     * "app was closed" notice about the same server waits until it has opened).
     */
    ServerOpening,

    /** An answer to a waiting question did not go through: the person must know to send it again. */
    AnswerFailure,

    /** An archive, delete or pin did not happen; the row is back in the list. */
    ArchiveFailure,

    /** A rename did not happen. */
    RenameFailure,

    /** The person stopped This phone's OpenCode: "You stopped OpenCode on this phone", with Start OpenCode. */
    PhoneStopped,

    /** A connection that did not answer, with Try again. */
    ServerUnreachable,

    /** Part of the list could not be read, with Try again. */
    ListIncomplete,

    /** The app was closed, the background connection paused, the phone is hot, a crash report is on offer. */
    AppCondition,

    /** A phone agent lost its sign-in, with Sign in. */
    AgentSignedOut,

    /** A phone agent stopped in the background, with Resume. */
    AgentStopped,

    /**
     * A phone agent reached its plan limit: "try again later", with the agent's page, where its plan and usage are
     * (Flutter keeps the line on its home, agent_notices.dart:124; New conversation does not say it, so without it the
     * person learns only when a conversation fails).
     */
    AgentLimit,

    /** Clearing sign-ins needs the app closed and reopened. */
    AgentRestart,

    /** The one-time question about notifying when a reply is ready. */
    ReplyOffer,

    // Limits and progress the person cannot act on never show.
    /** "Loading N conversations": the refresh indicator already says it. */
    ListLoading(actionable = false),

    /** "This server can't list all projects": the projects chip names the project instead. */
    OnlyProject(actionable = false),

    /** "Can't tell what's new on this server": nothing to do; no line. */
    BriefUnknown(actionable = false),
}

public object HomeNotices {
    /** The one notice to show from [active], or null: the first actionable kind in priority order. */
    public fun shown(active: Set<HomeNoticeKind>): HomeNoticeKind? =
        HomeNoticeKind.entries.firstOrNull { it.actionable && it in active }

    /**
     * The connections the home lists as not answering, except the one still opening (it has its own card) and a server
     * on this phone that the person runs themselves ([ChatsListSourceUi.selfRun]): that one is off, not broken, unless
     * it is the server new conversations start on. A source the app itself is starting (This phone's server, the
     * phone's agents: [ChatsListSourceUi.starting], which covers an install or a restart of the helper in progress)
     * is waiting, not failing: it never reads "didn't start" beside its own "Starting..." line.
     */
    public fun unreachable(state: ChatsUi): List<ChatsListSourceUi> =
        state.home.sources.filter {
            it.shown && it.unreachable && !it.stoppedByPerson && it.starting == null &&
                it.profile != state.home.opening?.profile &&
                !(it.selfRun && !it.main)
        }

    /**
     * This phone's own OpenCode that the person stopped, except the one still opening (its card says it): the
     * person's stop is a state with Start OpenCode, never "isn't answering".
     */
    public fun phoneStopped(state: ChatsUi): List<ChatsListSourceUi> =
        state.home.sources.filter {
            it.shown && it.stoppedByPerson && it.profile != state.home.opening?.profile
        }

    /**
     * The servers whose list is only partly read and that have no line of their own. A source the app itself is
     * starting (This phone's server, the phone's agents) is not "couldn't load": its saved rows stand in while it
     * starts and the chip says it is starting (issue 147: after an app update "This phone couldn't load · Try again"
     * showed for about 25 s of an ordinary start).
     */
    public fun partial(state: ChatsUi): List<String> =
        state.incomplete.filterNot { name ->
            unreachable(state).any { it.name == name } ||
                name == state.home.opening?.serverName ||
                state.home.sources.any {
                    (it.starting != null || it.stoppedByPerson || (it.selfRun && it.unreachable)) && it.name == name
                }
        }

    /** Every kind the home currently has something to say about. */
    public fun active(
        state: ChatsUi,
        appCondition: Boolean,
        replyOffer: Boolean,
    ): Set<HomeNoticeKind> = buildSet {
        // The same two cases the screen draws: the failure card, or the "still opening" line after New conversation.
        state.home.opening?.let {
            if (it.phase == ChatsOpeningPhase.Failed || state.newBlocked || state.newWaiting) add(HomeNoticeKind.ServerOpening)
        }
        if (state.answerFailure != null) add(HomeNoticeKind.AnswerFailure)
        if (state.archiveFailure != null) add(HomeNoticeKind.ArchiveFailure)
        if (state.renameFailure != null) add(HomeNoticeKind.RenameFailure)
        if (phoneStopped(state).isNotEmpty()) add(HomeNoticeKind.PhoneStopped)
        if (unreachable(state).isNotEmpty()) add(HomeNoticeKind.ServerUnreachable)
        if (partial(state).isNotEmpty()) add(if (state.refreshing) HomeNoticeKind.ListLoading else HomeNoticeKind.ListIncomplete)
        if (appCondition) add(HomeNoticeKind.AppCondition)
        if (state.phase != ChatsPhase.Opening) {
            if (state.agentNotices.restart) add(HomeNoticeKind.AgentRestart)
            state.agentNotices.lines.forEach { add(agentKind(it.kind)) }
        }
        if (replyOffer) add(HomeNoticeKind.ReplyOffer)
        if (state.onlyProject != null) add(HomeNoticeKind.OnlyProject)
        if (state.brief?.unknown == true) add(HomeNoticeKind.BriefUnknown)
    }

    /** The home's kind for one phone-agent line. */
    public fun agentKind(kind: HomeAgentNoticeKind): HomeNoticeKind =
        when (kind) {
            HomeAgentNoticeKind.SignedOut -> HomeNoticeKind.AgentSignedOut
            HomeAgentNoticeKind.Stopped -> HomeNoticeKind.AgentStopped
            HomeAgentNoticeKind.LimitReached -> HomeNoticeKind.AgentLimit
        }
}
