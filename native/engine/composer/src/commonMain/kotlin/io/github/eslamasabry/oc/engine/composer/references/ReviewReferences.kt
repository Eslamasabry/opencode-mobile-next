package io.github.eslamasabry.oc.engine.composer.references

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where a staged reference was picked up (Flutter `ReviewReferenceKind`; [wire] is the stored Dart name). */
public enum class ReviewReferenceKind(public val wire: String) {
    File("file"), ChangedFile("changedFile"), Hunk("hunk"), Selection("selection"), Comment("comment");

    public companion object {
        public fun fromWire(name: String): ReviewReferenceKind? = entries.firstOrNull { it.wire == name }
    }
}

/** Which diff the reference was read from; OC2 `session.diff` is the working tree, so labels stay honest. */
public enum class ReviewReferenceScope(public val wire: String) {
    None("none"), Session("session"), WorkingTree("workingTree"), Branch("branch");

    public companion object {
        public fun fromWire(name: String): ReviewReferenceScope? = entries.firstOrNull { it.wire == name }
    }
}

/**
 * A pointer from Files/Changes/Review into a prompt (plan 09 §10.7; port of `lib/state/review_handoff.dart`). Nothing
 * is uploaded: on send it becomes structured Markdown in the prompt text.
 */
public data class ReviewReference(
    val id: String,
    val kind: ReviewReferenceKind,
    val path: String,
    val scope: ReviewReferenceScope = ReviewReferenceScope.None,
    val lineLabel: String? = null,
    val snippet: String? = null,
    val comment: String? = null,
    val added: Int? = null,
    val removed: Int? = null,
    val status: String? = null,
) {
    val basename: String get() = path.split('/').lastOrNull { it.isNotEmpty() } ?: path

    /** The chip's data: the file, plus the line range when there is one. */
    val label: String get() = if (lineLabel.isNullOrEmpty()) basename else "$basename · $lineLabel"

    /** Collapses duplicate stages; every comment is its own note (two comments on one range stay distinct). */
    val signature: String get() = if (kind == ReviewReferenceKind.Comment) id else "${kind.wire}|$path|${lineLabel.orEmpty()}|${scope.wire}"

    override fun toString(): String = "ReviewReference($id, ${kind.wire}, ${path.length} chars)"

    /**
     * The block this reference adds to the sent prompt. [snippetBudget] caps its share of the send-wide budget (null:
     * unbounded); whatever is cut is said in the text. This is prompt content for the agent, not app copy.
     */
    public fun toPromptText(snippetBudget: Int? = null): String {
        val out = StringBuilder("`$path`")
        val detail =
            listOfNotNull(
                lineLabel?.takeIf { it.isNotEmpty() },
                status?.takeIf { it.isNotEmpty() },
                if (added != null || removed != null) "+${added ?: 0} −${removed ?: 0}" else null,
                scopeName(scope),
            )
        if (detail.isNotEmpty()) out.append(" (").append(detail.joinToString(" · ")).append(')')
        val note = comment?.trim()
        if (!note.isNullOrEmpty()) out.append('\n').append(note)
        val body = snippet?.trimEnd()
        if (!body.isNullOrEmpty()) {
            val (text, omitted) = truncate(body, snippetBudget)
            val fence = fenceFor(text)
            if (text.isNotEmpty()) out.append("\n\n").append(fence).append("diff\n").append(text).append('\n').append(fence)
            if (omitted > 0) {
                val lines = body.count { it == '\n' } + 1
                val shown = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
                out.append(
                    "\n[Snippet truncated to fit the prompt: showing $shown of $lines lines; $omitted characters omitted. " +
                        "Read `$path` for the rest.]",
                )
            }
        }
        return out.toString()
    }

    public companion object {
        /** Characters of snippet body one send carries across every staged reference. */
        public const val MAX_SNIPPET_CHARS: Int = 24_000

        public fun scopeName(scope: ReviewReferenceScope): String? =
            when (scope) {
                ReviewReferenceScope.None -> null
                ReviewReferenceScope.Session -> "session changes"
                ReviewReferenceScope.WorkingTree -> "working tree"
                ReviewReferenceScope.Branch -> "branch changes"
            }

        /** Keeps whole lines and reports how many characters it dropped. */
        private fun truncate(body: String, budget: Int?): Pair<String, Int> {
            if (budget == null || body.length <= budget) return body to 0
            if (budget <= 0) return "" to body.length
            // Dart lastIndexOf(pattern, start) finds a match starting at or before start.
            val breakAt = body.lastIndexOf('\n', budget)
            val kept = body.substring(0, if (breakAt > 0) breakAt else budget).trimEnd()
            return kept to body.length - kept.length
        }

        /** A fence longer than any backtick run inside, so the snippet cannot close it. */
        private fun fenceFor(body: String): String {
            var longest = 0
            var run = 0
            for (c in body) {
                run = if (c == '`') run + 1 else 0
                if (run > longest) longest = run
            }
            return "`".repeat(if (longest < 3) 3 else longest + 1)
        }

        /**
         * The prompt preamble for a staged set: "Reference:" or "References:", blocks separated by blank lines. Snippets
         * share [budget] max-min fairly: every one that fits its equal share goes whole, the rest is shared by the
         * large ones, so one huge diff never starves nine small ones (B-CHAT-099).
         */
        public fun format(references: List<ReviewReference>, budget: Int = MAX_SNIPPET_CHARS): String {
            val budgets = budgets(references, budget)
            val blocks = references.mapIndexed { i, r -> r.toPromptText(budgets[i]) }.filter { it.isNotBlank() }
            if (blocks.isEmpty()) return ""
            return (if (blocks.size == 1) "Reference:" else "References:") + "\n\n" + blocks.joinToString("\n\n")
        }

        private fun budgets(references: List<ReviewReference>, total: Int): List<Int?> {
            val budgets = MutableList<Int?>(references.size) { null }
            val sizes = references.map { it.snippet?.trimEnd()?.length ?: 0 }
            val pending = sizes.indices.filter { sizes[it] > 0 }.toMutableList()
            var remaining = if (total < 0) 0 else total
            while (pending.isNotEmpty()) {
                val share = remaining / pending.size
                val fits = pending.filter { sizes[it] <= share }
                if (fits.isEmpty()) {
                    pending.forEach { budgets[it] = share }
                    break
                }
                for (i in fits) {
                    budgets[i] = sizes[i]
                    remaining -= sizes[i]
                }
                pending.removeAll(fits)
            }
            return budgets
        }
    }
}

