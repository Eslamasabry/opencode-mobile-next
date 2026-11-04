package io.github.eslamasabry.oc.android.termux

/**
 * Per-item bounds shared by the review and the start (Flutter migration_space.dart). The archive bound includes
 * conservative tar headers; the disk budget also allows allocation overhead and three simultaneously retained copies
 * across both sandboxes.
 */
public const val MIGRATION_MAX_ARCHIVE_BYTES: Long = 512L * 1024 * 1024
public const val MIGRATION_MAX_ENTRIES: Long = 20_000
public const val MIGRATION_RESERVE_BYTES: Long = 64L * 1024 * 1024

public data class TermuxMigrationSpace(
    val requiredBytes: Long?,
    /** The lower of the two free-space readings, or null if either is unknown. */
    val availableBytes: Long?,
    val appAvailableBytes: Long?,
    val sourceAvailableBytes: Long?,
    /** Null means the capacity could not be established, never permission to start. Empty needs zero, but is no start. */
    val sufficient: Boolean?,
)

/** Why one measured item cannot be copied (the per-item limit said before packing), or null. */
public fun migrationItemProblem(size: TermuxMigrationSize): TermuxMigrationFailure? =
    size.problem ?: if (size.files > MIGRATION_MAX_ENTRIES || size.bytes + size.files * 1024 + 10240 > MIGRATION_MAX_ARCHIVE_BYTES) {
        TermuxMigrationFailure.tooLarge
    } else {
        null
    }

/**
 * The exact selected-item preflight used by both review and start. A refused selected item keeps its typed refusal; it
 * is never counted as a zero-byte success. Malformed or missing selections fail with a fixed code. Negative free-space
 * readings are unknown, not measured zero bytes.
 */
public fun migrationSpaceFor(
    source: TermuxMigrationSource,
    selected: Set<TermuxMigrationItem>,
    appAvailableBytes: Long?,
): MigrationAnswer<TermuxMigrationSpace> =
    try {
        MigrationAnswer.Ok(computeSpace(source, selected, appAvailableBytes))
    } catch (e: TermuxMigrationException) {
        MigrationAnswer.Refused(e.code)
    }

internal fun computeSpace(
    source: TermuxMigrationSource,
    selected: Set<TermuxMigrationItem>,
    appAvailableBytes: Long?,
): TermuxMigrationSpace {
    val byItem = HashMap<TermuxMigrationItem, TermuxMigrationSize>()
    for (item in source.items) {
        if (byItem.containsKey(item.item) || item.bytes < 0 || item.files < 0) fail(TermuxMigrationFailure.invalidSelection)
        byItem[item.item] = item
    }
    var total = 0L
    for (item in selected) {
        val size = byItem[item] ?: fail(TermuxMigrationFailure.invalidSelection)
        size.problem?.let { fail(it) }
        if (size.bytes > MIGRATION_MAX_ARCHIVE_BYTES ||
            size.files > MIGRATION_MAX_ENTRIES ||
            size.bytes + size.files * 1024 + 10240 > MIGRATION_MAX_ARCHIVE_BYTES
        ) {
            fail(TermuxMigrationFailure.tooLarge)
        }
        total += size.bytes + size.files * 4096 + 10240
    }
    val required = if (selected.isEmpty()) 0L else 3 * total + MIGRATION_RESERVE_BYTES
    val appFree = appAvailableBytes?.takeIf { it >= 0 }
    val sourceFree = source.freeBytes.takeIf { it >= 0 }
    val available = if (appFree == null || sourceFree == null) null else minOf(appFree, sourceFree)
    return TermuxMigrationSpace(
        requiredBytes = required,
        availableBytes = available,
        appAvailableBytes = appFree,
        sourceAvailableBytes = sourceFree,
        sufficient = available?.let { it >= required },
    )
}
