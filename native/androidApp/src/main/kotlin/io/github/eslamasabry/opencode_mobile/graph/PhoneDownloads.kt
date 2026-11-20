package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.voice.device.AndroidSetupNetwork
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallResult
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadMapping
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHold
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeHold
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloads
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHelperStep
import io.github.eslamasabry.oc.feature.chats.presentation.HomeInstallPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeShortSpace
import io.github.eslamasabry.oc.feature.chats.presentation.NoHomeDownloads
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__agentHelper
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The two big automatic downloads after an app update (the phone's OpenCode refresh, about 130 MB, and the agents'
 * helper install, about 330 MB), as Home's status line shows them: held on mobile data (or a network that cannot be
 * told), downloading, or failed. It is also the one reading of the network for both updates ([metered]: only an
 * unmetered network is free; unknown and offline count as paid, the voice download's own rule).
 *
 * The helper's first install after an update is the agents' auto update (`PhoneAgentAutoUpdate` starts the Agents
 * page's own install, `AppPhoneAgents.install` -> `NativeAgentSetupJobs.start`, whose `agent-paseo` step runs
 * `npm ci`), so the hold, the ask and the progress sit on that one path.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, replaces = [NoHomeDownloads::class])
class PhoneDownloads(
    app: Application,
    private val setup: NativePhoneSetup,
    private val phones: AppPhoneAgents,
    private val agentStatus: PhoneAgentUpdateStatus,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : HomeDownloads {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val network = AndroidSetupNetwork(app)

    @Volatile private var now: SetupNetwork = SetupNetwork.Unknown

    /** The network has been read at least once: until then "waiting for Wi-Fi" is not said (it is not known yet). */
    private val networkRead = MutableStateFlow(false)
    @Volatile private var started = false

    /** The phone is on a network that is free to download over; false for mobile data, unknown and offline. */
    val metered: Boolean get() = downloadNetworkMetered(now)

    /** The agents' install that stopped for want of room: what it needs and what the phone has (null when none). */
    internal val helperShort = MutableStateFlow<HomeShortSpace?>(null)
    /** OpenCode's automatic update ended below the pin, tried again a few times: Home says it failed. */
    internal val openCodeShort = MutableStateFlow(false)

    /** A hold of the agents' update that lasted past its short wait (null: none, or not told yet). */
    internal val helperHold = MutableStateFlow<HomeHold?>(null)

    /** OpenCode's update ended below the pin and the next try waits for its time and an idle phone. */
    internal val openCodeRetrying = MutableStateFlow(false)
    internal val openCodeWaiting = MutableStateFlow(false)
    internal val openCodeHeld = MutableStateFlow<OpenCodeHold?>(null)
    @Volatile internal var openCode: PhoneOpenCodeAutoUpdate? = null
    @Volatile internal var agents: PhoneAgentAutoUpdate? = null

    /** Reads the network now and on every change; asked once, by whichever update starts first. */
    fun start() {
        if (started) return
        started = true
        scope.launch(dispatchers.io) {
            now = network.current()
            networkRead.value = true
        }
        scope.launch(dispatchers.io) {
            network.changes.collect {
                now = it
                networkRead.value = true
            }
        }
    }

    override val facts: StateFlow<List<HomeDownloadFact>> =
        combine(
                combine(
                    combine(openCodeWaiting, openCodeHeld, helperShort) { a, b, c -> Triple(a, b, c) },
                    combine(openCodeShort, openCodeRetrying, helperHold) { d, e, f -> Triple(d, e, f) },
                ) { (a, b, c), (d, e, f) -> SideFacts(a, b, c, d, e, f) },
                setup.progress,
                phones.state,
                agentStatus.decision,
                networkRead,
            ) { side, progress, agentState, decision, read ->
                val update = setup.updateJob.owns(progress.jobId)
                val install = agentState.installation
                val helper =
                    when (decision.step) {
                        AgentUpdateStep.WaitingMetered -> if (read) HomeHelperStep.Waiting else HomeHelperStep.None
                        AgentUpdateStep.Updating -> HomeHelperStep.Updating
                        AgentUpdateStep.Failed -> HomeHelperStep.Failed
                        // The install job itself says it runs, whatever the updater's decision reads at this look (it
                        // reads Waiting/Refused for the NEXT agent while the first one downloads).
                        else ->
                            if (install?.phase == PhoneAgentInstallPhase.Installing) {
                                HomeHelperStep.Updating
                            } else {
                                HomeHelperStep.None
                            }
                    }
                val agentName =
                    (install?.takeIf { it.phase == PhoneAgentInstallPhase.Installing }?.id ?: decision.agent?.raw)
                        ?.let { phones.nameOf(AgentId(it)) }
                // The job says which part runs now (Node.js, the helper, then the agent): the line names THAT part
                // with its own share, never the agent's name over another part's number.
                val part =
                    if (install?.phase == PhoneAgentInstallPhase.Installing) {
                        runningPart(progress.components) { id -> phones.nameOf(AgentId(id)) }
                    } else {
                        null
                    }
                val partName =
                    part?.let { if (it.helper) getString(Res.string.runningPhoneName__agentHelper) else it.name }
                val said =
                    HomeDownloadMapping.facts(
                        openCodeWaiting = downloadWaitingSaid(side.waiting, read),
                        openCodeRunning = update && progress.state == SetupState.Running,
                        // The update ended below its pin (read again after the job): the failed row, with Try again.
                        openCodeFailed =
                            (update && progress.state == SetupState.Failed && !side.retrying) || side.openCodeShort,
                        // OpenCode's OWN progress, never another component's (the job lists the shared parts too).
                        openCodePercent =
                            progress.components
                                .firstOrNull { it.id == SetupComponentIds.OPENCODE }
                                ?.fraction
                                ?.let { (it * PERCENT).roundToInt() },
                        helper = helper,
                        helperInstall =
                            when (install?.phase) {
                                PhoneAgentInstallPhase.Installing -> HomeInstallPhase.Installing
                                PhoneAgentInstallPhase.Failed,
                                PhoneAgentInstallPhase.Interrupted -> HomeInstallPhase.Failed
                                else -> HomeInstallPhase.None
                            },
                        helperPercent =
                            if (part != null) part.percent else install?.fraction?.let { (it * PERCENT).roundToInt() },
                    )
                        // A helper install that stopped for want of room says how much, beside the failed row.
                        .map { fact ->
                            when {
                                fact.kind != HomeDownloadKind.Helper -> fact
                                fact.phase == HomeDownloadPhase.Failed -> fact.copy(short = side.helperShort)
                                fact.phase is HomeDownloadPhase.Running -> fact.copy(name = partName ?: agentName)
                                else -> fact
                            }
                        }
                        // A hold of the agents' update that lasted past its short wait is said, with its true reason.
                        .let { facts ->
                            val hold = side.helperHold
                            if (hold != null && facts.none { it.kind == HomeDownloadKind.Helper }) {
                                facts + HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Held(hold))
                            } else {
                                facts
                            }
                        }
                HomeDownloadMapping.withHeld(
                    said,
                    when (side.held) {
                        OpenCodeHold.Busy -> HomeHold.Reply
                        OpenCodeHold.NotReady -> HomeHold.PhoneNotReady
                        null -> null
                    },
                )
            }
            .combine(agentStatus.choices.offer) { rows, offer ->
                if (offer?.neededGoesAhead == true && offer.network != AgentUpdateNetwork.Unmetered &&
                    rows.none { it.kind == HomeDownloadKind.Helper }) {
                    rows + HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting)
                } else rows
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun downloadNow(kind: HomeDownloadKind) {
        val phase = facts.value.firstOrNull { it.kind == kind }?.phase
        scope.launch(dispatchers.io) {
            if (phase == HomeDownloadPhase.Failed) {
                run(kind)
            } else {
                when (kind) {
                    HomeDownloadKind.OpenCode -> openCode?.allowMeteredOnce()
                    HomeDownloadKind.Helper -> agents?.allowMeteredOnce()
                }
            }
        }
    }

    override fun tryAgain(kind: HomeDownloadKind): Boolean {
        if (metered) return false
        scope.launch(dispatchers.io) { run(kind) }
        return true
    }

    /** A failed download started again by the person: the same job the Update row and the Agents page start. */
    private suspend fun run(kind: HomeDownloadKind) {
        when (kind) {
            HomeDownloadKind.OpenCode -> agentStatus.choices.startIfChosen(AgentId.OPENCODE) {
                setup.update() is Outcome.Ok
            }
            HomeDownloadKind.Helper ->
                agentStatus.decision.value.agent?.let { agent ->
                    agentStatus.choices.startIfChosen(agent) {
                        val answer = (phones.install(agent.raw) as? Outcome.Ok)?.value
                        val started = answer == PhoneAgentInstallResult.Started
                        agents?.afterTry(agent, started, short = true)
                        started
                    }
                }
        }
    }
}

