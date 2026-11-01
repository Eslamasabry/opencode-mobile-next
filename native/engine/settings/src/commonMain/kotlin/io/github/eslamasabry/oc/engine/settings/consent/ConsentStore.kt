package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.atomics.AtomicReference

/**
 * The three-state consent read (plan 03 §10.2 `CleartextConsent` shape, plan 16 SI-22): a missing record is
 * [Unknown], never [Refused]; a corrupt, unreadable, closed or not-yet-acknowledged record is never [Granted].
 * Only [Granted] authorises anything.
 */
public enum class ConsentRead {
    /** Never answered, still waiting for an answer, or unreadable. Not granted. */
    Unknown,
    Granted,
    Refused,
    ;

    public val granted: Boolean get() = this == Granted
}

/** The consents of DESKTOP-WEB-CROSS-48 that live in `:engine:settings`. */
public enum class ConsentTopic {
    MobileDownload,
    ReadStateSharing,
    BatteryExemption,
    MakerAutoStart,
    NeedsYouNotifications,
}

/** The four stored answers of the ask-once consents (Flutter `unseen/offered/accepted/denied`, stored by name). */
public enum class ConsentChoice(internal val wire: String) {
    Unseen("unseen"),
    Offered("offered"),
    Accepted("accepted"),
    Denied("denied"),
    ;

    public val read: ConsentRead
        get() = when (this) {
            Accepted -> ConsentRead.Granted
            Denied -> ConsentRead.Refused
            Unseen, Offered -> ConsentRead.Unknown
        }

    internal companion object {
        fun fromWire(value: Any?): ConsentChoice? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One shape for every consent store (DESKTOP-WEB-CROSS-48 "a single ConsentStore abstraction with the three-state
 * read"). One instance per store serves every profile, so a revocation reaches every observer at once: all of them read
 * the same [revision] and the same [read].
 */
public interface ConsentStore {
    public val topics: Set<ConsentTopic>

    /** Bumped on every visible change (including a change that only makes a profile private while it saves). */
    public val revision: StateFlow<Long>

    /** Synchronous, fail-closed read. A topic this store does not own reads [ConsentRead.Unknown]. */
    public fun read(profileId: ProfileId, topic: ConsentTopic): ConsentRead

    /** Synchronous: from now on the profile's consent reads as not granted and new writes are refused. */
    public fun closeProfile(profileId: ProfileId)

    /**
     * Profile deletion: closes, drains pending writes, removes the profile's records. Idempotent. A refused removal
     * returns `Failure.StorageRefused` and the profile stays closed (nothing reads as granted).
     */
    public suspend fun forgetProfile(profileId: ProfileId): Outcome<Unit>
}

/** Every observer of one profile's topic sees the same sequence of reads. */
public fun ConsentStore.observe(profileId: ProfileId, topic: ConsentTopic): Flow<ConsentRead> =
    revision.map { read(profileId, topic) }.distinctUntilChanged()

/** Whether a profile is still saved (Flutter checks `oc.profiles` on every load and write). */
public fun interface SavedProfiles {
    public fun contains(profileId: ProfileId): Boolean

    public companion object {
        /** Reads `oc.profiles` like Dart does; an unreadable list protects nothing (fails closed: not saved). */
        public fun from(store: KeyValueStore): SavedProfiles = SavedProfiles { id ->
            val rows = store.string(DefaultProfileScope.PROFILES_KEY)?.let(DartJson::decodeOrNull) as? List<*>
            rows != null && rows.any { (it as? Map<*, *>)?.get("id") == id.raw }
        }
    }
}

/** Storage names, exactly as the Flutter app wrote them (plan 05 Appendix A). */
public object ConsentKeys {
    public const val MOBILE_DOWNLOAD_PREFIX: String = "oc.mobileDownloadConsent."
    public const val IN_FLOW_PREFIX: String = "oc.inFlowConsent."
    public const val SHARE_VIEWS_PREFIX: String = "oc.shareSessionViews."
    public const val SHARE_VIEWS_ORIGIN_PREFIX: String = "oc.shareSessionViewsOrigin."
    public const val SHARE_VIEWS_DEFAULT_PREFIX: String = "oc.shareSessionViewsDefault."

    /** The legacy app-wide value, read once by the read-state migration (L3). */
    public const val SHARE_VIEWS_LEGACY: String = "oc.shareSessionViews"
    public const val READ_STATE_MIGRATION: String = "oc.readStateConsentMigration.v1"

    public fun mobileDownload(id: ProfileId): String = MOBILE_DOWNLOAD_PREFIX + id.raw
    public fun inFlow(id: ProfileId): String = IN_FLOW_PREFIX + id.raw
    public fun shareViews(id: ProfileId): String = SHARE_VIEWS_PREFIX + id.raw
    public fun shareViewsOrigin(id: ProfileId): String = SHARE_VIEWS_ORIGIN_PREFIX + id.raw
    public fun shareViewsDefault(id: ProfileId): String = SHARE_VIEWS_DEFAULT_PREFIX + id.raw

    private const val OWNER = ":engine:settings"

    /** Ledger rows for `ProfileScope.register` (the coordinator wires the multibinding). */
    public val shapes: List<KeyShape> = listOf(
        KeyShape(MOBILE_DOWNLOAD_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "mobile-data download consent"),
        KeyShape(IN_FLOW_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "ask-once in-flow consents"),
        KeyShape(SHARE_VIEWS_PREFIX, KeyScope.Profile, PrefType.Bool, OWNER, "read-state sharing consent"),
        KeyShape(SHARE_VIEWS_ORIGIN_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "read-state consent origin"),
        KeyShape(SHARE_VIEWS_DEFAULT_PREFIX, KeyScope.Profile, PrefType.Bool, OWNER, "read-state consent default"),
        KeyShape(READ_STATE_MIGRATION, KeyScope.Other, PrefType.Str, OWNER, "L3 read-state migration journal"),
    )
}

/** Flutter's opaque saved-profile id rule (`^[a-zA-Z0-9_-]{1,128}$`). Anything else is never stored or granted. */
internal fun ProfileId.isOpaque(): Boolean =
    raw.length in 1..128 && raw.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }

internal fun closing(): Failure = Failure.Refused(RefusalReason.ProfileClosing)

internal fun unreadable(): Failure = Failure.StorageRefused()

internal fun refusedWrite(): Failure = Failure.StorageRefused()

/** Dart `jsonDecode(x)['version'] == 1`: an int 1 (or a double 1.0, which Dart also calls equal). */
internal fun isVersionOne(value: Any?): Boolean = value == 1L || value == 1.0

/** One fair mutex per profile: a profile's consent commands run in order, other profiles never wait for it. */
internal class ProfileMutexes {
    private val mutexes = AtomicReference<PersistentMap<String, Mutex>>(persistentHashMapOf())

    fun of(id: ProfileId): Mutex {
        while (true) {
            val current = mutexes.load()
            current[id.raw]?.let { return it }
            val created = Mutex()
            if (mutexes.compareAndSet(current, current.putting(id.raw, created))) return created
        }
    }
}

/** The shared revision counter of a store. */
internal class Revision {
    private val counter = MutableStateFlow(0L)
    val flow: StateFlow<Long> = counter.asStateFlow()

    fun bump() {
        counter.update { it + 1 }
    }
}
