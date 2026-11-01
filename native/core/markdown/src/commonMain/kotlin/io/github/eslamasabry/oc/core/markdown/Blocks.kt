package io.github.eslamasabry.oc.core.markdown

import kotlinx.collections.immutable.ImmutableList

/**
 * One block of an agent's Markdown, as the transcript list draws it (plan 09 §9.1). Blocks are values: a block that
 * did not change between two parses compares equal, and [StreamingMarkdown] hands back the very same instance.
 *
 * [start] and [end] are offsets into the part's raw text (the text as received, CR/LF kept): [start] is the first
 * character of the block's first line, [end] the end of its last line (before its line break). [key] is
 * `<partId>:<start>`, unique within a part and stable while the part grows.
 */
public sealed interface Block {
    public val key: String
    public val start: Int
    public val end: Int

    /** A paragraph: one or more lines of prose, joined by `\n` in [inlines]. */
    public data class Prose(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val inlines: Inlines,
    ) : Block

    /** `#`…`######`. Levels 1–2 use the headline role, 3–6 the row-title role. */
    public data class Heading(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val level: Int,
        val inlines: Inlines,
    ) : Block

    /**
     * Consecutive `>` lines, drawn with a visible rail in the secondary tone. Flutter draws a quote as one run of
     * text (no nested blocks), so this holds [inlines], not child blocks (deviation from the plan sketch, see the
     * S2-MD report).
     */
    public data class Quote(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val inlines: Inlines,
    ) : Block

    /**
     * One item of a list. [number] is the ordered marker to draw (`null` for a bullet): the top level counts on
     * from the list's first number, a nested run restarts at 1. [depth] is 0–4, normalised by the list's smallest
     * indent step so 2- and 4-space nesting both land one level deeper. [firstInList] marks the item that sits on
     * the list's own top edge (no item gap above it).
     */
    public data class ListItem(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val ordered: Boolean,
        val number: Int?,
        val depth: Int,
        val firstInList: Boolean,
        val inlines: Inlines,
    ) : Block

    /**
     * A fenced block. [info] is the first word of the info string (the language), [text] the body without the
     * fence's indent, [copyText] the body exactly as written (CR/LF and indent kept, a trailing line break when the
     * fence closed). An [open] fence is still streaming: it is never highlighted.
     */
    public data class Code(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val info: String?,
        val text: String,
        val copyText: String,
        val open: Boolean,
        val kind: CodeKind,
    ) : Block

    /** A GitHub table. Rows keep the cell count they were written with; the renderer pads short rows. */
    public data class Table(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val header: ImmutableList<Inlines>,
        val aligns: ImmutableList<Align>,
        val rows: ImmutableList<ImmutableList<Inlines>>,
    ) : Block

    /** Lines that hold only pictures (blank lines between them included), deduplicated by where each lives. */
    public data class PictureGroup(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val pictures: ImmutableList<MarkdownPicture>,
    ) : Block

    /** A **closed** ```` ```choices ````, ```` ```checklist ```` or ```` ```command ```` fence. */
    public data class AgentBlock(
        override val key: String,
        override val start: Int,
        override val end: Int,
        val type: AgentBlockType,
        val items: ImmutableList<AgentItem>,
    ) : Block

    /** `---`, `***` or `___`. */
    public data class Rule(
        override val key: String,
        override val start: Int,
        override val end: Int,
    ) : Block
}

/** Where a code block came from. */
public enum class CodeKind {
    /** A fence inside the Markdown. */
    Fence,

    /** The whole part is code in one language ([MarkdownBlocks.wholeCode]). */
    WholePart,
}

/** A table column's alignment, from its delimiter cell (`:---`, `:---:`, `---:`). */
public enum class Align { None, Start, Center, End }

/**
 * A run of inline Markdown with its marks removed. [text] is what is drawn (a link is its label; a picture inside a
 * sentence is one U+FFFC placeholder the renderer replaces with its tile); [spans] mark ranges of [text]. Spans nest
 * and may overlap (`***x***` gives a Bold and an Italic span over the same range). Lines are joined with `\n`;
 * no span crosses a line break.
 */
public data class Inlines(val text: String, val spans: ImmutableList<InlineSpan>)

/** [start] inclusive, [end] exclusive, in [Inlines.text]. */
public data class InlineSpan(val start: Int, val end: Int, val kind: SpanKind)

public sealed interface SpanKind {
    public data object Bold : SpanKind

    public data object Italic : SpanKind

    public data object Strike : SpanKind

    /** Inline code: mono, LTR-isolated. */
    public data object Code : SpanKind

    /** A `[label](url)` or a bare http(s) address. Opened only through the link policy, never directly. */
    public data class Link(val url: String) : SpanKind

    /**
     * Inline code that looks like a file path ([PathChipScanner.looksLikeFilePath]); always over the same range as a
     * [Code] span. It becomes a link only after the engine confirms the file exists. [path] has any trailing
     * `:line` removed; [line] is that number.
     */
    public data class PathCandidate(val path: String, val line: Int?) : SpanKind

    /** A picture inside a sentence: covers one U+FFFC placeholder. */
    public data class Picture(val picture: MarkdownPicture) : SpanKind
}

/** The three agent block fences (`AgentBlockKinds` in Flutter). */
public enum class AgentBlockType(public val info: String) {
    Choices("choices"),
    Checklist("checklist"),
    Command("command"),
    ;

    public companion object {
        /** The type an info string claims (trimmed, any case), or null. */
        public fun of(info: String?): AgentBlockType? {
            val kind = info?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.info == kind }
        }
    }
}

/**
 * One line of an agent block. [done] is set only for a checklist item (`[x]` true, `[ ]` or a bare line false);
 * choices and commands leave it null.
 */
public data class AgentItem(val text: String, val done: Boolean? = null)

/** What a streaming update changed: [stable] blocks are the very instances published before; [tail] are new. */
public data class BlockDelta(val stable: ImmutableList<Block>, val tail: ImmutableList<Block>) {
    public val all: List<Block> get() = stable + tail
}

/**
 * Whether the streaming caret may sit after this block's last words (Flutter `_withCaret`). A reply that ends in
 * code, a table, pictures, an agent block or a rule shows no caret: it would make a line of its own.
 */
public val Block.carriesCaret: Boolean
    get() = this is Block.Prose || this is Block.Heading || this is Block.Quote || this is Block.ListItem
