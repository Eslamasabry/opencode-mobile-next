package io.github.eslamasabry.oc.engine.lifecycle

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

// Port of the deletion half of lib/termux/managed_server_recovery.dart (audit 2, B-SEC-012): per-profile restart
// recovery records `oc.managedServerRecovery.<id>` share one installation budget. Every write (alias mirrors too) goes
// through one serial lane, so deletion drains each admitted writer of an id before sweeping it; a deletion that aborts
// keeps the person's policy and reopens the same recovery owner. The Termux restart loop itself is not ported here.

/** The preference store the recovery records live in. */
public interface RecoveryPreferences {
    public fun get(key: String): String?

    public suspend fun set(key: String, value: String): Boolean

    public suspend fun remove(key: String): Boolean
}

/** Revokes an installation's background recovery permit (TermuxBridge.recoveryControlScript(token, enable = false)). */
public fun interface RecoveryRevoker {
    public suspend fun revoke(token: String)
}

/** Whether the person's automation policy allows restarting the phone server for a profile. */
public fun interface RestartPolicyRead {
    public fun allowsRestart(profileId: String): Boolean
}

public class RecoveryPolicyRegistry(
    private val prefs: RecoveryPreferences,
    private val revoker: RecoveryRevoker,
    private val policy: RestartPolicyRead,
) {
    private val instances = linkedMapOf<String, RecoveryPolicy>()
    private var owner: String? = null
    private var known: Set<String>? = null
    private val retired = mutableSetOf<String>()
    private val deleting = mutableSetOf<String>()
    private val lane = Mutex()

    /** The one recovery owner object per profile; the first profile of a single-profile installation owns it. */
    public fun forProfile(profileId: String): RecoveryPolicy {
        val k = known
        if (owner == null && ((k == null && instances.isEmpty()) || (k?.size == 1 && profileId in k))) owner = profileId
        return instances.getOrPut(profileId) { RecoveryPolicy(profileId).also { it.restore() } }
    }

    /** The saved local profiles now. A profile whose deletion aborted is reopened here, same object. */
    public fun syncProfiles(ids: Collection<String>) {
        val set = ids.toSet()
        if (set.isEmpty()) {
            instances.clear()
            owner = null
            known = null
            retired.clear()
            return
        }
        known = set
        val resumed = deleting.intersect(set)
        deleting -= set
        retired -= set
        for (id in resumed) instances[id]?.admissionClosed = false
        instances.keys.retainAll(set)
        if (owner !in set) owner = null
        for (id in set) forProfile(id)
    }

    /** Deletion transaction abort: reopens [profileId] without a profile-list sync. */
    public fun reopen(profileId: String) {
        deleting -= profileId
        retired -= profileId
        instances[profileId]?.admissionClosed = false
    }

    /**
     * Closes admission for [profileId] now, revokes its permit only when it owns the installation (an alias never
     * revokes the surviving owner's permit), then waits for every admitted record write. The policy is not edited.
     */
    public suspend fun prepareForProfileDeletion(profileId: String) {
        deleting += profileId
        retired += profileId
        val current = instances[profileId]
        if (current != null) {
            current.admissionClosed = true
            if (current.ownsInstallation && current.token.isNotEmpty()) revoker.revoke(current.token)
        } else {
            val raw = prefs.get(preferenceKey(profileId))
            if (raw != null && (owner == null || owner == profileId)) {
                val token = decode(raw)?.get("token")?.let { it as? JsonPrimitive }?.takeIf { it.isString }?.content
                if (!token.isNullOrEmpty()) revoker.revoke(token)
            }
        }
        lane.withLock { }
    }

    /** Removes the deleted profile's record after the drain; false when the store refused. */
    public suspend fun sweep(profileId: String): Boolean =
        lane.withLock { prefs.get(preferenceKey(profileId)) == null || prefs.remove(preferenceKey(profileId)) }

    /** A successful manual start adopts the installation for [profileId]; never for a profile being deleted. */
    public suspend fun resumeAfterManualStart(profileId: String) {
        if (profileId in deleting) return
        val requested = forProfile(profileId)
        if (requested.cannotSave) return
        requested.manuallySuspended = true
        requested.save()
        if (requested.cannotSave) return
        owner = profileId
    }

    private fun idsSharingBudget(): Set<String> = (known ?: instances.keys.toSet()) - retired

    public inner class RecoveryPolicy internal constructor(public val profileId: String) {
        public var attempts: Int = 0
        internal var token: String = ""
        private var operation: String = ""
        private var pendingOperation: String = ""
        internal var admissionClosed: Boolean = false
        internal var manuallySuspended: Boolean = false

        public val ownsInstallation: Boolean get() = owner == profileId
        internal val cannotSave: Boolean get() = profileId in deleting

        public val enabled: Boolean
            get() = !cannotSave && !admissionClosed && profileId !in retired && policy.allowsRestart(profileId)

        internal fun restore() {
            val data = prefs.get(preferenceKey(profileId))?.let(::decode) ?: return
            val t = data.string("token")
            val count = (data["attempts"] as? JsonPrimitive)?.intOrNull
            val op = data.string("operation")
            val pending = data.string("pendingOperation")
            if (t == null || !SAFE.matches(t) || count == null || count !in 0..MAX_ATTEMPTS || op == null ||
                !SAFE.matches(op) || pending == null || !SAFE.matches(pending)
            ) {
                return
            }
            token = t
            attempts = count
            operation = op
            pendingOperation = pending
        }

        /** A manual check resumes a transient probe error without resetting retries. */
        public suspend fun retryCheck() {
            save()
        }

        internal suspend fun save(): Boolean =
            lane.withLock {
                if (cannotSave) return@withLock true
                attempts = sharedAttempts()
                val record =
                    buildJsonObject {
                        put("enabled", enabled)
                        put("token", token)
                        put("attempts", attempts)
                        put("operation", operation)
                        put("pendingOperation", pendingOperation)
                        put("manuallySuspended", manuallySuspended)
                    }
                if (!prefs.set(preferenceKey(profileId), record.toString())) return@withLock false
                // Aliases share one installation: mirror only the spent budget, never their policy.
                for (id in idsSharingBudget().filter { it != profileId }) {
                    if (id in deleting) continue
                    val data = prefs.get(preferenceKey(id))?.let(::decode) ?: JsonObject(emptyMap())
                    val previous = (data["attempts"] as? JsonPrimitive)?.intOrNull ?: 0
                    if (previous >= attempts) continue
                    val merged = data.toMutableMap()
                    merged["attempts"] = JsonPrimitive(attempts)
                    merged.getOrPut("token") { JsonPrimitive(token) }
                    merged.getOrPut("operation") { JsonPrimitive("") }
                    merged.getOrPut("pendingOperation") { JsonPrimitive("") }
                    if (!prefs.set(preferenceKey(id), JsonObject(merged).toString())) return@withLock false
                    instances[id]?.attempts = attempts
                }
                true
            }

        private fun sharedAttempts(): Int {
            var spent = attempts
            for (id in idsSharingBudget()) {
                val count = prefs.get(preferenceKey(id))?.let(::decode)?.get("attempts")?.let { it as? JsonPrimitive }?.intOrNull ?: continue
                if (count in 0..MAX_ATTEMPTS && count > spent) spent = count
            }
            return spent
        }
    }

    public companion object {
        public const val MAX_ATTEMPTS: Int = 3
        private val SAFE = Regex("^[a-zA-Z0-9_-]{0,64}$")

        public fun preferenceKey(profileId: String): String = "oc.managedServerRecovery.$profileId"

        private fun decode(raw: String): JsonObject? =
            try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
