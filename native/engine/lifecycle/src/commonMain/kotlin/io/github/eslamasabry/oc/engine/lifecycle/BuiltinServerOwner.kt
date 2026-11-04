package io.github.eslamasabry.oc.engine.lifecycle

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Port of lib/state/builtin_server_owner.dart and the owner step of ConnectionController.deleteProfileAndLocalData
// (audit 2, B-SEC-011). The one phone-runtime pointer shared by local profiles has a serial owner. Deletion stops
// admission before waiting for a write already in flight, so its final tombstone always follows that write on disk.

/**
 * The preference store as SharedPreferences behaves: [write] updates the in-memory cache before the durable write is
 * acknowledged (an optimistic cache), and answers false when the write was refused or failed.
 */
public interface OwnerPreferences {
    public fun cached(key: String): String?

    public suspend fun write(key: String, value: String): Boolean

    /** Re-reads the durable values into the cache; false when they could not be read. */
    public suspend fun reload(): Boolean
}

public class BuiltinServerOwner(private val prefs: OwnerPreferences) {
    private val closed = mutableSetOf<String>()
    private val lane = Mutex()
    private var cacheKnown = true

    /**
     * Records [profileId] as the runtime's owner. Refused for a closed (deleting) or unreadable profile, also when the
     * deletion closes it while the write is in flight; a failed write leaves the cache untrusted until reconciled.
     */
    public suspend fun claim(profileId: String, isReadable: () -> Boolean): Outcome<Unit> {
        fun admitted() = profileId.isNotEmpty() && profileId !in closed && isReadable()
        if (!admitted()) return UNAVAILABLE.failed()
        return lane.withLock {
            if (!reconcile()) return@withLock UNREADABLE.failed()
            if (!admitted()) return@withLock UNAVAILABLE.failed()
            if (!writePointer(profileId)) return@withLock NOT_SAVED.failed()
            if (!admitted()) return@withLock NOT_SAVED.failed()
            Unit.ok()
        }
    }

    /** Closes [profileId] now, then (after any admitted write) writes the empty tombstone when it is still the owner. */
    public suspend fun clearProfile(profileId: String): Outcome<Unit> {
        closed += profileId
        return lane.withLock {
            if (!reconcile()) return@withLock UNREADABLE.failed()
            if (prefs.cached(KEY) != profileId) return@withLock Unit.ok()
            // An empty pointer suppresses the legacy fallback to another local profile.
            if (!writePointer("")) NOT_SAVED.failed() else Unit.ok()
        }
    }

    /** Only the deletion transaction reopens a profile, after its abort completed. Claims still need [claim]'s predicate. */
    public fun cancelDeletion(profileId: String) {
        closed -= profileId
    }

    private suspend fun reconcile(): Boolean {
        if (cacheKnown) return true
        if (!prefs.reload()) return false
        cacheKnown = true
        return true
    }

    private suspend fun writePointer(value: String): Boolean {
        if (prefs.write(KEY, value)) return true
        // The cache moved before the durable write failed: as uncertain as a failed tombstone.
        cacheKnown = false
        reconcile()
        return false
    }

    public companion object {
        public const val KEY: String = "oc.builtinServerOwner"
        private val UNAVAILABLE = Failure.Refused(RefusalReason.ProfileDeleted)
        private val UNREADABLE = Failure.StorageRefused()
        private val NOT_SAVED = Failure.StorageRefused()
    }
}

/** What a profile deletion did (ConnectionController.ProfileDeletionResult, the parts these drains decide). */
public data class ProfileDeletionResult(val removedProfile: Boolean, val failures: List<Failure> = emptyList())

/** The profile list as the deletion sees it. */
public interface DeletableProfiles {
    public fun isReadable(profileId: String): Boolean

    /** Removes the profile and its local data; false when the store refused. */
    public suspend fun remove(profileId: String): Boolean
}

/**
 * The deletion drain for the phone runtime's owner pointer and recovery policy. Owner clear and recovery drain run
 * before the profile goes; if either is refused or unreadable the deletion aborts, the profile stays readable, the
 * owner is reopened, and a retry starts clean.
 */
public class PhoneProfileDeletion(
    private val profiles: DeletableProfiles,
    private val owner: BuiltinServerOwner,
    private val recovery: RecoveryPolicyRegistry? = null,
    private val policy: RestartPolicyPause? = null,
) {
    public suspend fun delete(profileId: String): Outcome<ProfileDeletionResult> {
        policy?.pauseForDeletion(profileId)
        recovery?.prepareForProfileDeletion(profileId)
        val cleared = owner.clearProfile(profileId)
        val removed = cleared is Outcome.Ok && (recovery?.sweep(profileId) ?: true) && profiles.remove(profileId)
        if (!removed) {
            owner.cancelDeletion(profileId)
            policy?.cancelDeletion(profileId)
            recovery?.reopen(profileId)
            return ((cleared as? Outcome.Failed)?.failure ?: Failure.StorageRefused()).failed()
        }
        return ProfileDeletionResult(removedProfile = true).ok()
    }
}

/** The automation policy owner's deletion pause (AutomationPolicyController.pauseForDeletion/cancelDeletion). */
public interface RestartPolicyPause {
    public fun pauseForDeletion(profileId: String)

    public fun cancelDeletion(profileId: String)
}
