package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException

/**
 * Failed-run receipts the Flutter build's worker left in `oc.scheduleReceipts.<profile>` (ScheduledPromptReceipts.kt:
 * a JSON array of `{id, scheduleId, revision, scheduledAtMs, finishedAtMs, status, error}`) when it died or timed out
 * before Dart could record the run (E00-S05). Port of lib/scheduling/controller.dart `_mergeReceipts`: on the
 * schedule list's load each receipt becomes a failed run in the history the list shows, advances the occurrence it
 * names when nothing moved it since, and is then acknowledged (removed). Native's own runs never write receipts.
 */
internal class ScheduleReceipts(
    private val backend: ScheduleBackend,
    private val store: ScheduleStore,
    private val nowMs: () -> Long,
    private val zone: () -> TimeZone,
) {
    suspend fun merge(profileId: String) {
        val raw = read(ScheduleKeys.receipts(profileId)) ?: return
        val rows = DartJson.decodeOrNull(raw) as? List<*> ?: throw ScheduleStoreException(ScheduleCode.StorageCorrupt)
        for (row in rows) {
            val receipt = row as? Map<*, *> ?: throw ScheduleStoreException(ScheduleCode.StorageCorrupt)
            val id = receipt["id"] as? String
            val scheduleId = receipt["scheduleId"] as? String
            val dueMs = receipt["scheduledAtMs"] as? Long
            val finishMs = receipt["finishedAtMs"] as? Long
            if (id == null || scheduleId == null || dueMs == null || finishMs == null) {
                throw ScheduleStoreException(ScheduleCode.StorageCorrupt)
            }
            val schedule = store.load(profileId).firstOrNull { it.id == scheduleId }
            if (schedule != null) {
                val existing = store.history(profileId).firstOrNull { it.id == id }
                if (existing == null || existing.status == RunStatus.Running) {
                    store.recordRun(
                        profileId,
                        ScheduledRun(
                            id = id,
                            scheduleId = scheduleId,
                            scheduledAtMs = dueMs,
                            startedAtMs = existing?.startedAtMs ?: finishMs,
                            finishedAtMs = finishMs,
                            status = RunStatus.Failed,
                            sessionId = existing?.sessionId,
                            late = finishMs - dueMs > Recurrences.LATE_TOLERANCE_MS,
                            errorCode = errorCode(receipt["error"]),
                        ),
                    )
                }
                // A persisted terminal receipt may precede a crash during advancement. Advance it too, while
                // keeping edits made since that occurrence.
                if (receipt["revision"] == schedule.revision.toLong() && schedule.nextRunAtMs == dueMs) {
                    store.advanceOccurrence(schedule, Recurrences.next(schedule.recurrence, nowMs(), zone()))
                }
            }
            acknowledge(profileId, id)
        }
    }

    /** Only the fixed codes Flutter kept; anything else reads "interrupted". */
    private fun errorCode(error: Any?): String =
        when (error) {
            "timeout" -> "timeout"
            "unavailable" -> "unavailable"
            else -> "interrupted"
        }

    private suspend fun acknowledge(profileId: String, id: String) {
        val key = ScheduleKeys.receipts(profileId)
        repeat(ATTEMPTS) {
            val raw = read(key) ?: return
            val rows = DartJson.decodeOrNull(raw) as? List<*> ?: throw ScheduleStoreException(ScheduleCode.StorageCorrupt)
            val remaining = rows.filterNot { (it as? Map<*, *>)?.get("id") == id }
            if (remaining.size == rows.size) return
            if (write(key, raw, DartJson.encode(remaining))) return
        }
        throw ScheduleStoreException(ScheduleCode.StorageBusy)
    }

    private suspend fun read(key: String): String? = io { backend.read(key) }

    private suspend fun write(key: String, expected: String, value: String): Boolean = io { backend.compareAndSet(key, expected, value) }

    private suspend fun <T> io(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScheduleStoreException) {
            throw e
        } catch (e: Exception) {
            throw ScheduleStoreException(ScheduleCode.StorageUnavailable)
        }

    private companion object {
        const val ATTEMPTS = 20
    }
}
