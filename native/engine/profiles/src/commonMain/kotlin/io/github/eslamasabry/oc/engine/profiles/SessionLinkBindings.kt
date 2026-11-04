package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Verified installation metadata only. This is never session access authority. */
public data class SessionLinkBinding(
    val origin: String,
    val instanceId: String,
    val verifiedAtMs: Long,
) {
    override fun toString(): String = "SessionLinkBinding"
}

/**
 * B-STATE-108…110. A single process owner per profile, including deletion tombstones. The
 * address-handoff capability remains closed until a private authenticated discovery/lookup
 * transport can supply a verified binding; ordinary session lookup must never call [save].
 */
public class SessionLinkBindingOwners(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val profiles: ProfileStore,
    private val redactor: Redactor,
) {
    private data class Owners(
        val open: Map<ProfileId, SessionLinkBindings> = emptyMap(),
        val closed: Set<ProfileId> = emptySet(),
        val epochs: Map<ProfileId, Long> = emptyMap(),
    )

    private val owners = AtomicReference(Owners())

    private fun member(profile: ProfileId): Boolean {
        if (!PROFILE.matches(profile.raw) || redactor.containsSecret(profile.raw)) return false
        val raw = (store.get("oc.profiles") as? PrefValue.Str)?.value ?: return false
        val rows = DartJson.decodeOrNull(raw) as? List<*> ?: return false
        val decoded = rows.map { (it as? Map<*, *>)?.let(StoredProfileJson::decode) }
        if (
            decoded.any { it == null || !PROFILE.matches(it.id.raw) } ||
                decoded.map { it?.id }.distinct().size != decoded.size
        )
            return false
        return decoded.singleOrNull { it?.id == profile } == profiles.profile(profile)?.profile &&
            profiles.profile(profile) != null
    }

    public suspend fun forProfile(profile: ProfileId): Outcome<SessionLinkBindings> =
        when (val checked = attempt({ Failure.StorageRefused() }) { member(profile) }) {
            is Outcome.Failed -> checked
            is Outcome.Ok -> {
                if (!checked.value) Failure.Refused(RefusalReason.ProfileDeleted).failed()
                else owner(profile)
            }
        }

    private fun owner(profile: ProfileId): Outcome<SessionLinkBindings> {
        while (true) {
            val before = owners.load()
            if (profile in before.closed)
                return Failure.Refused(RefusalReason.ProfileClosing).failed()
            before.open[profile]?.let {
                return it.ok()
            }
            val epoch = before.epochs[profile]
            val fresh =
                SessionLinkBindings(profile, store, lanes, redactor) {
                    val current = owners.load()
                    profile !in current.closed &&
                        current.epochs[profile] == epoch &&
                        member(profile)
                }
            if (owners.compareAndSet(before, before.copy(open = before.open + (profile to fresh))))
                return fresh.ok()
        }
    }

    /** Synchronous: closes even before a facade exists, and invalidates every old facade. */
    public fun stopAdmission(profile: ProfileId) {
        while (true) {
            val before = owners.load()
            val after =
                before.copy(
                    closed = before.closed + profile,
                    epochs = before.epochs + (profile to ((before.epochs[profile] ?: 0L) + 1)),
                )
            if (owners.compareAndSet(before, after)) return
        }
    }

    /**
     * Await before discovering and sweeping profile keys. An admitted platform write is drained.
     */
    public suspend fun drain(profile: ProfileId) {
        owners.load().open[profile]?.drain()
    }

    /** Only an aborted deletion creates a fresh owner; previously returned facades stay closed. */
    public suspend fun cancelDeletion(profile: ProfileId) {
        val before = owners.load()
        if (profile !in before.closed) return
        before.open[profile]?.drain()
        val exists = attempt({ Failure.StorageRefused() }) { member(profile) }
        if ((exists as? Outcome.Ok)?.value != true) return
        while (true) {
            val current = owners.load()
            if (
                current.epochs[profile] != before.epochs[profile] ||
                    current.open[profile] !== before.open[profile]
            )
                return
            if (
                owners.compareAndSet(
                    current,
                    current.copy(
                        open = current.open - profile,
                        closed = current.closed - profile,
                    ),
                )
            )
                return
        }
    }

    public companion object {
        private val PROFILE = Regex("[A-Za-z0-9_-]{1,128}")
        public val SHAPE: KeyShape =
            KeyShape(
                "oc.sessionLinkBinding.",
                KeyScope.Profile,
                PrefType.Str,
                ":engine:profiles",
                "verified installation metadata; never session authority",
            )
    }
}

