package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
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
)

/** Android binds the native runtime and setup checker; reply timing starts with the application. */
public interface PhoneDetailsSource {
    public val state: StateFlow<PhoneDetailsUi>

    public fun shown(visible: Boolean)

    public suspend fun refresh()

    public suspend fun refreshHistory(): Boolean

    public suspend fun refreshLog()

    public suspend fun connect(): Outcome<Unit>
}
