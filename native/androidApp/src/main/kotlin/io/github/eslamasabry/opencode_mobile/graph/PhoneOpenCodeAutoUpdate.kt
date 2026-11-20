package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.engine.runtime.install.ManagedRuntime
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeAutoUpdater
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeHostFacts
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeUpdateJobs
import io.github.eslamasabry.oc.engine.runtime.server.UpdaterHooks
import io.github.eslamasabry.oc.engine.runtime.server.launchOpenCodeAutoUpdate
import io.github.eslamasabry.oc.engine.runtime.server.openCodeAutoUpdateLooks
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import kotlin.time.Duration.Companion.seconds
import android.os.SystemClock
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeIdleSettle
import kotlinx.coroutines.flow.MutableSharedFlow
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Keeps the phone server on the OpenCode version this app pins, by itself (Flutter `inAppAutoUpdateProvider`): the
 * same job as This phone's "Update OpenCode" row, once per app run, only when nothing is working and the network is not
 * metered (the agents' update's own reading; the row itself runs on any network). It looks at start, every 30 s, and
 * whenever the host, the setup job or the work on the server changes, so it runs at the first look on unmetered data.
 */
@SingleIn(AppScope::class)
@Inject
class PhoneOpenCodeAutoUpdate(
    private val runtime: BuiltinRuntime,
    private val setup: NativePhoneSetup,
    private val leases: NativeChatLeases,
    private val appScope: AppCoroutineScope,
    private val downloads: PhoneDownloads,
    private val gates: UpdaterGates,
) {
    private var started = false
    @Volatile private var updaterRef: OpenCodeAutoUpdater? = null
    private val choices get() = gates.choices
    private val wakeLock = Any()
    private var wakeJob: kotlinx.coroutines.Job? = null

    /**
     * The person chose Download now on mobile data (Home's ask): the pending refresh runs on this network, once, and
     * is looked at now rather than at the next 30 s look.
     */
    fun allowMeteredOnce() {
        val updater = updaterRef ?: return
        updater.allowMeteredOnce()
        appScope.launch { updater.changed() }
    }
    private val log = AppLog.tagged("OcAutoUpdate")

    fun start() {
        if (started) return
        started = true
        log.i { "start() reached" }
        val host =
            object : OpenCodeHostFacts {
                override val updateAllowed: Boolean get() = choices.allowed(AgentId.OPENCODE)
                // The read also takes the program on disk again: right after an update the old server still runs
                // until its restart, so the running version alone says "below the pin" for a finished update.
                override suspend fun refresh() {
                    this@PhoneOpenCodeAutoUpdate.runtime.refreshServer()
                    choices.refresh()
                }

                override val installedVersion: String?
                    get() = choices.openCodeOnDisk
                        ?: this@PhoneOpenCodeAutoUpdate.runtime.serverFacts.value.installedVersion
                override val pinnedVersion: String get() = ManagedRuntime.OpenCode2.pinnedVersion
                override val runtime: String get() = ManagedRuntime.OpenCode2.wireName
                override val switchPending: Boolean get() = this@PhoneOpenCodeAutoUpdate.runtime.switchPending()
                override val installProblem: Boolean
                    get() = this@PhoneOpenCodeAutoUpdate.runtime.serverFacts.value.status?.phase == RuntimePhase.Failed

                // About 126 MB: never over data that is paid for, unless the person said Download now on Home's ask;
                // only an unmetered network is free (unknown and offline count as paid, as the voice download).
                override val metered: Boolean get() = downloads.metered
            }
        val jobs =
            object : OpenCodeUpdateJobs {
                override val running: Boolean get() = setup.progress.value.state == SetupState.Running
                override val failed: Boolean get() = setup.progress.value.state == SetupState.Failed

                // Refused before it started (not known idle, a job running): the try is not spent. A job that
                // started and failed is left to the manual row; the setup job reports its own failure.
                override suspend fun updateOpenCode(runtime: String): Boolean =
                    choices.startIfChosen(AgentId.OPENCODE) {
                    val result = setup.update()
                    // Busy in either form (a runtime slot, or a payload lease held by an agents' install) means it did
                    // not start: the try is kept, never spent.
                    val refused =
                        (result as? Outcome.Failed)?.failure.let {
                            (it is Failure.Runtime && it.problem == RuntimeProblem.SlotBusy) || it is Failure.Busy
                        }
                    if (refused) log.i { "update refused before it started (work running or not known idle)" }
                    !refused
                }
            }
        val settled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        // `leases.busy` reads false only after a real status read of a connected server (NativeChatLeases `known`), and
        // goes back to unknown when that read fails; the 30 s settle on top is Flutter's `inAppAutoUpdateSettle`.
        val settle = OpenCodeIdleSettle(appScope, { SystemClock.elapsedRealtime() }) { settled.tryEmit(Unit) }
        // The newest idle reading. The chat-lease map has no entry for the phone server until a chat watch has read it,
        // so a read of its own is made here (while the server is behind the pin), not only when a lease says so.
        val idle =
            java.util.concurrent.atomic.AtomicReference(
                BuiltinRuntime.WorkIdle(idle = false, lease = null, direct = null, agent = false)
            )
        val updater =
            OpenCodeAutoUpdater(
                host,
                jobs,
                log = { log.i { it } },
                nowMs = { SystemClock.elapsedRealtime() },
                // A retry waiting for its time wakes itself: ONE pending delayed look (a newer ask replaces
                // the older one), cancelled with the app's scope.
                hooks =
                    UpdaterHooks(
                        onRead = { UpdaterReads.opencode.incrementAndGet() },
                        wake = { delayMs ->
                            synchronized(wakeLock) {
                                wakeJob?.cancel()
                                wakeJob =
                                    appScope.launch {
                                        kotlinx.coroutines.delay(delayMs)
                                        updaterRef?.changed()
                                    }
                            }
                        },
                    ),
            ) {
                // Flutter also refuses while `localPhoneAgentWorkBusy`: the agent's `<owner>.agents` reading.
                val reading = idle.get()
                // Not in front: the restart that ends the update needs the foreground service admission only a
                // visible app has, so the try is kept (with the backoff) and the next look in front runs it.
                settle.busy(reading.idle) || gates.visibility.state.value != Visibility.Foreground
            }
        updaterRef = updater
        downloads.openCode = this
        downloads.start()
        appScope.launch { updater.waitingForUnmetered.collect { downloads.openCodeWaiting.value = it } }
        // One line a minute while the updaters are active: how many reads each made, and the heap, so a look can assert
        // "under N" and a loop shows at once (never found by a crash again).
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(READ_REPORT_SECONDS.seconds)
                val rt = Runtime.getRuntime()
                log.i {
                    "updater reads: opencode ${UpdaterReads.opencode.getAndSet(0)}, " +
                        "agents ${UpdaterReads.agents.getAndSet(0)} in the last minute; " +
                        "heap ${(rt.totalMemory() - rt.freeMemory()) / MB} MB"
                }
            }
        }
        // Held a while for a busy or not-ready phone: Home says so in its one download line (never at once).
        appScope.launch { updater.held.collect { downloads.openCodeHeld.value = it } }
        appScope.launch { updater.fellShort.collect { downloads.openCodeShort.value = it } }
        appScope.launch { updater.retrying.collect { downloads.openCodeRetrying.value = it } }
        val reads =
            openCodeAutoUpdateLooks(host, {
                val now = runtime.readWorkIdle()
                if (now != idle.get()) log.i { "work idle=${now.idle} lease=${now.lease} direct=${now.direct} agent=${now.agent}" }
                idle.set(now)
            })
        appScope.launchOpenCodeAutoUpdate(
            updater,
            host,
            merge(
                // Only what the decision reads counts as a change: a read the updater itself makes refreshes the
                // server's facts, and that must never ask for the next look.
                combine(
                    decisionFacts(runtime.serverFacts),
                    setup.progress,
                    leases.busy,
                    gates.visibility.state,
                ) { _, _, _, _ -> },
                settled,
                reads,
                choices.offer.map { Unit },
            ),
            log = { log.i { it } },
        )
        appScope.launch {
            var last: SetupState? = null
            setup.progress.collect {
                // Only the job the update itself started shows on the Update row. Every setup job lists all the
                // required components, so the list cannot tell the update from Repair.
                val update = setup.updateJob.owns(it.jobId)
                if (update && it.state != last) log.i { "update job ${it.state}" }
                if (update) last = it.state
                runtime.noteUpdateJob(
                    update && it.state == SetupState.Running,
                    update && it.state == SetupState.Failed,
                )
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface PhoneOpenCodeAutoUpdateAccessors {
    val phoneOpenCodeAutoUpdate: PhoneOpenCodeAutoUpdate
}

/** The server facts the updater's decision reads (version, move pending, install phase, running); nothing else. */
private fun decisionFacts(facts: kotlinx.coroutines.flow.StateFlow<PhoneServerFacts>) =
    facts
        .map { listOf(it.installedVersion, it.needsOpenCode2Move, it.status?.phase, it.status?.serverRunning) }
        .distinctUntilChanged()

private const val READ_REPORT_SECONDS = 60
private const val MB = 1_048_576L
