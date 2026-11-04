package io.github.eslamasabry.oc.feature.chat.composer.history

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role

/** Sent words kept on this device for one server. Only a confirmed send is recorded. */
public interface ComposerHistory {
    public suspend fun recent(profile: ProfileId): Outcome<List<String>>

    public suspend fun record(profile: ProfileId, text: String): Outcome<Unit>
}

@ContributesTo(AppScope::class)
public interface ComposerHistoryBindings {
    @Multibinds(allowEmpty = true) public val composerHistories: Set<ComposerHistory>
}

/**
 * A search or pick copies words into the current draft; it never repeats a send or copies files.
 */
public data class ComposerHistoryUi(
    val prompts: List<String> = emptyList(),
    val loading: Boolean = false,
    val readFailure: Failure? = null,
    val saveFailure: Failure? = null,
) {
    override fun toString(): String =
        "ComposerHistoryUi(count=${prompts.size}, loading=$loading, readFailed=${readFailure != null}, saveFailed=${saveFailure != null})"
}

public object ComposerHistoryRules {
    /**
     * Conversation text first, newest first, followed by this server's recent sends; no local
     * optimistic text.
     */
    public fun recent(messages: List<MessageWithParts>, saved: List<String>): List<String> =
        (messages
                .asReversed()
                .asSequence()
                .filter { it.info.role == Role.User && !it.info.id.startsWith("local-") }
                .map { message ->
                    message.parts
                        .filter { it.type == PartType.Text && !it.synthetic }
                        .joinToString("\n") { it.text }
                        .trim()
                }
                .filter { it.isNotEmpty() } + saved.asSequence().filter { it.isNotBlank() })
            .distinct()
            .take(50)
            .toList()
}
