package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.text.PlainMarkdown
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
            // The tools stay facts: the screen names each in the plain words of its step row, never the tool's id.
            val tools =
                parts
                    .filter { it.type == PartType.Tool && it.tool != null }
                    .map { toolFacts(it) }
                    .distinctBy { it.phrase to it.titleArgs to it.subject }
                    .toImmutableList()
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
            val noticePart =
                parts.firstOrNull {
                    it.type in setOf(PartType.Notice, PartType.Compaction, PartType.Switch)
                }
            // A change of model keeps the model's id in its text: say it as the conversation's own replies do.
            val notice =
                noticePart?.let { part ->
                    if (part.type == PartType.Switch && (part.noticeMetadata["kind"] ?: part.toolName) == "model")
                        ReplyModelNames.fromId(part.text)
                    else part.text
                }.orEmpty()
            val spoken = sequenceOf(matched.orEmpty(), text, files).firstOrNull { it.isNotBlank() }.orEmpty()
            // Flutter's order: words, files, the tools in words, then the thought; a notice last.
            val preview =
                if (spoken.isNotBlank() || tools.isNotEmpty()) spoken
                else sequenceOf(plainPreview(thought), plainPreview(notice)).firstOrNull { it.isNotBlank() }.orEmpty()
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
                if (spoken.isBlank()) tools else persistentListOf(),
            )
        }
        .toImmutableList()
}

private fun plainPreview(text: String): String = PlainMarkdown.oneLine(text)
