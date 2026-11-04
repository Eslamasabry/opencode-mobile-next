package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One writer for confirmed phone restarts, preserving the Flutter while-away history and its
 * acknowledgements.
 */
@Inject
@SingleIn(AppScope::class)
class NativeRuntimeActs(private val stores: PreviewStores, private val redactor: Redactor) {
    private val lane = Mutex()

    suspend fun recordRestart(profileId: String, eventId: String, atMillis: Long): Outcome<Unit> =
        lane.withLock {
            if (
                !PROFILE.matches(profileId) ||
                    eventId.isEmpty() ||
                    eventId.length > 256 ||
                    atMillis <= 0
            )
                return@withLock refused()
            val id = ProfileId(profileId)
            val profile = stores.profiles.profile(id) ?: return@withLock deleted()
            val location = DartJson.encode(listOf(profile.profile.baseUrl))
            val event = DartJson.sha256Hex(DartJson.encode(listOf(location, eventId)))
            val key = "oc.automaticActivity.$profileId"
            val summary =
                redactor
                    .diagnostics(profile.profile.name, 1600)
                    .replace(Regex("[\\s\\u0000-\\u001f\\u007f]+"), " ")
                    .trim()
                    .take(400)
            if (summary.isEmpty()) return@withLock refused()
            try {
                repeat(3) {
                    if (stores.profiles.profile(id) == null) return@withLock deleted()
                    val before = stores.prefs.get(key)
                    val entries = decode(before) ?: return@withLock refused()
                    if (entries.any { it["id"] == event }) return@withLock Outcome.Ok(Unit)
                    val next =
                        entries +
                            mapOf(
                                "id" to event,
                                "location" to DartJson.sha256Hex(location),
                                "kind" to "restart",
                                "summary" to summary,
                                "at" to atMillis,
                                "session" to null,
                                "acknowledged" to false,
                                "undoAttempted" to false,
                                "undone" to false,
                            )
                    val bounded =
                        next
                            .sortedWith(
                                compareByDescending<Map<String, Any?>> { it["at"] as Long }
                                    .thenBy { it["id"] as String }
                            )
                            .take(200)
                    val encoded = DartJson.encode(mapOf("version" to 1, "acts" to bounded))
                    if (encoded.length > 512000) return@withLock refused()
                    when (
                        val saved =
                            stores.prefs.compareAndCommit(
                                stores.lanes.forProfile(id),
                                mapOf(key to before),
                            ) {
                                putString(key, encoded)
                            }
                    ) {
                        is Outcome.Failed -> return@withLock saved
                        is Outcome.Ok -> if (saved.value) return@withLock Outcome.Ok(Unit)
                    }
                }
                refused()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refused()
            }
        }

    private fun decode(before: PrefValue?): List<Map<String, Any?>>? {
        if (before == null) return emptyList()
        val raw = (before as? PrefValue.Str)?.value ?: return null
        if (raw.length > 512000) return null
        val record = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        if (record["version"] != 1L) return null
        val entries = record["acts"] as? List<*> ?: return null
        if (entries.size > 200) return null
        val ids = mutableSetOf<String>()
        return entries.map { value ->
            val row = value as? Map<*, *> ?: return null
            val id = row["id"] as? String ?: return null
            val location = row["location"] as? String ?: return null
            val summary = row["summary"] as? String ?: return null
            val at = row["at"] as? Long ?: return null
            val session = row["session"]
            val attempted = row["undoAttempted"] as? Boolean ?: return null
            val undone = row["undone"] as? Boolean ?: return null
            if (
                !HASH.matches(id) ||
                    !HASH.matches(location) ||
                    !ids.add(id) ||
                    summary.isBlank() ||
                    summary.length > 1600 ||
                    at <= 0 ||
                    row["kind"] !in KINDS ||
                    row["acknowledged"] !is Boolean ||
                    (session != null && (session !is String || session.length > 256)) ||
                    (undone && !attempted)
            )
                return null
            row.entries.associate { (key, field) -> (key as? String ?: return null) to field }
        }
    }

    private fun refused(): Outcome.Failed = Outcome.Failed(Failure.StorageRefused())

    private fun deleted(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))

    private companion object {
        val PROFILE = Regex("[A-Za-z0-9_-]{1,128}")
        val HASH = Regex("[a-f0-9]{64}")
        val KINDS =
            setOf(
                "reconnect",
                "restart",
                "heatPause",
                "heatStop",
                "heatResume",
                "update",
                "permissionApproval",
                "queuedSend",
                "other",
            )
    }
}
