package io.github.eslamasabry.oc.feature.monitor.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

public enum class MonitorRowStatus {
    Off,
    Waiting,
    Checking,
    Current,
    Partial,
    Unknown,
    Paused,
}

public data class MonitorSessionUi(
    val id: String,
    val title: String,
    val failed: Boolean,
    val fresh: Boolean,
)

public data class MonitorProfileUi(
    val id: ProfileId,
    val name: String,
    val enabled: Boolean,
    val supported: Boolean,
    val usable: Boolean,
    val status: MonitorRowStatus,
    val pending: Int? = null,
    val running: Int? = null,
    val sessions: ImmutableList<MonitorSessionUi> = persistentListOf(),
)

public data class MonitorUi(
    val rows: ImmutableList<MonitorProfileUi> = persistentListOf(),
    val checking: Boolean = false,
    val failed: Boolean = false,
)

/** Explicit foreground observations only; closing the last page revokes every active read. */
public interface MonitorHost {
    public val state: StateFlow<MonitorUi>

    public suspend fun open()

    public suspend fun setEnabled(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun check(): Outcome<Unit>

    public fun sessionTarget(profile: ProfileId, session: String): ConversationRef?

    public fun close()
}

public interface MonitorActions {
    public fun back()

    public fun setEnabled(profile: ProfileId, enabled: Boolean)

    public fun check()

    public fun openSession(profile: ProfileId, session: String)
}
