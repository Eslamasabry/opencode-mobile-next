package io.github.eslamasabry.oc.ui.kitchat.diff

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// The diff model, ported from lib/ui/kit/diff/kit_diff_model.dart (plan 09 §9.3). Pure Kotlin: no Compose here. Every
// function is total: any text, malformed or not, gives a file (never an exception).

/** What one line of a [DiffFile] is. A [Hunk] line carries the `@@` header as its text (kit_diff_model.dart:4). */
public enum class DiffLineKind { Context, Added, Removed, Hunk }

/** How the file changed (kit_diff_model.dart:27). */
public enum class DiffFileStatus { Modified, Added, Deleted, Renamed }

/** One entry of [DiffFile.segments], in file order: a [DiffLine] or a folded [DiffGap]. */
public sealed interface DiffSegment

/**
 * One line of a diff with the numbers it has in each file (kit_diff_model.dart:8). [text] never ends in a carriage
 * return. [noNewlineAtEnd] is the patch's `\ No newline at end of file` note: it belongs to this line and is not a line
 * of either file.
 */
public data class DiffLine(
    val text: String,
    val kind: DiffLineKind,
    val oldNo: Int? = null,
    val newNo: Int? = null,
    val noNewlineAtEnd: Boolean = false,
) : DiffSegment

/**
 * Unchanged lines that start folded (kit_diff_model.dart:19). [lines] is null when the source (a unified patch) does not
 * carry them: the fold line then only states the count.
 */
public data class DiffGap(val count: Int, val lines: ImmutableList<DiffLine>? = null) : DiffSegment

/**
 * One hunk that holds a change: a run of lines between `@@` headers or folded gaps (kit_diff_model.dart:411). The
 * ranges are the numbers of the lines it shows on each side (null when it shows none on that side, e.g. an added file
 * has no old range). [headerSegment] is the index of its `@@` line in [DiffFile.segments], when the source had one.
 */
public data class DiffHunk(
    val oldStart: Int?,
    val oldEnd: Int?,
    val newStart: Int?,
    val newEnd: Int?,
    val headerSegment: Int?,
)

/**
 * One file's change, normalised into lines and gaps (kit_diff_model.dart:32). Build it with [DiffModel]. The instance is
 * the revision: derived facts ([changeCount], [hunks], …) are computed once per instance, on first read.
 */
public class DiffFile(
    public val path: String,
    /** [DiffLine] and [DiffGap] entries in file order, context already folded. */
    public val segments: ImmutableList<DiffSegment>,
    public val added: Int,
    public val removed: Int,
    public val status: DiffFileStatus = DiffFileStatus.Modified,
    public val oldPath: String? = null,
    /** A binary patch: no lines, the view only says so. */
    public val binary: Boolean = false,
) {
    private val index by lazy { FileIndex(segments) }

    /** The number of changes (runs of added/removed lines) in this file (kit_diff_model.dart:186). */
    public val changeCount: Int get() = index.changes.size

    /** The segment index where each change starts. */
    public val changeStarts: ImmutableList<Int> get() = index.changes

    /** The hunks holding a change. */
    public val hunks: ImmutableList<DiffHunk> get() = index.hunks

    /** Every line the file shows, folded or not (hunk headers excluded): the "of N lines" of a capped view. */
    public val totalLines: Int get() = index.total

    /** The largest line number on either side (at least 1): sizes the number gutter. */
    public val maxLineNumber: Int get() = index.maxNumber

    override fun toString(): String = "DiffFile($path, $status, +$added −$removed, ${segments.size} segments)"
}

/** Where a fold line sits against the changes, so the caller can word "… 27 lines above" (kit_diff_rows.dart:411). */
public enum class DiffGapPosition { Above, Between, Below }

/** One row a unified diff view draws. [segment] is the index in [DiffFile.segments] it comes from (a stable key). */
public sealed interface DiffRow {
    public val segment: Int

    public data class Line(val line: DiffLine, override val segment: Int) : DiffRow

    /**
     * A hunk's `@@` header as line ranges (each at least one line). The Flutter view words the new side ("Lines
     * 10–13"), or the old side when [newCount] is 0 (kit_diff_rows.dart:67-76).
     */
    public data class HunkHeader(
        val oldStart: Int,
        val oldEnd: Int,
        val newStart: Int,
        val newEnd: Int,
        override val segment: Int,
        val newCount: Int = newEnd - newStart + 1,
    ) : DiffRow

