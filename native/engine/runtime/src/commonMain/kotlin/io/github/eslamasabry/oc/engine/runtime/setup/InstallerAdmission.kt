package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

/**
 * Port of the installer admission and ownership monitor decisions of the Flutter app's native side
 * (android/app/.../BuiltinLinux.kt `reclaimDeadInstaller`, `admitCheckProcess`, the pre-launch guards of
 * `startQualifiedInstaller`, `monitorInstallerOwnership`, `finishInstaller`/`drainAndReleaseInstaller`, with
 * NativeInstallerAdmission.kt and NativeProcRace.kt), as pure Kotlin over [InstallerOwnershipPort]. The Android
 * binding maps the port onto :android:linux BuiltinLinux (/proc inventory, durable ticket, PRoot launch).
 *
 * The slot is confined to one serial lane (BuiltinLinux's `this` + `installerGuard` locks); nothing here signals a
 * process: only [InstallerOwnershipPort.drain] may.
 */
public enum class InstallerOperation { Check, Install }

public enum class InstallerTarget { OpenCode1, OpenCode2, Paseo, Claude, LegacyClaude }

/** A launched installer process; identity is the launch. */
public interface InstallerLaunch {
    public val alive: Boolean
}

/** The durable installer ticket (private lifecycle metadata only: never a script, output or account value). */
public data class InstallerTicket(
    val id: String,
    val targets: Set<InstallerTarget> = InstallerTarget.entries.toSet(),
    val operation: InstallerOperation = InstallerOperation.Check,
    val observed: List<Int> = emptyList(),
)

/** One launch the port started: the process, its nonce and the committed ticket. */
public data class LaunchedInstaller(val launch: InstallerLaunch, val launchId: String, val committed: InstallerTicket)

/** What the slot needs from the phone. Failures are `Failure.Runtime` values; nothing here throws. */
public interface InstallerOwnershipPort {
    /** The durable ticket, or null. */
    public fun readTicket(): InstallerTicket?

    /** `remove("ticket").commit()`: false when the store refused; generation and rootfs are kept. */
    public fun clearTicket(): Boolean

    public fun saveTicket(ticket: InstallerTicket): Outcome<Unit>

    /**
     * The drain plan's still-running owned members (empty = quiescent). [live]/[launchId] name the current warm owner
     * (the live plan); null is the cold plan. An unknown writer or an unconfirmed absence is a failure.
     */
    public fun ownedMembers(ticket: InstallerTicket, live: InstallerLaunch?, launchId: String?): Outcome<List<Int>>

    /** Stops the ticket's exact members and proves them gone. */
    public fun drain(ticket: InstallerTicket, live: InstallerLaunch?, launchId: String?): Outcome<Unit>

    /** Releases the work lease of a finished launch. */
    public fun releaseLease(launch: InstallerLaunch)

    /** One cold recovery (rollback of a pending INSTALL journal) before the first guest command. */
    public fun recoverColdComponentUpdates(): Outcome<Unit>

    /** Saves the prepared ticket, launches the gated script and commits its exact identity. */
    public fun launch(
        script: String,
        targets: Set<InstallerTarget>,
        operation: InstallerOperation,
        agentUser: Boolean,
    ): Outcome<LaunchedInstaller>
}

/** NativeInstallerAdmission: warm admission only, with exact ownership proof. */
public object InstallerAdmission {
    /** The installer ticket read first, and whether the cached owner is still alive. */
    public data class Claim(val ticket: InstallerTicket?, val ownerLive: Boolean)

    /**
     * Exit or missing /proc entries alone cannot authorize clearing a writer: the complete inventory is re-evaluated
     * immediately before the durable change, twice, with the owner and ticket rechecked around each proof.
     */
    public fun reclaim(
        claim: Claim,
        ownerCurrent: () -> Boolean,
        readTicket: () -> InstallerTicket?,
        quiescent: (InstallerTicket) -> Outcome<Boolean>,
        clearTicket: (InstallerTicket) -> Outcome<Boolean>,
    ): Outcome<Boolean> {
        val ticket = claim.ticket
        if (ticket == null || claim.ownerLive || !ownerCurrent() || readTicket() != ticket) return false.ok()
        repeat(2) {
            when (val proof = quiescent(ticket)) {
                is Outcome.Failed -> return proof
                is Outcome.Ok -> if (!proof.value) return false.ok()
            }
            if (!ownerCurrent() || readTicket() != ticket) return false.ok()
        }
        return clearTicket(ticket)
    }
}

