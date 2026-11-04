package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Flutter timeline: one row per real message, newest first, including files, tools and thoughts.
 */
internal fun timelineEntries(
    feed: TurnFeed,
    transcript: TranscriptState,
    query: String,
    canFork: Boolean,
): ImmutableList<TimelineEntryUi> {
    val q = query.trim()
    val turnsByMessage =
        feed.ledger.turns
            .flatMap { turn -> turn.messages.map { it.info.id to turn.turnId } }
            .toMap()
    val rowsByMessage = transcript.rows.groupBy { RowIds.messageIdOf(it) }
    val hits =
        if (q.isBlank()) emptySet()
        else TranscriptFind.search(transcript, q).map { it.rowKey }.toSet()
    return feed.ledger.messages
        .asReversed()
        .mapNotNull { message ->
            val info = message.info
            val rows =
                rowsByMessage[info.id].orEmpty() +
                    transcript.rows.filter { it.key.startsWith("notice:${info.id}/") }
            // A folded tool or notice shares the work line of its turn; the engine records that
            // source-to-row map.
            val sourceRows =
                transcript.searchText.filter {
                    it.sourceId == info.id || it.sourceId.startsWith("${info.id}/")
                }
            val row =
                if (q.isBlank())
                    rows.lastOrNull()?.key
                        ?: sourceRows.firstOrNull()?.rowKey
                        ?: transcript.rows.firstOrNull { it.turnId == turnsByMessage[info.id] }?.key
                else
                    rows.firstOrNull { it.key in hits }?.key
                        ?: sourceRows
                            .firstOrNull {
                                it.rowKey in hits && it.text.contains(q, ignoreCase = true)
                            }
                            ?.rowKey
            if (row == null) return@mapNotNull null
            val parts = message.parts.filterNot { it.synthetic }
            val text =
                parts
                    .filter { it.type == PartType.Text }
                    .joinToString(" ") { plainPreview(it.text) }
                    .trim()
            val files =
                parts
                    .filter { it.type == PartType.File }
                    .mapNotNull { it.filename?.takeIf(String::isNotBlank) }
                    .joinToString(", ")
            val tools =
                parts
                    .filter { it.type == PartType.Tool }
                    .mapNotNull { it.tool?.title?.takeIf(String::isNotBlank) ?: it.toolName }
                    .distinct()
                    .joinToString(", ")
            val thought =
                parts
                    .firstOrNull { it.type == PartType.Reasoning && it.text.isNotBlank() }
                    ?.text
                    .orEmpty()
            val matched =
                if (q.isBlank()) null
                else
                    sourceRows
                        .firstOrNull {
                            it.rowKey == row && it.text.contains(q, ignoreCase = true)
                        }
                        ?.text
            val notice =
                parts
                    .firstOrNull {
                        it.type in setOf(PartType.Notice, PartType.Compaction, PartType.Switch)
                    }
                    ?.text
                    .orEmpty()
            val preview =
                sequenceOf(
                        matched.orEmpty(),
                        text,
                        files,
                        tools,
                        plainPreview(thought),
                        plainPreview(notice),
                    )
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
            TimelineEntryUi(
                info.id,
                row,
                info.role == Role.User,
                preview,
                info.time?.createdMs,
                canFork &&
                    info.role == Role.User &&
                    !info.id.startsWith("local-") &&
                    parts.any { it.type == PartType.Text },
            )
        }
        .toImmutableList()
}

private fun plainPreview(text: String): String =
    text
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("`+|\\*\\*|__"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun findExcerpt(state: TranscriptState, hit: FindHit): String? {
    val text =
        state.searchText
            .firstOrNull { it.sourceId == hit.sourceId && it.rowKey == hit.rowKey }
            ?.text
            ?: when (val row = state.rows.firstOrNull { it.key == hit.rowKey }) {
                is io.github.eslamasabry.oc.engine.transcript.state.PromptRow -> row.text
                is io.github.eslamasabry.oc.engine.transcript.state.BlockRow -> row.text
                is io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow -> row.firstLine
                else -> null
            }
            ?: return null
    val from = (hit.start - 60).coerceAtLeast(0).coerceAtMost(text.length)
    val end = (hit.end + 100).coerceAtMost(text.length)
    return (if (from > 0) "…" else "") +
        text.substring(from, end) +
        (if (end < text.length) "…" else "")
}