    /** A fold line: [hidden] unchanged lines, [lines] when they can be revealed. */
    public data class Gap(
        val hidden: Int,
        val position: DiffGapPosition,
        val lines: ImmutableList<DiffLine>?,
        override val segment: Int,
    ) : DiffRow
}

/** The rows of one file, maybe cut at a line cap: [shownLines] of [totalLines] lines are shown. */
public data class DiffRows(
    val rows: ImmutableList<DiffRow>,
    val shownLines: Int,
    val totalLines: Int,
    val cut: Boolean,
)

/** Builds [DiffFile]s from unified patches or before/after text, and the rows a view draws. */
public object DiffModel {
    /** Context lines kept visible on each side of a change before the rest of a run folds (kit_diff_model.dart:195). */
    public const val VISIBLE_CONTEXT: Int = 3

    /**
     * Above this many cells the line diff falls back to one changed block (the common prefix and suffix stay context): a
     * quadratic table is worse than a coarser diff (kit_diff_model.dart:200).
     */
    public const val LCS_CELL_LIMIT: Int = 1_000_000

    private val hunkHeader = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
    private val binaryFiles = Regex("^Binary files .* differ\r?$", RegexOption.MULTILINE)

    /**
     * Parses one file's unified patch (Dart `KitDiffFile.fromPatch`, kit_diff_model.dart:47-137). `@@` headers become
     * [DiffLineKind.Hunk] lines; the lines skipped before and between hunks become count-only gaps; `diff --git`, `---`,
     * `+++` and other preamble lines before the first hunk are dropped; `\ No newline at end of file` marks the line
     * above. A header that does not parse adds no hunk line, but its lines still count.
     */
    public fun parseFilePatch(
        path: String,
        patch: String,
        status: DiffFileStatus? = null,
        oldPath: String? = null,
    ): DiffFile {
        if (isBinary(patch)) {
            return DiffFile(path, persistentListOf(), 0, 0, status ?: DiffFileStatus.Modified, oldPath, binary = true)
        }
        val segments = ArrayList<DiffSegment>()
        var oldNo = 0
        var newNo = 0
        var inHunk = false
        var added = 0
        var removed = 0
        for (raw in patch.split('\n')) {
            val line = raw.removeSuffix("\r")
            if (line.startsWith("@@")) {
                val header = parseHeader(line)
                if (header != null) {
                    // kit_diff_model.dart:78: the lines skipped before this hunk, counted on the new side.
                    val skipped = if (inHunk) header.newStart - newNo else header.newStart - 1
                    if (skipped > 0) segments += DiffGap(skipped)
                    oldNo = header.oldStart
                    newNo = header.newStart
                    segments += DiffLine(line, DiffLineKind.Hunk, oldNo = header.oldStart, newNo = header.newStart)
                }
                inHunk = true
                continue
            }
            // `--- a/x` / `+++ b/x` and `diff --git` preambles repeat what the file header already says.
            if (!inHunk) continue
            when {
                line.startsWith('+') -> {
                    added++
                    segments += DiffLine(line.substring(1), DiffLineKind.Added, newNo = newNo++)
                }
                line.startsWith('-') -> {
                    removed++
                    segments += DiffLine(line.substring(1), DiffLineKind.Removed, oldNo = oldNo++)
                }
                line.startsWith('\\') -> {
                    // "\ No newline at end of file": a note about the line above, not a line of either file.
                    val last = segments.lastOrNull()
                    if (last is DiffLine && last.kind != DiffLineKind.Hunk) {
                        segments[segments.lastIndex] = last.copy(noNewlineAtEnd = true)
                    }
                }
                // kit_diff_model.dart:116: an empty line inside a hunk is skipped, not counted.
                line.isEmpty() -> Unit
                else -> {
                    val text = if (line.startsWith(' ')) line.substring(1) else line
                    segments += DiffLine(text, DiffLineKind.Context, oldNo = oldNo++, newNo = newNo++)
                }
            }
        }
        return DiffFile(path, foldContext(segments), added, removed, status ?: DiffFileStatus.Modified, oldPath)
    }

