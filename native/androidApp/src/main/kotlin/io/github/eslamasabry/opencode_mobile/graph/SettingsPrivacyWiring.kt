package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.engine.delivery.receipts.*
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.settings.presentation.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Explicit privacy commands only. Pending-command journals are compared, never changed or removed.
 */
@SingleIn(AppScope::class)
@Inject
class SettingsPrivacy(
    private val stores: PreviewStores,
    private val owners: Set<ConversationDrafts>,
    private val registry: ConnectionRegistry,
    private val scope: AppCoroutineScope,
) : LocalDataSettings {
    private val mutable = MutableStateFlow<LocalDataFacts?>(null)
    override val facts: StateFlow<LocalDataFacts?> = mutable.asStateFlow()
    private val operations = Mutex()
    private val reads = Mutex()
    private val queue by lazy { OfflineQueueStore(stores.prefs, stores.lanes.app) }

    init {
        scope.launch {
            if (stores.warm() is Outcome.Ok) {
                refresh()
                stores.prefs.changes.collect { keys ->
                    if (Drafts.KEY in keys || OfflineQueueStore.KEY in keys) refresh()
                }
            }
        }
    }

    override fun refresh() {
        scope.launch { reads.withLock { readFacts() } }
    }

    private suspend fun readFacts() {
        if (stores.warm() is Outcome.Failed) {
            mutable.value = LocalDataFacts(queueReadable = false, draftsReadable = false)
            return
        }
        val drafts = owners.singleOrNull()?.storageFacts()
        val d = (drafts as? Outcome.Ok)?.value
        val q = queue.load() as? QueueRead.Readable
        val bytes =
            (queue.version() as? PrefValue.Str)?.value?.encodeToByteArray()?.size?.toLong() ?: 0
        mutable.value =
            LocalDataFacts(
                q?.entries?.size ?: 0,
                bytes,
                q != null,
                d?.count ?: 0,
                d?.bytes ?: 0,
                d?.readable == true,
            )
    }

    private data class Guard(
        val expected: Map<String, PrefValue?>,
        val receiptIds: Set<String>,
        val pending: Boolean,
    )

    private fun guard(): Outcome<Guard> {
        val expected = LinkedHashMap<String, PrefValue?>()
        expected[ProfileKeys.PROFILES] = stores.prefs.get(ProfileKeys.PROFILES)
        val ids = stores.profiles.profiles.value.map { it.id }.toMutableSet()
        stores.prefs.keys(PendingCommandJournal.KEY_PREFIX).forEach {
            ids += ProfileId(it.removePrefix(PendingCommandJournal.KEY_PREFIX))
        }
        val journals =
            PendingCommandJournals(stores.prefs, stores.lanes, ProfileDirectory { it in ids })
        val protected = HashSet<String>()
        var pending = false
        for (id in ids) {
            val key = PendingCommandJournal.KEY_PREFIX + id.raw
            expected[key] = stores.prefs.get(key)
            val journal =
                when (val found = journals.forProfile(id)) {
                    is Outcome.Failed -> return found
                    is Outcome.Ok -> found.value
                }
            when (val receipts = journal.read()) {
                is Outcome.Failed -> return receipts
                is Outcome.Ok ->
                    for (receipt in receipts.value) {
                        if (!receipt.state.terminal) pending = true
                        if (receipt.commandId.startsWith("queue:"))
                            protected += receipt.commandId.removePrefix("queue:")
                    }
            }
        }
        return Guard(expected, protected, pending).ok()
    }

    override suspend fun discard(kind: LocalDataKind): Outcome<LocalDeleteResult> =
        scope.async { discardOwned(kind) }.await()

    private suspend fun discardOwned(kind: LocalDataKind): Outcome<LocalDeleteResult> =
        operations.withLock {
            if (stores.warm() is Outcome.Failed) return@withLock Failure.StorageRefused().failed()
            val guarded =
                when (val current = guard()) {
                    is Outcome.Failed -> return@withLock current
                    is Outcome.Ok -> current.value
                }
            val result =
                attempt({ Failure.StorageRefused() }) {
                        when (kind) {
                            LocalDataKind.Queue -> {
                                when (
                                    val cleared =
                                        queue.discardUnsent(guarded.expected, guarded.receiptIds)
                                ) {
                                    is Outcome.Failed -> cleared
                                    is Outcome.Ok -> {
                                        registry.live.value.values.forEach {
                                            (it as? ChatGraphAccessors)
                                                ?.chatDelivery
                                                ?.queue
                                                ?.refreshAfterExternalEdit()
                                        }
                                        val kept =
                                            (queue.load() as? QueueRead.Readable)?.entries?.size
                                                ?: return@attempt Failure.StorageRefused().failed()
                                        LocalDeleteResult(cleared.value, kept).ok()
                                    }
                                }
                            }
                            LocalDataKind.Drafts -> {
                                if (guarded.pending)
                                    return@attempt Failure.Refused(RefusalReason.DispatchUncertain)
                                        .failed()
                                val owner =
                                    owners.singleOrNull()
                                        ?: return@attempt Failure.StorageRefused().failed()
                                val strings = LinkedHashMap<String, String?>()
                                for ((key, value) in guarded.expected) {
                                    if (value != null && value !is PrefValue.Str)
                                        return@attempt Failure.StorageRefused().failed()
                                    strings[key] = (value as? PrefValue.Str)?.value
                                }
                                when (val cleared = owner.discardAll(strings)) {
                                    is Outcome.Failed -> cleared
                                    is Outcome.Ok ->
                                        LocalDeleteResult(
                                                cleared.value.removed,
                                                payloadsCollected = cleared.value.payloadsCollected,
                                            )
                                            .ok()
                                }
                            }
                        }
                    }
                    .flatMap { it }
            reads.withLock { readFacts() }
            result
        }
}
