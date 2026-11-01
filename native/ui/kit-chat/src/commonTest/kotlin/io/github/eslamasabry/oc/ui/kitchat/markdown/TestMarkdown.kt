package io.github.eslamasabry.oc.ui.kitchat.markdown

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * TEST-ONLY stand-in for `:core:markdown` `StreamingMarkdown` (plan 09 §3.8 / §9.1), which S2-MD builds on the
 * JetBrains parser and which kit-chat may not depend on yet (the module does not exist on this branch, and only
 * `:core:markdown` may declare `org.jetbrains:markdown`). It keeps the contract the renderers rely on, the same one as
 * P3's `StreamingBlocks`: settled blocks keep their key **and instance** forever; only the tail is rebuilt per append;
 * the tail's key is the key it settles with. The Markdown it understands is the subset the fixtures use.
 */
class TestStreamingMarkdown(private val part: String = "p") {
    private val text = StringBuilder()
    private val settled = ArrayList<KitMdBlock>()
    private var blockStart = 0
    private var lineStart = 0
    private var inFence = false
    private var tail: KitMdBlock? = null
    private var tailDirty = true

    /** Tail rebuilds (the unstable block re-parsed after an append). */
    var tailBuilds: Int = 0
        private set

    val source: String get() = text.toString()

    fun append(more: String) {
        if (more.isEmpty()) return
        text.append(more)
        while (true) {
            val nl = text.indexOf('\n', lineStart)
            if (nl < 0) break
            onLine(lineStart, nl)
            lineStart = nl + 1
        }
        tailDirty = true
    }

    /** Settled blocks then the tail, if any. Settled entries are the same instances on every call. */
    val blocks: ImmutableList<KitMdBlock>
        get() {
            if (tailDirty) {
                tailDirty = false
                val raw = text.substring(blockStart)
                tail =
                    if (raw.isBlank()) {
                        null
                    } else {
                        tailBuilds++
                        build(key(settled.size), raw.trimEnd('\n'), open = inFence)
                    }
            }
            return (settled + listOfNotNull(tail)).toImmutableList()
        }

    private fun key(i: Int) = "$part:$i"

    private fun line(ls: Int, nl: Int) = text.substring(ls, nl)

    private fun onLine(ls: Int, nl: Int) {
        val l = line(ls, nl)
        val t = l.trimStart()
        if (inFence) {
            if (t.startsWith("```")) {
                emit(blockStart, nl + 1, open = false)
                blockStart = nl + 1
                inFence = false
            }
            return
        }
        val pending = if (blockStart < ls) text.substring(blockStart, ls) else ""
        when {
            t.startsWith("```") -> {
                flush(ls)
                blockStart = ls
                inFence = true
            }
            t.isEmpty() -> {
                flush(ls)
                blockStart = nl + 1
            }
            t.startsWith("#") || isRule(t) -> {
                flush(ls)
                emit(ls, nl + 1, open = false)
                blockStart = nl + 1
            }
            isListItem(l) -> {
                flush(ls)
                blockStart = ls
            }
            pending.trimStart().startsWith("|") && !t.startsWith("|") -> {
                flush(ls)
                blockStart = ls
            }
        }
    }

    private fun flush(upTo: Int) {
        if (blockStart < upTo && text.substring(blockStart, upTo).isNotBlank()) emit(blockStart, upTo, open = false)
    }

    private fun emit(start: Int, end: Int, open: Boolean) {
        settled += build(key(settled.size), text.substring(start, end).trimEnd('\n'), open)
    }

