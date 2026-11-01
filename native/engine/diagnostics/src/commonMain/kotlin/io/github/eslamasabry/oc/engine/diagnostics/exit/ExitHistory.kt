package io.github.eslamasabry.oc.engine.diagnostics.exit

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.engine.diagnostics.crash.NativeCrashRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/** Fixed history outcomes; never carries native text. */
public enum class ExitHistoryError { InvalidLimit, Unavailable }

/** One main-process exit as the history shows it: numbers, UTC time and the kind. */
public data class ExitEntry(
    val reason: Int,
    val subReason: Int,
    val importance: Int,
    val at: Instant,
    val kind: ExitKind,
)

/** `supported` false + no error: this platform keeps no exit history (API < 30, desktop, no bridge). */
public data class ExitHistoryResult(
    val supported: Boolean,
    val entries: ImmutableList<ExitEntry> = persistentListOf(),
    val error: ExitHistoryError? = null,
) {
    public companion object {
        public val Unsupported: ExitHistoryResult = ExitHistoryResult(supported = false)
    }
}

/** What the platform hands over at start, unvalidated (`lastCrash` is the crash handler's raw record). */
public class RawLaunchReport(
    public val exit: ExitRecord? = null,
    public val previousServices: List<String?> = emptyList(),
    public val lastCrash: Map<String, Any?>? = null,
)

/** What the app learns at start about the process before this one (port of Dart `AppLaunchReport`). */
public data class LaunchReport(
    /** The newest exit not reported before; null on a first start, before API 30, or when there was none. */
    val exit: ExitRecord? = null,
    /** The built-in services that ran when that process ended (`server`, `aiteam`). */
    val previousServices: ImmutableList<String> = persistentListOf(),
    /** The last JVM crash, also when Android's exit record is missing. Bounded safe metadata only. */
    val lastCrash: NativeCrashRecord? = null,
) {
    public companion object {
        public val Empty: LaunchReport = LaunchReport()

        public fun from(raw: RawLaunchReport): LaunchReport =
            LaunchReport(
                exit = raw.exit?.takeIf { it.hasTime },
                previousServices = raw.previousServices.filterNotNull().filter { it.isNotEmpty() }.toImmutableList(),
                lastCrash = NativeCrashRecord.from(raw.lastCrash),
            )
    }
}

/**
 * The platform half of the lifecycle bridge (`AndroidExitHistory`, copied `AppLifecycle` parts). Methods may fail or
 * throw; [ExitHistory] answers safely either way.
 */
public interface LifecycleSource {
    /** API ≥ 30 on Android; false on desktop. */
    public val exitHistorySupported: Boolean

    /** The main process's name; exits of other processes are not the app's. */
    public val mainProcessName: String

    /** Every retained exit record (`maxNum = 0`), unfiltered. */
    public suspend fun retainedExits(): Outcome<List<ExitRecord>>

    public suspend fun launchReport(): Outcome<RawLaunchReport>

    public companion object {
        /** No bridge at all (desktop, tests). */
        public val Absent: LifecycleSource =
            object : LifecycleSource {
                override val exitHistorySupported: Boolean = false
                override val mainProcessName: String = ""

                override suspend fun retainedExits(): Outcome<List<ExitRecord>> = Outcome.Ok(emptyList())

                override suspend fun launchReport(): Outcome<RawLaunchReport> = Outcome.Ok(RawLaunchReport())
            }
    }
}

/** Exit history and the launch report (plan 03 `interface ExitHistory`). */
public interface ExitHistory {
    /** Recent main-process exits, newest first; independent of the startup report (reads never consume). */
    public suspend fun history(limit: Int = DEFAULT_LIMIT): ExitHistoryResult

    /** Never fails: a missing or failing bridge answers [LaunchReport.Empty] (B-ANDR-006). */
    public suspend fun launchReport(): LaunchReport

    public companion object {
        public const val DEFAULT_LIMIT: Int = 10
        public const val MIN_LIMIT: Int = 1
        public const val MAX_LIMIT: Int = 50
    }
}

/** [ExitHistory] over a [LifecycleSource], applying the ported `AppExitHistoryPolicy`. */
public class PolicyExitHistory(private val source: LifecycleSource) : ExitHistory {
    override suspend fun history(limit: Int): ExitHistoryResult {
        if (limit !in ExitHistory.MIN_LIMIT..ExitHistory.MAX_LIMIT) {
            return ExitHistoryResult(supported = false, error = ExitHistoryError.InvalidLimit)
        }
        if (!source.exitHistorySupported) return ExitHistoryResult.Unsupported
        val read = attempt(::readFailed) { source.retainedExits() }.flatMap { it }
        val records =
            when (read) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return ExitHistoryResult(supported = true, error = ExitHistoryError.Unavailable)
            }
        return ExitHistoryPolicy.history(records, source.mainProcessName, limit)
    }

    override suspend fun launchReport(): LaunchReport =
        when (val read = attempt(::readFailed) { source.launchReport() }.flatMap { it }) {
            is Outcome.Ok -> attempt(::readFailed) { LaunchReport.from(read.value) }.let { (it as? Outcome.Ok)?.value ?: LaunchReport.Empty }
            is Outcome.Failed -> LaunchReport.Empty
        }

    // The thrown error's text is never kept: the failure carries no details.
    private fun readFailed(@Suppress("UNUSED_PARAMETER") error: Throwable): Failure = Failure.Device()
}

/** The pure history policy (port of `AppExitHistoryPolicy.history`, plus the Dart bridge's fail-closed rule). */
internal object ExitHistoryPolicy {
    private const val MIN_TIMESTAMP = 1L
    private const val MAX_TIMESTAMP = 8_640_000_000_000_000L

    fun history(records: List<ExitRecord>, mainProcess: String, limit: Int): ExitHistoryResult {
        // Secondary processes and other packages are not the app's exits.
        val main = records.filter { it.processName == mainProcess }
        val valid = main.filter { it.timestampMs in MIN_TIMESTAMP..MAX_TIMESTAMP }
        // Malformed metadata never becomes a successful empty history.
        if (main.isNotEmpty() && valid.isEmpty()) {
            return ExitHistoryResult(supported = true, error = ExitHistoryError.Unavailable)
        }
        val entries =
            valid.sortedByDescending { it.timestampMs }.take(limit).map {
                ExitEntry(it.reason, it.subReason, it.importance, it.at, it.kind)
            }
        return ExitHistoryResult(supported = true, entries = entries.toImmutableList())
    }
}
