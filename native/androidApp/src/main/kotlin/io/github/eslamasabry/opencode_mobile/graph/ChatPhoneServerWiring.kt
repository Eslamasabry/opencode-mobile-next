package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource

/*
 * S24-CHAT story 1: the chat's connection line restarts this phone's OpenCode through the phone
 * server's one path (`PhoneServerSource.restart`: stop, then the manual start; no recovery attempt
 * is spent). Only the profile the built-in server belongs to (`oc.builtinServerOwner`) is "on this
 * phone".
 */
@ContributesIntoSet(AppScope::class, binding = binding<ChatPhoneServer>())
@SingleIn(AppScope::class)
@Inject
class AndroidChatPhoneServer(
    private val stores: PreviewStores,
    private val server: PhoneServerSource,
    private val agentHost: Lazy<AppLocalAgentHostStarter>,
) : ChatPhoneServer {
    override fun runsHere(profile: ProfileId): Boolean =
        (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == profile.raw

    /** Starts this phone's OpenCode (the This phone page's own start path), e.g. after the person stopped it. */
    suspend fun start(profile: ProfileId): Outcome<Unit> =
        if (runsHere(profile)) server.start()
        else Outcome.Failed(Failure.Unavailable(FeatureId("startPhoneServer")))

    /** The saved "Phone agents" server (`<owner>-agents`), whose helper the app starts. */
    override fun isAgentHelper(profile: ProfileId): Boolean =
        PhoneAgentRules.isHelperProfile(
            profile.raw,
            (stores.prefs.get("oc.phoneAgentOwner.${profile.raw}") as? PrefValue.Str)?.value,
        )

    /** The same path a fresh connection of this server takes ([AppLocalAgentHostStarter.ensureHelper]). */
    override suspend fun startAgentHelper(profile: ProfileId): Outcome<Unit> =
        if (isAgentHelper(profile)) agentHost.value.ensureHelper(profile)
        else Outcome.Ok(Unit)

    override suspend fun restart(profile: ProfileId): Outcome<Unit> =
        if (runsHere(profile)) server.restart()
        else Outcome.Failed(Failure.Unavailable(FeatureId("restartPhoneServer")))
}
