package io.github.eslamasabry.oc.feature.chats.attention

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActKind
import io.github.eslamasabry.oc.engine.attention.feed.AttentionKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public data class HistoryIdentity(val profile: String, val id: String)

public data class HistoryRow(
    val identity: HistoryIdentity,
    val kind: AutomaticActKind,
    val target: String,
    val at: String,
)

public data class AttentionRow(
    val key: String,
    val kind: AttentionKind,
    val server: String,
    val title: String?,
    val fresh: Boolean,
    val ref: ConversationRef?,
)

public data class MonitorRow(val profile: String, val name: String, val enabled: Boolean)

public data class AttentionUi(
    val loading: Boolean = true,
    val requests: ImmutableList<AttentionRow> = persistentListOf(),
    val history: ImmutableList<HistoryRow> = persistentListOf(),
    val incomplete: Boolean = false,
    val corrupt: Boolean = false,
    val saveFailed: Boolean = false,
    val monitors: ImmutableList<MonitorRow> = persistentListOf(),
)

public interface AttentionHistory {
    public val state: StateFlow<AttentionUi>

    public suspend fun refresh(): Outcome<Unit>

    public suspend fun acknowledge(shown: List<HistoryIdentity>): Outcome<Unit>

    public suspend fun setMonitored(profile: String, enabled: Boolean): Outcome<Unit>
}

/** The desktop gallery has no live transport or saved phone history. */
@ContributesBinding(AppScope::class)
@Inject
public class NoAttentionHistory : AttentionHistory {
    override val state: StateFlow<AttentionUi> = MutableStateFlow(AttentionUi(loading = false))

    override suspend fun refresh(): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun acknowledge(shown: List<HistoryIdentity>): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun setMonitored(profile: String, enabled: Boolean): Outcome<Unit> =
        Outcome.Ok(Unit)
}

public interface AttentionActions {
    public fun back()

    public fun refresh()

    public fun open(ref: ConversationRef)

    public fun acknowledge()

    public fun setMonitored(profile: String, enabled: Boolean)
}
