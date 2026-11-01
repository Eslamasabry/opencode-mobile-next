package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What an edit does to the profile's connection secret (`pw.<id>` or `oc.codexToken.<id>`). */
public sealed interface SecretEdit {
    /** Leave the stored secret alone (the engine never holds a copy to write back). */
    public data object Keep : SecretEdit

    public class Set(public val value: String) : SecretEdit {
        override fun toString(): String = "SecretEdit.Set(len=${value.length})"
    }

    /** Delete the stored secret (Flutter: an empty password on save). */
    public data object Clear : SecretEdit
}

/** Transient, never stored: "enter it again" (Flutter `requiresPasswordReentry` / `requiresCodexTokenReentry`). */
public data class SecretReentry(val password: Boolean, val agentToken: Boolean) {
    public companion object {
        public val None: SecretReentry = SecretReentry(password = false, agentToken = false)
    }
}

/** A saved profile as the engine publishes it: metadata, trust, flags. Never a secret value. */
public data class LoadedProfile(
    val profile: StoredProfile,
    val trust: ProfileTrustRecord,
    val reentry: SecretReentry,
    /** A connection secret is stored (its value stays in [SecretStore]). */
    val hasSecret: Boolean,
    /** `oc.tailscale.<id>`: the Tailscale guidance belongs to this profile (B-AGENT-244). */
    val tailscale: Boolean,
) {
    val id: ProfileId get() = profile.id

    /** Plain HTTP to a non-loopback address the person has not confirmed for this exact address. */
    val cleartextUnconfirmed: Boolean
        get() = !profile.usesAgentSocket && ProfileOrigins.needsCleartextConsent(profile.baseUrl) &&
            trust.cleartext?.origin != ProfileOrigins.trustOrigin(profile.baseUrl)

    /** The `:core:model` connection shape, or null when the address is not usable. */
    public fun toServerProfile(managedOnPhone: Boolean = false): ServerProfile? {
        val origin = ProfileOrigins.serverOrigin(profile.baseUrl) ?: return null
        val applying = trust.forOrigin(origin.toString())
        return ServerProfile(
            id = profile.id,
            name = profile.name,
            backend = profile.backend,
            origin = origin,
            trust = ProfileTrust(applying.pin?.leafSha256, applying.cleartext?.let { 0L }),
            managedOnPhone = managedOnPhone,
            username = profile.username.ifEmpty { null },
        )
    }
}

/**
 * The profile store (plan 03 §11.2 `profiles`): `oc.profiles` metadata through the [KeyValueStore], connection secrets
 * in the [SecretStore] only, trust under its own keys. One serial lane ([mutex]); writes go through the app
 * [WriterLanes.app] lane.
 *
 * Ported from Flutter `ProfileStore` (`lib/state/profiles.dart`): metadata is kept when a secret is unreadable
 * (B-STATE-065), a failed secret write or delete leaves metadata and the published list unchanged (B-STATE-098,
 * B-SEC-135), and an origin change drops pin and consent in the same commit as the new address (plan 03 §10.3).
 */
