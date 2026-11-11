package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

public enum class PhoneProotMode {
    Fast,
    Slow,
    Unknown,
}

public enum class PhoneHistoryPhase {
    Moving,
    Failed,
}

public data class PhoneHistoryInfo(
    val phase: PhoneHistoryPhase,
    val done: Int? = null,
    val total: Int? = null,
)

public data class PhoneReplyReading(
    val firstMs: Long?,
    val serverFirstMs: Long?,
    val totalMs: Long,
    val model: Redacted?,
)

/**
 * Real, bounded readings only. Null means no observation; failed inventory keeps the previous list.
 */
public data class PhoneDetailsUi(
    val installed: Boolean = false,
    val memoryBytes: Long? = null,
    val history: PhoneHistoryInfo? = null,
    val canConnect: Boolean = false,
    val reply: PhoneReplyReading? = null,
    val prootMode: PhoneProotMode? = null,
    val workHeld: Boolean? = null,
    val log: Redacted? = null,
    val nativeFailure: Redacted? = null,
    val historyFailure: Redacted? = null,
    val installedNames: ImmutableList<String>? = null,
    val loading: Boolean = false,
    val inventoryFailure: Failure? = null,
    val logFailure: Failure? = null,
    /**
     * A setup job (Add tools' install) runs now. It holds Linux's one task slot for minutes, so the
     * installed check is not tried: the row says "Installing…" over the previous list, and the job's
     * end reads again.
     */
    val addingTools: Boolean = false,
)

/** What the Installed row's line says. */
public enum class InstalledLine {
    /** The installed list (or nothing yet). */
    Names,

    /** A read runs now ("Checking…"). */
    Checking,

    /** A setup job runs now ("Installing…"); no read is tried and no failure is shown. */
    Installing,
}

public fun installedLine(details: PhoneDetailsUi): InstalledLine =
    when {
        details.addingTools -> InstalledLine.Installing
        details.loading -> InstalledLine.Checking
        else -> InstalledLine.Names
    }

/** The "Could not check what is installed" line shows only when nothing runs and nothing installs. */
public fun installedFailureShown(details: PhoneDetailsUi): Failure? =
    details.inventoryFailure?.takeIf { installedLine(details) == InstalledLine.Names }

/** Android binds the native runtime and setup checker; reply timing starts with the application. */
public interface PhoneDetailsSource {
    public val state: StateFlow<PhoneDetailsUi>

    public fun shown(visible: Boolean)

    public suspend fun refresh()

    public suspend fun refreshHistory(): Boolean

    public suspend fun refreshLog()

    public suspend fun connect(): Outcome<Unit>
}

/**
 * When This phone reads what is installed again. The check runs inside Linux, which runs one phone
 * task at a time: a check refused because another task held that slot (Add tools' own check, a git
 * read, an agent probe) is not a failure, so the page keeps saying "Checking…" and asks again, up to
 * [ATTEMPTS] reads in all. Any other failure gets the one second read it always had.
 */
public object InstalledInventoryRetry {
    public const val ATTEMPTS: Int = 5
    public const val PAUSE_MS: Long = 1_500

    /**
     * Whether to read again after the [attempt]-th read (1-based) failed with [failure]. Never while a
     * setup job runs ([addingTools]): the job holds the slot for minutes, so waiting reads would end in
     * a false "Could not check"; the job's end reads again instead.
     */
    public fun again(attempt: Int, failure: Failure, addingTools: Boolean = false): Boolean =
        !addingTools &&
            attempt < ATTEMPTS &&
            (attempt == 1 || (failure as? Failure.Runtime)?.problem == RuntimeProblem.SlotBusy)
}
