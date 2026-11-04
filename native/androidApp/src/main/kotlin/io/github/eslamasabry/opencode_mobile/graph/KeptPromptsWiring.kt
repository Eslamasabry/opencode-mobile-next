package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.feature.prompts.presentation.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.keptConversation
import kotlin.io.encoding.Base64
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString

/**
 * The app-owned shelf is independent of the removed profile and keeps its complete queue records.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<KeptQueuedPrompts>())
class AndroidKeptQueuedPrompts(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
) : KeptQueuedPrompts {
    private val lane = Mutex()

    private data class Row(val raw: Any?, val prompt: QueuedPrompt) {
        val id: String
            get() =
                "kept-" +
                    Base64.UrlSafe.encode(
                        DartJson.encode(listOf(prompt.profileId.raw, prompt.id)).encodeToByteArray()
                    )

        override fun toString(): String = "KeptRow(${prompt.id},${prompt.attachments.size})"
    }

    private data class Shelf(val value: PrefValue?, val rows: List<Row>)

    private fun read(): Outcome<Shelf> {
        val value = stores.prefs.get(KEY)
        if (value != null && value !is PrefValue.Str) return Failure.StorageRefused().failed()
        val raw = (value as? PrefValue.Str)?.value ?: return Shelf(null, emptyList()).ok()
        if (raw.encodeToByteArray().size > QueueLimits.MAX_TOTAL_BYTES)
            return Failure.StorageRefused().failed()
        val list =
            DartJson.decodeOrNull(raw) as? List<*> ?: return Failure.StorageRefused().failed()
        if (list.size > QueueLimits.MAX_ENTRIES) return Failure.StorageRefused().failed()
        val rows = ArrayList<Row>()
        val seen = HashSet<Pair<String, String>>()
        try {
            for (item in list) {
                val prompt =
                    QueuedPromptCodec.fromJson(item) ?: return Failure.StorageRefused().failed()
                if (
                    !seen.add(prompt.profileId.raw to prompt.id) ||
                        prompt.payloadBytes > QueueLimits.MAX_ENTRY_BYTES
                )
                    return Failure.StorageRefused().failed()
                rows += Row(item, prompt)
            }
        } catch (_: Exception) {
            return Failure.StorageRefused().failed()
        }
        return Shelf(value, rows).ok()
    }

    override suspend fun list(): Outcome<List<SavedPromptItem>> {
        if (stores.warm() !is Outcome.Ok) return Failure.StorageRefused().failed()
        return when (val shelf = read()) {
            is Outcome.Failed -> shelf
            is Outcome.Ok ->
                shelf.value.rows
                    .map { row ->
                        SavedPromptItem(
                            row.id,
                            row.prompt.text,
                            row.prompt.createdAt,
                            row.prompt.attachments.size,
                            0,
                            kept = true,
                            uncertain = row.prompt.dispatched,
                        )
                    }
                    .sortedByDescending { it.createdAt }
                    .ok()
        }
    }

    override suspend fun delete(id: String): Outcome<SavedPromptRestore> = lane.withLock {
        if (stores.warm() !is Outcome.Ok) return@withLock Failure.StorageRefused().failed()
        repeat(8) {
            val shelf =
                when (val result = read()) {
                    is Outcome.Failed -> return@withLock result
                    is Outcome.Ok -> result.value
                }
            val row =
                shelf.rows.firstOrNull { it.id == id }
                    ?: return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            val next = shelf.rows.filterNot { it.id == id }.map { it.raw }
            when (
                val committed =
                    stores.prefs.compareAndCommit(stores.lanes.app, mapOf(KEY to shelf.value)) {
                        if (next.isEmpty()) remove(KEY) else putString(KEY, DartJson.encode(next))
                    }
            ) {
                is Outcome.Failed -> return@withLock committed
                is Outcome.Ok ->
                    if (committed.value) {
                        val undoLane = Mutex()
                        var restored = false
                        return@withLock SavedPromptRestore {
                            undoLane.withLock {
                                if (restored) Unit.ok()
                                else restoreRow(row).also { if (it is Outcome.Ok) restored = true }
                            }
                        }
                            .ok()
                    }
            }
        }
        Failure.StorageRefused().failed()
    }

    private suspend fun restoreRow(row: Row): Outcome<Unit> = lane.withLock {
        repeat(8) {
            val shelf =
                when (val result = read()) {
                    is Outcome.Failed -> return@withLock result
                    is Outcome.Ok -> result.value
                }
            val existing = shelf.rows.firstOrNull { it.id == row.id }
            if (existing != null)
                return@withLock if (DartJson.encode(existing.raw) == DartJson.encode(row.raw))
                    Unit.ok()
                else Failure.StorageRefused().failed()
            val next = shelf.rows.map { it.raw } + listOf(row.raw)
            val encoded = DartJson.encode(next)
            if (
                next.size > QueueLimits.MAX_ENTRIES ||
                    encoded.encodeToByteArray().size > QueueLimits.MAX_TOTAL_BYTES
            )
                return@withLock Failure.StorageRefused().failed()
            when (
                val committed =
                    stores.prefs.compareAndCommit(stores.lanes.app, mapOf(KEY to shelf.value)) {
                        putString(KEY, encoded)
                    }
            ) {
                is Outcome.Failed -> return@withLock committed
                is Outcome.Ok -> if (committed.value) return@withLock Unit.ok()
            }
        }
        Failure.StorageRefused().failed()
    }

    override suspend fun destinations(): Outcome<List<KeptPromptDestination>> {
        if (stores.warm() !is Outcome.Ok) return Failure.StorageRefused().failed()
        val rows = ArrayList<KeptPromptDestination>()
        var failed: Failure? = null
        for ((id, graph) in registry.live.value) {
            val profile = stores.profiles.profile(id) ?: continue
            if (
                !stores.lanes.forProfile(id).isAdmitting ||
                    profile.toServerProfile(managedOnPhone = graph.profile.managedOnPhone) !=
                        graph.profile
            )
                continue
            val location = graph.session.server.location.value
            val page = withTimeoutOrNull(10_000) { graph.session.server.sessionPage(limit = 100) }
            if (
                registry.live.value[id] !== graph ||
                    stores.profiles.profile(id) != profile ||
                    graph.session.server.location.value != location ||
                    !stores.lanes.forProfile(id).isAdmitting
            )
                continue
            if (page is Outcome.Ok)
                for (session in page.value.items) {
                    if (session.time?.archivedMs != null) continue
                    rows +=
                        KeptPromptDestination(
                            ConversationRef(id, session.id),
                            session.title?.takeIf { it.isNotBlank() }
                                ?: getString(Res.string.keptConversation),
                            profile.profile.name,
                        )
                }
            else failed = (page as? Outcome.Failed)?.failure ?: Failure.TimedOut()
        }
        return if (rows.isEmpty() && failed != null) failed.failed() else rows.ok()
    }

    override suspend fun restore(
        id: String,
        destination: ConversationRef,
    ): Outcome<KeptPromptDraft> {
        if (stores.warm() !is Outcome.Ok) return Failure.StorageRefused().failed()
        val shelf =
            when (val result = read()) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value
            }
        val row =
            shelf.rows.firstOrNull { it.id == id }
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val profile =
            stores.profiles.profile(destination.profileId)
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val graph =
            registry.live.value[destination.profileId]
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        if (
            profile.toServerProfile(managedOnPhone = graph.profile.managedOnPhone) !=
                graph.profile || profile.reentry.password || profile.reentry.agentToken
        )
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val location = graph.session.server.location.value
        fun current(): Boolean =
            stores.prefs.get(KEY) == shelf.value &&
                stores.profiles.profile(destination.profileId) == profile &&
                registry.live.value[destination.profileId] === graph &&
                graph.session.server.location.value == location &&
                stores.lanes.forProfile(destination.profileId).isAdmitting
        if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val checked =
            withTimeoutOrNull(10_000) { graph.session.server.session(destination.sessionId) }
        if (checked !is Outcome.Ok || checked.value.id != destination.sessionId || !current())
            return Failure.Refused(RefusalReason.StaleScope).failed()
        // A removed server's URL is never reinterpreted as a file on this server.
        val portable =
            row.prompt.attachments.filter { it.url.startsWith("data:") }.toImmutableList()
        val unavailable =
            row.prompt.attachments
                .filterNot { it.url.startsWith("data:") }
                .map { it.filename }
                .toImmutableList()
        return KeptPromptDraft(row.prompt.text, portable, unavailable, ::current).ok()
    }

    companion object {
        const val KEY = "oc.keptQueuedPrompts"
    }
}
