package io.github.eslamasabry.oc.feature.git.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.git.GitProblem

/** The one step that fixes a failed Send or Save (Flutter git_sheet.dart `_wayForward`). */
public enum class GitWay {
    /** "Sign in to GitHub": the send needs the app's GitHub sign-in. */
    SignIn,

    /** "Get latest": someone else sent first. */
    GetLatest,

    /** "Your name on saves": Git has no name yet. */
    SetIdentity,
}

/**
 * Flutter `_wayForward`: sign-in only where the app's own GitHub sign-in is what a push uses ([offersGitHubSignIn])
 * and there is a sign-in to start ([canSignIn]); null when the page can do nothing about the problem.
 */
public fun gitWayForward(problem: GitProblem, offersGitHubSignIn: Boolean, canSignIn: Boolean): GitWay? =
    when (problem) {
        GitProblem.AuthRequired,
        GitProblem.GitHubTokenRejected -> if (offersGitHubSignIn && canSignIn) GitWay.SignIn else null
        GitProblem.PushRejected -> GitWay.GetLatest
        GitProblem.IdentityMissing -> GitWay.SetIdentity
        else -> null
    }

/** A save whose send needs a sign-in says so: "Saved here, not sent yet. {place} needs you to sign in first." */
public fun gitSavedNeedsSignIn(problem: GitProblem, offersGitHubSignIn: Boolean, hasPlace: Boolean): Boolean =
    offersGitHubSignIn && hasPlace && (problem == GitProblem.AuthRequired || problem == GitProblem.GitHubTokenRejected)

/**
 * Sends one prompt to the conversation's agent through the chat's own send pipeline (Flutter `onAskAgent`:
 * "Ask the agent to fix the conflicts").
 */
public interface GitAskAgent {
    /** Whether this conversation has a send pipeline to ask through. */
    public fun available(ref: ConversationRef): Boolean

    public suspend fun ask(ref: ConversationRef, prompt: String): Outcome<Unit>
}

/** No agent to ask: the row is not offered. */
public class NoGitAskAgent : GitAskAgent {
    override fun available(ref: ConversationRef): Boolean = false

    override suspend fun ask(ref: ConversationRef, prompt: String): Outcome<Unit> =
        Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("gitAskAgent")))
}