/** NativeProcRace.Strikes: consecutive failed checks before the installer is stopped. */
public class InstallerStrikes(private val limit: Int = MONITOR_ATTEMPTS) {
    init {
        require(limit >= 1)
    }

    public var count: Int = 0
        private set

    public fun failed(): Boolean = ++count >= limit

    public fun passed() {
        count = 0
    }

    public companion object {
        /** ~0.5 s at the 100 ms poll. */
        public const val MONITOR_ATTEMPTS: Int = 5
    }
}

/** How a finished installer ended (`finishInstaller`). */
public sealed interface InstallerFinish {
    public data object Done : InstallerFinish

    /** The ownership monitor stopped it ([cause] kept): Continue setup may run the step again. */
    public data class Interrupted(val cause: Failure) : InstallerFinish

    public data class Refused(val failure: Failure) : InstallerFinish
}

/**
 * The one installer slot. [elapsedMillis] is the monotonic clock (SystemClock.elapsedRealtime); [pause] waits that
 * many milliseconds. [componentUpdatesRecovered] starts false on a cold process: the first admission keeps its boot
 * and rollback recovery authority.
 */
public class InstallerSlot(
    private val port: InstallerOwnershipPort,
    private val installed: () -> Boolean,
    private val elapsedMillis: () -> Long,
    private val pause: suspend (Long) -> Unit,
    componentUpdatesRecovered: Boolean = false,
) {
    public var process: InstallerLaunch? = null
        private set
    public var launchId: String? = null
        private set
    public var monitorFailure: Failure? = null
        private set
    public var componentUpdatesRecovered: Boolean = componentUpdatesRecovered
        private set

    /** Adopts a launch already in the slot (a warm process, or a test fixture). */
    public fun select(launch: InstallerLaunch?, id: String?) {
        process = launch
        launchId = id
    }

    /** A failed launch/completion must not permanently reserve a dead warm writer. Never signals. */
    public fun reclaimDeadInstaller(): Outcome<Boolean> {
        val cached = process
        if (cached?.alive == true) return false.ok()
        if (!componentUpdatesRecovered && cached == null) return false.ok()
        val owner = launchId
        if (cached != null && owner == null) return componentUpdateFailure()
        val currentOwner = { process === cached && launchId == owner && cached?.alive != true }
        return InstallerAdmission.reclaim(
            InstallerAdmission.Claim(port.readTicket(), ownerLive = false),
            currentOwner,
            port::readTicket,
            { ticket -> port.ownedMembers(ticket, cached, owner).let { if (it is Outcome.Ok) it.value.isEmpty().ok() else it as Outcome.Failed } },
            { ticket ->
                if (!currentOwner() || port.readTicket() != ticket) {
                    componentUpdateFailure()
                } else if (!port.clearTicket()) {
                    false.ok()
                } else {
                    process = null
                    launchId = null
                    // A dead INSTALL can leave a journal: rollback still precedes the next guest command.
                    this.componentUpdatesRecovered = false
                    cached?.let(port::releaseLease)
                    true.ok()
                }
            },
        )
    }

    /** A check queues behind a running installer for at most [timeoutMillis] (capped by the admission window). */
    public suspend fun admitCheck(
        script: String,
        timeoutMillis: Long,
        began: Long,
        agentUser: Boolean,
    ): Outcome<InstallerLaunch> {
        val queueDeadline = began + minOf(timeoutMillis, RUN_ADMISSION_WINDOW_MS)
        while (true) {
            val reclaimed = reclaimDeadInstaller()
            if (reclaimed is Outcome.Failed) return reclaimed
            if (process == null) {
                if (elapsedMillis() - began > timeoutMillis) return busy()
                return startInstaller(script, InstallerTarget.entries.toSet(), InstallerOperation.Check, agentUser)
            }
            if (elapsedMillis() >= queueDeadline) return busy()
            pause(PROCESS_POLL_MS)
        }
    }

    /** Every real pre-launch admission guard, then the port's launch. */
    public fun startInstaller(
        script: String,
        targets: Set<InstallerTarget>,
        operation: InstallerOperation,
        agentUser: Boolean,
    ): Outcome<InstallerLaunch> {
        if (!installed()) return componentUpdateFailure()
        val reclaimed = reclaimDeadInstaller()
        if (reclaimed is Outcome.Failed) return reclaimed
        if (process != null) return componentUpdateFailure()
        if (!componentUpdatesRecovered) {
            val recovered = port.recoverColdComponentUpdates()
            if (recovered is Outcome.Failed) return recovered
            componentUpdatesRecovered = true
        }
        if (port.readTicket() != null) return componentUpdateFailure()
        monitorFailure = null
        return when (val launched = port.launch(script, targets, operation, agentUser)) {
            is Outcome.Failed -> launched
            is Outcome.Ok -> {
                process = launched.value.launch
                launchId = launched.value.launchId
                launched.value.launch.ok()
            }
        }
    }

    /**
     * Watches [launched]'s ownership until it leaves the slot. One read can catch apt's processes mid-exit; only a
     * failure that stays for consecutive reads records [monitorFailure] and drains the launch, and only while the
     * exact launch is still current (a replaced launch is never blamed).
     */
    public suspend fun monitor(launched: InstallerLaunch, nonce: String, committed: InstallerTicket) {
        val strikes = InstallerStrikes()
        while (process === launched) {
            val step: Outcome<Unit> = observe(launched, nonce) ?: return
            if (step is Outcome.Failed) {
                if (!strikes.failed()) {
                    pause(OWNERSHIP_POLL_MS)
                    continue
                }
                if (process !== launched || launchId != nonce) return
                monitorFailure = step.failure
                port.drain(committed, launched, nonce)
                return // The durable receipt remains; completion must still prove the drain.
            }
            strikes.passed()
            pause(OWNERSHIP_POLL_MS)
        }
    }

    /** One observation; null means the monitor has nothing left to watch. */
    private fun observe(launched: InstallerLaunch, nonce: String): Outcome<Unit>? {
        if (process !== launched) return null
        val ticket = port.readTicket() ?: return null
        if (ticket.id != nonce) return null
        return when (val members = port.ownedMembers(ticket, launched, nonce)) {
            is Outcome.Failed -> members
            is Outcome.Ok -> if (members.value != ticket.observed) port.saveTicket(ticket.copy(observed = members.value)) else Unit.ok()
        }
    }

    /** Drains the finished installer, frees its slot first, then reports a monitor stop as [InstallerFinish.Interrupted]. */
    public fun finish(launch: InstallerLaunch): InstallerFinish {
        port.releaseLease(launch)
        if (process !== launch) return InstallerFinish.Refused(recoveryFailure)
        val ticket = port.readTicket() ?: return InstallerFinish.Refused(recoveryFailure)
        val owner = launchId ?: return InstallerFinish.Refused(recoveryFailure)
        val drained = port.drain(ticket, launch, owner)
        if (drained is Outcome.Failed) return InstallerFinish.Refused(drained.failure)
        if (!port.clearTicket()) return InstallerFinish.Refused(recoveryFailure)
        process = null
        launchId = null
        // Release the slot before reporting failure: Continue setup must be able to repair the interrupted apt
        // transaction in this same app process, without weakening the drain proof.
        val failure = monitorFailure ?: return InstallerFinish.Done
        monitorFailure = null
        componentUpdatesRecovered = false
        return InstallerFinish.Interrupted(failure)
    }

    public companion object {
        public const val RUN_ADMISSION_WINDOW_MS: Long = 10_000L
        public const val PROCESS_POLL_MS: Long = 50L
        public const val OWNERSHIP_POLL_MS: Long = 100L

        private val recoveryFailure: Failure = Failure.Runtime(RuntimeProblem.ComponentRecoveryRequired)

        private fun componentUpdateFailure(): Outcome<Nothing> = recoveryFailure.failed()

        private fun busy(): Outcome<Nothing> = Failure.Runtime(RuntimeProblem.SlotBusy).failed()
    }
}
