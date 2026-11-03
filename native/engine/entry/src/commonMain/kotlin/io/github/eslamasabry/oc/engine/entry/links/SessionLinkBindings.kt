package io.github.eslamasabry.oc.engine.entry.links

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.flatMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The per-profile session-link binding writers (Flutter `SessionLinkBindings`, lib/state/session_link_bindings.dart).
 * One shared [SessionLinkBindingOwner] per profile; the profile-deletion transaction calls [closeProfile] (admission
 * closes at once), awaits [drain] (a write already on disk finishes) and sweeps the key; an aborted deletion calls
 * [cancelDeletion], which reopens only a FRESH owner and only after durable membership is confirmed. A closed owner
 * never reopens.
 *
 * Main thread only for the registry calls; each owner's writes run one at a time.
 */
public class SessionLinkBindings(
    private val storage: SessionLinkStorage,
    private val profiles: SessionLinkProfiles,
    private val secrets: SecretRecognizer,
) {
    private val owners = mutableMapOf<String, SessionLinkBindingOwner>()
    private val closed = mutableSetOf<String>()
    private val closeEpochs = mutableMapOf<String, Int>()

    /** The profile's shared owner; InvalidLink for an unsafe id, ProfileMissing for a closed or unknown profile. */
    public fun forProfile(profileId: String): SessionLinkResult<SessionLinkBindingOwner> {
        if (!PROFILE_ID.matches(profileId) || secrets.containsSecret(profileId)) {
            return SessionLinkResult.Failed(SessionLinkFailure.InvalidLink)
        }
        if (profileId in closed || !profiles.isListed(profileId)) return SessionLinkResult.Failed(SessionLinkFailure.ProfileMissing)
        return SessionLinkResult.Ok(owners.getOrPut(profileId) { SessionLinkBindingOwner(profileId, storage, profiles, secrets) })
    }

    /** Closes admission for [profileId] now: its owner refuses every new write and [forProfile] refuses it. */
    public fun closeProfile(profileId: String) {
        closed += profileId
        closeEpochs[profileId] = (closeEpochs[profileId] ?: 0) + 1
        owners[profileId]?.close()
    }

    /** Waits for the write already running for [profileId] to finish (it finishes; it is not cut off). */
    public suspend fun drain(profileId: String) {
        owners[profileId]?.awaitIdle()
    }

    /** Only the completed deletion-abort path calls this. Existing owners stay closed; a later [forProfile] is fresh. */
    public suspend fun cancelDeletion(profileId: String) {
        if (profileId !in closed) return
        val previous = owners[profileId]
        val epoch = closeEpochs[profileId]
        previous?.awaitIdle()
        val listed = attempt({ Failure.StorageRefused() }) { profiles.confirm(profileId) }.flatMap { it }
        if (listed !is Outcome.Ok || !listed.value) return // an uncertain membership never reopens a writer
        if (closeEpochs[profileId] != epoch || owners[profileId] !== previous) return
        owners.remove(profileId)
        closed -= profileId
    }

    public companion object {
        private val PROFILE_ID = Regex("""^[a-zA-Z0-9_-]{1,128}$""")

        /** The per-profile key (`oc.<what>.<profileId>`), swept by profile deletion. */
        public fun keyFor(profileId: String): String = "oc.sessionLinkBinding.$profileId"
    }
}

