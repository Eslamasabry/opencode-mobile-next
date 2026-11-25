package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyBlock

/** Picks a replacement model when a chat's chosen model is no longer listed on the server. */
public object ModelFallback {
    /** Models that already answered in this chat, newest first (null timestamps last), without repeats. */
    public fun answered(rows: Iterable<TranscriptRow>): List<ModelKey> =
        rows
            .asSequence()
            .filterIsInstance<FooterRow>()
            .filter { !it.failed && !it.stopped }
            .mapNotNull { row ->
                val provider = row.providerId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val model = row.modelId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ModelKey(provider, model) to row.createdMs
            }
            .sortedWith(
                compareByDescending<Pair<ModelKey, Long?>> { it.second != null }
                    .thenByDescending { it.second }
            )
            .map { it.first }
            .distinct()
            .toList()

    /** Models refused for regional/country reasons in this chat. */
    public fun refused(rows: Iterable<TranscriptRow>): Set<ModelKey> =
        rows
            .asSequence()
            .filterIsInstance<FooterRow>()
            .filter { it.failed && ReplyBlock.of(it.reason) == ReplyBlock.CountryRefusal }
            .mapNotNull { row ->
                val provider = row.providerId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val model = row.modelId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ModelKey(provider, model)
            }
            .toSet()

    /** Picks a usable replacement for [gone], preferring an answered model before same-provider fallback. */
    public fun pick(
        gone: ModelKey?,
        answered: List<ModelKey>,
        models: List<ComposerModel>,
        refused: Set<ModelKey> = emptySet(),
    ): ComposerModel? {
        val enabled = models.filter { it.enabled && ModelKey(it.providerId, it.modelId) !in refused }
        val fromAnswered =
            answered.firstNotNullOfOrNull { key ->
                if (key == gone || key in refused) null
                else enabled.firstOrNull { it.providerId == key.providerId && it.modelId == key.modelId }
            }
        return fromAnswered
            ?: gone?.let { g ->
                enabled.firstOrNull { it.providerId == g.providerId && it.modelId != g.modelId }
            }
    }
}