    /**
     * Splits a patch that may hold several files (`diff --git` blocks, or plain `---`/`+++` pairs) and parses each with
     * [parseFilePatch]. A file's path comes from `+++` (or `---` when the file was deleted, or `rename to`, or the
     * `diff --git` line), with git's `a/` and `b/` prefixes removed; text before the first file is dropped. Hunk line
     * counts decide where a hunk ends, so a removed line that reads `--- x` is not taken for a new file. A patch with
     * hunks but no file header is one file at [defaultPath].
     */
    public fun parsePatch(patch: String, defaultPath: String = ""): ImmutableList<DiffFile> {
        if (patch.isEmpty()) return persistentListOf()
        val lines = patch.split('\n')
        val starts = ArrayList<Int>()
        starts += 0
        var oldLeft = 0
        var newLeft = 0
        var headerOpen = false
        for (i in lines.indices) {
            val line = lines[i].removeSuffix("\r")
            val inHunk = oldLeft > 0 || newLeft > 0
            if (!inHunk) {
                val opens = line.startsWith("diff --git ") ||
                    (!headerOpen && line.startsWith("--- ") && i + 1 < lines.size && lines[i + 1].startsWith("+++ "))
                if (opens) {
                    if (i > starts.last()) starts += i
                    headerOpen = true
                }
            }
            if (line.startsWith("@@")) {
                val header = parseHeader(line)
                oldLeft = header?.oldCount ?: 0
                newLeft = header?.newCount ?: 0
                headerOpen = false
                continue
            }
            if (!inHunk) continue
            when (line.firstOrNull()) {
                '+' -> newLeft--
                '-' -> oldLeft--
                '\\' -> Unit
                else -> { oldLeft--; newLeft-- } // context, or a blank context line whose space was trimmed
            }
        }
        val out = ArrayList<DiffFile>()
        for ((n, start) in starts.withIndex()) {
            val end = if (n + 1 < starts.size) starts[n + 1] else lines.size
            chunkFile(lines.subList(start, end), defaultPath)?.let { out += it }
        }
        return out.toImmutableList()
    }

    /**
     * Diffs a before/after pair (Dart `KitDiffFile.fromTexts`, kit_diff_model.dart:140-174). A null or empty [before]
     * with an [after] is an added file; a null [after] with a [before] is a deleted one; [status] overrides both. Lines
     * are compared as written (a line-ending change still shows), and shown without a trailing carriage return.
     */
    public fun fromBeforeAfter(
        path: String,
        before: String?,
        after: String?,
        status: DiffFileStatus? = null,
    ): DiffFile {
        val resolved = status ?: when {
            before == null && after != null -> DiffFileStatus.Added
            before == "" && !after.isNullOrEmpty() -> DiffFileStatus.Added
            after == null && before != null -> DiffFileStatus.Deleted
            else -> DiffFileStatus.Modified
        }
        val a = if (before.isNullOrEmpty()) emptyList() else splitLines(before)
        val b = if (after.isNullOrEmpty()) emptyList() else splitLines(after)
        val lines = lineDiff(a, b)
        val added = lines.count { it.kind == DiffLineKind.Added }
        val removed = lines.count { it.kind == DiffLineKind.Removed }
        return DiffFile(path, foldContext(lines), added, removed, resolved)
    }

    /**
     * Folds long runs of context into gaps, keeping [VISIBLE_CONTEXT] lines next to each change so the reader still sees
     * where they are (kit_diff_model.dart:299-341). A run at the start or end keeps nothing on its outer side; a run
     * that would hide a single line is kept whole. Gaps and hunk lines end a run like a change does, but only a change
     * keeps context around itself.
     */
    public fun foldContext(segments: List<DiffSegment>): ImmutableList<DiffSegment> {
        val out = ArrayList<DiffSegment>(segments.size)
        var run = ArrayList<DiffLine>()
        fun flush(atStart: Boolean, atEnd: Boolean) {
            if (run.isEmpty()) return
            val keepTop = if (atStart) 0 else VISIBLE_CONTEXT
            val keepBottom = if (atEnd) 0 else VISIBLE_CONTEXT
            if (run.size <= keepTop + keepBottom + 1) {
                out += run
            } else {
                out += run.subList(0, keepTop)
                out += DiffGap(run.size - keepTop - keepBottom, run.subList(keepTop, run.size - keepBottom).toImmutableList())
                out += run.subList(run.size - keepBottom, run.size)
            }
            run = ArrayList()
        }

        var seenChange = false
        for (segment in segments) {
            if (segment is DiffLine && segment.kind == DiffLineKind.Context) {
                run += segment
                continue
            }
            val isChange = segment is DiffLine && (segment.kind == DiffLineKind.Added || segment.kind == DiffLineKind.Removed)
            flush(atStart = !seenChange, atEnd = !isChange)
            if (isChange) seenChange = true
            out += segment
        }
        flush(atStart = !seenChange, atEnd = true)
        return out.toImmutableList()
    }

