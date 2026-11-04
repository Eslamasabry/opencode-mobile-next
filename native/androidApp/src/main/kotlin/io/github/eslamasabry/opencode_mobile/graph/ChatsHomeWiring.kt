package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtras
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningRow
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.NoChatsHomeExtras
import io.github.eslamasabry.oc.feature.chats.presentation.SessionTitles
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One source selector over the actual runtime; hiding a source never closes a graph an open chat
 * may still own.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoChatsHomeExtras::class])
@Inject
class AppChatsHome(
    private val runtime: PreviewRuntime,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val clock: Clock,
    private val monitor: AppChatsMonitor,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) : ChatsHomeExtras {
    private val lane = dispatchers.serial("chats-home")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val mutable = MutableStateFlow(ChatsHomeExtraState())
    override val snapshot: StateFlow<ChatsHomeExtraState> = mutable.asStateFlow()
    private var actionFailure: Failure? = null
    private val cachedRevisions = mutableMapOf<ProfileId, Pair<String, List<ChatFeedItem>>>()

    init {
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            combine(
                    runtime.servers,
                    runtime.busy,
                    runtime.snapshot,
                    runtime.profiles.activeId,
                    monitor.snapshot,
                ) { _, _, _, _, _ ->
                    Unit
                }
                .collect {
                    publish()
                    saveCurrentInventory()
                }
        }
    }

    private fun publish() {
        val active = runtime.profiles.activeId.value
        val sources =
            runtime.profiles.profiles.value
                .map { loaded ->
                    ChatsListSourceUi(
                        profile = loaded.id,
                        name = loaded.profile.name,
                        shown = runtime.listSourceShown(loaded.id),
                        main = loaded.id == active,
                        onThisPhone = runtime.sourceOnThisPhone(loaded.profile),
                        unreachable = loaded.id in runtime.sourceFailures,
                    )
                }
                .toImmutableList()
        val owner = active?.let(runtime.profiles::profile)
        val opening =
            if (owner != null && !runtime.activeListReady()) {
                ChatsOpeningUi(
                    phase =
                        if (runtime.busy.value) ChatsOpeningPhase.Connecting
                        else ChatsOpeningPhase.Failed,
                    serverName = owner.profile.name,
                    rows = readInventory(owner).toImmutableList(),
                    failure = runtime.sourceFailures[active],
                )
            } else null
        mutable.value = ChatsHomeExtraState(sources, opening, actionFailure, monitor.snapshot.value)
    }

    override suspend fun showSource(profile: ProfileId, shown: Boolean): Outcome<Unit> =
        record(runtime.showListSource(profile, shown))

    override suspend fun retrySource(profile: ProfileId): Outcome<Unit> =
        record(runtime.retryListSource(profile))

    override suspend fun retryOpening(): Outcome<Unit> {
        val active =
            runtime.profiles.activeId.value
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        return record(runtime.retryListSource(active))
    }

    override suspend fun setWatched(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        monitor.setWatched(profile, enabled)

    override suspend fun refreshWatched(): Outcome<Unit> = monitor.refresh()

    override suspend fun openWatched(
        key: String
    ): Outcome<io.github.eslamasabry.oc.core.common.ids.ConversationRef> = monitor.open(key)

    private suspend fun record(result: Outcome<Unit>): Outcome<Unit> =
        withContext(lane) {
            actionFailure = (result as? Outcome.Failed)?.failure
            publish()
            result
        }

    private fun location(owner: LoadedProfile): ServerLocation {
        registry.live.value[owner.id]?.session?.server?.location?.value?.let {
            return it
        }
        if (owner.profile.usesAgentSocket)
            return ServerLocation(directory = owner.profile.codexDirectory)
        val saved =
            stores.prefs.string("oc.location.${owner.id.raw}")?.let(DartJson::decodeOrNull)
                as? Map<*, *>
        return ServerLocation(
            directory = saved?.get("directory") as? String,
            workspace = saved?.get("workspace") as? String,
        )
    }

    private fun inventoryScope(owner: LoadedProfile): String {
        val p = owner.profile
        val loc = location(owner)
        val backend =
            when (p.backend) {
                BackendKind.OpenCode2 -> "openCode"
                BackendKind.Paseo -> "paseo"
                BackendKind.Codex -> "codex"
            }
        return DartJson.sha256Hex(
            DartJson.encode(
                listOf(backend, "v2", p.baseUrl, p.username, loc.directory, loc.workspace)
            )
        )
    }

    private fun readInventory(owner: LoadedProfile): List<ChatsOpeningRow> {
        if (owner.cleartextUnconfirmed || owner.reentry.password || owner.reentry.agentToken)
            return emptyList()
        val raw = stores.prefs.string("oc.sessionInventory.${owner.id.raw}") ?: return emptyList()
        if (raw.length > 65_536) return emptyList()
        val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return emptyList()
        if (data["v"] != 1L && data["v"] != 1) return emptyList()
        if (data["scope"] != inventoryScope(owner)) return emptyList()
        val rows = data["rows"] as? List<*> ?: return emptyList()
        if (rows.size > 100) return emptyList()
        return rows
            .mapNotNull { value ->
                val row = value as? Map<*, *> ?: return@mapNotNull null
                val id = row["id"] as? String ?: return@mapNotNull null
                val title = row["title"] as? String ?: return@mapNotNull null
                if (id.isEmpty() || id.length > 512 || title.length > 1024) return@mapNotNull null
                ChatsOpeningRow(id, SessionTitles.present(redactor.redact(title)))
            }
            .distinctBy { it.key }
    }

    private suspend fun saveCurrentInventory() {
        val id = runtime.profiles.activeId.value ?: return
        val owner = runtime.profiles.profile(id) ?: return
        if (!runtime.activeListReady()) return
        val graph = registry.live.value[id] ?: return
        val signature = inventoryScope(owner)
        val items =
            runtime.snapshot.value.items
                .filter { it.profileId == id }
                .sortedByDescending { it.lastActivityMs }
                .take(100)
        if (cachedRevisions[id] == (signature to items)) return
        val rows = mutableListOf<Map<String, Any>>()
        var bytes = 0
        for (item in items) {
            if (item.sessionId.raw.isEmpty() || item.sessionId.raw.length > 512) continue
            val row =
                mapOf(
                    "id" to item.sessionId.raw,
                    "title" to redactor.redact(item.title.orEmpty()).take(256),
                    "updated" to item.lastActivityMs,
                )
            bytes += DartJson.encode(row).encodeToByteArray().size + 1
            if (bytes > 65_024) break
            rows += row
        }
        val encoded =
            DartJson.encode(
                mapOf(
                    "v" to 1,
                    "scope" to signature,
                    "at" to clock.now().toEpochMilliseconds(),
                    "rows" to rows,
                )
            )
        val result =
            stores.prefs.commit(stores.lanes.forProfile(id)) {
                if (
                    runtime.profiles.profile(id) == owner &&
                        registry.live.value[id] === graph &&
                        inventoryScope(owner) == signature
                ) {
                    putString("oc.sessionInventory.${id.raw}", encoded)
                }
            }
        if (result is Outcome.Ok) cachedRevisions[id] = signature to items
    }
}
