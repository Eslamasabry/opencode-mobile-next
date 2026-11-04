package io.github.eslamasabry.oc.engine.runtime.recovery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.server.DeliberateStop
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Why the previous process ended, in the app's terms (Flutter `AppExitKind`). Same values, same order, as
 * `:engine:diagnostics` `ExitKind`, which classifies Android's `ApplicationExitInfo` numbers; the binding maps it by
 * name (this module depends on `:core:common` only).
 */
public enum class PreviousExitKind {
    Normal,
    Update,
    ForceStop,
    LowMemory,
    MemoryLimit,
    Crash,
    Killed,
    ;

    /** Whether the person hears about it when something was running. */
    public val notable: Boolean get() = this != Normal && this != Update

    /** A crash is ours to fix, not a phone setting to change. */
    public val offersKeepAlive: Boolean get() = this != Crash
}

/** The newest exit not reported before, as `:engine:diagnostics` `LaunchIntake` read it. */
public data class PreviousExit(val kind: PreviousExitKind, val at: Instant)

/**
 * What the app learns at start about the process before this one (Flutter `AppLaunchReport`, `oc/lifecycle
 * launchReport`). [previousServices] are the built-in Ubuntu's services that ran when it ended ([SERVER], [TEAM]); a
 * Stop the person gave clears them, so anything here was ended by Android.
 */
public data class LaunchFacts(val exit: PreviousExit? = null, val previousServices: List<String> = emptyList()) {
    public companion object {
        public const val SERVER: String = "server"
        public const val TEAM: String = "aiteam"
    }
}

/** Reads the launch report once (the binding over `:engine:diagnostics` `LaunchIntake.runOnce`). */
public fun interface LaunchFactsPort {
    public suspend fun launchFacts(): LaunchFacts
}

/** One saved profile of the phone's own OpenCode. [hasTeam]: the built-in AI Team is on for it (D6). */
public data class PhoneProfile(val id: ProfileId, val hasTeam: Boolean = false)

/** The profiles recovery chooses from, and the one the app opens on (null: none, or another server). */
public data class RecoveryScene(val phoneProfiles: List<PhoneProfile>, val active: ProfileId?) {
    public val activeIsPhone: Boolean get() = active != null && phoneProfiles.any { it.id == active }
}

/** The one shared healing owner (phone server healing): the only path that restarts a server on its own. */
public fun interface HealingOwner {
    public suspend fun recover(profile: ProfileId)
}

/** A confirmed restart, filed once per exit episode ([eventId] `app-exit:<exit time in µs>`). */
public data class RestartAct(val profile: ProfileId, val eventId: String, val at: Instant)

/** A notable exit, for diagnostics and the persisted problem report (numbers and kinds only, never OS text). */
public data class ExitReport(val exit: PreviousExit, val whileRunning: List<String>)

/**
 * What the person is told once after Android ended the app while the phone's OpenCode (and maybe the AI Team) ran in
 * it (Flutter `AppExitNotice`). [recoveryAllowed] is the policy for this launch, not proof of success: false is never
 * worded "starting again". [phoneProfile] lets a page showing another server offer to switch to it.
 */
public data class AppExitNotice(
    val kind: PreviousExitKind,
    val at: Instant,
    val teamStopped: Boolean,
    val recoveryAllowed: Boolean = true,
    val phoneProfile: ProfileId? = null,
) {
    public val offersKeepAlive: Boolean get() = kind.offersKeepAlive
}

/** How one [AppExitRecovery.runOnce] ended, for tests and the trace. */
public enum class RecoveryOutcome {
    /** Already ran in this process. */
    AlreadyRan,

    /** Nothing of ours ran when it ended (or the person had stopped it): nothing to bring back. */
    NothingRan,

    /** The shared healing owner was handed the profile. */
    Delegated,

    /** The app opens on the phone's own server: the shell's app-open start (`AppOpenStart`) starts it. */
    ShellStarts,

    /** No saved phone profile. */
    NoProfile,

    /** Restarting the phone server is off for that profile. */
    PolicyOff,

    /** A stopped server was started and answers; one [RestartAct] filed. */
    Restarted,

    /** Nothing was started: already running, withdrawn, or the start failed. No act filed. */
    NotRestarted,
}

/**
 * On start: learns why the previous process ended and what ran in it, brings back the phone's OpenCode (and the AI Team)
 * Android stopped with it, and holds the one-time notice (port of lib/builtin/app_exit_recovery.dart; plan 11 §6.4,
 * B-LINUX-010…013).
 *
 * The restart goes through the one shared [healing] owner when there is one; otherwise through [autoStart] (the one
 * start path) and only when another server is selected, since the shell starts the server the app opens on. Gated on
 * [RuntimeAutomation.RestartPhoneServer], read before and again during the start. A [RestartAct] is filed only for a
 * confirmed start of a stopped server, once per process.
 */