    companion object {
        private val listItem = Regex("""^(\s*)([-*+]|(\d+)[.)])\s+(\[[ xX]]\s+)?(.*)$""")

        fun isListItem(l: String): Boolean = listItem.matches(l)

        fun isRule(t: String): Boolean = t.length >= 3 && (t.all { it == '-' } || t.all { it == '*' } || t.all { it == '_' })

        /** Parses a whole text at once (goldens and scenes). */
        fun parse(source: String, part: String = "p"): ImmutableList<KitMdBlock> =
            TestStreamingMarkdown(part).apply { append(source + "\n") }.blocks

        fun build(key: String, raw: String, open: Boolean): KitMdBlock {
            val first = raw.lineSequence().first()
            val t = first.trimStart()
            return when {
                t.startsWith("```") -> {
                    val body = raw.lines().drop(1).let { if (!open && it.isNotEmpty()) it.dropLast(1) else it }
                    KitMdBlock.Code(key, t.removePrefix("```").trim().ifEmpty { null }, body.joinToString("\n"), open)
                }
                t.startsWith("#") -> {
                    val level = t.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    KitMdBlock.Heading(key, level, parseInlines(t.drop(level).trim()))
                }
                isRule(t) -> KitMdBlock.Rule(key)
                t.startsWith(">") -> {
                    val inner = raw.lines().joinToString(" ") { it.trimStart().removePrefix(">").trim() }
                    KitMdBlock.Quote(key, persistentListOf(KitMdBlock.Prose("$key:0", parseInlines(inner))))
                }
                t.startsWith("|") && raw.lines().size >= 2 -> table(key, raw.lines())
                isListItem(first) -> {
                    val m = listItem.matchEntire(first)!!
                    val rest = raw.lines().drop(1).joinToString(" ") { it.trim() }
                    val box = m.groupValues[4].trim()
                    KitMdBlock.ListItem(
                        key = key,
                        ordered = m.groupValues[3].isNotEmpty(),
                        number = m.groupValues[3].toIntOrNull(),
                        depth = m.groupValues[1].length / 2,
                        inlines = parseInlines((m.groupValues[5] + " " + rest).trim()),
                        checked = if (box.isEmpty()) null else box.contains('x', ignoreCase = true),
                    )
                }
                else -> KitMdBlock.Prose(key, parseInlines(raw.lines().joinToString(" ") { it.trim() }))
            }
        }

        private fun cells(row: String) = row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

        private fun table(key: String, lines: List<String>): KitMdBlock {
            val aligns =
                cells(lines[1]).map {
                    when {
                        it.startsWith(":") && it.endsWith(":") -> KitAlign.Center
                        it.endsWith(":") -> KitAlign.End
                        else -> KitAlign.Start
                    }
                }
            return KitMdBlock.Table(
                key,
                cells(lines[0]).map(::parseInlines).toImmutableList(),
                aligns.toImmutableList(),
                lines.drop(2).filter { it.isNotBlank() }.map { r -> cells(r).map(::parseInlines).toImmutableList() }.toImmutableList(),
            )
        }

        /** Path rule of B-CHAT-064 (the real one is `:core:markdown` `PathChipScanner`). */
        fun looksLikePath(s: String): Boolean {
            if (s.length !in 4..300 || s.any { it.isWhitespace() } || "://" in s) return false
            val bare = s.replace(Regex(""":\d+$"""), "")
            val anchored = bare.startsWith("/") || bare.startsWith("~/") || bare.startsWith("./")
            val ext = bare.substringAfterLast('/').substringAfterLast('.', "")
            return '/' in bare && (anchored || ext.length in 1..9)
        }

        /** `code`, **bold**, *italic*, ~~strike~~ and [text](url); inline code that looks like a path becomes a path. */
        fun parseInlines(src: String): KitInlines {
            val out = StringBuilder()
            val spans = ArrayList<KitSpan>()
            fun nested(inner: String, kind: KitSpanKind) {
                val sub = parseInlines(inner)
                val at = out.length
                out.append(sub.text)
                sub.spans.forEach { spans += it.copy(start = it.start + at, end = it.end + at) }
                spans += KitSpan(at, out.length, kind)
            }
            var i = 0
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '`' && src.indexOf('`', i + 1) > i -> {
                        val j = src.indexOf('`', i + 1)
                        val code = src.substring(i + 1, j)
                        val at = out.length
                        out.append(code)
                        val kind =
                            if (looksLikePath(code)) {
                                val line = Regex(""":(\d+)$""").find(code)?.groupValues?.get(1)?.toIntOrNull()
                                KitSpanKind.Path(code.replace(Regex(""":\d+$"""), ""), line)
                            } else {
                                KitSpanKind.Code
                            }
                        spans += KitSpan(at, out.length, kind)
                        i = j + 1
                    }
                    src.startsWith("**", i) && src.indexOf("**", i + 2) > i + 2 -> {
                        val j = src.indexOf("**", i + 2)
                        nested(src.substring(i + 2, j), KitSpanKind.Bold)
                        i = j + 2
                    }
                    src.startsWith("~~", i) && src.indexOf("~~", i + 2) > i + 2 -> {
                        val j = src.indexOf("~~", i + 2)
                        nested(src.substring(i + 2, j), KitSpanKind.Strike)
                        i = j + 2
                    }
                    (c == '*' || c == '_') && i + 1 < src.length && !src[i + 1].isWhitespace() && src.indexOf(c, i + 1) > i + 1 -> {
                        val j = src.indexOf(c, i + 1)
                        nested(src.substring(i + 1, j), KitSpanKind.Italic)
                        i = j + 1
                    }
                    c == '[' && src.indexOf("](", i) > i && src.indexOf(')', src.indexOf("](", i)) > 0 -> {
                        val mid = src.indexOf("](", i)
                        val end = src.indexOf(')', mid)
                        nested(src.substring(i + 1, mid), KitSpanKind.Link(src.substring(mid + 2, end)))
                        i = end + 1
                    }
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
            return KitInlines(out.toString(), spans.toImmutableList())
        }
    }
}
