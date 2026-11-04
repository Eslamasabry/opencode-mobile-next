package io.github.eslamasabry.oc.engine.attention.monitor

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * The key-value store the monitor keeps its per-profile records in (Android: the app's preferences). Reads are
 * synchronous snapshots; a write returns false when the store refused it. Every key the monitor writes is
 * `oc.<what>.<profileId>` ([ProfileMonitor.profileScopedKeys]) so the profile deletion sweep finds it.
 */
public interface MonitorPrefs {
    public fun getString(key: String): String?

    public fun getStringList(key: String): List<String>?

    public fun contains(key: String): Boolean

    public suspend fun putString(key: String, value: String): Boolean

    public suspend fun putStringList(key: String, value: List<String>): Boolean

    public suspend fun remove(key: String): Boolean

    /** Re-reads the durable copy after a refused write. */
    public suspend fun reload() {}
}

/** Which kind of server a profile is; only the monitor's own reachability rules depend on it. */
public enum class MonitorBackend { OpenCode, Paseo, Codex }

public data class MonitorLocation(val directory: String? = null, val workspace: String? = null)

/**
 * One saved server as the monitor sees it. [credential] is hashed with a per-process salt at once and never kept;
 * [usable] is false when the profile needs its password or token typed again, its cleartext address is unconfirmed or
 * its address does not pass the app's address rules (the read then counts as a failure, with backoff).
 */
public data class MonitorProfile(
    val id: String,
    val baseUrl: String,
    val username: String = "",
    val flavor: String = "",
    val backend: MonitorBackend = MonitorBackend.OpenCode,
    val credential: String = "",
    val usable: Boolean = true,
    /** A Paseo daemon's bound project folder, read when no project is chosen yet. */
    val boundDirectory: String? = null,
    /** The phone project engine's bearer token (B-AGENT-138); empty means missing. */
    val teamEngineToken: String = "",
    val hasPhoneEngine: Boolean = false,
) {
    override fun toString(): String = "MonitorProfile($id)"
}

/** The saved servers and their chosen locations (Android: the profile store). */
public interface MonitorProfiles {
    public fun profiles(): List<MonitorProfile>

    public fun location(profileId: String): MonitorLocation?
}

public data class MonitorSession(
    val id: String,
    val title: String? = null,
    val directory: String? = null,
    val workspace: String? = null,
)

public data class MonitorSessionPage(val items: List<MonitorSession>, val hasMore: Boolean = false)

/** One pending permission, question or form as listed by the server (only the routing fields). */
public data class MonitorPending(val id: String, val sessionId: String)

public data class MonitorMessage(
    val id: String,
    val role: String,
    /** The assistant message's finish reason ("length" = cut off). */
    val finish: String? = null,
    val failed: Boolean = false,
)

public data class MonitorMessagePage(val items: List<MonitorMessage>, val hasMore: Boolean = false)

/**
 * The bounded, read-only server surface one poll uses (Flutter's `ServerGateway` + `ServerOperationsGateway` pair).
 * Every read returns a Failure value on error; the monitor never shows it.
 */
public interface MonitorGateway {
    public val formsSupported: Boolean
    public val directory: String?
    public val workspace: String?

    public fun setLocation(directory: String?, workspace: String?)

    public suspend fun sessionPage(limit: Int): Outcome<MonitorSessionPage>

    public suspend fun pendingPermissions(): Outcome<List<MonitorPending>>

    public suspend fun pendingPermissionsV2(): Outcome<List<MonitorPending>>

    public suspend fun listQuestions(): Outcome<List<MonitorPending>>

    public suspend fun pendingQuestionsV2(): Outcome<List<MonitorPending>>

    public suspend fun pendingForms(): Outcome<List<MonitorPending>>

    public suspend fun sessionStatuses(): Outcome<Map<String, String>>

    public suspend fun session(id: String): Outcome<MonitorSession>

    public suspend fun messagePage(sessionId: String, limit: Int): Outcome<MonitorMessagePage>

    public fun close()
}

/** Posts and retires monitored alerts on the notification shade (Android: :android:notifications). */
public interface MonitorAlerts {
    /** True when the shade shows it. Monitored alerts carry an opaque [token] and never reply actions. */
    public suspend fun alert(profileId: String, request: MonitoredRequest, key: String, token: String): Boolean

    public suspend fun dismiss(key: String): Boolean
}

/**
 * The per-profile automation consent for "monitor other servers" (Flutter `AutomationPolicyController`). Only a stored
 * record is consent; a default never is.
 */
public interface MonitorConsent {
    public fun allows(profileId: String): Boolean

    public fun hasRecord(profileId: String): Boolean

    public suspend fun set(profileId: String, allowed: Boolean): Boolean

    /** [listener] runs on every change of [profileId]'s policy; returns the remover. */
    public fun observe(profileId: String, listener: () -> Unit): () -> Unit
}

/** The shared notification preferences (quiet hours, Wi-Fi only, check-in duration) once they are migrated. */
public interface MonitorSharedRules {
    public fun applyTo(own: MonitorNotifyRules): MonitorNotifyRules

    /** The global "request alerts" switch. */
    public val requestsAllowed: Boolean

    public object None : MonitorSharedRules {
        override fun applyTo(own: MonitorNotifyRules): MonitorNotifyRules = own

        override val requestsAllowed: Boolean = true
    }
}

/** Optional enrichment read inside the poll (the [MonitorAttentionReader]). */
public fun interface MonitorAttentionReading {
    public suspend fun read(
        profile: MonitorProfile,
        gateway: MonitorGateway,
        sessions: List<MonitorSession>,
        statuses: Map<String, String>,
        current: () -> Boolean,
    ): MonitorAttentionDetails
}
