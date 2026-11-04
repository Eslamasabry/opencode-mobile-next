package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.engine.profiles.*

/**
 * Keeps the exact confirmed queue as recoverable app-owned prompts, in the same CAS that removes
 * its rows.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class ServerQueueDeletionCleaner(
    private val stores: PreviewStores,
    private val redactor: Redactor,
) : ProfileResourceCleaner {
    override val owner: String = ":engine:delivery (confirmed queue)"
    override val order: Int = DeletionStep.QUEUE
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        repeat(8) {
            val queueValue = stores.prefs.get(OfflineQueueStore.KEY)
            if (queueValue != null && queueValue !is PrefValue.Str)
                return Failure.StorageRefused().failed()
            val raw = (queueValue as? PrefValue.Str)?.value
            val read =
                OfflineQueueStore(stores.prefs, stores.lanes.app).load() as? QueueRead.Readable
                    ?: return Failure.StorageRefused().failed()
            val mine = read.entries.filter { it.profileId == profileId }
            if (mine.isEmpty()) return 0.ok()
            val actual = QueuedPromptRemovalPlan.fromSnapshot(profileId, raw)
            if (actual is Outcome.Failed) return actual
            if ((actual as Outcome.Ok).value != options.confirmedQueue)
                return Failure.Refused(RefusalReason.StaleScope).failed()
            val keptValue = stores.prefs.get(KEPT)
            if (keptValue != null && keptValue !is PrefValue.Str)
                return Failure.StorageRefused().failed()
            val nextKept =
                if (options.keepQueuedPromptsAsDrafts) {
                    val list =
                        if (keptValue == null) emptyList<Any?>()
                        else
                            DartJson.decodeOrNull((keptValue as PrefValue.Str).value) as? List<*>
                                ?: return Failure.StorageRefused().failed()
                    if (list.size > QueueLimits.MAX_ENTRIES)
                        return Failure.StorageRefused().failed()
                    val seen = HashSet<Pair<String, String>>()
                    for (existing in list) {
                        val decoded =
                            attempt({ Failure.StorageRefused() }) {
                                QueuedPromptCodec.fromJson(existing)
                            }
                        val entry =
                            (decoded as? Outcome.Ok)?.value
                                ?: return Failure.StorageRefused().failed()
                        if (
                            !seen.add(entry.profileId.raw to entry.id) ||
                                entry.payloadBytes > QueueLimits.MAX_ENTRY_BYTES
                        )
                            return Failure.StorageRefused().failed()
                    }
                    if (
                        DartJson.encode(list).encodeToByteArray().size > QueueLimits.MAX_TOTAL_BYTES
                    )
                        return Failure.StorageRefused().failed()
                    val saved = list.toMutableList()
                    fun redact(value: Any?): Any? =
                        when (value) {
                            is String -> redactor.redact(value)
                            is List<*> -> value.map(::redact)
                            is Map<*, *> -> value.mapValues { redact(it.value) }
                            else -> value
                        }
                    for (entry in mine) {
                        val safe = redact(QueuedPromptCodec.toJson(entry))
                        val decoded =
                            attempt({ Failure.StorageRefused() }) {
                                QueuedPromptCodec.fromJson(safe)
                            }
                        val next =
                            (decoded as? Outcome.Ok)?.value
                                ?: return Failure.StorageRefused().failed()
                        if (
                            next.payloadBytes > QueueLimits.MAX_ENTRY_BYTES ||
                                next.id != entry.id ||
                                next.profileId != entry.profileId ||
                                next.sessionId != entry.sessionId
                        )
                            return Failure.StorageRefused().failed()
                        val same = saved.filter {
                            val row = it as? Map<*, *>
                            row?.get("id") == entry.id &&
                                row?.get("profileID") == entry.profileId.raw
                        }
                        if (same.isNotEmpty()) {
                            if (
                                same.size != 1 ||
                                    DartJson.encode(same.single()) != DartJson.encode(safe)
                            )
                                return Failure.StorageRefused().failed()
                        } else saved += safe
                    }
                    val encoded = DartJson.encode(saved)
                    if (
                        saved.size > QueueLimits.MAX_ENTRIES ||
                            encoded.encodeToByteArray().size > QueueLimits.MAX_TOTAL_BYTES
                    )
                        return Failure.StorageRefused().failed()
                    encoded
                } else null
            val nextQueue = read.entries.filterNot { it.profileId == profileId }
            val expected = mapOf(OfflineQueueStore.KEY to queueValue, KEPT to keptValue)
            when (
                val write =
                    stores.prefs.compareAndCommit(stores.lanes.app, expected) {
                        if (nextQueue.isEmpty()) remove(OfflineQueueStore.KEY)
                        else
                            putString(
                                OfflineQueueStore.KEY,
                                QueuedPromptCodec.encodeQueue(nextQueue),
                            )
                        if (nextKept != null) putString(KEPT, nextKept)
                    }
            ) {
                is Outcome.Failed -> return write
                is Outcome.Ok -> if (write.value) return mine.size.ok()
            }
        }
        return Failure.StorageRefused().failed()
    }

    companion object {
        const val KEPT = "oc.keptQueuedPrompts"
    }
}
