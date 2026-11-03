package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/*
 * The kit-local Markdown render model (plan 09 §0.4 C5: kit-chat parts take kit-local UI models). It mirrors
 * `:core:markdown`'s `Block` / `Inlines` / `InlineSpan` (plan 09 §9.1) one to one, so the mapping in `:feature:chat`
 * is a field copy. The parser (JetBrains `org.jetbrains:markdown` `StreamingMarkdownFile`, GFM) lives in
 * `:core:markdown` (S2-MD); the build forbids it anywhere else (ExternalRules). Keys come from the parser and are
 * stable while a reply streams: a settled block keeps its key and its instance forever, only the tail changes.
 */

/** Text with styled ranges. Offsets are UTF-16 indices into [text]; the renderer clamps anything out of range. */
@Immutable
public data class KitInlines(val text: String, val spans: ImmutableList<KitSpan> = persistentListOf())

/** One styled range `[start, end)` of a [KitInlines]. Spans may nest (bold inside a link); order does not matter. */
@Immutable
public data class KitSpan(val start: Int, val end: Int, val kind: KitSpanKind)

/** What a span does. Links never open on their own: the renderer hands the address to the caller (R17). */
@Immutable
public sealed interface KitSpanKind {
    public data object Bold : KitSpanKind

    public data object Italic : KitSpanKind

    public data object Strike : KitSpanKind

    /** Inline code: mono, isolated from the paragraph's direction (FSI…PDI). */
    public data object Code : KitSpanKind

    /** A link; [url] goes to `onLink`, never to the platform. */
    public data class Link(val url: String) : KitSpanKind

    /** Text that looks like a file path (B-CHAT-064); a chip, and a link only once the caller confirmed the file. */
    public data class Path(val path: String, val line: Int?) : KitSpanKind
}

/** Column alignment of a table. */
public enum class KitAlign { Start, Center, End }

/** One top-level block of a reply (plan 09 §9.1 `Block`). [key] is the parser's stable key. */
@Immutable
public sealed interface KitMdBlock {
    public val key: String

    public data class Prose(override val key: String, val inlines: KitInlines) : KitMdBlock

    /** h1–h2 take the headline role, h3–h6 the row-title role. */
    public data class Heading(override val key: String, val level: Int, val inlines: KitInlines) : KitMdBlock

    /** A quote draws a visible rail beside its children. */
    public data class Quote(override val key: String, val children: ImmutableList<KitMdBlock>) : KitMdBlock

    /** [number] is the item's own number for ordered lists (lists start at their first item's number). */
    public data class ListItem(
        override val key: String,
        val ordered: Boolean,
        val number: Int?,
        val depth: Int,
        val inlines: KitInlines,
        val checked: Boolean? = null,
    ) : KitMdBlock

    /** A fenced block. [open] while the closing fence has not arrived: drawn plain, never coloured. */
    public data class Code(override val key: String, val info: String?, val text: String, val open: Boolean) : KitMdBlock

    public data class Table(
        override val key: String,
        val header: ImmutableList<KitInlines>,
        val aligns: ImmutableList<KitAlign>,
        val rows: ImmutableList<ImmutableList<KitInlines>>,
    ) : KitMdBlock

    public data class Rule(override val key: String) : KitMdBlock

    /** P12-CHAT-FEEL: a line of only pictures (B-CHAT-059), drawn as tiles the person taps to open. */
    public data class Pictures(override val key: String, val pictures: ImmutableList<KitPicture>) : KitMdBlock
}

/**
 * One Markdown picture (B-CHAT-059). [url] is set for a web picture (never fetched: a tap goes through the link
 * policy), [path] for a file on the server (the chat may read it for its viewer). [label] is what names it: the
 * description, else the file name, else null (the caller's own word).
 */
@Immutable
public data class KitPicture(val label: String?, val url: String? = null, val path: String? = null, val host: String? = null)

/** Whether a block ends in text a caret can follow (a reply ending in code, a table or a rule shows no caret). */
public val KitMdBlock.takesCaret: Boolean
    get() = this is KitMdBlock.Prose || this is KitMdBlock.Heading || this is KitMdBlock.ListItem ||
        (this is KitMdBlock.Quote && children.lastOrNull()?.takesCaret == true)