/** Only an unmetered network is free: mobile data, a network that cannot be told, and no network all count as paid. */
internal fun downloadNetworkMetered(network: SetupNetwork): Boolean = network != SetupNetwork.Unmetered

/**
 * "Waiting for Wi-Fi" is said only once the network was read: before the first read the phone is not known to be on
 * mobile data, so the line stays quiet (the update itself still waits; unknown stays "ask").
 */
internal fun downloadWaitingSaid(waiting: Boolean, networkRead: Boolean): Boolean = waiting && networkRead

private const val PERCENT = 100

/** What the downloads line reads beside the setup job: waiting, held, the helper's room, a short OpenCode update. */
private data class SideFacts(
    val waiting: Boolean,
    val held: OpenCodeHold?,
    val helperShort: HomeShortSpace?,
    val openCodeShort: Boolean,
    val retrying: Boolean,
    val helperHold: HomeHold?,
)

/** The part of an agent's install job that runs now: its name (the helper's is a string), and its own share. */
private class RunningPart(val name: String?, val helper: Boolean, val percent: Int?)

private fun runningPart(components: List<ComponentProgress>, agentName: (String) -> String?): RunningPart? {
    val running = components.firstOrNull { it.id.startsWith(AGENT_PART) && it.state == ComponentState.Running }
        ?: return null
    val percent = running.fraction?.let { (it * PERCENT).roundToInt() }
    return when (val id = running.id.removePrefix(AGENT_PART)) {
        "node" -> RunningPart("Node.js", false, percent)
        "paseo" -> RunningPart(null, true, percent)
        "user" -> null
        else -> RunningPart(agentName(id), false, percent)
    }
}

private const val AGENT_PART = "agent-"
