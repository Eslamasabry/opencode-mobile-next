package io.github.eslamasabry.oc.feature.chats.presentation

/**
 * The Conversations home shows at most one notice, in the notice place under the header (owner, 2026-11-11: three
 * stacked notices, two of them limits nobody can act on). A notice earns its place only when the person can do
 * something about it or must know it to avoid a mistake. The declaration order of the kinds is the priority order:
 * the first active, actionable kind shows; closing it or resolving it lets the next one show.
 */
public enum class HomeNoticeKind(public val actionable: Boolean = true) {
    /** "Press back again to exit": transient, so it outranks everything while it lasts. */
    BackExit,

    /** An answer to a waiting question did not go through: the person must know to send it again. */
    AnswerFailure,

    /** An archive, delete or pin did not happen; the row is back in the list. */
    ArchiveFailure,

    /** A rename did not happen. */
    RenameFailure,

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

    /** "Plan limit reached": nothing to do from here. */
    AgentLimit(actionable = false),
}

public object HomeNotices {
    /** The one notice to show from [active], or null: the first actionable kind in priority order. */
    public fun shown(active: Set<HomeNoticeKind>): HomeNoticeKind? =
        HomeNoticeKind.entries.firstOrNull { it.actionable && it in active }

    /** The connections the home lists as not answering, except the one still opening (it has its own card). */
    public fun unreachable(state: ChatsUi): List<ChatsListSourceUi> =
        state.home.sources.filter { it.shown && it.unreachable && it.profile != state.home.opening?.profile }

    /** The servers whose list is only partly read and that have no line of their own. */
    public fun partial(state: ChatsUi): List<String> =
        state.incomplete.filterNot { name ->
            unreachable(state).any { it.name == name } || name == state.home.opening?.serverName
        }

    /** Every kind the home currently has something to say about. */
    public fun active(
        state: ChatsUi,
        appCondition: Boolean,
        replyOffer: Boolean,
        backExit: Boolean,
    ): Set<HomeNoticeKind> = buildSet {
        if (backExit) add(HomeNoticeKind.BackExit)
        if (state.answerFailure != null) add(HomeNoticeKind.AnswerFailure)
        if (state.archiveFailure != null) add(HomeNoticeKind.ArchiveFailure)
        if (state.renameFailure != null) add(HomeNoticeKind.RenameFailure)
        if (unreachable(state).isNotEmpty()) add(HomeNoticeKind.ServerUnreachable)
        if (partial(state).isNotEmpty()) add(if (state.refreshing) HomeNoticeKind.ListLoading else HomeNoticeKind.ListIncomplete)
        if (appCondition) add(HomeNoticeKind.AppCondition)
        if (state.phase != ChatsPhase.Opening) {
            if (state.agentNotices.restart) add(HomeNoticeKind.AgentRestart)
            state.agentNotices.lines.forEach {
                add(
                    when (it.kind) {
                        HomeAgentNoticeKind.SignedOut -> HomeNoticeKind.AgentSignedOut
                        HomeAgentNoticeKind.Stopped -> HomeNoticeKind.AgentStopped
                        HomeAgentNoticeKind.LimitReached -> HomeNoticeKind.AgentLimit
                    }
                )
            }
        }
        if (replyOffer) add(HomeNoticeKind.ReplyOffer)
        if (state.onlyProject != null) add(HomeNoticeKind.OnlyProject)
        if (state.brief?.unknown == true) add(HomeNoticeKind.BriefUnknown)
    }
}
