package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.time.Duration

/*
 * The ports of lib/builtin/phone_server_healing.dart and the parts it owns (phone_server_idle.dart,
 * phone_agent_work_watch.dart, builtin_server_recovery.dart): the native built-in Linux calls and what healing reads
 * from the connection controller and the profile store.
 */

/** A saved in-app server profile (Dart `looksLikeInAppServer`). [password] goes only to the starter and the probe. */
public data class HealingProfile(val id: String, val password: String) {
    public val target: StartTarget get() = StartTarget(ProfileId(id), password)

    override fun toString(): String = "HealingProfile(id=$id, password=<hidden>)"
}

/** The connection's stream state (Dart `StreamStatus`). */
public enum class LinkStatus { Connected, Connecting, Reconnecting, Disconnected }

/** The person's automation switches (Dart `AutomationBehavior`), read per profile. */
public enum class AutomationBehavior { RestartPhoneServer, PollRestartHealth, Reconnect }

/** What healing reads from and asks of the connection controller and the profile store. */
public interface HealingConnection {
    /** Saved profiles that look like the in-app server, readable or not. */
    public fun inAppProfiles(): List<HealingProfile>

    /** False while the profile is being deleted or is gone (deletion admission). */
    public fun isReadable(profileId: String): Boolean

    /** `oc.builtinServerOwner`: null when never written, empty when cleared by a deletion. */
    public fun runtimeOwner(): String?

    /** Persists [profileId] as the runtime owner; false when storage refused. */
    public suspend fun claimRuntimeOwner(profileId: String): Boolean

    /** `oc.phoneAgentOwner.<id>`: the canonical owner of the local agent helper, else [profileId]. */
    public fun agentOwnerId(profileId: String): String

    /** The selected (or connected) profile id. */
    public fun selectedProfileId(): String?

    public fun status(): LinkStatus

    /** A live transport exists (Dart `api != null || hasConnectedServer`). */
    public fun hasConnectedServer(): Boolean

    /** Connects [profileId]; true when the attempt ended with an error (Dart `lastError != null`). */
    public suspend fun connect(profileId: String): Boolean

    /** Records the automatic restart in the while-away log; false when the profile cannot take it. */
    public suspend fun recordRestart(profileId: String, eventId: String, atEpochMillis: Long): Boolean

    public fun allows(profileId: String, behavior: AutomationBehavior): Boolean
}

/** The native lease answer (Dart `BuiltinWorkLeaseStatus`). */
public data class LeaseAnswer(val held: Boolean, val capped: Boolean = false)

/** The built-in Linux calls healing makes beyond the one start path ([PhoneServerPort]). */
public interface HealingHost : PhoneServerPort {
    public suspend fun resumeIdleStopped(profileId: String, expectedIdleGeneration: Long): Outcome<RuntimeStatus>

    public suspend fun completeIdleResume(profileId: String, expectedIdleGeneration: Long): Outcome<RuntimeStatus>

    /** Native idle truth for the readable server alias; null busy is unknown. */
    public suspend fun observeAgentWork(profileId: String, busy: Boolean?): Outcome<Unit>

    /** One bounded CPU lease for the canonical agent owner; [hold] zero closes CPU but keeps the logical key. */
    public suspend fun setAgentWorkLease(profileId: String, leaseId: String, on: Boolean, hold: Duration): Outcome<LeaseAnswer>

    /** Moves the native recovery generation: any automatic restart in flight is refused. */
    public suspend fun cancelRecovery(): Outcome<Unit>

    /** Binds native recovery authority to [profileId] (enabled per policy); answers the native budget's attempts. */
    public suspend fun bindRecovery(profileId: String, enabled: Boolean): Outcome<Int>
}

/** One profile's legacy crash-restart budget (`oc.builtinRecovery.<id>`, version 1). Times are epoch milliseconds. */
public data class RecoveryBudget(
    val attempts: Int = 0,
    val nextAt: Long? = null,
    val pending: Boolean = false,
    val eventId: String? = null,
    val recoveryGeneration: Long? = null,
    val confirmedAt: Long? = null,
) {
    public fun confirmed(at: Long): RecoveryBudget = copy(confirmedAt = at)

    public fun finished(): RecoveryBudget = RecoveryBudget(attempts = attempts, nextAt = nextAt)
}

/** Reads and writes the legacy budget; [read] answers null when the saved value is unreadable (fails closed). */
public interface RecoveryBudgetStore {
    public fun read(profileId: String): RecoveryBudget?

    public suspend fun write(profileId: String, budget: RecoveryBudget): Boolean

    public companion object {
        public fun keyFor(profileId: String): String = "oc.builtinRecovery.$profileId"
    }
}

internal suspend fun HealingHost.statusOrNull(): RuntimeStatus? = (refreshStatus() as? Outcome.Ok)?.value
