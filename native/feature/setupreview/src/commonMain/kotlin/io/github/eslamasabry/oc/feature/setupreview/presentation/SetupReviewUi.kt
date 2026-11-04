package io.github.eslamasabry.oc.feature.setupreview.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

/** A live, exact graph-and-location owner. [owner] is opaque to this feature. */
public data class SetupReviewContext(
    val profile: ProfileId,
    val directory: String?,
    val workspace: String?,
    val online: Boolean,
    val supported: Boolean,
    val owner: Any,
)

/**
 * Only redacted display values cross this boundary; raw configuration never enters presentation.
 */
public interface SetupReviewHost {
    public fun contexts(profile: ProfileId): Flow<SetupReviewContext>

    public suspend fun read(context: SetupReviewContext): Outcome<SetupReviewContent>
}

public data class SetupValueUi(val name: String, val value: String)

public data class SetupSourceUi(
    val type: String,
    val path: String?,
    val values: ImmutableList<SetupValueUi>,
)

public enum class SetupMcpState {
    Connected,
    Pending,
    Disabled,
    Failed,
    NeedsAuth,
    Unknown,
}

public data class SetupMcpUi(val name: String, val status: SetupMcpState)

public data class SetupReviewContent(
    val sources: ImmutableList<SetupSourceUi> = persistentListOf(),
    val servers: ImmutableList<SetupMcpUi> = persistentListOf(),
) {
    val isEmpty: Boolean
        get() = sources.isEmpty() && servers.isEmpty()
}

public enum class SetupReviewPhase {
    Loading,
    Ready,
    Empty,
    Offline,
    Unsupported,
    NeedsSignIn,
    Error,
}

public data class SetupReviewUi(
    val phase: SetupReviewPhase = SetupReviewPhase.Loading,
    val content: SetupReviewContent = SetupReviewContent(),
    val refreshing: Boolean = false,
    val online: Boolean = false,
)

public interface SetupReviewActions {
    public fun back()

    public fun refresh()

    public fun signIn()
}
