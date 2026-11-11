package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId

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
}

@ContributesTo(AppScope::class)
public interface ChatPhoneServerBindings {
    @Multibinds(allowEmpty = true) public val chatPhoneServers: Set<ChatPhoneServer>
}
