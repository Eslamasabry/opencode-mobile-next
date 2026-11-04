package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public enum class ChatsHomeSheet {
    Projects,
    Sources,
    Inbox,
    OpenProject,
    Monitoring,
}

public enum class ChatsOpeningPhase {
    Connecting,
    Failed,
}

public data class ChatsOpeningRow(val key: String, val title: RowTitle)

public data class ChatsOpeningUi(
    val phase: ChatsOpeningPhase,
    val serverName: String,
    val rows: ImmutableList<ChatsOpeningRow> = persistentListOf(),
    val failure: Failure? = null,
)

public data class ChatsListSourceUi(
    val profile: ProfileId,
    val name: String,
    val shown: Boolean,
    val main: Boolean,
    val onThisPhone: Boolean,
    val unreachable: Boolean,
)

public data class ChatsHomeExtraState(
    val sources: ImmutableList<ChatsListSourceUi> = persistentListOf(),
    val opening: ChatsOpeningUi? = null,
    val failure: Failure? = null,
    val monitoring: ChatsMonitoringUi = ChatsMonitoringUi(),
)

public enum class ChatsMonitorStatus {
    Off,
    Waiting,
    Checking,
    Current,
    Unavailable,
    WifiRequired,
    Paused,
}

public enum class ChatsWatchedKind {
    Permission,
    Question,
    Form,
}

public data class ChatsMonitorSourceUi(
    val profile: ProfileId,
    val name: String,
    val enabled: Boolean,
    val status: ChatsMonitorStatus,
)

public data class ChatsWatchedRequestUi(
    val key: String,
    val ref: ConversationRef,
    val title: RowTitle,
    val sourceName: String,
    val kind: ChatsWatchedKind,
    val checkedAtMs: Long,
)

public data class ChatsMonitoringUi(
    val sources: ImmutableList<ChatsMonitorSourceUi> = persistentListOf(),
    val requests: ImmutableList<ChatsWatchedRequestUi> = persistentListOf(),
    val refreshing: Boolean = false,
    val failure: Failure? = null,
)

/**
 * Saved source visibility and read-only opening labels. Android supplies the real connection owner.
 */
public interface ChatsHomeExtras {
    public val snapshot: StateFlow<ChatsHomeExtraState>

    public suspend fun showSource(profile: ProfileId, shown: Boolean): Outcome<Unit>

    public suspend fun retrySource(profile: ProfileId): Outcome<Unit>

    public suspend fun retryOpening(): Outcome<Unit>

    public suspend fun setWatched(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun refreshWatched(): Outcome<Unit>

    public suspend fun openWatched(key: String): Outcome<ConversationRef>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoChatsHomeExtras : ChatsHomeExtras {
    override val snapshot: StateFlow<ChatsHomeExtraState> =
        MutableStateFlow(ChatsHomeExtraState()).asStateFlow()

    override suspend fun showSource(profile: ProfileId, shown: Boolean): Outcome<Unit> =
        unavailable()

    override suspend fun retrySource(profile: ProfileId): Outcome<Unit> = unavailable()

    override suspend fun retryOpening(): Outcome<Unit> = unavailable()

    override suspend fun setWatched(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        unavailable()

    override suspend fun refreshWatched(): Outcome<Unit> = unavailable()

    override suspend fun openWatched(key: String): Outcome<ConversationRef> =
        Outcome.Failed(Failure.Unavailable(FeatureId("profileMonitor")))

    private fun unavailable(): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("conversationSources")))
}
