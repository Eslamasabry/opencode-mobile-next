package io.github.eslamasabry.oc.android.termux

// The backend contract for a foreground, owner-started move from Termux (Flutter lib/domain/termux_migration.dart).
// Values exposed to the UI hold no command output, file content or credentials. Enum constant names are the wire
// names of the Flutter app (journal records and script output use them), so they stay lowerCamelCase.

@Suppress("EnumEntryName", "EnumNaming")
public enum class TermuxMigrationPhase {
    checking, ready, needsSpace, needsBuiltin, termuxUnreachable, packing, copying, unpacking, verifying, switching,
    done, failed, cancelled,
}

/** The fixed failure codes. The UI says each in plain words (`:ui:words` migration*), never the cause's own text. */
@Suppress("EnumEntryName", "EnumNaming")
public enum class TermuxMigrationFailure {
    unavailable, sourceChanged, sourceBusy, unsupportedEntry, linkedFileUnreadable, tooLarge, invalidArchive,
    checksumMismatch, destinationConflict, storage, timedOut, invalidSelection, profileSwitch, cancelled,
    ;

    internal companion object {
        fun byName(name: String): TermuxMigrationFailure? = entries.firstOrNull { it.name == name }
    }
}

@Suppress("EnumEntryName", "EnumNaming")
public enum class TermuxMigrationItem {
    projects, config, sessions, gitConfig, shellFiles, aiTeam,
    ;

    internal companion object {
        fun byName(name: String): TermuxMigrationItem? = entries.firstOrNull { it.name == name }
    }
}

public enum class TermuxMigrationDisposition { Migrate, PrivateExport }

public data class TermuxMigrationSize(
    val item: TermuxMigrationItem,
    val bytes: Long,
    val files: Long,
    /** A refused item has unknown size; never present its placeholder zero as measured. */
    val problem: TermuxMigrationFailure? = null,
) {
    val disposition: TermuxMigrationDisposition
        get() = if (item == TermuxMigrationItem.projects) TermuxMigrationDisposition.Migrate else TermuxMigrationDisposition.PrivateExport
}

public data class TermuxMigrationSource(
    val items: List<TermuxMigrationSize>,
    val freeBytes: Long,
    val oc1Version: String? = null,
    val oc2Version: String? = null,
    val oc2Isolated: Boolean = false,
)

public data class TermuxMigrationArchive(val bytes: Long, val sha256: String)

public data class TermuxMigrationSnapshot(
    val phase: TermuxMigrationPhase,
    val failure: TermuxMigrationFailure? = null,
    val jobId: String? = null,
    val item: TermuxMigrationItem? = null,
    val source: TermuxMigrationSource? = null,
    val requiredBytes: Long? = null,
    val availableBytes: Long? = null,
    val destinationProfileId: String? = null,
)

/** A durable successful copy receipt; not evidence of current server health. */
public data class TermuxMigrationCompletedJob(val jobId: String, val destinationProfileId: String)

public enum class TermuxMigrationDiscardResult { Discarded, NothingSaved, AlreadyCompleted }

/**
 * An expected refusal of a migration query, as a value (plan 03 rule 5). Only the fixed [code] crosses; the cause's
 * text never does.
 */
public sealed interface MigrationAnswer<out T> {
    public data class Ok<out T>(val value: T) : MigrationAnswer<T>

    public data class Refused(val code: TermuxMigrationFailure) : MigrationAnswer<Nothing>
}

public fun <T> MigrationAnswer<T>.valueOrNull(): T? = (this as? MigrationAnswer.Ok)?.value

/**
 * The module's internal signal between its own layers (archive store, transport, switcher, controller). It carries a
 * fixed code only, so its text can never hold a path, an output line or a credential. The controller and the owner turn
 * it into published state or a [MigrationAnswer]; it never leaves the module's public API.
 */
internal class TermuxMigrationException(val code: TermuxMigrationFailure) : Exception() {
    override val message: String get() = "TermuxMigrationException(${code.name})"

    override fun toString(): String = message

    override fun fillInStackTrace(): Throwable = this
}

internal fun fail(code: TermuxMigrationFailure): Nothing = throw TermuxMigrationException(code)

internal val JOB_ID: Regex = Regex("^[a-f0-9]{32}$")
internal val SHA256: Regex = Regex("^[a-f0-9]{64}$")
