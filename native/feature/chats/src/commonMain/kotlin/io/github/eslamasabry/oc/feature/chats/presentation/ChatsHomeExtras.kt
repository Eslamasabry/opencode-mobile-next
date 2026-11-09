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

    /** The one-tap server switcher the home's server pill opens (F: lib/ui/widgets/server_switcher_sheet.dart:73). */
    Servers,
}

public enum class ChatsOpeningPhase {
    Connecting,

    /** This phone's own server was just started by the app and has not answered yet (E05-S20). */
    Starting,
    Failed,
}

public data class ChatsOpeningUi(
    val phase: ChatsOpeningPhase,
    val serverName: String,
    /** The saved rows, as the live feed's own rows: the home draws them with its normal layout. */
    val rows: ImmutableList<io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem> = persistentListOf(),
    val failure: Failure? = null,
    /** The server that is opening, so its sign-in can be edited from the failure card. */
    val profile: ProfileId? = null,
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
    /**
     * False until the saved servers have been read once. Before that the home does not know its server, so it shows
     * neither the server pill nor the new-conversation button; drawn early, the pill arrived late and pushed the
     * filters aside, and the button showed, hid while the server opened and showed again (owner 2026-11-10).
     */
    val known: Boolean = false,
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

    /**
     * The switcher's tap: connect [profile] and make it the server new conversations start on. By default there is
     * no connection owner to ask, so nothing switches and the sheet says why.
     */
    public suspend fun switchTo(profile: ProfileId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("serverSwitch")))

    /**
     * B-161: the git branch of each folder the home lists; a folder without git is absent. The connection owner
     * fills it (OC2 `GET /vcs` per folder); by default rows show no branch.
     */
    public val branches: StateFlow<Map<BranchPlace, String>>
        get() = NoHomeRowFacts.branches

    /** B-161: the folders the home lists now, so [branches] reads those and only those. */
    public fun wantBranches(places: Set<BranchPlace>) {}

    /** The last-message lines read so far (by row identity), kept with the saved rows for the next start. */
    public fun rememberPreviews(lines: Map<String, String>) {}

    /**
     * G4: each project folder's kind and whether it is a git repository, by directory, for the project sheet's glyphs
     * and "Git" pill. The connection owner fills it; by default every project shows the plain folder and no pill.
     */
    public val projectFacts: StateFlow<Map<String, HomeProjectFacts>>
        get() = NoHomeRowFacts.projectFacts

    /** B-162: the usage limits known now, for the Usage row's line; by default none, and the row says "Usage". */
    public val usageLimits: StateFlow<ImmutableList<HomeUsageLimit>>
        get() = NoHomeRowFacts.usageLimits
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