public enum class ReviewStageOutcome { Staged, Duplicate, Full }

/**
 * Staged references per conversation, in memory only (they point at a diff that moves; plan 09 §10.7). Files,
 * Changes and Review stage here; the composer reads [staged] and [take]s them into a send.
 */
public class ReviewReferences {
    public companion object {
        public const val MAX_PER_SESSION: Int = 10
    }

    private val _staged = MutableStateFlow<Map<String, List<ReviewReference>>>(emptyMap())
    private var ids = 0

    public val staged: StateFlow<Map<String, List<ReviewReference>>> = _staged.asStateFlow()

    /** A new id, unique within the app run. */
    public fun nextId(prefix: String): String = "$prefix-${++ids}"

    public fun referencesFor(sessionId: String): List<ReviewReference> = _staged.value[sessionId].orEmpty()

    public fun stage(sessionId: String, reference: ReviewReference): ReviewStageOutcome {
        var outcome = ReviewStageOutcome.Staged
        _staged.update { all ->
            val list = all[sessionId].orEmpty()
            outcome =
                when {
                    list.any { it.signature == reference.signature } -> ReviewStageOutcome.Duplicate
                    list.size >= MAX_PER_SESSION -> ReviewStageOutcome.Full
                    else -> ReviewStageOutcome.Staged
                }
            if (outcome == ReviewStageOutcome.Staged) all + (sessionId to list + reference) else all
        }
        return outcome
    }

    /** Removing an unknown id is silent (no change published). */
    public fun remove(sessionId: String, id: String) {
        _staged.update { all ->
            val list = all[sessionId] ?: return@update all
            val next = list.filterNot { it.id == id }
            when {
                next.size == list.size -> all
                next.isEmpty() -> all - sessionId
                else -> all + (sessionId to next)
            }
        }
    }

    /** Drops and returns everything staged for [sessionId] (folded into a sent prompt). */
    public fun take(sessionId: String): List<ReviewReference> {
        var taken: List<ReviewReference> = emptyList()
        _staged.update { all ->
            taken = all[sessionId].orEmpty()
            if (taken.isEmpty()) all else all - sessionId
        }
        return taken
    }
}