public class AppExitRecovery(
    private val launch: LaunchFactsPort,
    private val deliberateStop: DeliberateStop,
    automation: AutomationGate,
    private val autoStart: PhoneAutoStart,
    private val server: PhoneServerPort,
    private val clock: Clock,
    private val healing: HealingOwner? = null,
    private val reviveTeam: (suspend (ProfileId) -> Unit)? = null,
    private val onRestart: (suspend (RestartAct) -> Boolean)? = null,
    private val onNotableExit: (suspend (ExitReport) -> Unit)? = null,
) {
    private val restart = RecoveryGate(automation, RuntimeAutomation.RestartPhoneServer)
    private var ran = false
    private val noticeState = MutableStateFlow<AppExitNotice?>(null)
    private val phoneRunningState = MutableStateFlow(false)

    /** The notice to show until [dismiss]. */
    public val notice: StateFlow<AppExitNotice?> = noticeState.asStateFlow()

    /** The phone's OpenCode runs again while another server is selected: the notice says "running again". */
    public val phoneServerRunning: StateFlow<Boolean> = phoneRunningState.asStateFlow()

    /** The last start's facts, for diagnostics and tests. */
    public var facts: LaunchFacts? = null
        private set

    public fun dismiss() {
        noticeState.value = null
    }

    /** Runs once per app process; later calls do nothing. */
    public suspend fun runOnce(scene: RecoveryScene): RecoveryOutcome {
        if (ran) return RecoveryOutcome.AlreadyRan
        ran = true
        val report = launch.launchFacts()
        facts = report
        val stoppedOnPurpose = scene.phoneProfiles.any { deliberateStop.isMarked(it.id) }
        val serverWas = !stoppedOnPurpose && LaunchFacts.SERVER in report.previousServices
        // The team runs on the phone's OpenCode: a server stopped on purpose leaves nothing to bring back.
        val teamWas = !stoppedOnPurpose && LaunchFacts.TEAM in report.previousServices
        val wasRunning = serverWas || teamWas
        val exit = report.exit
        if (exit != null && ExitReportRule.reported(exit.kind, wasRunning)) {
            onNotableExit?.invoke(ExitReport(exit, report.previousServices.takeIf { wasRunning }.orEmpty()))
        }
        if (!wasRunning) return RecoveryOutcome.NothingRan
        val chosen = if (scene.activeIsPhone) scene.active else inAppProfile(scene, team = teamWas)?.id
        if (exit != null && exit.kind.notable) {
            noticeState.value =
                AppExitNotice(
                    kind = exit.kind,
                    at = exit.at,
                    teamStopped = teamWas,
                    recoveryAllowed = chosen != null && restart.allows(chosen),
                    phoneProfile = chosen,
                )
        }
        val eventId = "app-exit:" + micros(exit?.at ?: clock.now())
        val outcome = restart(scene, chosen, eventId)
        if (noticeState.value != null && !scene.activeIsPhone) readPhoneServer()
        if (teamWas && reviveTeam != null) reviveTeam(scene, reviveTeam)
        return outcome
    }

    private suspend fun restart(scene: RecoveryScene, chosen: ProfileId?, eventId: String): RecoveryOutcome {
        if (healing != null) {
            if (chosen == null) return RecoveryOutcome.NoProfile
            healing.recover(chosen)
            return RecoveryOutcome.Delegated
        }
        // The shell starts the server it opens on; nothing to add here.
        if (scene.activeIsPhone) return RecoveryOutcome.ShellStarts
        val profile = chosen ?: return RecoveryOutcome.NoProfile
        if (!restart.allows(profile)) return RecoveryOutcome.PolicyOff
        val started = autoStart.autoStartIfStopped(profile) { restart.allows(profile) }
        if (!started) return RecoveryOutcome.NotRestarted
        onRestart?.invoke(RestartAct(profile, eventId, clock.now()))
        return RecoveryOutcome.Restarted
    }

    /** Brings back the AI Team on the server's terms: the profile still has it on and the policy allows it. */
    private suspend fun reviveTeam(scene: RecoveryScene, revive: suspend (ProfileId) -> Unit) {
        val profile = inAppProfile(scene, team = true) ?: return
        if (!profile.hasTeam || !restart.allows(profile.id)) return
        revive(profile.id)
    }

    private suspend fun readPhoneServer() {
        val status = (server.refreshStatus() as? Outcome.Ok)?.value ?: return
        if (status.serverRunning) phoneRunningState.value = true
    }

    private fun inAppProfile(scene: RecoveryScene, team: Boolean): PhoneProfile? {
        val candidates = scene.phoneProfiles
        if (team) candidates.firstOrNull { it.hasTeam }?.let { return it }
        return candidates.firstOrNull()
    }

    private fun micros(at: Instant): Long = at.epochSeconds * 1_000_000 + at.nanosecondsOfSecond / 1_000
}

/** Which exits are a problem worth keeping (B-LINUX-013). */
public object ExitReportRule {
    /**
     * A notable exit is kept, except a force stop of an app with nothing of ours running: that is the person (or the
     * phone's housekeeping, a clean reinstall, "clear data") closing an idle app, and must not light the problem badge.
     * A crash, a memory kill, or any stop that took our server or team with it is kept.
     */
    public fun reported(kind: PreviousExitKind, wasRunning: Boolean): Boolean =
        kind.notable && !(kind == PreviousExitKind.ForceStop && !wasRunning)
}
