package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFile
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFileStatus
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffGap
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLine
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLineKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// B-156: comment on a diff line, and the agent fixes it. The comment goes to the conversation's own agent as a steer
// (or its next prompt when it is idle), quoting file:line and the line; it stays under the file as "Waiting for the
// agent" until the agent's next edit changes that line, then "Fixed".

/** Which side of the diff a comment points at: the file as it is now, or a removed line. */
public enum class CommentSide { New, Old }

/** Where a comment points: [start]..[end] on [side], with the lines as the person saw them (redacted). */
public data class CommentAnchor(
    val path: String,
    val side: CommentSide,
    val start: Int,
    val end: Int,
    val quote: ImmutableList<String>,
) {
    override fun toString(): String = "CommentAnchor(${path.length} chars, $side, $start..$end)"
}

public enum class CommentStatus {
    /** Written, not sent yet: "Send N comments" sends every draft in one prompt. */
    Draft,

    /** On its way to the agent. */
    Sending,

    /** Sent: waiting for the agent's next edit of that line. */
    Pending,

    /** The agent's edit changed the line after the comment was sent. */
    Resolved,
}

/** One comment on a diff line. [raw] is the line text the resolve rule compares; it is never logged or stored. */
public data class LineComment(
    val id: String,
    val anchor: CommentAnchor,
    val text: String,
    val status: CommentStatus = CommentStatus.Draft,
    val raw: ImmutableList<String> = anchor.quote,
    /** The file's version when the comment was sent: only a newer version can resolve it. */
    val sentVersion: String? = null,
) {
    override fun toString(): String = "LineComment($id, $anchor, $status)"
}

/** Sends one prompt to this conversation's agent: a steer while it works, its next prompt when it is idle. */
public fun interface ReviewPromptSender {
    public suspend fun send(text: String): Outcome<Unit>
}

/**
 * The prompt the agent gets (prompt content for the agent, not app copy, so it stays English like the Flutter review
 * references). One comment reads `In src/a.kt line 42 `val x = 1`: rename this`; several are numbered.
 */
public object ReviewCommentPrompt {
    /** The quoted line is cut at this many characters. */
    public const val QUOTE_CHARS: Int = 60

    public fun format(comments: List<LineComment>): String {
        val notes = comments.filter { it.text.isNotBlank() }
        return when (notes.size) {
            0 -> ""
            1 -> one(notes[0])
            else ->
                "Please address these review comments on your changes:\n" +
                    notes.mapIndexed { i, c -> "${i + 1}. " + one(c).replace("\n", "\n   ") }.joinToString("\n")
        }
    }

    public fun one(comment: LineComment): String {
        val a = comment.anchor
        val lines = if (a.start == a.end) "line ${a.start}" else "lines ${a.start}-${a.end}"
        val where = if (a.side == CommentSide.Old) "removed $lines" else lines
        val quote = quote(a.quote)
        val code = if (quote.isEmpty()) "" else " " + code(quote)
        return "In ${a.path} $where$code: ${comment.text.trim()}"
    }

    /** The first non-blank line, trimmed and cut; "…" says when more was selected or the line was cut. */
    public fun quote(lines: List<String>): String {
        val first = lines.firstOrNull { it.isNotBlank() }?.trim() ?: return ""
        val cut = first.length > QUOTE_CHARS
        val more = lines.count { it.isNotBlank() } > 1
        val body = if (cut) first.take(QUOTE_CHARS).trimEnd() else first
        return if (cut || more) "$body …" else body
    }

    private fun code(text: String): String = if ('`' in text) "`` $text ``" else "`$text`"
}

/** The lines of one side of a diff, in file order; null marks a fold the diff does not carry. */
public object CommentLines {
    public fun side(file: DiffFile, side: CommentSide): List<Pair<Int, String>?> {
        val out = ArrayList<Pair<Int, String>?>()
        fun take(line: DiffLine) {
            when (side) {
                CommentSide.New -> line.newNo?.takeIf { line.kind != DiffLineKind.Hunk && line.kind != DiffLineKind.Removed }?.let { out += it to line.text }
                // The old side a comment can sit on is what was removed (context reads as the new side).
                CommentSide.Old -> line.oldNo?.takeIf { line.kind == DiffLineKind.Removed }?.let { out += it to line.text }
            }
        }
        for (segment in file.segments) {
            when (segment) {
                is DiffLine -> if (segment.kind == DiffLineKind.Hunk) out += null else take(segment)
                is DiffGap -> segment.lines?.forEach(::take) ?: run { out += null }
            }
        }
        return out
    }

    /** The line texts [start]..[end] on [side]; empty when the diff does not show them all. */
    public fun range(file: DiffFile, side: CommentSide, start: Int, end: Int): List<String> {
        val byNumber = side(file, side).filterNotNull().toMap()
        val texts = (start..end).map { byNumber[it] ?: return emptyList() }
        return texts
    }

