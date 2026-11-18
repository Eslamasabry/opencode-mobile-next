@file:Suppress("TooGenericExceptionCaught")

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
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
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentSpace
import io.github.eslamasabry.oc.feature.chats.presentation.HomeShortSpace
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.install.AgentAutoUpdater
import io.github.eslamasabry.oc.engine.agents.install.AgentInstallRecord
import io.github.eslamasabry.oc.engine.agents.install.AgentInstallRecorded
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateHost
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateJobs
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.engine.runtime.server.OpenCodeIdleSettle
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallResult
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
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
    private val downloads: PhoneDownloads,
) {
    private var started = false

    /**
     * The person chose Download now on mobile data (Home's ask): the pending install runs on this network, once, and
     * is looked at now rather than at the next 30 s look.
     */
    fun allowMeteredOnce() {
        val updater = updaterRef ?: return
        updater.allowMeteredOnce()
        appScope.launch(dispatchers.io) { safely { updater.changed() } }
    }

    /**
     * What each agent's install of its pinned version recorded (issue 147), read straight from the phone's Linux tree:
     * two small files per agent, no process and no wait for the setup lane, so it is read at every look and can never
     * be missed. (The first version ran a script for it, once; on the Galaxy it waited behind the start's version
     * reads, did not run, and was never asked again, so Codex was never found behind.)
     */
    private fun records(): Map<AgentId, AgentInstallRecorded> {
        val linux = BuiltinLinux.get(app)
        if (!linux.installed) return emptyMap()
        return AgentCatalog.builtIn.agents.mapNotNull { agent ->
            val dir = AgentInstallRecord.installDir(agent) ?: return@mapNotNull null
            val folder = java.io.File(linux.rootfs, dir.removePrefix("/"))
            fun text(name: String): String? =
                java.io.File(folder, name)
                    .takeIf { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) && it.length() <= 4096 }
                    ?.readText()
            agent.id to AgentInstallRecord.fromFiles(text(".oc-pin"), text("launch"))
        }.toMap()
    }

    /** Installed agents whose install of the pinned version is not this app's pin (same version, other artifact). */
    private fun recordBehind(installed: List<AgentId>): Set<AgentId> {
        val architecture = PhoneAgentRules.architectureOf(Build.SUPPORTED_ABIS.toList()) ?: return emptySet()
        return AgentInstallRecord.behind(installed, records(), AgentCatalog.builtIn, architecture)
    }
    @Volatile private var updaterRef: AgentAutoUpdater? = null
    private val log = AppLog.tagged("AgentAutoUpdate")

    fun start() {
        if (started) return
        started = true
        val pins = AgentPins.builtIn
        // The artifact's checksum is part of a pin: the same version from another artifact is another pin (issue 147).
        val pinned =
            "paseo ${pins.paseo.version} " +
                AgentCatalog.builtIn.agents.joinToString(" ") { agent ->
                    "${agent.id.raw} ${agent.recipe?.version} " +
                        agent.recipe?.artifacts?.values?.joinToString(",") { it.sha256.take(12) }
                }
        val host =
            object : AgentUpdateHost {
                // The decision reads the kept facts of the last reads (no probe) and the install records (files).
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
                    get() =
                        phones.updateFacts()?.let { facts -> facts.behind + recordBehind(facts.installed) }.orEmpty()

                override val reinstall: Set<AgentId>
                    get() =
                        phones.updateFacts()?.let { facts -> recordBehind(facts.installed) - facts.behind }.orEmpty()

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

                // A download of hundreds of megabytes never goes over data that is paid for, unless the person said
                // Download now on Home's ask; only an unmetered network is free (unknown and offline count as paid).
                override val metered: Boolean get() = downloads.metered
            }
        val jobs =
            object : AgentUpdateJobs {
                override val running: Boolean get() = setup.progress.value.state == SetupState.Running
                override val failed: Boolean get() = false

                // Refused because the phone is busy: the try is not spent. Any other refusal (no room, not
                // installable here) is left to the manual install, which says why.
                override suspend fun update(agent: AgentId): Boolean {
                    val answer =
                        (phones.install(agent.raw) as? Outcome.Ok)?.value
                            ?: return true.also { afterTry(agent, started = false, short = false) }
                    return when (answer) {
                        PhoneAgentInstallResult.Started -> {
                            log.i { "update started" }
                            // A reinstall of the same version: the helper's loaded copies of it keep running the old
                            // files until the helper starts them again, so it is started again once the install ends.
                            if (agent in sameVersion()) reloadAfter += agent
                            // Kept until the install ends, so a stop or crash in the middle resumes it next run.
                            phones.markUpdating(agent)
                            appScope.launch(dispatchers.io) { safely { finish(agent) } }
                            true
                        }
                        is PhoneAgentInstallResult.Refused ->
                            (answer.issue != PhoneAgentInstallIssue.Busy).also { spent ->
                                if (spent) {
                                    val space = answer.issue == PhoneAgentInstallIssue.Space
                                    afterTry(agent, started = false, short = space)
                                }
                            }
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
        downloads.agents = this
        downloads.start()
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
                    if (reloadAfter.remove(agent)) reloadHelper() else restartOlderHelper()
                    phones.noteHelperInPlace()
                    phones.markUpdating(null)
                    downloads.helperShort.value = null
                    updaterRef?.finished(agent)
                    return
                }
                // No record of it (yet or any more). A job that still runs is waited for; one that ended Failed
                // without a record did not finish: the wait is over, never an "Updating" for ever.
                null -> {
                    when (setup.progress.value.state) {
                        SetupState.Running -> Unit
                        SetupState.Failed -> {
                            log.i { "update ended without finishing" }
                            updaterRef?.settle(agent, AgentUpdateStep.Failed)
                            return
                        }
                        else -> {
                            restartOlderHelper()
                            return
                        }
                    }
                }
                PhoneAgentInstallPhase.Failed,
                PhoneAgentInstallPhase.Interrupted -> {
                    updaterRef?.settle(agent, AgentUpdateStep.Failed)
                    return
                }
            }
        }
        // Past the bound (a quarter hour): the wait ends as a failure the person can try again.
        log.i { "update ran past its bound" }
        updaterRef?.settle(agent, AgentUpdateStep.Failed)
    }

    /**
     * The end of an install the person's Try again or the automatic try handed over. [started]: it runs, so the
     * decision says Updating again and a watcher follows it to its end. Otherwise (a failed hand-over, or a refusal
     * Try again cannot change; [short] when it was for want of room) no watcher exists, so the wait it announced is
     * ended here as Failed a moment after the decision, and Home's row says how much room is missing (the check runs
     * before the job starts, so nothing old was touched).
     */
    internal fun afterTry(agent: AgentId, started: Boolean, short: Boolean) {
        if (started) {
            downloads.helperShort.value = null
            updaterRef?.settle(agent, AgentUpdateStep.Updating)
            appScope.launch(dispatchers.io) { safely { finish(agent) } }
            return
        }
        appScope.launch(dispatchers.io) {
            var room: PhoneAgentSpace? = null
            if (short) safely { room = phones.installSpace(agent.raw)?.takeIf { it.short } }
            downloads.helperShort.value =
                room?.let {
                    HomeShortSpace(
                        phones.nameOf(agent) ?: agent.raw,
                        ((it.neededBytes + MB - 1) / MB).toInt(),
                        (it.freeBytes / MB).toInt(),
                    )
                }
            delay(UNSTARTED_GRACE.seconds)
            updaterRef?.settle(agent, AgentUpdateStep.Failed)
        }
    }

    /** The agents behind only by their install record (same version), for the update's end. */
    private fun sameVersion(): Set<AgentId> =
        phones.updateFacts()?.let { facts -> recordBehind(facts.installed) - facts.behind }.orEmpty()

    private val reloadAfter = java.util.concurrent.ConcurrentHashMap.newKeySet<AgentId>()

    /**
     * After a same-version reinstall: the helper is stopped with the guarded stop (refused while a turn runs) and
     * started again, so every conversation of that agent runs the new files; while a turn runs it tries again every
     * 15 s (up to two hours). A helper that does not run starts with the new files anyway.
     */
    private suspend fun reloadHelper() {
        val owner = phones.state.value.owner ?: return
        val linux = BuiltinLinux.get(app)
        val reload =
            io.github.eslamasabry.oc.engine.agents.providers.HelperReload(
                running = {
                    try {
                        linux.agentHost.status(owner.raw)["running"] == true
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                },
                busy = { runtime.agentWorkBusy() },
                stopGuarded = {
                    try {
                        linux.agentHost.stop(owner.raw, guardedForUpdate = true)
                        true
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                },
                start = { helper.checkHelper() },
            )
        repeat(RELOAD_TRIES) {
            if (reload.tryNow()) {
                log.i { "helper started again after a reinstall" }
                return
            }
            delay(RELOAD_EVERY.seconds)
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
        const val UNSTARTED_GRACE = 2L
        const val MB = 1_000_000L
        const val WAIT_STEPS = 180
        const val RELOAD_EVERY = 15L
        const val RELOAD_TRIES = 480
    }
}

/**
 * Whether the active network charges for data, for the automatic updates (the agents' and OpenCode's): a download of
 * hundreds of megabytes never goes over paid data, and a network that cannot be read counts as paid.
 */
internal fun activeNetworkMetered(app: Application): Boolean =
    try {
        app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
    } catch (_: Exception) {
        true
    }

@ContributesTo(AppScope::class)
interface PhoneAgentAutoUpdateAccessors {
    val phoneAgentAutoUpdate: PhoneAgentAutoUpdate
}
