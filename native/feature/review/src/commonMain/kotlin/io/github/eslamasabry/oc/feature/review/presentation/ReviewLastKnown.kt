package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.FileDiff
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the review pages open on what they last showed (the changed files, one file's
 * diff, the staged undo's files) and the live read replaces it in place. Nothing kept here ever enables an action:
 * "Undo all" and Keep / Put back wait for the live read (the presenters hold them while the page is `updating`).
 */
public interface ReviewLastKnown {
    public fun peek(ref: ConversationRef, item: String): KeptReview?

    public suspend fun read(ref: ConversationRef, item: String): KeptReview?

    public suspend fun keep(ref: ConversationRef, item: String, kept: KeptReview)
}

/** A [ReviewLastKnown] bound to one conversation; the presenters hold this. */
public class ReviewShelf(private val port: ReviewLastKnown, private val ref: ConversationRef) {
    public fun peek(item: String): KeptReview? = port.peek(ref, item)

    public suspend fun read(item: String): KeptReview? = port.read(ref, item)

    public suspend fun keep(item: String, kept: KeptReview) = port.keep(ref, item, kept)

    public companion object {
        internal fun changes(messageId: String?): String = "changes:${messageId ?: "all"}"

        internal fun file(source: FileDiffSource, messageId: String?, path: String): String = "file:${source.name}:${messageId.orEmpty()}:$path"

        internal const val STAGED: String = "staged"
    }
}

/** One kept page; exactly one of the three is set. */
@Serializable
public data class KeptReview(
    val changes: KeptChanges? = null,
    val file: KeptFile? = null,
    val staged: KeptStaged? = null,
)

@Serializable
public data class KeptChanges(
    val rows: List<Row> = emptyList(),
    val complete: Boolean = true,
    val scope: String = ReviewScope.Conversation.name,
    val scopes: List<String> = listOf(ReviewScope.Conversation.name),
) {
    @Serializable
    public data class Row(
        val path: String,
        val name: String,
        val folder: String? = null,
        val added: Int? = null,
        val removed: Int? = null,
        val hasDiff: Boolean,
    )

    internal fun frame(turn: Boolean, canSendBack: Boolean): ChangesUi {
        val files = rows.map { ChangeRowUi(it.path, it.name, it.folder, it.added, it.removed, it.hasDiff) }
        val chosen = ReviewScope.entries.firstOrNull { it.name == scope } ?: ReviewScope.Conversation
        val offered = scopes.mapNotNull { s -> ReviewScope.entries.firstOrNull { it.name == s } }.ifEmpty { listOf(chosen) }
        return ChangesUi(
            turn = turn,
            load = ReviewLoad.Ready,
            files = files.toImmutableList(),
            complete = complete,
            added = files.sumOrNull { it.added },
            removed = files.sumOrNull { it.removed },
            undoAll = UndoAllUi.Unknown, // Undo all needs the live boundary
            workingTree = chosen == ReviewScope.WorkingTree,
            scope = chosen,
            scopes = offered.toImmutableList(),
            canPickScope = offered.size > 1,
            canSendBack = canSendBack,
            updating = true,
        )
    }

    public companion object {
        internal const val MAX_ROWS = 300

        internal fun of(ui: ChangesUi): KeptChanges =
            KeptChanges(
                rows = ui.files.take(MAX_ROWS).map { Row(it.path, it.name, it.folder, it.added, it.removed, it.hasDiff) },
                complete = ui.complete,
                scope = ui.scope.name,
                scopes = ui.scopes.map { it.name },
            )
    }
}

/** One file's diff; a diff over [MAX_CHARS] is not kept (the page then opens as before). */
@Serializable
public data class KeptFile(
    val file: String,
    val before: String? = null,
    val after: String? = null,
    val patch: String? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val status: String? = null,
    val missing: Boolean = false,
    val noDiff: Boolean = false,
) {
    internal fun diff(): FileDiff = FileDiff(file, before, after, patch, additions, deletions, status)

    public companion object {
        internal const val MAX_CHARS = 150_000

        internal fun of(ui: FileDiffUi): KeptFile? {
            val d = ui.diff
            val size = (d?.before?.length ?: 0) + (d?.after?.length ?: 0) + (d?.patch?.length ?: 0)
            if (size > MAX_CHARS) return null
            return KeptFile(
                file = ui.path,
                before = d?.before,
                after = d?.after,
                patch = d?.patch,
                additions = d?.additions,
                deletions = d?.deletions,
                status = d?.status,
                missing = ui.missing,
                noDiff = ui.noDiff,
            ).takeIf { d != null }
        }
    }
}

/** The staged undo as last reviewed: its files (names and counts only, no lines), prompt and message count. */
@Serializable
public data class KeptStaged(
    val files: List<File> = emptyList(),
    val title: String? = null,
    val prompt: String? = null,
    val promptUnavailable: Boolean = false,
    val messagesAfter: Int? = null,
) {
    @Serializable
    public data class File(val file: String, val additions: Int? = null, val deletions: Int? = null, val status: String? = null)

    internal fun frame(): StagedUi =
        StagedUi(
            phase = StagedPhase.Review,
            title = title,
            prompt = prompt,
            promptUnavailable = promptUnavailable,
            messagesAfter = messagesAfter,
            files = files.map { FileDiff(it.file, additions = it.additions, deletions = it.deletions, status = it.status) }.toImmutableList(),
            updating = true,
        )

    public companion object {
        internal const val MAX_FILES = 300

        internal fun of(ui: StagedUi): KeptStaged =
            KeptStaged(
                files = ui.files.orEmpty().take(MAX_FILES).map { File(it.file, it.additions, it.deletions, it.status) },
                title = ui.title,
                prompt = ui.prompt?.take(2_000),
                promptUnavailable = ui.promptUnavailable,
                messagesAfter = ui.messagesAfter,
            )
    }
}

private inline fun <T> List<T>.sumOrNull(of: (T) -> Int?): Int? {
    var total = 0
    for (item in this) total += of(item) ?: return null
    return total
}
