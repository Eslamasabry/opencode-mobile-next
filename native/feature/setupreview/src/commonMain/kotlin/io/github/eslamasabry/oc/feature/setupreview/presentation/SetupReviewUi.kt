package io.github.eslamasabry.oc.feature.setupreview.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
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

    /** Local structured guidance only. Never contacts a server or applies an edit. */
    public fun plan(
        context: SetupReviewContext,
        intent: SetupGuidedIntent,
        inputs: Map<SetupGuidedField, String>,
    ): SetupGuidedReply
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
    val guided: SetupGuidedUi? = null,
)

public interface SetupReviewActions {
    public fun back()

    public fun refresh()

    public fun signIn()

    public fun openGuided()

    public fun chooseIntent(intent: SetupGuidedIntent)

    public fun editInput(field: SetupGuidedField, value: String)

    public fun prepareProposal()
}

public enum class SetupGuidedIntent {
    ChooseModel,
    DefaultAgent,
    Permission,
    RemoteMcp,
    LocalMcp,
    RemoveMcp,
}

public enum class SetupGuidedField {
    Model,
    Agent,
    Tool,
    Effect,
    Name,
    Url,
    Command,
}

public enum class SetupGuidedPhase {
    NeedsInput,
    Proposed,
    Unavailable,
}

public data class SetupGuidedReply(
    val phase: SetupGuidedPhase,
    val changes: ImmutableList<SetupValueUi> = persistentListOf(),
    val inputsInvalid: Boolean = false,
)

public data class SetupGuidedUi(
    val intent: SetupGuidedIntent = SetupGuidedIntent.ChooseModel,
    val inputs: ImmutableMap<SetupGuidedField, String> = persistentMapOf(),
    val reply: SetupGuidedReply? = null,
)

public fun SetupGuidedIntent.fields(): List<SetupGuidedField> =
    when (this) {
        SetupGuidedIntent.ChooseModel -> listOf(SetupGuidedField.Model)
        SetupGuidedIntent.DefaultAgent -> listOf(SetupGuidedField.Agent)
        SetupGuidedIntent.Permission -> listOf(SetupGuidedField.Tool, SetupGuidedField.Effect)
        SetupGuidedIntent.RemoteMcp -> listOf(SetupGuidedField.Name, SetupGuidedField.Url)
        SetupGuidedIntent.LocalMcp -> listOf(SetupGuidedField.Name, SetupGuidedField.Command)
        SetupGuidedIntent.RemoveMcp -> listOf(SetupGuidedField.Name)
    }
