package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// Fakes for the controller, owner and presenter tests (Flutter test/termux_migration_controller_test.dart _Fixture and
// test/support/termux_migration_fakes.dart).

internal const val JOB = "0123456789abcdef0123456789abcdef"
internal const val AMPLE_SPACE = 1024L * 1024 * 1024
internal val ARCHIVE = TermuxMigrationArchive(10240, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

internal class MutableClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now(): Instant = now
}

internal class MemoryPreferences(initial: Map<String, Any> = emptyMap()) : MigrationPreferences {
    val values = LinkedHashMap<String, Any>(initial)

    override fun getString(key: String): String? = values[key] as? String

    override fun getBoolean(key: String): Boolean? = values[key] as? Boolean

    override fun setString(key: String, value: String): Boolean {
        values[key] = value
        return true
    }

    override fun setBoolean(key: String, value: Boolean): Boolean {
        values[key] = value
        return true
    }

    override fun remove(key: String): Boolean {
        values.remove(key)
        return true
    }

    override fun contains(key: String): Boolean = key in values

    override fun keys(): Set<String> = values.keys.toSet()
}

internal class FakeTransport : TermuxMigrationTransport {
    var inspectCalls = 0
    var packCalls = 0
    var copyCalls = 0
    var sourceFreeBytes = AMPLE_SPACE
    var inspectError: Exception? = null
    var packError: Exception? = null
    var onCancel: (suspend () -> Unit)? = null
    var onPack: (suspend () -> Unit)? = null
    var archive = ARCHIVE
    var items = listOf(TermuxMigrationSize(TermuxMigrationItem.projects, 12, 2))
    var onCopy: (suspend (cancelled: () -> Boolean) -> Unit)? = null
    val cancelledJobs = mutableListOf<String>()

    override suspend fun inspect(): TermuxMigrationSource {
        inspectCalls++
        inspectError?.let { throw it }
        return TermuxMigrationSource(items = items, freeBytes = sourceFreeBytes, oc1Version = "1.18.32")
    }

    override suspend fun pack(jobId: String, item: TermuxMigrationItem, maxBytes: Long): TermuxMigrationArchive {
        packCalls++
        onPack?.invoke()
        packError?.let { throw it }
        return archive
    }

    override suspend fun copy(
        jobId: String,
        item: TermuxMigrationItem,
        archive: TermuxMigrationArchive,
        destination: Path,
        cancelled: () -> Boolean,
    ) {
        copyCalls++
        onCopy?.invoke(cancelled)
    }

    override suspend fun cancel(jobId: String) {
        cancelledJobs.add(jobId)
        onCancel?.invoke()
    }
}

internal class FakeArchives : TermuxMigrationArchives {
    var installed = true
    var importCalls = 0
    var cleanupCalls = 0
    var verifyError: Exception? = null
    var cleanupError: Exception? = null
    var namesError: Exception? = null
    var names: List<String> = emptyList()
    var afterCommit: (() -> Unit)? = null
    val receipts = LinkedHashMap<TermuxMigrationItem, TermuxMigrationArchive>()
    val discarded = mutableListOf<String>()

    override suspend fun builtinInstalled(): Boolean = installed

    override fun jobDirectory(jobId: String): Path = Path.of("/fake-app-private/$jobId")

    override suspend fun verify(archive: Path, expected: TermuxMigrationArchive) {
        verifyError?.let { throw it }
    }

    override suspend fun importItem(
        jobId: String,
        item: TermuxMigrationItem,
        archive: Path,
        expected: TermuxMigrationArchive,
        cancelled: () -> Boolean,
    ) {
        assertEquals(false, cancelled())
        importCalls++
        receipts[item] = expected
        afterCommit?.invoke()
    }

    override suspend fun imported(jobId: String, item: TermuxMigrationItem, expected: TermuxMigrationArchive): Boolean =
        receipts[item] == expected

    override suspend fun cleanupPartial(jobId: String) {
        cleanupCalls++
        cleanupError?.let { throw it }
        discarded.add(jobId)
    }

    override suspend fun providerNames(jobId: String, expected: TermuxMigrationArchive): List<String> {
        namesError?.let { throw it }
        return names
    }
}

internal class MemoryJournal : TermuxMigrationJournal {
    var readGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val records = LinkedHashMap<String, JsonObject>()

    override suspend fun remove(sourceProfileId: String) {
        records.remove(sourceProfileId)
    }

    override suspend fun read(sourceProfileId: String): JsonObject? {
        readGate?.await()
        return records[sourceProfileId]
    }

    override suspend fun write(sourceProfileId: String, value: JsonObject) {
        records[sourceProfileId] = value
    }

    fun edit(sourceProfileId: String, change: MutableMap<String, kotlinx.serialization.json.JsonElement>.() -> Unit) {
        records[sourceProfileId] = JsonObject(records.getValue(sourceProfileId).toMutableMap().apply(change))
    }
}

internal class Fixture {
    val transport = FakeTransport()
    val archives = FakeArchives()
    val journal = MemoryJournal()
    val clock = MutableClock()
    var freeBytes: Long? = AMPLE_SPACE
    var switches = 0
    var paths: Map<String, String>? = null
    var switchError: Exception? = null

    fun controller(scope: CoroutineScope): TermuxMigrationController = TermuxMigrationController(
        transport = transport,
        archives = archives,
        journal = journal,
        availableBytes = { freeBytes },
        switchProfile = { sourceProfileId, stillWanted, verifiedProjectPaths ->
            assertEquals("termux", sourceProfileId)
            assertTrue(stillWanted())
            assertTrue(TermuxMigrationItem.projects in archives.receipts || verifiedProjectPaths.isEmpty())
            switchError?.let { throw it }
            switches++
            paths = verifiedProjectPaths
            "builtin"
        },
        sourceProfileExists = { it == "termux" },
        clock = clock,
        scope = scope,
        newJobId = { JOB },
    )
}