    /**
     * The unified rows a view draws for [file] (Dart `_itemsFor(split: false)`, kit_diff_model.dart:472), cut at
     * [maxLines] lines when given (kit_diff_chrome.dart:824-852: only lines count against the cap; the first line past
     * it ends the rows). A hunk header right after a gap is left out: the fold line already says where the hunk starts
     * (kit_diff_rows.dart:61-65).
     */
    public fun displayRows(file: DiffFile, maxLines: Int? = null): DiffRows {
        val segments = file.segments
        val rows = ArrayList<DiffRow>(segments.size)
        var shown = 0
        var cut = false
        for ((i, segment) in segments.withIndex()) {
            when (segment) {
                is DiffGap -> {
                    val position = when (i) {
                        0 -> DiffGapPosition.Above
                        segments.lastIndex -> DiffGapPosition.Below
                        else -> DiffGapPosition.Between
                    }
                    rows += DiffRow.Gap(segment.count, position, segment.lines, i)
                }
                is DiffLine -> {
                    if (segment.kind == DiffLineKind.Hunk) {
                        if (i > 0 && segments[i - 1] is DiffGap) continue
                        rows += hunkRow(segment, i)
                        continue
                    }
                    if (maxLines != null && shown + 1 > maxLines) {
                        cut = true
                        break
                    }
                    shown++
                    rows += DiffRow.Line(segment, i)
                }
            }
        }
        return DiffRows(rows.toImmutableList(), shown, file.totalLines, cut)
    }

    private fun hunkRow(line: DiffLine, segment: Int): DiffRow.HunkHeader {
        val header = parseHeader(line.text)
        val oldStart = header?.oldStart ?: line.oldNo ?: 0
        val newStart = header?.newStart ?: line.newNo ?: 0
        val oldCount = header?.oldCount ?: 1
        val newCount = header?.newCount ?: 1
        // kit_diff_rows.dart:76: a range of at least one line.
        return DiffRow.HunkHeader(
            oldStart = oldStart,
            oldEnd = oldStart + maxOf(oldCount, 1) - 1,
            newStart = newStart,
            newEnd = newStart + maxOf(newCount, 1) - 1,
            segment = segment,
            newCount = newCount,
        )
    }

    private class Header(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int)

    /** The `@@ -a,b +c,d @@` numbers (absent counts are 1), or null when it does not parse or overflows. */
    private fun parseHeader(line: String): Header? {
        val match = hunkHeader.find(line) ?: return null
        val g = match.groups
        val oldStart = g[1]?.value?.toIntOrNull() ?: return null
        val oldCount = g[2]?.value?.let { it.toIntOrNull() ?: return null } ?: 1
        val newStart = g[3]?.value?.toIntOrNull() ?: return null
        val newCount = g[4]?.value?.let { it.toIntOrNull() ?: return null } ?: 1
        return Header(oldStart, oldCount, newStart, newCount)
    }

    /** kit_diff_model.dart:53: a git binary patch or a "Binary files … differ" line. */
    private fun isBinary(patch: String): Boolean = patch.contains("GIT binary patch") || binaryFiles.containsMatchIn(patch)

