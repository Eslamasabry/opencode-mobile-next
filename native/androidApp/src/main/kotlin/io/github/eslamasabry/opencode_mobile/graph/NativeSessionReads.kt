package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.domain.SessionReadStateGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.agents.feed.BriefBlocker
import io.github.eslamasabry.oc.engine.agents.feed.BriefPreferences
import io.github.eslamasabry.oc.engine.agents.feed.BriefScope
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBriefStore
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.settings.consent.ConsentKeys
import io.github.eslamasabry.oc.engine.settings.consent.ReadStateConsentStore
import io.github.eslamasabry.oc.engine.settings.scope.SettingsOwners
import io.github.eslamasabry.oc.feature.chat.world.SessionReadRecorder
import io.github.eslamasabry.oc.feature.chats.presentation.NoReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBriefFeed
import io.github.eslamasabry.oc.feature.servers.presentation.ServerReadSharing
import io.github.eslamasabry.oc.feature.servers.presentation.ServerReadSharingUi
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable device-only views and exact return-brief dismissal, shared by every live connection. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(
    AppScope::class,
    binding = binding<ReturnBriefFeed>(),
    replaces = [NoReturnBrief::class],
)
class NativeSessionReads(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val activities: ResumedActivities,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) : ReturnBriefFeed, SessionReadRecorder, ProfileCloser {
    private val lock = Any()
    private val watermarks = mutableMapOf<ProfileId, LinkedHashMap<String, Long>>()
    private val closed = ConcurrentHashMap.newKeySet<ProfileId>()
    private val writes = ConcurrentHashMap<ProfileId, Mutex>()

    private fun lane(id: ProfileId): Mutex = writes.getOrPut(id) { Mutex() }

    private val changes = MutableStateFlow(0L)
    val revision: StateFlow<Long> = changes.asStateFlow()
    private val ownersChanged = MutableStateFlow(0L)
    private val owners =
        object : SettingsOwners {
            override val revision: StateFlow<Long> = ownersChanged

            override fun fingerprint(profileId: ProfileId): String? {
                val saved = stores.profiles.profile(profileId)?.profile ?: return null
                return DartJson.encode(
                    listOf(saved.baseUrl, saved.backend.name, saved.username, saved.codexDirectory)
                )
            }
        }
    // No app-wide legacy default is migrated here: a new native profile remains private.
    private val consent = ReadStateConsentStore(stores.prefs, stores.lanes, owners)
    private val mutableBrief = MutableStateFlow(ReturnBrief.None)
    override val brief: StateFlow<ReturnBrief> = mutableBrief.asStateFlow()
    private val briefStore =
        ReturnBriefStore(
            object : BriefPreferences {
                override suspend fun read(key: String): String? = stores.prefs.string(key)

                override suspend fun write(key: String, value: String): Boolean {
                    val profile = ProfileId(key.removePrefix(ReturnBriefStore.PREFIX))
                    if (profile in closed || stores.profiles.profile(profile) == null) return false
                    return stores.prefs.commit(stores.lanes.forProfile(profile)) {
                        putString(key, value)
                    } is Outcome.Ok
                }

                override suspend fun remove(key: String) {
                    // Profile deletion uses the generic sweep after our admitted writes drain.
                    val profile = ProfileId(key.removePrefix(ReturnBriefStore.PREFIX))
                    stores.prefs.commit(stores.lanes.forProfile(profile)) { remove(key) }
                }
            }
        )

    init {
        appScope.launch(dispatchers.main) {
            if (stores.warm() !is Outcome.Ok) return@launch
            launch {
                stores.profiles.profiles.collect {
                    ownersChanged.update { it + 1 }
                    changed()
                }
            }
            launch { consent.revision.collect { changed() } }
        }
    }

    private fun changed() {
        changes.update { it + 1 }
    }

    /** Uses the saved URL verbatim, preserving Flutter's persisted location identity. */
    fun locationKey(profile: ServerProfile, location: ServerLocation): String =
        DartJson.encode(listOf(baseUrl(profile), location.directory, location.workspace))

    private fun baseUrl(profile: ServerProfile): String =
        stores.profiles.profile(profile.id)?.profile?.baseUrl ?: profile.origin.toString()

