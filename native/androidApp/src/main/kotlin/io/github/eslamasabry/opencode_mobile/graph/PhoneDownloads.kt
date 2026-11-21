package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.voice.device.AndroidSetupNetwork
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupJobRecord
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallStep
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallResult
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadMapping
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadSizes
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldAgents
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
    private val log = AppLog.tagged("DownloadsLine")
    @Volatile private var lastSaid = ""

    @Volatile private var now: SetupNetwork = SetupNetwork.Unknown

    /** The network has been read at least once: until then "waiting for Wi-Fi" is not said (it is not known yet). */
    private val networkRead = MutableStateFlow(false)

    /** The network as last read (null before the first read): Home's line follows it, not the offer's older copy. */
    internal val liveNetwork = MutableStateFlow<SetupNetwork?>(null)
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
    internal val sharedRunning = MutableStateFlow<HomeDownloadFact?>(null)
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
            liveNetwork.value = now
            networkRead.value = true
        }
        scope.launch(dispatchers.io) {
            network.changes.collect {
                now = it
                liveNetwork.value = it
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
                    helperStepOf(decision.step, install?.phase == PhoneAgentInstallPhase.Installing, read)
                val agentName =
                    (install?.takeIf { it.phase == PhoneAgentInstallPhase.Installing }?.id ?: decision.agent?.raw)
                        ?.let { phones.nameOf(AgentId(it)) }
                // The agents' install job names the step it is on (Node.js, the helper, then the agent): the line names
                // THAT part with the step's own share, never the agent's name over another step's number. (The
                // components of `setup.progress` belong to the setup page's job, not to this one.)
                val part =
                    if (install?.phase == PhoneAgentInstallPhase.Installing) {
                        runningPart(install.step, install.fraction) { phones.nameOf(AgentId(install.id)) }
                    } else {
                        null
                    }
                val partName =
                    part?.let { if (it.helper) getString(Res.string.runningPhoneName__agentHelper) else it.name }
                if (part != null) {
                    val saying = "${install?.step} ${part.percent}"
                    if (saying != lastSaid) {
                        lastSaid = saying
                        log.i { "update line: step=${install?.step} share=${part.percent ?: "none"}" }
                    }
                }
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
            .combine(sharedRunning) { rows, shared -> if (shared == null) rows else listOf(shared) }
            .combine(agentStatus.choices.offer) { rows, read -> rows to read }
            .combine(liveNetwork) { (rows, read), live ->
                // The offer keeps the network of its last read; the line says the live one.
                val offer = if (read != null && live != null) read.copy(network = live.asOfferNetwork()) else read
                val withHelper =
                    if (offer?.neededGoesAhead == true && offer.network != AgentUpdateNetwork.Unmetered &&
                        rows.none { it.kind == HomeDownloadKind.Helper }) {
                        rows + HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting)
                    } else rows
                // The agent that downloads now is not waiting for Wi-Fi, whatever the offer still lists for it.
                val running =
                    phones.state.value.installation
                        ?.takeIf { it.phase == PhoneAgentInstallPhase.Installing }
                        ?.id
                        ?.let { AgentId(it) }
                // Every item the person said yes to is named: OpenCode too, from the offer, not one updater's flag.
                val named = withChosenOpenCode(withHelper, offer)
                // Agents chosen on the sheet that wait for Wi-Fi join this row (a second sentence, or the line).
                HomeDownloadMapping.withHeldAgents(
                    HomeDownloadMapping.openCodeWithHelper(named, openCodeWithHelperMb(offer)),
                    heldAgentsOf(offer, running, consented = agentStatus.choices.mobile::covers),
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun downloadNow(kind: HomeDownloadKind) {
        val fact = facts.value.firstOrNull { it.kind == kind } ?: return
        val phase = fact.phase
        if (phase != HomeDownloadPhase.Waiting && phase != HomeDownloadPhase.Failed) return
        scope.launch(dispatchers.io) {
            if (phase == HomeDownloadPhase.Failed) {
                run(kind)
            } else {
                when (kind) {
                    HomeDownloadKind.OpenCode -> {
                        openCode?.allowMeteredOnce()
                        // OpenCode waits for the shared parts the offer lists: the same yes releases them.
                        if (fact.withHelper) agents?.allowMeteredOnce(sharedOnly = true)
                    }
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

/**
 * The agents the person chose to update on the sheet that now wait for Wi-Fi, with their size as the sheet shows it
 * (decimal MB). OpenCode and the shared parts have their own lines and are not counted. Null when none waits, when the
 * network is free, or when a size is unknown (a total with a hole would be a guess).
 */
internal fun heldAgentsOf(
    offer: AgentUpdateOffer?,
    running: AgentId? = null,
    liveNetwork: AgentUpdateNetwork? = null,
    consented: (AgentId) -> Boolean = { false },
): HomeHeldAgents? {
    val net = liveNetwork ?: offer?.network
    val waiting = offer?.takeIf { it.answered && net != AgentUpdateNetwork.Unmetered }
    val held =
        waiting?.items.orEmpty().filter {
            it.group == AgentUpdateGroup.Optional && it.id != AgentId.OPENCODE && it.id != running &&
                it.choice == AgentUpdateChoice.Update && !consented(it.id)
        }
    val bytes = held.map { it.downloadBytes }
    return if (held.isEmpty() || bytes.any { it == null }) null
    else HomeHeldAgents(held.size, HomeDownloadSizes.roundedMb(bytes.sumOf { it ?: 0L }))
}

/**
 * OpenCode's update with the shared parts it waits for (the Agent helper), in decimal MB from the sheet's own sizes;
 * null when the offer lists no shared part. Without a size for each, the usual sizes are added (130 + 330).
 */
internal fun openCodeWithHelperMb(offer: AgentUpdateOffer?): Int? {
    val shared = offer?.items.orEmpty().filter { it.neededWhen != null }
    val openCode = offer?.items.orEmpty().firstOrNull { it.id == AgentId.OPENCODE }
    val sizes = (listOfNotNull(openCode) + shared).map { it.downloadBytes }
    return when {
        shared.isEmpty() -> null
        openCode == null || sizes.any { it == null } -> HomeDownloadSizes.OPEN_CODE_MB + HomeDownloadSizes.HELPER_MB
        else -> HomeDownloadSizes.roundedMb(sizes.sumOf { it ?: 0L })
    }
}

/** The agents' update step Home says: the install job's own "running" beats the updater's reading for the next one. */
internal fun helperStepOf(step: AgentUpdateStep, installing: Boolean, networkRead: Boolean): HomeHelperStep =
    when (step) {
        // The install job itself says it runs: the updater reads Waiting/Refused for the NEXT agent while the first
        // one downloads, and that must never be said as "waiting for Wi-Fi" (10114, journey M1).
        AgentUpdateStep.WaitingMetered ->
            when {
                installing -> HomeHelperStep.Updating
                networkRead -> HomeHelperStep.Waiting
                else -> HomeHelperStep.None
            }
        AgentUpdateStep.Updating -> HomeHelperStep.Updating
        AgentUpdateStep.Failed -> HomeHelperStep.Failed
        else -> if (installing) HomeHelperStep.Updating else HomeHelperStep.None
    }

/** OpenCode, chosen for update on a paid network, is named even before its own updater says it waits. */
internal fun withChosenOpenCode(rows: List<HomeDownloadFact>, offer: AgentUpdateOffer?): List<HomeDownloadFact> {
    val chosen =
        offer?.takeIf { it.answered && it.network != AgentUpdateNetwork.Unmetered }?.items.orEmpty().any {
            it.id == AgentId.OPENCODE && it.choice == AgentUpdateChoice.Update
        }
    val optionalOnly = offer?.answered == true && offer.items.none { it.neededWhen != null } &&
        offer.items.any { it.id != AgentId.OPENCODE && it.choice == AgentUpdateChoice.Update }
    val named = if (optionalOnly) {
        rows.filterNot { it.kind == HomeDownloadKind.Helper && it.phase == HomeDownloadPhase.Waiting }
    } else rows
    return if (!chosen || named.any { it.kind == HomeDownloadKind.OpenCode }) {
        named
    } else {
        listOf(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting)) + named
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

private fun runningPart(step: PhoneAgentInstallStep?, fraction: Double?, agentName: () -> String?): RunningPart? {
    val percent = fraction?.let { (it * PERCENT).roundToInt() }
    return when (step) {
        PhoneAgentInstallStep.Runtime -> RunningPart("Node.js", false, percent)
        PhoneAgentInstallStep.Helper -> RunningPart(null, true, percent)
        PhoneAgentInstallStep.Agent -> RunningPart(agentName(), false, percent)
        PhoneAgentInstallStep.Linux, null -> null
    }
}

/** A shared-only job bypasses the agent install marker. Read the active component's own bytes from the runner. */
internal fun sharedDownloadFact(job: SetupJobRecord, parts: List<AgentUpdateItem>): HomeDownloadFact {
    val active = job.current?.let(job::component)
    val item = parts.firstOrNull { it.id.raw == active?.id }
    val fraction = active?.let {
        ComponentProgress(
            it.id, it.componentState, bytesDone = it.done, bytesTotal = it.total, percent = it.percent,
        ).fraction
    }
    val size = item?.downloadBytes?.let(HomeDownloadSizes::roundedMb)
    return HomeDownloadFact(
        HomeDownloadKind.Helper,
        HomeDownloadPhase.Running(fraction?.let { (it * PERCENT).roundToInt() }, size),
        name = item?.name,
    )
}

private fun SetupNetwork.asOfferNetwork(): AgentUpdateNetwork =
    when (this) {
        SetupNetwork.Unmetered -> AgentUpdateNetwork.Unmetered
        SetupNetwork.Metered -> AgentUpdateNetwork.Metered
        else -> AgentUpdateNetwork.Unknown
    }
