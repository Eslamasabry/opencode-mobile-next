package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the "bring a Claude conversation in" list opens on the list it last read for
 * this folder, then the live list replaces it in place. Without a binding the list loads as before.
 */
public interface ImportsLastKnown {
    public fun peek(profile: ProfileId, directory: String): KeptImports?

    public suspend fun read(profile: ProfileId, directory: String): KeptImports?

    public suspend fun keep(profile: ProfileId, directory: String, kept: KeptImports)
}

/** Importable rows as listed: handle, title, folder, age and a clipped first prompt. */
@Serializable
public data class KeptImports(
    val rows: List<Row> = emptyList(),
    val alreadyImported: Int = 0,
    val readAtMs: Long = 0,
) {
    @Serializable
    public data class Row(
        val agentId: String,
        val handle: String,
        val directory: String,
        val lastUsedMs: Long,
        val title: String?,
        val firstPrompt: String?,
    )

    public fun toImports(): ImportableConversations =
        ImportableConversations(
            rows.map { ImportableConversation(it.agentId, it.handle, it.directory, Instant.fromEpochMilliseconds(it.lastUsedMs), it.title, it.firstPrompt) }
                .toImmutableList(),
            alreadyImported,
        )

    public companion object {
        public const val MAX_ROWS: Int = 100
        public const val MAX_PROMPT: Int = 240

        public fun of(found: ImportableConversations, readAtMs: Long): KeptImports =
            KeptImports(
                found.items.take(MAX_ROWS).map {
                    Row(it.agentId, it.handle, it.directory, it.lastUsed.toEpochMilliseconds(), it.title, it.firstPrompt?.take(MAX_PROMPT))
                },
                found.alreadyImported,
                readAtMs,
            )
    }
}
