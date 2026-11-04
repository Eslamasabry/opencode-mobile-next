package io.github.eslamasabry.oc.engine.runtime.switching

import io.github.eslamasabry.oc.core.common.Outcome

/*
 * Port of `prepareManagedRuntimeSwitch` (lib/state/connection/connect.dart): before an explicitly confirmed switch of
 * the managed OpenCode runtime, check the work this app observes on the phone's managed servers, durably suspend
 * their automatic recovery, clear the remembered selection, and only then retire the local transport. Saved profiles,
 * credentials, drafts and queued prompts (the saved offline queue, readable or not) stay untouched.
 */

/** The work the open connection holds right now (Dart `ConnectionController` fields). Counts only, never content. */
public data class LocalWork(
    val busySessions: Int = 0,
    val retryingSessions: Int = 0,
    val permissions: Int = 0,
    val questions: Int = 0,
    val forms: Int = 0,
    /** Admitted inbox items (`session.inbox.enqueued`) not yet taken by the server. */
    val inboxItems: Int = 0,
    val flushingOfflineQueue: Boolean = false,
    /** The id of the queued prompt on the wire, if any. */
    val queuedPromptInFlight: String? = null,
    val pendingReplies: Int = 0,
    val revertMutations: Int = 0,
) {
    val any: Boolean
        get() =
            busySessions > 0 || retryingSessions > 0 || permissions > 0 || questions > 0 || forms > 0 ||
                inboxItems > 0 || flushingOfflineQueue || queuedPromptInFlight != null || pendingReplies > 0 ||
                revertMutations > 0
}

/** What the background profile monitor saw on one profile (Dart `ProfileMonitorSnapshot`). */
public data class MonitoredWork(val isCurrent: Boolean, val runningCount: Int, val pendingCount: Int)

/** One saved offline-queue entry: only the facts the switch reads. */
public data class QueuedPromptRef(val id: String, val profileId: String)

/** What the switch reads and does through the connection (the Android binding is the connection controller). */
public interface SwitchConnection {
    /** The saved profiles. */
    public fun profiles(): List<LocalProfile>

    /** The connected profile, else the selected one. */
    public fun currentProfile(): LocalProfile?

    public fun localWork(): LocalWork

    public fun monitored(profileId: String): MonitoredWork?

    /** The saved offline queue; an unreadable queue reads empty (Dart `OfflineQueueStore.load`) and is never written. */
    public fun savedQueue(): List<QueuedPromptRef>

    /** The serialized active-profile writer: clears `oc.activeProfile`. Failed when storage refused. */
    public suspend fun clearActiveProfile(): Outcome<Unit>

    /** Retire the transport, keeping the (already cleared) active pointer as it is. */
    public suspend fun disconnectKeepingActive()
}

/** Durably suspends automatic recovery for one profile without turning off the person's policy. */
public fun interface RecoverySuspension {
    public suspend fun suspendForProfile(profileId: String): Outcome<Unit>
}

/** Why the switch did not go ahead. Each maps 1:1 to the Dart `StateError` sentence named on it. */
public enum class SwitchBlock {
    /** "Finish the running work and pending requests on this phone before switching OpenCode versions." */
    LocalWorkRunning,

    /** "This phone has running work or pending requests. Open its server profile and finish them before switching…" */
    MonitoredWorkRunning,

    /** "Wait for the prompt being sent on this phone to finish before switching OpenCode versions." */
    SendInFlight,

    /** "Automatic server recovery could not be disabled. Try again before switching OpenCode versions." */
    RecoveryNotSuspended,

    /** "The local connection could not be cleared. Try again before switching OpenCode versions." */
    SelectionNotCleared,
}

public sealed interface SwitchResult {
    /** Ready to switch. [disconnected] is true when a managed local connection was retired (remote work stays). */
    public data class Ready(val disconnected: Boolean) : SwitchResult

    /** Nothing was disconnected; no transport was closed. */
    public data class Blocked(val reason: SwitchBlock) : SwitchResult
}

public class ManagedRuntimeSwitch(
    private val connection: SwitchConnection,
    private val recovery: RecoverySuspension,
) {
    private fun managedIds(): Set<String> = connection.profiles().filter(ManagedLocalServer::isManagedLocal).map { it.id }.toSet()

    /** Work this app observes on the managed servers; checked before and after every persistence step. */
    private fun knownWork(): SwitchBlock? {
        val ids = managedIds()
        val work = connection.localWork()
        if (ManagedLocalServer.isManagedLocal(connection.currentProfile()) && work.any) return SwitchBlock.LocalWorkRunning
        for (id in ids) {
            val seen = connection.monitored(id)
            if (seen?.isCurrent == true && (seen.runningCount > 0 || seen.pendingCount > 0)) return SwitchBlock.MonitoredWorkRunning
        }
        val inFlight = work.queuedPromptInFlight
        if (inFlight != null && connection.savedQueue().any { it.id == inFlight && it.profileId in ids }) {
            return SwitchBlock.SendInFlight
        }
        return null
    }

    public suspend fun prepare(): SwitchResult {
        knownWork()?.let { return SwitchResult.Blocked(it) }
        for (id in managedIds()) {
            if (recovery.suspendForProfile(id) is Outcome.Failed) return SwitchResult.Blocked(SwitchBlock.RecoveryNotSuspended)
        }
        // Work may have arrived while recovery revocation was being persisted.
        knownWork()?.let { return SwitchResult.Blocked(it) }
        if (!ManagedLocalServer.isManagedLocal(connection.currentProfile())) return SwitchResult.Ready(disconnected = false)
        // Clear the remembered connection before retiring its transport, so lifecycle recovery cannot reconnect the
        // old runtime at this address. A storage refusal stops the switch (disconnect alone would absorb it).
        if (connection.clearActiveProfile() is Outcome.Failed) return SwitchResult.Blocked(SwitchBlock.SelectionNotCleared)
        knownWork()?.let { return SwitchResult.Blocked(it) }
        if (!ManagedLocalServer.isManagedLocal(connection.currentProfile())) return SwitchResult.Ready(disconnected = false)
        connection.disconnectKeepingActive()
        return SwitchResult.Ready(disconnected = true)
    }
}
