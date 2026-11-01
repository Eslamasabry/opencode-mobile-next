package io.github.eslamasabry.oc.tools.strings

/**
 * The ICU MessageFormat subset that Flutter gen-l10n accepts with `use-escaping: false` (this repo's l10n.yaml):
 * `{name}`, `{name, plural, sel{…} …}` and `{name, select, sel{…} …}`. Apostrophes are literal text; there is no
 * quoting. Anything else is a parse error.
 */
sealed interface IcuNode {
    data class Text(val text: String) : IcuNode
    data class Arg(val name: String) : IcuNode
    data class Plural(val arg: String, val branches: LinkedHashMap<String, List<IcuNode>>) : IcuNode
    data class Select(val arg: String, val branches: LinkedHashMap<String, List<IcuNode>>) : IcuNode
}

class IcuParseException(message: String) : RuntimeException(message)

object Icu {
    fun parse(message: String): List<IcuNode> = Parser(message).parseAll()

    /** Writes [nodes] back as ICU text; `parse(print(n)) == n` for anything [parse] returns. */
    fun print(nodes: List<IcuNode>): String = buildString {
        fun branches(arg: String, kind: String, b: Map<String, List<IcuNode>>) {
            append('{').append(arg).append(", ").append(kind).append(',')
            for ((sel, body) in b) append(' ').append(sel).append('{').append(print(body)).append('}')
            append('}')
        }
        for (n in nodes) when (n) {
            is IcuNode.Text -> append(n.text)
            is IcuNode.Arg -> append('{').append(n.name).append('}')
            is IcuNode.Plural -> branches(n.arg, "plural", n.branches)
            is IcuNode.Select -> branches(n.arg, "select", n.branches)
        }
    }

    /** Names of arguments in order of first appearance (plural/select selector args count where they open). */
    fun argOrder(nodes: List<IcuNode>): List<String> {
        val out = LinkedHashSet<String>()
        fun walk(ns: List<IcuNode>) {
            for (n in ns) when (n) {
                is IcuNode.Text -> Unit
                is IcuNode.Arg -> out += n.name
                is IcuNode.Plural -> { out += n.arg; n.branches.values.forEach(::walk) }
                is IcuNode.Select -> { out += n.arg; n.branches.values.forEach(::walk) }
            }
        }
        walk(nodes)
        return out.toList()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseAll(): List<IcuNode> {
            val nodes = parseNodes(inBranch = false)
            if (i != s.length) fail("unexpected '}'")
            return nodes
        }

        private fun fail(what: String): Nothing = throw IcuParseException("$what at offset $i in: $s")

        private fun parseNodes(inBranch: Boolean): List<IcuNode> {
            val nodes = mutableListOf<IcuNode>()
            val text = StringBuilder()
            fun flush() { if (text.isNotEmpty()) { nodes += IcuNode.Text(text.toString()); text.clear() } }
            while (i < s.length) {
                when (val c = s[i]) {
                    '{' -> { flush(); nodes += parseArgument() }
                    '}' -> {
                        if (!inBranch) fail("unbalanced '}'")
                        flush(); return nodes
                    }
                    else -> { text.append(c); i++ }
                }
            }
            if (inBranch) fail("unterminated branch")
            flush()
            return nodes
        }

        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun identifier(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
            if (start == i) fail("expected an argument name")
            return s.substring(start, i)
        }

        private fun parseArgument(): IcuNode {
            i++ // '{'
            skipWs()
            val name = identifier()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return IcuNode.Arg(name) }
            if (i >= s.length || s[i] != ',') fail("expected ',' or '}' after argument \"$name\"")
            i++
            skipWs()
            val kind = identifier()
            if (kind != "plural" && kind != "select") fail("unsupported argument type \"$kind\"")
            skipWs()
            if (i >= s.length || s[i] != ',') fail("expected ',' after \"$kind\"")
            i++
            val branches = LinkedHashMap<String, List<IcuNode>>()
            while (true) {
                skipWs()
                if (i >= s.length) fail("unterminated $kind")
                if (s[i] == '}') { i++; break }
                val start = i
                while (i < s.length && !s[i].isWhitespace() && s[i] != '{' && s[i] != '}') i++
                val selector = s.substring(start, i)
                if (selector.isEmpty()) fail("expected a selector")
                if (selector.startsWith("offset:")) fail("plural offset is not supported")
                skipWs()
                if (i >= s.length || s[i] != '{') fail("expected '{' after selector \"$selector\"")
                i++
                val body = parseNodes(inBranch = true)
                i++ // '}'
                if (selector in branches) fail("duplicate selector \"$selector\"")
                branches[selector] = body
            }
            if (branches.isEmpty()) fail("$kind without branches")
            return if (kind == "plural") IcuNode.Plural(name, branches) else IcuNode.Select(name, branches)
        }
    }
}
