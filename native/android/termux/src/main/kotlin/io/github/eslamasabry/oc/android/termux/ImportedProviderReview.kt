package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Read-only counterpart of TermuxMigrationController.providerNames for the native sign-in review.
 * The exact saved receipt supplies the job and expected archive; the archive store rechecks the
 * imported manifest, every file hash, modes and parents before the fixed-label reader sees a
 * config. Neither a caller-supplied path nor a live config is accepted. A missing, malformed or
 * changed export discloses no names.
 */
public suspend fun readSavedMigrationProviderNames(
    record: JsonObject?,
    archives: TermuxMigrationArchives,
): List<String> {
    if (
        record == null ||
            (record["schema"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull != 1
    )
        return emptyList()
    val job =
        (record["job"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return emptyList()
    if (!JOB_ID.matches(job)) return emptyList()
    val config =
        (record["archives"] as? JsonObject)?.get("config") as? JsonObject ?: return emptyList()
    val bytes =
        (config["bytes"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            ?: return emptyList()
    val sha =
        (config["sha256"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return emptyList()
    if (bytes < 0 || bytes > TermuxMigrationArchiveStore.MAX_BYTES || !SHA256.matches(sha))
        return emptyList()
    return try {
        archives.providerNames(job, TermuxMigrationArchive(bytes, sha))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
}