    /** One file of [parsePatch], or null for text that holds neither a file header nor a hunk. */
    private fun chunkFile(chunk: List<String>, defaultPath: String): DiffFile? {
        var git: Pair<String?, String?>? = null
        var minus: String? = null
        var plus: String? = null
        var renameFrom: String? = null
        var renameTo: String? = null
        var newFile = false
        var deletedFile = false
        var hasHunk = false
        for (raw in chunk) {
            val line = raw.removeSuffix("\r")
            if (line.startsWith("@@")) {
                hasHunk = true
                break
            }
            when {
                line.startsWith("diff --git") -> git = gitPaths(line.removePrefix("diff --git").trimStart())
                line.startsWith("--- ") && minus == null -> minus = headerPath(line.substring(4))
                line.startsWith("+++ ") && plus == null -> plus = headerPath(line.substring(4))
                line.startsWith("rename from ") -> renameFrom = line.substring("rename from ".length)
                line.startsWith("rename to ") -> renameTo = line.substring("rename to ".length)
                line.startsWith("new file mode") -> newFile = true
                line.startsWith("deleted file mode") -> deletedFile = true
            }
        }
        if (!hasHunk && git == null && minus == null && plus == null) return null
        val gitPrefixed = git != null ||
            ((minus == NULL_PATH || minus?.startsWith("a/") == true) && (plus == NULL_PATH || plus?.startsWith("b/") == true))
        val oldSide = minus?.takeIf { it != NULL_PATH }?.let { if (gitPrefixed) it.removePrefix("a/") else it }
        val newSide = plus?.takeIf { it != NULL_PATH }?.let { if (gitPrefixed) it.removePrefix("b/") else it }
        val status = when {
            renameTo != null -> DiffFileStatus.Renamed
            minus == NULL_PATH || newFile -> DiffFileStatus.Added
            plus == NULL_PATH || deletedFile -> DiffFileStatus.Deleted
            else -> DiffFileStatus.Modified
        }
        val path = newSide ?: renameTo ?: oldSide ?: git?.second ?: git?.first ?: defaultPath
        val oldPath = if (status == DiffFileStatus.Renamed) renameFrom ?: oldSide ?: git?.first else null
        return parseFilePatch(path, chunk.joinToString("\n"), status, oldPath)
    }

    /** `a/x b/y` → (x, y); null halves when the line does not have that shape. */
    private fun gitPaths(rest: String): Pair<String?, String?> {
        val split = rest.lastIndexOf(" b/")
        if (!rest.startsWith("a/") || split < 2) return null to null
        return rest.substring(2, split) to rest.substring(split + 3)
    }

    /** A `---`/`+++` path without its tab-separated timestamp. */
    private fun headerPath(raw: String): String = raw.substringBefore('\t').trimEnd()

    private const val NULL_PATH = "/dev/null"

    /** kit_diff_model.dart:202: one trailing newline ends the text, not an empty last line. */
    private fun splitLines(text: String): List<String> = text.removeSuffix("\n").split('\n')

    /**
     * A line diff of [a] against [b] (kit_diff_model.dart:212-295): common prefix and suffix, then a longest common
     * subsequence of the middle, or a single removed-then-added block when the middle is over [LCS_CELL_LIMIT] cells.
     * Within a change its removed lines come before its added lines.
     */
    private fun lineDiff(a: List<String>, b: List<String>): List<DiffLine> {
        fun shown(s: String) = s.removeSuffix("\r")
        var p = 0
        while (p < a.size && p < b.size && a[p] == b[p]) p++
        var s = 0
        while (s < a.size - p && s < b.size - p && a[a.size - 1 - s] == b[b.size - 1 - s]) s++
        val out = ArrayList<DiffLine>(a.size + b.size)
        for (i in 0 until p) out += DiffLine(shown(a[i]), DiffLineKind.Context, oldNo = i + 1, newNo = i + 1)
        val n = a.size - p - s
        val m = b.size - p - s
        if (n > 0 && m > 0 && (n + 1).toLong() * (m + 1).toLong() <= LCS_CELL_LIMIT) {
            // table[i * width + j]: LCS length of a[p+i..] and b[p+j..].
            val width = m + 1
            val table = IntArray((n + 1) * width)
            for (i in n - 1 downTo 0) {
                for (j in m - 1 downTo 0) {
                    table[i * width + j] = if (a[p + i] == b[p + j]) {
                        table[(i + 1) * width + j + 1] + 1
                    } else {
                        maxOf(table[(i + 1) * width + j], table[i * width + j + 1])
                    }
                }
            }
            var i = 0
            var j = 0
            val removed = ArrayList<DiffLine>()
            val added = ArrayList<DiffLine>()
            fun flush() {
                out += removed
                out += added
                removed.clear()
                added.clear()
            }
            while (i < n || j < m) {
                if (i < n && j < m && a[p + i] == b[p + j]) {
                    flush()
                    out += DiffLine(shown(a[p + i]), DiffLineKind.Context, oldNo = p + i + 1, newNo = p + j + 1)
                    i++
                    j++
                } else if (j < m && (i >= n || table[i * width + j + 1] >= table[(i + 1) * width + j])) {
                    added += DiffLine(shown(b[p + j]), DiffLineKind.Added, newNo = p + j + 1)
                    j++
                } else {
                    removed += DiffLine(shown(a[p + i]), DiffLineKind.Removed, oldNo = p + i + 1)
                    i++
                }
            }
            flush()
        } else {
            for (i in p until a.size - s) out += DiffLine(shown(a[i]), DiffLineKind.Removed, oldNo = i + 1)
            for (j in p until b.size - s) out += DiffLine(shown(b[j]), DiffLineKind.Added, newNo = j + 1)
        }
        for (k in 0 until s) {
            val ai = a.size - s + k
            val bi = b.size - s + k
            out += DiffLine(shown(a[ai]), DiffLineKind.Context, oldNo = ai + 1, newNo = bi + 1)
        }
        return out
    }
}