public class ProfileStore(
    private val kv: KeyValueStore,
    private val secrets: SecretStore,
    private val lanes: WriterLanes,
    private val redactor: Redactor,
    private val platform: SecretPlatform = SecretPlatform.Android,
) {
    private val log = AppLog.tagged("profiles")
    private val mutex = Mutex()
    private val mutableProfiles = MutableStateFlow<PersistentList<LoadedProfile>>(persistentListOf())
    private val mutableActive = MutableStateFlow<ProfileId?>(null)
    private val deleting = MutableStateFlow<Set<String>>(emptySet())

    public val profiles: StateFlow<ImmutableList<LoadedProfile>> = mutableProfiles.asStateFlow()
    public val activeId: StateFlow<ProfileId?> = mutableActive.asStateFlow()

    public fun profile(id: ProfileId): LoadedProfile? = mutableProfiles.value.firstOrNull { it.id == id }

    /** The trust that applies to [id] at its current address. */
    public fun trust(id: ProfileId): ProfileTrustRecord =
        profile(id)?.let { it.trust.forOrigin(ProfileOrigins.trustOrigin(it.profile.baseUrl)) } ?: ProfileTrustRecord.None

    /** Reads every saved profile. Never writes; a secret that cannot be read marks the profile for re-entry. */
    public suspend fun load(): Outcome<ImmutableList<LoadedProfile>> = mutex.withLock {
        val stored = StoredProfileJson.decodeList(kv.string(ProfileKeys.PROFILES))
        val loaded = stored.map { readProfile(it) }.toPersistentList()
        mutableProfiles.value = loaded
        mutableActive.value = kv.string(ProfileKeys.ACTIVE)?.let(::ProfileId)
        loaded.ok()
    }

    /**
     * Saves [profile]: metadata and trust in ONE commit, then the secret. A refused secret write restores the
     * previous metadata and trust and publishes nothing (B-STATE-098). A transient transport lease is refused before
     * anything is written (B-SEC-136).
     */
    public suspend fun upsert(
        profile: StoredProfile,
        secret: SecretEdit = SecretEdit.Keep,
        trust: TrustInput = TrustInput(),
    ): Outcome<LoadedProfile> = mutex.withLock {
        if (profile.id.raw in deleting.value) return Failure.Refused(RefusalReason.ProfileClosing).failed()
        trust.pin?.let { if (!TrustCodec.validPin(it.leafSha256)) return Failure.Refused(RefusalReason.InvalidAddress).failed() }
        if (profile.transientTransport) return Failure.StorageRefused().failed()
        if (secret is SecretEdit.Set) redactor.registerSecret(secret.value)

        val id = profile.id
        val previous = Snapshot.of(kv, id)
        val rows = StoredProfileJson.rows(previous.profiles).orEmpty()
        val index = rows.indexOfFirst { (it as? Map<*, *>)?.get("id") == id.raw }
        val row = StoredProfileJson.encode(profile, rows.getOrNull(index) as? Map<*, *>)
        val nextRows = rows.toMutableList().apply { if (index >= 0) set(index, row) else add(row) }
        val storedTrust = ProfileTrustRecord(TrustCodec.decodePin(previous.pin), previous.cleartext?.let(::CleartextOkRecord))
        val nextTrust = TrustCodec.next(profile, trust, storedTrust)

        val committed = kv.commit(lanes.app) {
            putString(ProfileKeys.PROFILES, StoredProfileJson.encodeRows(nextRows))
            putOrRemove(ProfileKeys.cleartextOk(id), nextTrust.cleartext?.origin)
            putOrRemove(ProfileKeys.certificatePin(id), nextTrust.pin?.let(TrustCodec::encodePin))
        }
        if (committed is Outcome.Failed) return committed

        val secretResult = when (secret) {
            SecretEdit.Keep -> Unit.ok()
            is SecretEdit.Set -> secrets.write(profile.secretName, secret.value)
            SecretEdit.Clear -> secrets.delete(profile.secretName)
        }
        if (secretResult is Outcome.Failed) {
            restore(previous, id)
            log.w { "profile save refused by secure storage; metadata restored" }
            return Failure.SecretUnavailable().failed()
        }
        if (secret is SecretEdit.Set) clearReentryFlag(id, if (profile.usesAgentSocket) "codexToken" else "pw")

        val old = profile(id)
        val hasSecret = when (secret) {
            SecretEdit.Keep -> old?.hasSecret == true && old.profile.secretName == profile.secretName
            is SecretEdit.Set -> secret.value.isNotEmpty()
            SecretEdit.Clear -> false
        }
        val reentry = if (secret is SecretEdit.Keep && old != null && old.profile.secretName == profile.secretName) {
            old.reentry
        } else {
            SecretReentry.None // Flutter upsert clears both flags; a later load flags a missing Codex token again
        }
        val loaded = LoadedProfile(profile, nextTrust, reentry, hasSecret, kv.bool(ProfileKeys.tailscale(id)) == true)
        mutableProfiles.update { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) list.replacingAt(i, loaded) else list.adding(loaded)
        }
        loaded.ok()
    }

    /** `oc.activeProfile`; null clears it. */
    public suspend fun setActive(id: ProfileId?): Outcome<Unit> = mutex.withLock {
        if (id != null && id.raw in deleting.value) return Failure.Refused(RefusalReason.ProfileClosing).failed()
        val result = kv.commit(lanes.app) { putOrRemove(ProfileKeys.ACTIVE, id?.raw) }
        if (result is Outcome.Ok) mutableActive.value = id
        result
    }

    /** The profile's connection secret for the transport, read through (never cached here). */
    public suspend fun secret(id: ProfileId): Outcome<String?> {
        val profile = profile(id)?.profile ?: return null.ok()
        val read = secrets.read(profile.secretName)
        if (read is Outcome.Ok) read.value?.let(redactor::registerSecret)
        return read
    }

    /** Records (or drops) the Tailscale guidance for [id] in the profile's own lane (B-AGENT-244). */
    public suspend fun setTailscaleGuidance(id: ProfileId, shown: Boolean): Outcome<Unit> {
        val result = kv.commit(lanes.forProfile(id)) {
            if (shown) putBool(ProfileKeys.tailscale(id), true) else remove(ProfileKeys.tailscale(id))
        }
        if (result is Outcome.Ok) mutableProfiles.update { list -> list.map { if (it.id == id) it.copy(tailscale = shown) else it }.toPersistentList() }
        return result
    }

    /**
     * Null when secure storage answers; otherwise why a typed password could not be kept. Only a Linux desktop is
     * probed (Flutter `secureStorageProblem`, B-SEC-134); the wording lives in `:ui:words`.
     */
    public suspend fun secureStorageProblem(): SecureStorageProblem? {
        if (platform != SecretPlatform.LinuxDesktop) return null
        return when (secrets.read(ProfileKeys.SECURE_PROBE)) {
            is Outcome.Ok -> null
            is Outcome.Failed -> SecureStorageProblem.LinuxKeyringMissing
        }
    }

    /** The plain-words kind of a secret-storage failure on this platform (B-SEC-134). */
    public fun problemOf(failure: Failure): SecureStorageProblem? = SecureStorageProblem.of(failure, platform)

    // ---- deletion and reset hooks (same module)

    /** Step 0 of the deletion: saves for [id] are refused from now on. */
    internal fun beginDeletion(id: ProfileId) {
        deleting.update { it + id.raw }
    }

    /** A failed deletion keeps the profile: saves are admitted again and trust/flags are re-read. */
    internal suspend fun cancelDeletion(id: ProfileId) {
        deleting.update { it - id.raw }
        mutex.withLock {
            val current = StoredProfileJson.decodeList(kv.string(ProfileKeys.PROFILES)).firstOrNull { it.id == id } ?: return
            val old = profile(id)
            val trustNow = TrustCodec.restored(current, kv.string(ProfileKeys.certificatePin(id)), kv.string(ProfileKeys.cleartextOk(id)))
            val refreshed = LoadedProfile(
                current, trustNow, old?.reentry ?: SecretReentry.None, old?.hasSecret == true,
                kv.bool(ProfileKeys.tailscale(id)) == true,
            )
            mutableProfiles.update { list -> list.map { if (it.id == id) refreshed else it }.toPersistentList() }
        }
    }

    /** The deletion finished: the id may be saved again later as a new server. */
    internal fun endDeletion(id: ProfileId) {
        deleting.update { it - id.raw }
    }

    /**
     * Plan 05 §3.5 step 9: the profile row and its secrets as one unit. Writes the new `oc.profiles` (and clears
     * `oc.activeProfile` if it names [id]) in one commit, deletes [secretNames] in order, then verifies none is left.
     * Any failure restores the previous `oc.profiles` and active pointer and publishes nothing.
     */
    internal suspend fun removeRowAndSecrets(id: ProfileId, secretNames: List<String>): Outcome<Unit> = mutex.withLock {
        val previousRaw = kv.string(ProfileKeys.PROFILES)
        val previousActive = kv.string(ProfileKeys.ACTIVE)
        val rows = StoredProfileJson.rows(previousRaw)
        val nextRows = rows?.filterNot { (it as? Map<*, *>)?.get("id") == id.raw }
        val removed = kv.commit(lanes.app) {
            if (nextRows != null) putString(ProfileKeys.PROFILES, StoredProfileJson.encodeRows(nextRows))
            if (previousActive == id.raw) remove(ProfileKeys.ACTIVE)
        }
        if (removed is Outcome.Failed) return removed

        val secretsGone = deleteAndVerify(secretNames)
        if (secretsGone is Outcome.Failed) {
            val restored = kv.commit(lanes.app) {
                putOrRemove(ProfileKeys.PROFILES, previousRaw)
                if (previousActive == id.raw) putString(ProfileKeys.ACTIVE, previousActive)
            }
            if (restored is Outcome.Failed) log.e { "profile row could not be restored after a refused secret delete" }
            return Failure.SecretUnavailable().failed()
        }
        mutableProfiles.update { list -> list.filterNot { it.id == id }.toPersistentList() }
        if (mutableActive.value == id) mutableActive.value = null
        Unit.ok()
    }

    /**
     * "Reset saved sign-ins" (Flutter `resetSavedSignIns`, first lines): every cached profile loses its secret and is
     * flagged for re-entry, metadata kept, before storage is touched.
     */
    internal fun dropCachedSecrets() {
        mutableProfiles.update { list ->
            list.map { loaded ->
                val p = loaded.profile
                val reentry = SecretReentry(
                    password = !p.usesAgentSocket && (loaded.hasSecret || loaded.reentry.password),
                    agentToken = p.usesAgentSocket &&
                        (p.agentSocketSecretRequired || loaded.hasSecret || loaded.reentry.agentToken),
                )
                loaded.copy(hasSecret = false, reentry = reentry)
            }.toPersistentList()
        }
    }

    internal fun activeCleared() {
        mutableActive.value = null
    }

    // ---- helpers

    private suspend fun readProfile(p: StoredProfile): LoadedProfile {
        val id = p.id
        val trust = TrustCodec.restored(p, kv.string(ProfileKeys.certificatePin(id)), kv.string(ProfileKeys.cleartextOk(id)))
        val flagged = reentryFlags(id)
        val read = secrets.read(p.secretName)
        val (hasSecret, reentry) = when (read) {
            is Outcome.Ok -> {
                read.value?.let(redactor::registerSecret)
                val has = !read.value.isNullOrEmpty()
                val flag = if (p.usesAgentSocket) "codexToken" in flagged else "pw" in flagged
                has to if (p.usesAgentSocket) {
                    SecretReentry(password = false, agentToken = !has && (p.agentSocketSecretRequired || flag))
                } else {
                    SecretReentry(password = !has && flag, agentToken = false)
                }
            }
            // Keystore entries become unreadable after a restore or a lock-screen change: keep the profile usable so
            // the person can enter the secret again (B-STATE-065). A flagged secret is never an empty password.
            is Outcome.Failed -> false to SecretReentry(password = !p.usesAgentSocket, agentToken = p.usesAgentSocket)
        }
        return LoadedProfile(p, trust, reentry, hasSecret, kv.bool(ProfileKeys.tailscale(id)) == true)
    }

    /** `oc.secretReentry.<id>` = `{"v":1,"names":["pw"],...}` written by migration step M2 (plan 05 §7.4). */
    private fun reentryFlags(id: ProfileId): Set<String> {
        val raw = kv.string(ProfileKeys.secretReentry(id)) ?: return emptySet()
        val names = (DartJson.decodeOrNull(raw) as? Map<*, *>)?.get("names") as? List<*> ?: return emptySet()
        return names.filterIsInstance<String>().toSet()
    }

    private suspend fun clearReentryFlag(id: ProfileId, name: String) {
        val key = ProfileKeys.secretReentry(id)
        val raw = kv.string(key) ?: return
        val value = DartJson.decodeOrNull(raw) as? Map<*, *>
        val names = (value?.get("names") as? List<*>)?.filterIsInstance<String>()
        if (value != null && names != null && name !in names) return
        val left = names.orEmpty() - name
        val next = if (value == null || left.isEmpty()) null else DartJson.encode(LinkedHashMap(value).apply { put("names", left) })
        // Best effort: the flag only asks for re-entry; the new secret is already stored.
        kv.commit(lanes.forProfile(id)) { putOrRemove(key, next) }
    }

    private suspend fun deleteAndVerify(names: List<String>): Outcome<Unit> {
        for (name in names) {
            val deleted = secrets.delete(name)
            if (deleted is Outcome.Failed) return deleted
        }
        for (name in names) {
            when (val left = secrets.names(name)) {
                is Outcome.Failed -> return left
                is Outcome.Ok -> if (name in left.value) return Failure.SecretUnavailable().failed()
            }
        }
        return Unit.ok()
    }

    private suspend fun restore(previous: Snapshot, id: ProfileId) {
        val restored = kv.commit(lanes.app) {
            putOrRemove(ProfileKeys.PROFILES, previous.profiles)
            putOrRemove(ProfileKeys.cleartextOk(id), previous.cleartext)
            putOrRemove(ProfileKeys.certificatePin(id), previous.pin)
        }
        if (restored is Outcome.Failed) log.e { "saved profiles could not be restored after a refused secret write" }
    }

    private class Snapshot(val profiles: String?, val cleartext: String?, val pin: String?) {
        companion object {
            fun of(kv: KeyValueStore, id: ProfileId) = Snapshot(
                kv.string(ProfileKeys.PROFILES),
                kv.string(ProfileKeys.cleartextOk(id)),
                kv.string(ProfileKeys.certificatePin(id)),
            )
        }
    }
}

internal fun PrefEdit.putOrRemove(key: String, value: String?) {
    if (value == null) remove(key) else putString(key, value)
}