/** One profile's binding: read and save the canonical verified binding. Obtained from [SessionLinkBindings]. */
public class SessionLinkBindingOwner internal constructor(
    public val profileId: String,
    private val storage: SessionLinkStorage,
    private val profiles: SessionLinkProfiles,
    private val secrets: SecretRecognizer,
) {
    private val lane = Mutex()
    private var admitted = true
    private var storageAvailable = true
    private val mutableRevision = MutableStateFlow(0)

    /** Bumps on every saved binding, on a failed write and when the owner closes (the Flutter listener's notices). */
    public val revision: StateFlow<Int> = mutableRevision.asStateFlow()

    private val key: String get() = SessionLinkBindings.keyFor(profileId)

    public val isAvailable: Boolean get() = admitted && storageAvailable && profiles.isListed(profileId)

    private fun admission(): SessionLinkFailure? =
        when {
            !admitted || !profiles.isListed(profileId) -> SessionLinkFailure.ProfileMissing
            !storageAvailable -> SessionLinkFailure.Storage
            else -> null
        }

    internal fun close() {
        if (!admitted) return
        admitted = false
        mutableRevision.value++
    }

    internal suspend fun awaitIdle() {
        lane.withLock { }
    }

    /** The stored binding (Ok(null) when none). Unreadable, corrupt or unknown shapes are [SessionLinkFailure.Storage]. */
    public suspend fun read(): SessionLinkResult<SessionLinkBinding?> {
        admission()?.let { return SessionLinkResult.Failed(it) }
        return when (val stored = guardedRead()) {
            is Outcome.Failed -> SessionLinkResult.Failed(SessionLinkFailure.Storage)
            is Outcome.Ok -> decode(stored.value)
        }
    }

    private fun decode(raw: String?): SessionLinkResult<SessionLinkBinding?> {
        if (raw == null) return SessionLinkResult.Ok(null)
        return try {
            SessionLinkResult.Ok(SessionLinkCodec.decode(raw, secrets))
        } catch (refusal: SessionLinkRefusal) {
            SessionLinkResult.Failed(refusal.failure)
        }
    }

    /**
     * Stores [binding] in canonical form. Refused when it names another origin or installation than the stored one
     * (InstanceMismatch), when the profile is gone or closed (ProfileMissing), when it carries a credential
     * (Credentials / InvalidLink, checked again right before the durable write) and when storage fails (Storage, and
     * the owner stays unavailable: a value the platform may have kept is never published).
     */
    public suspend fun save(binding: SessionLinkBinding): SessionLinkResult<Unit> {
        admission()?.let { return SessionLinkResult.Failed(it) }
        val checked =
            try {
                SessionLinkCodec.validated(binding, secrets)
            } catch (refusal: SessionLinkRefusal) {
                return SessionLinkResult.Failed(refusal.failure)
            }
        return lane.withLock { saveInLane(checked) }
    }

    private suspend fun saveInLane(proposed: SessionLinkBinding): SessionLinkResult<Unit> {
        admission()?.let { return SessionLinkResult.Failed(it) }
        // Confirm membership and the previous binding against durable storage, never an optimistic cache.
        when (val listed = guardedConfirm()) {
            is Outcome.Failed -> return storageFailed(notify = false)
            is Outcome.Ok -> if (!listed.value) return SessionLinkResult.Failed(SessionLinkFailure.ProfileMissing)
        }
        admission()?.let { return SessionLinkResult.Failed(it) }
        val previous =
            when (val stored = guardedRead()) {
                is Outcome.Failed -> return storageFailed(notify = false)
                is Outcome.Ok ->
                    when (val decoded = decode(stored.value)) {
                        is SessionLinkResult.Failed -> return decoded
                        is SessionLinkResult.Ok -> decoded.value
                    }
            }
        if (previous != null && (previous.origin != proposed.origin || previous.instanceId != proposed.instanceId)) {
            return SessionLinkResult.Failed(SessionLinkFailure.InstanceMismatch)
        }
        // A secret can become known while this write waited: validate again right before plaintext storage.
        val binding =
            try {
                SessionLinkCodec.validated(proposed, secrets)
            } catch (refusal: SessionLinkRefusal) {
                return SessionLinkResult.Failed(refusal.failure)
            }
        val encoded = SessionLinkCodec.encode(binding)
        if (guardedWrite(encoded) is Outcome.Failed) return storageFailed(notify = true)
        val back = guardedRead()
        if (back !is Outcome.Ok || back.value != encoded) return storageFailed(notify = true)
        admission()?.let { return SessionLinkResult.Failed(it) }
        mutableRevision.value++
        return SessionLinkResult.Ok(Unit)
    }

    /** A port that throws instead of answering Failed is treated the same: storage could not be trusted. */
    private suspend fun guardedRead(): Outcome<String?> =
        attempt({ Failure.StorageRefused() }) { storage.read(key) }.flatMap { it }

    private suspend fun guardedWrite(value: String): Outcome<Unit> =
        attempt({ Failure.StorageRefused() }) { storage.write(key, value) }.flatMap { it }

    private suspend fun guardedConfirm(): Outcome<Boolean> =
        attempt({ Failure.StorageRefused() }) { profiles.confirm(profileId) }.flatMap { it }

    private fun storageFailed(notify: Boolean): SessionLinkResult<Unit> {
        storageAvailable = false
        if (notify) mutableRevision.value++
        return SessionLinkResult.Failed(SessionLinkFailure.Storage)
    }
}
