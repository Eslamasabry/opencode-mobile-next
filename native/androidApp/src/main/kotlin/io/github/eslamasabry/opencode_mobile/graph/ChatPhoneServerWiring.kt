package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.feature.chat.world.ChatConnectionOpener
import io.github.eslamasabry.oc.feature.chat.world.ChatPhoneServer
import io.github.eslamasabry.oc.feature.chat.world.PhoneServerWait
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.RecoveryFacts
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import java.util.concurrent.ConcurrentHashMap

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
    private val agentUpdate: PhoneAgentUpdateStatus,
    private val phoneAgents: Lazy<PhoneAgents>,
    private val appScope: AppCoroutineScope,
    private val registry: Lazy<ConnectionRegistry>,
    private val folders: Lazy<PhoneFolderPreflight>,
    /** The home's start loop of the agents' helper ([PreviewRuntime.wantHelper]): one start path for every page. */
    private val runtime: Lazy<PreviewRuntime>,
    /** The one stage source (what the start is really doing now). */
    private val stages: LaunchStages,
) : ChatPhoneServer {
    override fun runsHere(profile: ProfileId): Boolean =
        (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == profile.raw

    /** Starts this phone's OpenCode (the This phone page's own start path), e.g. after the person stopped it. */
    override suspend fun start(profile: ProfileId): Outcome<Unit> =
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

    override fun wantAgentHelper(profile: ProfileId) {
        if (isAgentHelper(profile)) runtime.value.wantHelper(profile, evenHidden = true)
    }

    /**
     * Starting (the one start path, or the crash recovery about to start it) or stopped for the OpenCode update (the
     * automatic one included): a conversation of this phone waits and says so instead of failing.
     */
    private val wait: StateFlow<PhoneServerWait> by lazy {
        server.facts.map(::waitOf).distinctUntilChanged().stateIn(appScope, SharingStarted.Eagerly, waitOf(server.facts.value))
    }

    /**
     * The phone's agents are being updated (the automatic update's install runs): their helper is down or about to be
     * replaced, so an agent conversation waits and says so instead of showing a saved list or a failure.
     */
    private val agentsWait: StateFlow<PhoneServerWait> by lazy {
        // Updating wins; otherwise a phone agent that needs a sign-in is said, so a conversation never reads only
        // "Connecting" for something the person has to do.
        combine(agentUpdate.decision, phoneAgents.value.state) { decision, agents ->
                when {
                    decision.step == AgentUpdateStep.Updating -> PhoneServerWait.UpdatingAgents
                    agents.agents.any { it.status == PhoneAgentStatus.SignInNeeded && !it.signInCheckFailed } ->
                        PhoneServerWait.AgentNeedsSignIn
                    else -> PhoneServerWait.None
                }
            }
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, PhoneServerWait.None)
    }

    override fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> =
        when {
            runsHere(profile) -> wait
            isAgentHelper(profile) -> agentsWait
            else -> NOT_WAITING
        }

    /** The real current stage of this server's start ([LaunchStages]); null for any other server. */
    override fun stage(profile: ProfileId): StateFlow<io.github.eslamasabry.oc.feature.chat.world.LaunchStage?> =
        when {
            runsHere(profile) -> stages.stageOfPhone()
            isAgentHelper(profile) -> stages.stageOfHelper(profile)
            else -> super.stage(profile)
        }

    /** Issue 147: the agents helper's own answer, the same one the home list reads ([ListRowFacts.needsNewChat]). */
    override fun needsNewSession(ref: ConversationRef): Boolean? =
        ListRowFacts.needsNewChat(registry.value.live.value[ref.profileId], ref.sessionId)

    // Issue 147: the conversation's folder in phone storage, checked from the side that works in it.
    override suspend fun phoneFolder(
        ref: ConversationRef,
        directory: String?,
        thorough: Boolean,
    ): io.github.eslamasabry.oc.feature.chat.world.ChatPhoneFolder? =
        folders.value.checkConversation(ref.profileId, directory, thorough)?.let {
            io.github.eslamasabry.oc.feature.chat.world.ChatPhoneFolder.valueOf(it.name)
        }

    override suspend fun fixPhoneFolder(
        ref: ConversationRef,
        reach: io.github.eslamasabry.oc.feature.chat.world.ChatPhoneFolder,
    ): Outcome<Unit> =
        folders.value.fixConversation(
            ref.profileId,
            io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderReach.valueOf(reach.name),
        )

    override suspend fun restart(profile: ProfileId): Outcome<Unit> =
        if (runsHere(profile)) server.restart()
        else Outcome.Failed(Failure.Unavailable(FeatureId("restartPhoneServer")))
}

/**
 * Try again in a chat opens the connection of a computer that is not open, by the home's own path
 * ([PreviewRuntime.connectInBackground]: the server on the home stays the one the person chose). Each open chat
 * holds its computer while it is shown; the queued-servers watch keeps a held connection.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ChatConnectionOpener>())
@SingleIn(AppScope::class)
@Inject
class AndroidChatConnectionOpener(
    private val runtime: Lazy<PreviewRuntime>,
    private val visibility: AppVisibility,
    private val network: NetworkStatus,
) : ChatConnectionOpener {
    private val holds = ConcurrentHashMap<ProfileId, Int>()

    override suspend fun open(profile: ProfileId): Outcome<Unit> = runtime.value.connectInBackground(profile)

    override fun hold(profile: ProfileId, held: Boolean) {
        holds.compute(profile) { _, count ->
            val next = (count ?: 0) + if (held) 1 else -1
            if (next <= 0) null else next
        }
    }

    override suspend fun awaitChance(profile: ProfileId) {
        combine(visibility.state, network.state) { seen, net ->
            seen == Visibility.Foreground && net.online != NetworkState.Online.No
        }.first { it }
    }

    /** True while an open chat shows [profile]. */
    fun held(profile: ProfileId) = (holds[profile] ?: 0) > 0
}

private val NOT_WAITING: StateFlow<PhoneServerWait> = MutableStateFlow(PhoneServerWait.None)

internal fun waitOf(facts: PhoneServerFacts): PhoneServerWait =
    when {
        facts.updateRunning -> PhoneServerWait.Updating
        // The person's own Stop, with nothing running or starting: its own state, never a wait or a failure.
        facts.stoppedByPerson && !facts.starting && facts.status?.serverRunning != true ->
            PhoneServerWait.StoppedByPerson
        facts.starting || facts.recovery is RecoveryFacts.Waiting || facts.recovery == RecoveryFacts.Restarting ->
            PhoneServerWait.Starting
        else -> PhoneServerWait.None
    }
