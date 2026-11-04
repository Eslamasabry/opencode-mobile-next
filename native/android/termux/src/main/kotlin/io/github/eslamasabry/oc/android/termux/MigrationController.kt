package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Finishes a verified import by switching to the in-app server (see [TermuxMigrationProfileSwitcher]). */
public fun interface MigrationProfileSwitch {
    public suspend fun switchProfile(
        sourceProfileId: String,
        stillWanted: () -> Boolean,
        verifiedProjectPaths: Map<String, String>,
    ): String
}

/**
 * One foreground migration owner (Flutter TermuxMigrationController). Keep it above routes; never start it from
 * composition. Call it from one lane (the main thread): like the Dart original it is not thread-safe, it only
 * interleaves at suspension points. [scope] runs the bounded stop command that [cancel] starts. No error or callback
 * data from either bridge is exposed in snapshots.
 */
public class TermuxMigrationController(
    private val transport: TermuxMigrationTransport,
    private val archives: TermuxMigrationArchives,
    private val journal: TermuxMigrationJournal,
    private val availableBytes: suspend () -> Long?,
    private val switchProfile: MigrationProfileSwitch,
    private val sourceProfileExists: (String) -> Boolean,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val newJobId: () -> String = ::randomJobId,
) {
    public companion object {
        public const val MAX_ARCHIVE_BYTES: Long = MIGRATION_MAX_ARCHIVE_BYTES
        public const val RESERVE_BYTES: Long = MIGRATION_RESERVE_BYTES
        public val ATTEMPT_LIMIT: kotlin.time.Duration = 15.minutes
        private val active = AtomicReference<TermuxMigrationController?>(null)

        private fun randomJobId(): String {
            val bytes = ByteArray(16).also(SecureRandom()::nextBytes)
            return hex(bytes)
        }
    }

    private val listeners = ArrayList<() -> Unit>()
    private var attemptStarted: Instant? = null
    private var cancelled = false
    private var disposed = false
    private var running = false
    private var settlement: CompletableDeferred<Unit>? = null
    private var stopCommand: Job? = null
    private var reviewSource: TermuxMigrationSource? = null
    private var appAvailableBytes: Long? = null
    private var jobId: String? = null
    private var sourceProfileId: String? = null

    public var snapshot: TermuxMigrationSnapshot = TermuxMigrationSnapshot(phase = TermuxMigrationPhase.checking)
        private set

    /** An admitted operation runs (true until it and its stop command have exited). */
    public val busy: Boolean get() = running

    public fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    public fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) listener()
    }

    /** Completes once the admitted operation AND its stop command have exited. Idle controllers are settled. */
    public suspend fun awaitSettled() {
        settlement?.await()
    }

    private val expired: Boolean
        get() = attemptStarted?.let { clock.now() - it >= ATTEMPT_LIMIT } ?: false

    private val wanted: Boolean
        get() = !cancelled && !disposed && !expired && (sourceProfileId?.let(sourceProfileExists) ?: true)

    private fun beginOperation(): Boolean {
        if (disposed || running) return false
        if (!active.compareAndSet(null, this) && active.get() !== this) return false
        running = true
        cancelled = false
        stopCommand = null
        settlement = CompletableDeferred()
        return true
    }

    private suspend fun finishOperation() {
        stopCommand?.join()
        running = false
        active.compareAndSet(this, null)
        attemptStarted = null
        sourceProfileId = null
        settlement?.complete(Unit)
        if (!disposed) notifyListeners()
    }

    private fun publish(
        phase: TermuxMigrationPhase,
        failure: TermuxMigrationFailure? = null,
        item: TermuxMigrationItem? = null,
        source: TermuxMigrationSource? = null,
        requiredBytes: Long? = null,
        available: Long? = null,
        destination: String? = null,
    ) {
        snapshot = TermuxMigrationSnapshot(
            phase = phase,
            failure = failure,
            jobId = jobId,
            item = item,
            source = source ?: snapshot.source,
            requiredBytes = requiredBytes,
            availableBytes = available,
            destinationProfileId = destination,
        )
        if (!disposed) notifyListeners()
    }

    private fun checkCancelled() {
        if (expired) fail(TermuxMigrationFailure.timedOut)
        if (!wanted) fail(TermuxMigrationFailure.cancelled)
    }

    /** A fresh inventory; no archive, destination or profile is written here. */
    public suspend fun check(selected: Set<TermuxMigrationItem> = emptySet()) {
        if (!beginOperation()) return
        jobId = null
        try {
            preflight(selected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e)
        } finally {
            finishOperation()
        }
    }

    /** Recomputes the selection cost from the latest check, with no bridge call. */
    public fun reviewSpace(selected: Set<TermuxMigrationItem>): MigrationAnswer<TermuxMigrationSpace> {
        val source = reviewSource ?: return MigrationAnswer.Refused(TermuxMigrationFailure.invalidSelection)
        return migrationSpaceFor(source, selected, appAvailableBytes)
    }

    private fun reviewSpaceOrFail(selected: Set<TermuxMigrationItem>): TermuxMigrationSpace =
        when (val space = reviewSpace(selected)) {
            is MigrationAnswer.Ok -> space.value
            is MigrationAnswer.Refused -> fail(space.code)
        }

    private fun recordJob(record: Map<String, JsonElement>): String {
        val job = (record["job"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (record.intValue("schema") != 1L || job == null || !JOB_ID.matches(job)) fail(TermuxMigrationFailure.storage)
        return job
    }

    /**
     * Historical completion, not a fresh health or integrity check. Does not read imported files (which people may
     * edit), contact Termux or switch profiles. A record that cannot be read is `storage`: unknown is not "unfinished".
     */
    public suspend fun completedJob(sourceProfileId: String): MigrationAnswer<TermuxMigrationCompletedJob?> =
        try {
            val record = journal.read(sourceProfileId)
            if (record == null || record["done"] != JsonPrimitive(true)) {
                MigrationAnswer.Ok(null)
            } else {
                val job = recordJob(record)
                val destination = (record["destination"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (destination == null || destination.isEmpty() || destination.length > 256) fail(TermuxMigrationFailure.storage)
                MigrationAnswer.Ok(TermuxMigrationCompletedJob(jobId = job, destinationProfileId = destination))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            MigrationAnswer.Refused(TermuxMigrationFailure.storage)
        }

    /** Offline, fixed provider labels from a receipt-verified config export. Empty means unknown or not exported. */
    public suspend fun providerNames(sourceProfileId: String): List<String> =
        try {
            val record = journal.read(sourceProfileId)
            if (record == null) {
                emptyList()
            } else {
                val job = recordJob(record)
                val expected = expected(record, TermuxMigrationItem.config)
                if (expected == null) emptyList() else archives.providerNames(job, expected)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Names are advisory; never return paths, content or parser errors.
            emptyList()
        }

    /**
     * Discards only the local transfer cache and unfinished metadata. Call [cancel], then [awaitSettled] first; an
     * active operation answers `sourceBusy`. Committed imports, completed receipts, profiles and Termux are preserved.
     */
    public suspend fun discardSavedCopy(sourceProfileId: String): MigrationAnswer<TermuxMigrationDiscardResult> {
        if (!beginOperation()) return MigrationAnswer.Refused(TermuxMigrationFailure.sourceBusy)
        jobId = null
        try {
            val record = journal.read(sourceProfileId) ?: return MigrationAnswer.Ok(TermuxMigrationDiscardResult.NothingSaved)
            val job = recordJob(record)
            if (record["done"] == JsonPrimitive(true)) return MigrationAnswer.Ok(TermuxMigrationDiscardResult.AlreadyCompleted)
            archives.cleanupPartial(job)
            // Keep the journal until cleanup succeeded, so a failed discard can be retried.
            journal.remove(sourceProfileId)
            jobId = null
            publish(TermuxMigrationPhase.cancelled)
            return MigrationAnswer.Ok(TermuxMigrationDiscardResult.Discarded)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return MigrationAnswer.Refused(TermuxMigrationFailure.storage)
        } finally {
            finishOperation()
        }
    }

    /** The exact saved selection, without paths or archive content; null when nothing is saved. */
    public suspend fun savedSelection(sourceProfileId: String): MigrationAnswer<Set<TermuxMigrationItem>?> =
        try {
            MigrationAnswer.Ok(savedSelectionOrFail(sourceProfileId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            MigrationAnswer.Refused(TermuxMigrationFailure.storage)
        }

    private suspend fun savedSelectionOrFail(sourceProfileId: String): Set<TermuxMigrationItem>? {
        val record = journal.read(sourceProfileId) ?: return null
        val names = record["selected"] as? JsonArray
        if (names == null || names.isEmpty()) fail(TermuxMigrationFailure.storage)
        return names.map { name ->
            TermuxMigrationItem.byName((name as JsonPrimitive).content) ?: fail(TermuxMigrationFailure.storage)
        }.toSet()
    }

    public suspend fun resume(sourceProfileId: String): Unit = start(sourceProfileId, null)

    private suspend fun preflight(selected: Set<TermuxMigrationItem>): TermuxMigrationSource? {
        reviewSource = null
        appAvailableBytes = null
        publish(TermuxMigrationPhase.checking)
        if (!archives.builtinInstalled()) {
            publish(TermuxMigrationPhase.needsBuiltin)
            return null
        }
        checkCancelled()
        val source = transport.inspect()
        validateSource(source)
        checkCancelled()
        reviewSource = source
        appAvailableBytes = try {
            availableBytes()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Capacity unavailable is unknown, not a successful zero-cost check.
            null
        }
        checkCancelled()
        val space = reviewSpaceOrFail(selected)
        publish(TermuxMigrationPhase.ready, source = source, requiredBytes = space.requiredBytes, available = space.availableBytes)
        return source
    }

    private fun validateSource(source: TermuxMigrationSource) {
        val seen = HashSet<TermuxMigrationItem>()
        if (source.freeBytes < 0 || source.items.any { it.bytes < 0 || it.files < 0 || it.files > 20_000 || !seen.add(it.item) }) {
            fail(TermuxMigrationFailure.tooLarge)
        }
    }

    /**
     * Starts or resumes the same source and selection. A different selection cannot change a saved job. Pass an
     * explicit selection only after showing the private-copy and sign-in-again warnings; only projects become active.
     */
    public suspend fun start(sourceProfileId: String, selected: Set<TermuxMigrationItem>?) {
        if (!beginOperation()) return
        jobId = null
        attemptStarted = clock.now()
        try {
            this.sourceProfileId = sourceProfileId
            val chosen = selected ?: savedSelectionOrFail(sourceProfileId)
            checkCancelled()
            if (!sourceProfileExists(sourceProfileId) || chosen == null || chosen.isEmpty()) {
                fail(TermuxMigrationFailure.invalidSelection)
            }
            val saved = journal.read(sourceProfileId)
            checkCancelled()
            val names = chosen.map { it.name }.sorted()
            val record: MutableMap<String, JsonElement>
            if (saved != null) {
                val job = (saved["job"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (saved.intValue("schema") != 1L || saved["selected"] != JsonArray(names.map(::JsonPrimitive)) ||
                    job == null || !JOB_ID.matches(job)
                ) {
                    fail(TermuxMigrationFailure.invalidSelection)
                }
                jobId = job
                record = saved.toMutableMap()
            } else {
                val fresh = newJobId()
                jobId = fresh
                if (!JOB_ID.matches(fresh)) fail(TermuxMigrationFailure.invalidSelection)
                record = mutableMapOf(
                    "schema" to JsonPrimitive(1),
                    "job" to JsonPrimitive(fresh),
                    "selected" to JsonArray(names.map(::JsonPrimitive)),
                    "archives" to JsonObject(emptyMap()),
                )
            }
            val job = jobId!!
            // Completed jobs verify their receipt and files, but need no Termux and no new server on a UI retry.
            if (record["done"] == JsonPrimitive(true)) {
                for (item in chosen) {
                    val expected = expected(record, item)
                    if (expected == null || !archives.imported(job, item, expected)) fail(TermuxMigrationFailure.destinationConflict)
                }
                checkCancelled()
                publish(TermuxMigrationPhase.done, destination = (record["destination"] as? JsonPrimitive)?.takeIf { it.isString }?.content)
                return
            }
            preflight(chosen) ?: return
            val space = reviewSpaceOrFail(chosen)
            if (space.sufficient != true) {
                publish(TermuxMigrationPhase.needsSpace, requiredBytes = space.requiredBytes, available = space.availableBytes)
                return
            }
            checkCancelled()
            journal.write(sourceProfileId, JsonObject(record))
            for (item in chosen) {
                checkCancelled()
                if (!sourceProfileExists(sourceProfileId)) fail(TermuxMigrationFailure.invalidSelection)
                var expected = expected(record, item)
                if (expected != null && archives.imported(job, item, expected)) continue
                publish(TermuxMigrationPhase.packing, item = item)
                val packed = transport.pack(job, item, MAX_ARCHIVE_BYTES)
                if (packed.bytes <= 0 || packed.bytes > MAX_ARCHIVE_BYTES || !SHA256.matches(packed.sha256)) {
                    fail(TermuxMigrationFailure.invalidArchive)
                }
                if (expected != null && (expected.sha256 != packed.sha256 || expected.bytes != packed.bytes)) {
                    fail(TermuxMigrationFailure.sourceChanged)
                }
                expected = packed
                val archivesRecord = (record["archives"] as JsonObject).toMutableMap()
                archivesRecord[item.name] = JsonObject(
                    mapOf("bytes" to JsonPrimitive(expected.bytes), "sha256" to JsonPrimitive(expected.sha256)),
                )
                record["archives"] = JsonObject(archivesRecord)
                journal.write(sourceProfileId, JsonObject(record))
                checkCancelled()
                val file = archives.jobDirectory(job).resolve("${item.name}.tar")
                publish(TermuxMigrationPhase.copying, item = item)
                transport.copy(job, item, expected, file) { !wanted }
                checkCancelled()
                publish(TermuxMigrationPhase.verifying, item = item)
                archives.verify(file, expected)
                checkCancelled()
                publish(TermuxMigrationPhase.unpacking, item = item)
                archives.importItem(job, item, file, expected) { !wanted }
                publish(TermuxMigrationPhase.verifying, item = item)
                if (!archives.imported(job, item, expected)) fail(TermuxMigrationFailure.checksumMismatch)
                checkCancelled()
            }
            checkCancelled()
            publish(TermuxMigrationPhase.switching)
            val destination = switchProfile.switchProfile(
                sourceProfileId = sourceProfileId,
                stillWanted = { wanted && sourceProfileExists(sourceProfileId) },
                verifiedProjectPaths = if (TermuxMigrationItem.projects in chosen) {
                    mapOf("/root/projects" to "/root/projects/termux-$job")
                } else {
                    emptyMap()
                },
            )
            checkCancelled()
            record["done"] = JsonPrimitive(true)
            record["destination"] = JsonPrimitive(destination)
            journal.write(sourceProfileId, JsonObject(record))
            // The private source archive stays in Termux for recovery. Termux projects, settings and history are never
            // removed here; any source cleanup is a separate job.
            archives.cleanupPartial(job)
            publish(TermuxMigrationPhase.done, destination = destination)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e)
        } finally {
            finishOperation()
        }
    }

    private fun expected(record: Map<String, JsonElement>, item: TermuxMigrationItem): TermuxMigrationArchive? {
        val values = record["archives"] as? JsonObject ?: fail(TermuxMigrationFailure.storage)
        val value = values[item.name]
        if (value == null || value == JsonNull) return null
        val bytes = ((value as? JsonObject)?.get("bytes") as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        val sha = ((value as? JsonObject)?.get("sha256") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (bytes == null || sha == null) fail(TermuxMigrationFailure.storage)
        return TermuxMigrationArchive(bytes = bytes, sha256 = sha)
    }

    private fun failure(error: Exception) {
        val code = when {
            expired -> TermuxMigrationFailure.timedOut
            !wanted -> TermuxMigrationFailure.cancelled
            error is TermuxMigrationException -> error.code
            else -> TermuxMigrationFailure.storage
        }
        publish(
            when (code) {
                TermuxMigrationFailure.cancelled -> TermuxMigrationPhase.cancelled
                TermuxMigrationFailure.unavailable -> TermuxMigrationPhase.termuxUnreachable
                else -> TermuxMigrationPhase.failed
            },
            failure = code,
        )
    }

    /** Requests a stop at once; this waits only for the bounded stop command. Await [awaitSettled] before Resume. */
    public suspend fun cancel() {
        if (!running) return
        cancelled = true
        val job = jobId
        if (job != null && stopCommand == null) stopCommand = scope.launch { sendStop(job) }
        publish(TermuxMigrationPhase.cancelled, failure = TermuxMigrationFailure.cancelled)
        stopCommand?.join()
    }

    private suspend fun sendStop(job: String) {
        try {
            transport.cancel(job)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fixed public state only; the operation still has its own timeout.
        }
    }

    /** Stops admitting work; a running copy is asked to stop. Dispose only after the pending operation settled. */
    public fun dispose() {
        disposed = true
        cancelled = true
        val job = jobId
        if (running && job != null && stopCommand == null) stopCommand = scope.launch { sendStop(job) }
        listeners.clear()
    }
}

private fun Map<String, JsonElement>.intValue(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
