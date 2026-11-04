package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Session
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList

/** A conversation started in the provider's own app. Its opaque [handle] is never display copy. */
public data class ImportableConversation(
    val agentId: String,
    val handle: String,
    val directory: String,
    val lastUsed: Instant,
    val title: String? = null,
    val firstPrompt: String? = null,
) {
    public val displayTitle: String?
        get() = title ?: firstPrompt
}

public data class ImportableConversations(
    val items: ImmutableList<ImportableConversation>,
    val alreadyImported: Int = 0,
)

/**
 * Provider-native conversations for this gateway's exact project, brought into its ordinary session
 * list.
 */
public interface ProviderConversationImportGateway {
    public val providerImportSupported: Boolean
    public val providerImportDirectory: String

    public suspend fun importableConversations(): Outcome<ImportableConversations>

    /** Import once; a lost mutation answer is uncertainty, never an automatic retry. */
    public suspend fun importConversation(conversation: ImportableConversation): Outcome<Session>
}