/** Derived facts about one file (Dart `_FileIndex`, kit_diff_model.dart:344-408), computed once per [DiffFile]. */
private class FileIndex(segments: List<DiffSegment>) {
    val changes: ImmutableList<Int>
    val hunks: ImmutableList<DiffHunk>
    var total: Int = 0
        private set
    var maxNumber: Int = 1
        private set

    init {
        val changeStarts = ArrayList<Int>()
        var inRun = false
        fun number(line: DiffLine) {
            line.oldNo?.let { if (it > maxNumber) maxNumber = it }
            line.newNo?.let { if (it > maxNumber) maxNumber = it }
        }
        for ((i, segment) in segments.withIndex()) {
            when (segment) {
                is DiffLine -> {
                    if (segment.kind != DiffLineKind.Hunk) total++
                    number(segment)
                    val change = segment.kind == DiffLineKind.Added || segment.kind == DiffLineKind.Removed
                    if (change && !inRun) changeStarts += i
                    inRun = change
                }
                is DiffGap -> {
                    inRun = false
                    total += segment.count
                    segment.lines?.forEach(::number)
                }
            }
        }
        changes = changeStarts.toImmutableList()

        val found = ArrayList<DiffHunk>()
        var open: HunkBuilder? = null
        fun close() {
            open?.takeIf { it.hasChange }?.let { found += it.build() }
            open = null
        }
        for ((i, segment) in segments.withIndex()) {
            when (segment) {
                is DiffGap -> close()
                is DiffLine -> if (segment.kind == DiffLineKind.Hunk) {
                    close()
                    open = HunkBuilder(i)
                } else {
                    (open ?: HunkBuilder(null).also { open = it }).add(segment)
                }
            }
        }
        close()
        hunks = found.toImmutableList()
    }
}

/** kit_diff_model.dart:411-441: one hunk's line ranges on each side. */
private class HunkBuilder(val header: Int?) {
    var oldLo: Int? = null
    var oldHi: Int? = null
    var newLo: Int? = null
    var newHi: Int? = null
    var hasChange = false

    fun add(line: DiffLine) {
        val old = if (line.kind == DiffLineKind.Added) null else line.oldNo
        val current = if (line.kind == DiffLineKind.Removed) null else line.newNo
        if (old != null) {
            oldLo = minOf(oldLo ?: old, old)
            oldHi = maxOf(oldHi ?: old, old)
        }
        if (current != null) {
            newLo = minOf(newLo ?: current, current)
            newHi = maxOf(newHi ?: current, current)
        }
        if (line.kind == DiffLineKind.Added || line.kind == DiffLineKind.Removed) hasChange = true
    }

    fun build(): DiffHunk = DiffHunk(oldLo, oldHi, newLo, newHi, header)
}
