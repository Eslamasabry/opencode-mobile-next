package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.diagnostics.CrashCategory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/** Where a crash record came from. [wire] is the persisted value (Flutter-era files stay readable, plan 05 §2239). */
public enum class CrashSource(public val wire: String) {
    Flutter("flutter"),
    Platform("platform"),
    Widget("widget"),
    Native("native"),
    Anr("anr"),
    ;

    public companion object {
        public fun ofWire(value: String): CrashSource? = entries.firstOrNull { it.wire == value }
    }
}

/** The fixed record categories (no exception text ever has a slot). [wire] is the persisted value. */
public enum class CrashRecordCategory(public val wire: String) {
    InvalidState("Invalid state"),
    InvalidArgument("Invalid argument"),
    MissingValue("Missing value"),
    UnsupportedOperation("Unsupported operation"),
    ApplicationError("Application error"),
    NativeApplicationError("Native application error"),
    PermissionDenied("Permission denied"),
    InputOutputFailure("Input/output failure"),
    InterruptedOperation("Interrupted operation"),
    NotResponding("Android reported that the app stopped responding"),
    ;

    public companion object {
        public fun ofWire(value: String): CrashRecordCategory? = entries.firstOrNull { it.wire == value }

        /** Category of a thrown error by type only; its message and stack are never read. */
        public fun of(error: Throwable): CrashRecordCategory =
            when (error) {
                is CancellationException -> InterruptedOperation
                is IllegalStateException -> InvalidState
                is IllegalArgumentException -> InvalidArgument
                is NullPointerException -> MissingValue
                is UnsupportedOperationException -> UnsupportedOperation
                else -> ofClassName(error::class.simpleName.orEmpty())
            }

        /** Category of a whitelisted JVM class name (the platform crash handler's record). */
        public fun ofClassName(name: String): CrashRecordCategory =
            when (name.substringAfterLast('.')) {
                "IllegalStateException" -> InvalidState
                "IllegalArgumentException", "NumberFormatException" -> InvalidArgument
                "NullPointerException", "KotlinNullPointerException" -> MissingValue
                "UnsupportedOperationException" -> UnsupportedOperation
                "SecurityException" -> PermissionDenied
                "IOException", "FileNotFoundException", "EOFException" -> InputOutputFailure
                "InterruptedException", "CancellationException" -> InterruptedOperation
                else -> ApplicationError
            }
    }
}

/** One validated crash record: fixed source, fixed category and a time. */
public data class CrashRecord(val source: CrashSource, val category: CrashRecordCategory, val at: Instant) {
    /** The analytics category of this record (plan 18 `crash_seen`); an ANR is a freeze, not a crash. */
    public val crashCategory: CrashCategory?
        get() =
            when (source) {
                CrashSource.Anr -> null
                CrashSource.Native -> CrashCategory.Native
                CrashSource.Platform, CrashSource.Flutter, CrashSource.Widget -> CrashCategory.Kotlin
            }
}

/** The files of the app-private crash directory (names as in Flutter: `crash-diagnostics*`, `native-last-crash*`). */
public enum class CrashFile { Consent, Records, Pending, NativeLastCrash }

/**
 * The platform's crash directory (plan 03 `interface CrashStore`; Android: next to the copied `NativeCrashStore`, owner-only
 * files; desktop: a file store). Text in, text out: validation and the record format live in common code.
 */
public interface CrashStore {
    /**
     * The file's text; Ok(null) when absent. A file larger than [maxBytes] is not read and answers
     * `Failure.Storage(InvalidValue)`; any other read problem is another `Failure.Storage`.
     */
    public suspend fun read(file: CrashFile, maxBytes: Int): Outcome<String?>

    /** Atomic: write `.pending`, sync, rename into place. */
    public suspend fun write(file: CrashFile, text: String): Outcome<Unit>

    /** Absent already is Ok. */
    public suspend fun delete(file: CrashFile): Outcome<Unit>
}
