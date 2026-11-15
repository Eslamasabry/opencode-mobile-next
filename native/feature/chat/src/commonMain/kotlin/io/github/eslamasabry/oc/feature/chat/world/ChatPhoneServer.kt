package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * S24-CHAT story 1 (Flutter `connectionKitStatus` `serverOnThisPhone` / `onRestartServer`): the
 * OpenCode server that runs on this phone, as the chat's connection line needs it. The Android app
 * binds it over the phone server's one start path; without a binding (desktop, tests) no
 * conversation's server is on this phone and Restart is never offered.
 */
public interface ChatPhoneServer {
    /** True when [profile] is the server this phone runs (the built-in Linux one). */
    public fun runsHere(profile: ProfileId): Boolean

    /** Stop then start it again; a person's restart spends no recovery attempt. */
    public suspend fun restart(profile: ProfileId): Outcome<Unit>

    /** True when [profile] is the saved "Phone agents" server, whose helper the app starts (Flutter `recoverAgentBackend`). */
    public fun isAgentHelper(profile: ProfileId): Boolean = false

    /** Makes sure that helper runs and answers, starting it when it does not. Ok for any other server. */
    public suspend fun startAgentHelper(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

    /**
     * What this phone's own server is doing for [profile] that a conversation should wait for: starting, or stopped
     * for the OpenCode update (the automatic one included). [PhoneServerWait.None] for any other server, and while
     * nothing is in progress.
     */
    public fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = NotWaiting

    /**
     * Issue 147: true when [ref] is a phone agent's conversation the agents helper no longer holds and cannot take up
     * again where it stopped (after a restart, for an agent whose own resume is not proven, or one that already failed
     * to resume): it opens to read what it had, and continuing starts a new conversation. False when it continues in
     * place; null when unknown (another server, or no record of it yet).
     */
    public fun needsNewSession(ref: ConversationRef): Boolean? = null
}

/** Why this phone's server does not answer yet, as far as the app that runs it knows. */
public enum class PhoneServerWait {
    None,
    Starting,
    Updating,

    /** The phone's agents (Claude Code and the others) are being updated; their helper is down meanwhile. */
    UpdatingAgents,

    /** A phone agent needs the person to sign in; its conversations wait for that and say so. */
    AgentNeedsSignIn,
}

private val NotWaiting: StateFlow<PhoneServerWait> = MutableStateFlow(PhoneServerWait.None)

@ContributesTo(AppScope::class)
public interface ChatPhoneServerBindings {
    @Multibinds(allowEmpty = true) public val chatPhoneServers: Set<ChatPhoneServer>
}
