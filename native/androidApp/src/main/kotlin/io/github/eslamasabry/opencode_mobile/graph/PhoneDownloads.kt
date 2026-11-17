package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.voice.device.AndroidSetupNetwork
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadMapping
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloads
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHelperStep
import io.github.eslamasabry.oc.feature.chats.presentation.HomeInstallPhase
import io.github.eslamasabry.oc.feature.chats.presentation.NoHomeDownloads
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    internal val openCodeWaiting = MutableStateFlow(false)
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
        combine(openCodeWaiting, setup.progress, phones.state, agentStatus.decision, networkRead) { waiting, progress, agentState, decision, read ->
                val update = setup.updateJob.owns(progress.jobId)
                val install = agentState.installation
                val helper =
                    when (decision.step) {
                        AgentUpdateStep.WaitingMetered -> if (read) HomeHelperStep.Waiting else HomeHelperStep.None
                        AgentUpdateStep.Updating -> HomeHelperStep.Updating
                        else -> HomeHelperStep.None
                    }
                HomeDownloadMapping.facts(
                    openCodeWaiting = downloadWaitingSaid(waiting, read),
                    openCodeRunning = update && progress.state == SetupState.Running,
                    openCodeFailed = update && progress.state == SetupState.Failed,
                    openCodePercent = (progress.overall * 100).roundToInt(),
                    helper = helper,
                    helperInstall =
                        when (install?.phase) {
                            PhoneAgentInstallPhase.Installing -> HomeInstallPhase.Installing
                            PhoneAgentInstallPhase.Failed,
                            PhoneAgentInstallPhase.Interrupted -> HomeInstallPhase.Failed
                            else -> HomeInstallPhase.None
                        },
                    helperPercent = install?.fraction?.let { (it * 100).roundToInt() },
                )
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
            HomeDownloadKind.OpenCode -> setup.update()
            HomeDownloadKind.Helper -> agentStatus.decision.value.agent?.let { phones.install(it.raw) }
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