public class SessionLinkBindings
internal constructor(
    profile: ProfileId,
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val redactor: Redactor,
    private val current: () -> Boolean,
) {
    private val profile = profile
    private val key = "oc.sessionLinkBinding.${profile.raw}"
    private val order = Mutex()
    private val available = AtomicReference(true)

    private suspend fun admission(): Outcome<Unit> =
        when (val check = attempt({ Failure.StorageRefused() }) { current() }) {
            is Outcome.Failed -> check
            is Outcome.Ok ->
                when {
                    !check.value -> Failure.Refused(RefusalReason.ProfileClosing).failed()
                    !available.load() -> Failure.StorageRefused().failed()
                    else -> Unit.ok()
                }
        }

    /**
     * A corrupt/unknown envelope makes this facade unavailable; no optimistic value is published.
     */
    public suspend fun read(): Outcome<SessionLinkBinding?> {
        val admitted = admission()
        if (admitted is Outcome.Failed) return admitted
        return when (
            val read =
                attempt({ Failure.StorageRefused() }) {
                    val raw = store.get(key) ?: return@attempt null
                    val text = (raw as? PrefValue.Str)?.value ?: error("invalid binding type")
                    require(text.length <= 2048)
                    val map = DartJson.decodeOrNull(text) as? Map<*, *> ?: error("invalid binding")
                    require(map.size == 4 && map["schemaVersion"] == 1L)
                    canonical(
                        SessionLinkBinding(
                            map["origin"] as? String ?: error("invalid binding"),
                            map["instanceId"] as? String ?: error("invalid binding"),
                            map["verifiedAt"] as? Long ?: error("invalid binding"),
                        )
                    ) ?: error("invalid binding")
                }
        ) {
            is Outcome.Failed -> {
                available.store(false)
                read
            }
            is Outcome.Ok -> read
        }
    }

    /** Only verified discovery can supply this metadata. Another installation never replaces it. */
    public suspend fun save(binding: SessionLinkBinding): Outcome<Unit> = order.withLock {
        val admitted = admission()
        if (admitted is Outcome.Failed) return@withLock admitted
        val checked =
            canonical(binding)
                ?: return@withLock Failure.Refused(RefusalReason.InvalidAddress).failed()
        val snapshot =
            when (val read = attempt({ Failure.StorageRefused() }) { store.get(key) }) {
                is Outcome.Failed -> {
                    available.store(false)
                    return@withLock read
                }
                is Outcome.Ok -> read.value
            }
        val previous =
            when (val read = read()) {
                is Outcome.Failed -> return@withLock read
                is Outcome.Ok -> read.value
            }
        if (
            previous != null &&
                (previous.origin != checked.origin || previous.instanceId != checked.instanceId)
        )
            return@withLock Failure.Refused(RefusalReason.OriginChanged).failed()
        val written =
            attempt({ Failure.StorageRefused() }) {
                val profileRegistry = store.get("oc.profiles")
                if (store.get(key) != snapshot) return@attempt Failure.StorageRefused().failed()
                val again = admission()
                if (again is Outcome.Failed) return@attempt again
                // A newly registered credential can invalidate formerly safe metadata while this
                // write waited.
                val fresh =
                    canonical(checked)
                        ?: return@attempt Failure.Refused(RefusalReason.CredentialsInUrl).failed()
                val encoded =
                    DartJson.encode(
                        linkedMapOf(
                            "schemaVersion" to 1L,
                            "origin" to fresh.origin,
                            "instanceId" to fresh.instanceId,
                            "verifiedAt" to fresh.verifiedAtMs,
                        )
                    )
                val saved =
                    store.compareAndCommit(
                        lanes.forProfile(profile),
                        mapOf(
                            "oc.profiles" to profileRegistry,
                            key to snapshot,
                        ),
                    ) {
                        putString(key, encoded)
                    }
                if (
                    (saved as? Outcome.Ok)?.value != true ||
                        (store.get(key) as? PrefValue.Str)?.value != encoded
                )
                    Failure.StorageRefused().failed()
                else admission()
            }
        when (written) {
            is Outcome.Failed -> {
                available.store(false)
                written
            }
            is Outcome.Ok -> {
                if (written.value is Outcome.Failed) available.store(false)
                written.value
            }
        }
    }

    internal suspend fun drain() {
        order.withLock {}
    }

    private fun canonical(value: SessionLinkBinding): SessionLinkBinding? {
        val match = ORIGIN.matchEntire(value.origin) ?: return null
        val host = match.groupValues[1].lowercase()
        val labels = host.split('.')
        if (
            host.length > 253 ||
                labels.size != 4 ||
                labels.takeLast(2) != listOf("ts", "net") ||
                labels.any { !LABEL.matches(it) }
        )
            return null
        val port =
            match.groupValues[2].let { if (it.isEmpty()) 443 else it.toIntOrNull() } ?: return null
        if (port !in 1..65535 || !INSTANCE.matches(value.instanceId) || value.verifiedAtMs < 0)
            return null
        val origin = "https://$host" + if (port == 443) "" else ":$port"
        val instance = value.instanceId.lowercase()
        if (listOf(value.origin, origin, value.instanceId, instance).any(redactor::containsSecret))
            return null
        return SessionLinkBinding(origin, instance, value.verifiedAtMs)
    }

    private companion object {
        val ORIGIN = Regex("https://([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?", RegexOption.IGNORE_CASE)
        val LABEL = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
        val INSTANCE =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
