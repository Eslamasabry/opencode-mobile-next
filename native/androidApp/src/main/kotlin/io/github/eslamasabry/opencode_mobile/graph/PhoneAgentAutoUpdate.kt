@file:Suppress("TooGenericExceptionCaught")

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.net.ConnectivityManager
import android.os.SystemClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.install.AgentAutoUpdater
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateHost
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateJobs
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeIdleSettle
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallResult
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Moves the phone's installed agents and their helper (Paseo) to the versions this app pins, by itself: the agents'
 * counterpart of [PhoneOpenCodeAutoUpdate]. After an app update whose pins are newer than what is installed, it runs
 * the Agents page's own install for an installed agent that is behind (the job also brings the helper and Node.js and
 * skips what is in place), once per app run and pinned set, only while nothing is working (the phone server, the
 * agent helper, a setup job), no switch or install is half finished, and the network is not metered. The install
 * shows on the agent's tile and in the conversation's "is updating" line like any install; a failed try is left to
 * the Agents page's manual install. When the update ends, a running helper of another version is replaced by the
 * pinned one (the start refuses while work is not idle).
 */
@SingleIn(AppScope::class)
@Inject
class PhoneAgentAutoUpdate(
    private val app: Application,
    private val phones: AppPhoneAgents,
    private val helper: AppLocalAgentHostStarter,
    private val runtime: BuiltinRuntime,
    private val setup: NativePhoneSetup,
    private val leases: NativeChatLeases,
    private val dispatchers: AppDispatchers,
    private val status: PhoneAgentUpdateStatus,
    private val appScope: AppCoroutineScope,
) {
    private var started = false
    @Volatile private var updaterRef: AgentAutoUpdater? = null
    private val log = AppLog.tagged("AgentAutoUpdate")

    fun start() {
        if (started) return
        started = true
        val pins = AgentPins.builtIn
        val pinned =
            "paseo ${pins.paseo.version} " +
                AgentCatalog.builtIn.agents.joinToString(" ") { "${it.id.raw} ${it.recipe?.version}" }
        val host =
            object : AgentUpdateHost {
                // The decision reads the kept facts of the last reads (no probe), so there is nothing to read again.
                override suspend fun refresh() = Unit

                // An update an earlier run of the app started and did not finish (stopped or crashed mid-install).
                override val resume: AgentId? get() = phones.interruptedUpdate()

                // The first look after an app update (or before any read) asks for one read of the installed
                // agents' versions, so the update never depends on the Agents page having been opened.
                override val readDue: Boolean get() = phones.updateReadDue()

                override suspend fun read() {
                    val done = phones.readForUpdate()
                    log.i { "read of the installed agents ${if (done) "done" else "did not finish"}" }
                }

                override val installed: List<AgentId>
                    get() = phones.updateFacts()?.installed.orEmpty()

                override val behind: Set<AgentId>
                    get() = phones.updateFacts()?.behind.orEmpty()

                override val helperBehind: Boolean
                    get() =
                        phones.updateFacts()?.let {
                            it.helperOnDiskBehind ||
                                (it.helperRunning && it.helperVersion != null && it.helperVersion != pins.paseo.version)
                        } == true

                override val pinKey: String = pinned
                override val switchPending: Boolean get() = runtime.switchPending()
                // An old failed or interrupted install must not hold the update for ever: a running job does (the
                // updater's own setup check), and a failed try is retried once per app run.
                override val installProblem: Boolean get() = false

                // A download of hundreds of megabytes never goes over data that is paid for; unknown counts as paid.
                override val metered: Boolean
                    get() =
                        try {
                            app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
                        } catch (_: Exception) {
                            true
                        }
            }
        val jobs =
            object : AgentUpdateJobs {
                override val running: Boolean get() = setup.progress.value.state == SetupState.Running
                override val failed: Boolean get() = false

                // Refused because the phone is busy: the try is not spent. Any other refusal (no room, not
                // installable here) is left to the manual install, which says why.
                override suspend fun update(agent: AgentId): Boolean {
                    val answer = (phones.install(agent.raw) as? Outcome.Ok)?.value ?: return true
                    return when (answer) {
                        PhoneAgentInstallResult.Started -> {
                            log.i { "update started" }
                            // Kept until the install ends, so a stop or crash in the middle resumes it next run.
                            phones.markUpdating(agent)
                            appScope.launch(dispatchers.io) { safely { finish(agent) } }
                            true
                        }
                        is PhoneAgentInstallResult.Refused ->
                            answer.issue != PhoneAgentInstallIssue.Busy
                    }
                }
            }
        val settled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        // As the OpenCode update: work counts as busy until known idle has held for 30 s without a break. Only the
        // agents' own work counts (the `<owner>.agents` reading), not the phone's OpenCode server: an old conversation
        // that server still lists as started would otherwise hold the update for ever, and the update does not restart
        // that server. The helper's own restart refuses by itself while its agents work.
        val settle = OpenCodeIdleSettle(appScope, { SystemClock.elapsedRealtime() }) { settled.tryEmit(Unit) }
        var busySince: Long? = null
        val updater =
            AgentAutoUpdater(host, jobs, log = { log.i { it } }) {
                val now = SystemClock.elapsedRealtime()
                val busy = settle.busy(!runtime.agentWorkBusy())
                if (!busy) {
                    busySince = null
                    false
                } else {
                    val since = busySince ?: now.also { busySince = it }
                    // A reading that says busy for this long without a break is taken as stale: the update goes on.
                    now - since > BUSY_BOUND_MS
                }
            }
        updaterRef = updater
        // Everything runs off the main thread: the decision reads the helper's status in the phone's Linux. Nothing the
        // updater does may end the app: an error in a look is logged by its type only and the next look goes on.
        appScope.launch(dispatchers.io) {
            safely { updater.decision.collect { status.set(it); phones.setUpdateNote(it) } }
        }
        appScope.launch(dispatchers.io) {
            delay(FIRST_LOOK.seconds)
            safely { phones.warmForUpdate() }
            safely { updater.changed() }
            safely {
                merge(combine(setup.progress, leases.busy) { _, _ -> }, settled).collect {
                    safely { updater.changed() }
                }
            }
        }
        appScope.launch(dispatchers.io) {
            delay(FIRST_LOOK.seconds)
            while (true) {
                safely { phones.warmForUpdate() }
                safely { updater.changed() }
                delay(LOOK_EVERY.seconds)
            }
        }
    }

    /** Runs [block]; any error but a cancellation is logged by its type and swallowed (the updater never ends the app). */
    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.i { "agent update look failed: ${e::class.simpleName}" }
        }
    }

    /** Waits for the update's install to end (at most a quarter hour), then replaces an older running helper. */
    private suspend fun finish(agent: AgentId) {
        repeat(WAIT_STEPS) {
            delay(WAIT_EVERY.seconds)
            phones.refresh()
            val installation = phones.state.value.installation?.takeIf { it.id == agent.raw }
            when (installation?.phase) {
                PhoneAgentInstallPhase.Installing -> Unit
                PhoneAgentInstallPhase.Done -> {
                    restartOlderHelper()
                    phones.noteHelperInPlace()
                    phones.markUpdating(null)
                    updaterRef?.finished(agent)
                    return
                }
                // No record of it (yet or any more): the mark stays, so the next run checks it again.
                null -> {
                    restartOlderHelper()
                    return
                }
                PhoneAgentInstallPhase.Failed,
                PhoneAgentInstallPhase.Interrupted -> return
            }
        }
    }

    private suspend fun restartOlderHelper() {
        val facts = phones.updateFacts() ?: return
        val pinned = AgentPins.builtIn.paseo.version
        if (!facts.helperRunning) {
            // The helper is not up (it was not running, or the update kept it from starting): start it the way any
            // connect does, so the agents' conversations connect again without a tap. It needs the app in front;
            // otherwise the next connect starts it.
            val started = helper.checkHelper()
            log.i { "helper start after the update ${if (started is Outcome.Ok) "done" else "refused"}" }
            return
        }
        if (facts.helperVersion == null || facts.helperVersion == pinned) return
        // Refused while work runs: the older helper then keeps running and the next start brings the pinned one.
        val answer = helper.restartForUpdate()
        log.i { "helper restart ${if (answer is Outcome.Ok) "done" else "refused"}" }
    }

    private companion object {
        const val FIRST_LOOK = 45L

        /** Agent work that reads busy for two hours without a break is taken as a stale reading. */
        const val BUSY_BOUND_MS = 2L * 60 * 60 * 1000
        const val LOOK_EVERY = 30L
        const val WAIT_EVERY = 5L
        const val WAIT_STEPS = 180
    }
}

@ContributesTo(AppScope::class)
interface PhoneAgentAutoUpdateAccessors {
    val phoneAgentAutoUpdate: PhoneAgentAutoUpdate
}