    private fun sessionKey(
        profile: ServerProfile,
        location: ServerLocation,
        session: Session,
    ): String =
        DartJson.encode(
            listOf(
                baseUrl(profile),
                session.directory ?: location.directory,
                session.workspaceId?.raw ?: location.workspace,
                session.id.raw,
            )
        )

    private fun load(profile: ProfileId): LinkedHashMap<String, Long> =
        watermarks.getOrPut(profile) {
            val decoded =
                stores.prefs.string(VIEWS + profile.raw)?.let(DartJson::decodeOrNull) as? Map<*, *>
            LinkedHashMap<String, Long>().also { out ->
                decoded?.forEach { (key, value) ->
                    if (
                        key is String &&
                            value is Number &&
                            value.toDouble() == value.toLong().toDouble() &&
                            value.toLong() >= 0
                    )
                        out[key] = value.toLong()
                }
            }
        }

    fun unread(
        profile: ServerProfile,
        location: ServerLocation,
        session: Session,
        busy: Boolean,
    ): Boolean {
        if (
            busy ||
                profile.id in closed ||
                stores.profiles.profile(profile.id)?.toServerProfile() != profile
        )
            return false
        val idle = session.time?.idleMs ?: 0
        if (idle <= 0) return false
        val local =
            synchronized(lock) { load(profile.id)[sessionKey(profile, location, session)] ?: 0 }
        val remote = if (consent.enabled(profile.id)) session.time?.viewedMs ?: 0 else 0
        return idle > local && idle > remote
    }

    fun sharing(profile: ProfileId): Boolean = profile !in closed && consent.enabled(profile)

    fun sharingSaving(profile: ProfileId): Boolean = consent.saving(profile)

    suspend fun setSharing(profile: ProfileId, enabled: Boolean): Outcome<Unit> {
        val saved = stores.profiles.profile(profile) ?: return stale()
        return consent.setEnabledIfCurrent(profile, enabled) {
            profile !in closed && stores.profiles.profile(profile) === saved
        }
    }

    override suspend fun viewed(
        graph: ConnectionGraph,
        session: Session,
        stillVisible: () -> Boolean,
    ): Outcome<Unit> {
        val profile = graph.profile
        val saved = stores.profiles.profile(profile.id) ?: return stale()
        val location = graph.session.server.location.value
        val idle = session.time?.idleMs?.takeIf { it > 0 } ?: return Outcome.Ok(Unit)
        val ticket = consent.ticket(profile.id)
        fun current(): Boolean =
            profile.id !in closed &&
                activities.current != null &&
                stillVisible() &&
                stores.profiles.profile(profile.id) === saved &&
                registry.live.value[profile.id] === graph &&
                graph.session.server.location.value == location
        if (!current()) return stale()
        val committed: Outcome<Unit> = lane(profile.id).withLock {
            if (!current()) return@withLock stale()
            val key = sessionKey(profile, location, session)
            val snapshot =
                synchronized(lock) {
                    val rows = load(profile.id)
                    rows[key] = maxOf(rows[key] ?: 0, idle)
                    if (rows.size > MAX_VIEWS) {
                        rows.entries
                            .sortedBy { it.value }
                            .take(rows.size - MAX_VIEWS)
                            .forEach { rows.remove(it.key) }
                    }
                    DartJson.encode(rows)
                }
            changed()
            val recorded =
                withContext(NonCancellable) {
                    if (profile.id in closed) return@withContext stale()
                    stores.prefs.commit(stores.lanes.forProfile(profile.id)) {
                        putString(VIEWS + profile.id.raw, snapshot)
                    }
                }
            if (recorded is Outcome.Failed) return@withLock recorded
            Outcome.Ok(Unit)
        }
        if (committed is Outcome.Failed) return committed
        // A local read never grants remote sharing, wakes a server or queues a write for later.
        // The receipt is sent outside the profile lane so a slow server never holds up the
        // brief/feed publication that waits on the same lane.
        val reader = graph.session.server as? SessionReadStateGateway
        if (
            reader != null &&
                ticket != null &&
                current() &&
                consent.stillShared(ticket) &&
                (session.time?.viewedMs ?: 0) < idle
        ) {
            val shared = reader.viewSession(session.id, idle)
            if (shared is Outcome.Failed) return shared
            if (current() && consent.stillShared(ticket)) changed()
        }
        return Outcome.Ok(Unit)
    }