    /**
     * Finds the lines a selection of the kit diff view holds ([selected] is its redacted text, lines joined by "\n";
     * [redact] is the same redactor). The new side is tried first, then removed lines. Null when nothing matches.
     */
    public fun anchor(file: DiffFile, selected: String, redact: (String) -> String = { it }): CommentAnchor? {
        val wanted = selected.split('\n')
        for (side in listOf(CommentSide.New, CommentSide.Old)) {
            val lines = side(file, side).filterNotNull()
            for (i in 0..lines.size - wanted.size) {
                val window = lines.subList(i, i + wanted.size)
                if (window.map { redact(it.second) } == wanted && window.zipWithNext().all { (a, b) -> b.first == a.first + 1 }) {
                    return CommentAnchor(file.path, side, window.first().first, window.last().first, wanted.toImmutableList())
                }
            }
        }
        return null
    }

    /** True when [raw] still appears as consecutive lines of [lines]. */
    public fun contains(lines: List<Pair<Int, String>?>, raw: List<String>): Boolean {
        if (raw.isEmpty()) return true
        var run = 0
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line != null && line.second == raw[run]) {
                run++
                if (run == raw.size) return true
            } else if (run > 0) {
                i -= run // retry from the line after the run's start
                run = 0
            }
            i++
        }
        return false
    }
}

/**
 * The resolve rule: a sent comment is fixed once the file changed after it was sent ([version] is newer than the one
 * it was sent against) and the commented lines are not there any more as they were (the agent's edit touched them).
 * A file that left the changes ([now] null) is fixed too. An edit elsewhere in the file leaves it waiting.
 */
public object CommentResolve {
    public fun resolved(comment: LineComment, version: String, now: DiffFile?): Boolean {
        if (comment.status != CommentStatus.Pending) return false
        if (comment.sentVersion == null || comment.sentVersion == version) return false
        if (now == null) return true
        return !CommentLines.contains(CommentLines.side(now, comment.anchor.side), comment.raw)
    }

    /** A version of one file's diff: changes whenever its text does. */
    public fun version(diff: FileDiff?): String =
        if (diff == null) "none" else "${diff.patch?.hashCode()}:${diff.before?.hashCode()}:${diff.after?.hashCode()}"

    /** The kit's diff file for [diff], as the diff page shows it. */
    public fun diffFile(diff: FileDiff): DiffFile {
        val status =
            when (diff.status) {
                "added" -> DiffFileStatus.Added
                "deleted" -> DiffFileStatus.Deleted
                else -> null
            }
        val patch = diff.patch
        return if (!patch.isNullOrBlank()) DiffModel.parseFilePatch(diff.file, patch, status)
        else DiffModel.fromBeforeAfter(diff.file, diff.before, diff.after, status)
    }
}

/**
 * One conversation's review comments, shared by its review pages (the Changes page and each file's diff), kept while
 * the review is open. In memory only: nothing about them is written to disk.
 */
public class ReviewCommentBook {
    private val mutable = MutableStateFlow<ImmutableList<LineComment>>(persistentListOf())
    public val comments: StateFlow<ImmutableList<LineComment>> = mutable.asStateFlow()
    private var counter = 0

    public val drafts: List<LineComment> get() = mutable.value.filter { it.status == CommentStatus.Draft }

    public fun add(anchor: CommentAnchor, text: String, raw: List<String>): LineComment {
        val comment = LineComment("c${++counter}", anchor, text.trim(), raw = (raw.ifEmpty { anchor.quote }).toImmutableList())
        mutable.update { (it + comment).toImmutableList() }
        return comment
    }

    public fun remove(id: String) {
        mutable.update { list -> list.filterNot { it.id == id && it.status == CommentStatus.Draft }.toImmutableList() }
    }

    /** Marks [ids] sending; returns the ones that were drafts. */
    public fun sending(ids: Set<String>): List<LineComment> {
        val taken = mutable.value.filter { it.id in ids && it.status == CommentStatus.Draft }
        mutable.update { list -> list.map { if (it in taken) it.copy(status = CommentStatus.Sending) else it }.toImmutableList() }
        return taken
    }

    /** The send finished: pending against each file's [versions], or back to drafts when it failed. */
    public fun sent(ids: Set<String>, ok: Boolean, versions: (String) -> String?) {
        mutable.update { list ->
            list.map { c ->
                when {
                    c.id !in ids || c.status != CommentStatus.Sending -> c
                    ok -> c.copy(status = CommentStatus.Pending, sentVersion = versions(c.anchor.path) ?: UNKNOWN)
                    else -> c.copy(status = CommentStatus.Draft)
                }
            }.toImmutableList()
        }
    }

    /** A fresh read of [path]: resolves the comments the agent's edit touched. */
    public fun reconcile(path: String, version: String, now: DiffFile?) {
        mutable.update { list ->
            list.map { c ->
                if (c.anchor.path == path && CommentResolve.resolved(c, version, now)) c.copy(status = CommentStatus.Resolved) else c
            }.toImmutableList()
        }
    }

    public fun forFile(path: String): List<LineComment> = mutable.value.filter { it.anchor.path == path }

    private companion object {
        /** Sent before the file's version was known: any read counts as newer. */
        const val UNKNOWN = "unknown"
    }
}
