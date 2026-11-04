package io.github.eslamasabry.oc.feature.handoff.presentation

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.handoff.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

public enum class HandoffChoiceIssue {
    PhoneCheck,
    Unavailable,
}

public data class AgentHandoffChoice(
    val id: String,
    val agent: String,
    val model: String,
    val issue: HandoffChoiceIssue? = null,
)

public data class AgentHandoffLink(val id: String, val source: String, val target: String)

/** A source-only review has no admitted destination, writer, conversation or send command. */
public class AgentHandoffReview(
    public val title: String,
    public val draft: HandoffDraft,
    private val sanitize: (String) -> String,
) {
    public fun sanitizePrompt(text: String): String = sanitize(text)

    override fun toString(): String = "AgentHandoffReview(bytes=${draft.record.byteLength})"
}

/**
 * One review lifetime. All mutations remain behind both pinned graph and native filesystem fences.
 */
public interface AgentHandoffSession {
    public val title: String
    public val directory: String
    public val choices: ImmutableList<AgentHandoffChoice>
    public val links: ImmutableList<AgentHandoffLink>
    public val state: StateFlow<HandoffState>

    public fun current(): Boolean

    public suspend fun prepare(choice: String): Outcome<HandoffPrepared>

    public fun sanitizePrompt(text: String): String

    public suspend fun send(prompt: String): Outcome<HandoffSent>

    public suspend fun retryLink(): Outcome<HandoffSent>

    public suspend fun openLink(id: String, continued: Boolean): Outcome<ConversationRef>

    public fun createdRef(): ConversationRef?

    public fun close()
}

public interface AgentHandoffHost {
    public suspend fun open(source: ConversationRef): Outcome<AgentHandoffSession>

    /** A read-only draft remains useful when source filesystem identity cannot be attested. */
    public suspend fun review(source: ConversationRef): Outcome<AgentHandoffReview?>
}

@Inject
@ContributesBinding(AppScope::class)
public class NoAgentHandoffHost : AgentHandoffHost {
    override suspend fun open(source: ConversationRef): Outcome<AgentHandoffSession> =
        Failure.Unavailable(FeatureId("agentHandoffFiles")).failed()

    override suspend fun review(source: ConversationRef): Outcome<AgentHandoffReview?> = null.ok()
}