    suspend fun publishBrief(
        profile: ServerProfile,
        location: ServerLocation,
        sessions: List<Session>,
        busy: Set<SessionId>,
        blockers: Map<SessionId, Pair<BriefBlocker, String>>,
        inventoryPartial: Boolean,
        readStateKnown: Boolean,
    ) {
        val saved = stores.profiles.profile(profile.id) ?: return
        fun current(): Boolean =
            profile.id !in closed &&
                stores.profiles.profile(profile.id) === saved &&
                stores.profiles.activeId.value == profile.id &&
                registry.live.value[profile.id]?.profile == profile &&
                registry.live.value[profile.id]?.session?.server?.location?.value == location
        if (!current()) return
        lane(profile.id).withLock {
            if (!current()) return@withLock
            val key = locationKey(profile, location)
            val ack = briefStore.acknowledged(profile.id, key)
            if (!current()) return@withLock
            mutableBrief.value =
                ReturnBrief.build(
                    sessions = sessions,
                    readStateKnown = readStateKnown,
                    inventoryPartial = inventoryPartial,
                    isUnread = { unread(profile, location, it, it.id in busy) },
                    isBusy = { it in busy },
                    blockerOf = { blockers[it] },
                    ack = ack,
                    scope = BriefScope(profile.id, "opencode:${profile.id.raw}", key),
                )
        }
    }

    fun clearBrief() {
        mutableBrief.value = ReturnBrief.None
    }

    override suspend fun dismiss(shown: ReturnBrief): Outcome<Unit> {
        val scope = shown.scope ?: return stale()
        return lane(scope.profileId).withLock {
            val graph = registry.live.value[scope.profileId] ?: return@withLock stale()
            if (
                scope.profileId in closed ||
                    brief.value.scope != scope ||
                    stores.profiles.activeId.value != scope.profileId ||
                    stores.profiles.profile(scope.profileId) == null ||
                    locationKey(graph.profile, graph.session.server.location.value) !=
                        scope.location
            )
                return@withLock stale()
            val result = briefStore.acknowledge(scope.profileId, scope.location, shown)
            if (result is Outcome.Ok) changed()
            result
        }
    }

    override val owner: String = ":engine:connection/read-state"

    override fun stopAdmission(profile: ProfileId) {
        closed.add(profile)
        consent.closeProfile(profile)
        if (mutableBrief.value.scope?.profileId == profile) clearBrief()
        changed()
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> =
        lane(profile).withLock {
            // The consent owner has its own serialized writes; drain them and prune its shared
            // journal too.
            when (val forgotten = consent.forgetProfile(profile)) {
                is Outcome.Failed -> return@withLock forgotten
                is Outcome.Ok -> Unit
            }
            synchronized(lock) { watermarks.remove(profile) }
            briefStore.forgetProfile(profile)
            Outcome.Ok(Unit)
        }

    override fun reopen(profile: ProfileId) {
        closed.remove(profile)
        consent.reopenProfile(profile)
        changed()
    }

    private fun stale(): Outcome.Failed = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    companion object {
        private const val VIEWS = "oc.sessionViews."
        private const val MAX_VIEWS = 2048
        val shapes: List<KeyShape> =
            listOf(
                KeyShape(
                    VIEWS,
                    KeyScope.Profile,
                    PrefType.Str,
                    ":engine:connection",
                    "device-only viewed idle watermarks",
                ),
                KeyShape(
                    ReturnBriefStore.PREFIX,
                    KeyScope.Profile,
                    PrefType.Str,
                    ":engine:connection",
                    "exact return brief dismissals",
                ),
            ) + ConsentKeys.shapes
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object SessionReadBindings {
    @Provides @IntoSet fun recorder(reads: NativeSessionReads): SessionReadRecorder = reads

    @Provides @IntoSet fun closer(reads: NativeSessionReads): ProfileCloser = reads

    @Provides
    @IntoSet
    fun sharing(reads: NativeSessionReads, registry: ConnectionRegistry): ServerReadSharing =
        object : ServerReadSharing {
            override val revision: StateFlow<Long> = reads.revision

            override fun state(profile: ProfileId): ServerReadSharingUi =
                ServerReadSharingUi(
                    available =
                        registry.live.value[profile]?.session?.server is SessionReadStateGateway,
                    enabled = reads.sharing(profile),
                    saving = reads.sharingSaving(profile),
                )

            override suspend fun set(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
                reads.setSharing(profile, enabled)
        }
}
