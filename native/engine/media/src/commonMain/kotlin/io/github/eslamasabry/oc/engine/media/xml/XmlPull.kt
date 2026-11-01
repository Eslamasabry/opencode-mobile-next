package io.github.eslamasabry.oc.engine.media.xml

/**
 * A small, hardened XML pull reader (plan 10 §10.7, M7) for Office parts and the static-SVG check.
 *
 * - A DOCTYPE fails the read (so no external entities and no entity expansion exist at all).
 * - Only the five predefined entities and numeric character references are decoded.
 * - Depth and element count are bounded ([XmlLimits]); exceeding either fails the read.
 * - The document must be well formed: one root, matched end tags, unique attribute names, closed at the end.
 * - Namespaces are resolved (`xmlns` / `xmlns:p`), so a caller can check an element's namespace URI.
 *
 * Failure is [XmlRefused], thrown by [next]; callers turn it into their own `Failure` value or fallback. It never
 * carries document text.
 */
public class XmlPull(private val source: String, private val limits: XmlLimits = XmlLimits.Office) {
    private var at = 0
    private val open = ArrayList<String>()
    private val scopes = ArrayList<Map<String, String>>()
    private var elements = 0
    private var sawRoot = false
    private var rootClosed = false
    private var pendingEnd: XmlEvent.End? = null
    private var done = false

    /** The current element depth (1 inside the root). */
    public val depth: Int get() = open.size

    /** The next event, or null at the end of a well-formed document. Throws [XmlRefused]. */
    public fun next(): XmlEvent? {
        pendingEnd?.let {
            pendingEnd = null
            return it
        }
        if (done) return null
        while (true) {
            if (at >= source.length) {
                if (open.isNotEmpty() || !sawRoot) refuse(XmlProblem.Malformed)
                done = true
                return null
            }
            val c = source[at]
            if (c == '<') {
                val event = markup() ?: continue
                return event
            }
            val text = text()
            if (open.isEmpty()) {
                if (text.any { !it.isXmlSpace() }) refuse(XmlProblem.Malformed)
                continue
            }
            return XmlEvent.Text(text)
        }
    }

    private fun markup(): XmlEvent? {
        when {
            source.startsWith("<!--", at) -> {
                val end = source.indexOf("-->", at + 4)
                if (end < 0) refuse(XmlProblem.Malformed)
                at = end + 3
                return null
            }
            source.startsWith("<![CDATA[", at) -> {
                if (open.isEmpty()) refuse(XmlProblem.Malformed)
                val end = source.indexOf("]]>", at + 9)
                if (end < 0) refuse(XmlProblem.Malformed)
                val text = source.substring(at + 9, end)
                at = end + 3
                return XmlEvent.Text(text)
            }
            source.startsWith("<!", at) -> refuse(XmlProblem.Declaration) // DOCTYPE, ENTITY, anything else
            source.startsWith("<?", at) -> {
                val end = source.indexOf("?>", at + 2)
                if (end < 0) refuse(XmlProblem.Malformed)
                val body = source.substring(at + 2, end)
                at = end + 2
                val target = body.takeWhile { !it.isXmlSpace() }
                if (target.isEmpty()) refuse(XmlProblem.Malformed)
                return XmlEvent.Instruction(target)
            }
            source.startsWith("</", at) -> return endTag()
            else -> return startTag()
        }
    }

    private fun endTag(): XmlEvent.End {
        at += 2
        val name = name()
        skipSpace()
        expect('>')
        val top = open.removeLastOrNull() ?: refuse(XmlProblem.Malformed)
        if (top != name) refuse(XmlProblem.Malformed)
        val scope = scopes.removeAt(scopes.lastIndex)
        if (open.isEmpty()) rootClosed = true
        return XmlEvent.End(XmlName.of(name, scope))
    }

    private fun startTag(): XmlEvent.Start {
        if (rootClosed) refuse(XmlProblem.Malformed)
        at += 1
        val name = name()
        val raw = ArrayList<Pair<String, String>>()
        while (true) {
            val hadSpace = skipSpace()
            if (at >= source.length) refuse(XmlProblem.Malformed)
            val c = source[at]
            if (c == '>' || c == '/') break
            if (!hadSpace) refuse(XmlProblem.Malformed)
            val attr = name()
            skipSpace()
            expect('=')
            skipSpace()
            val quote = source.getOrNull(at) ?: refuse(XmlProblem.Malformed)
            if (quote != '"' && quote != '\'') refuse(XmlProblem.Malformed)
            val end = source.indexOf(quote, at + 1)
            if (end < 0) refuse(XmlProblem.Malformed)
            val rawValue = source.substring(at + 1, end)
            if ('<' in rawValue) refuse(XmlProblem.Malformed)
            at = end + 1
            if (raw.any { it.first == attr }) refuse(XmlProblem.Malformed)
            raw += attr to decode(rawValue)
        }
        val selfClosing = source[at] == '/'
        if (selfClosing) {
            at++
            expect('>')
        } else {
            expect('>')
        }
        elements++
        if (elements > limits.maxElements) refuse(XmlProblem.TooLarge)
        if (open.size + 1 > limits.maxDepth) refuse(XmlProblem.TooDeep)
        sawRoot = true
        // Namespace scope: inherit, then apply this element's declarations.
        val parent = scopes.lastOrNull() ?: BASE_SCOPE
        var scope = parent
        for ((key, value) in raw) {
            if (key == "xmlns") scope = scope + ("" to value)
            else if (key.startsWith("xmlns:")) scope = scope + (key.substring(6) to value)
        }
        val element = XmlName.of(name, scope)
        if (element.prefix != null && element.namespace == null) refuse(XmlProblem.Malformed)
        val attributes =
            raw.map { (key, value) ->
                val prefix = key.substringBefore(':', "").ifEmpty { null }
                val local = if (prefix == null) key else key.substringAfter(':')
                // Unprefixed attributes have no namespace (XML Namespaces §6.2).
                val ns = if (prefix == null || prefix == "xmlns") null else scope[prefix] ?: refuse(XmlProblem.Malformed)
                XmlAttribute(XmlName(key, prefix, local, ns), value)
            }
        val start = XmlEvent.Start(element, attributes, selfClosing)
        if (selfClosing) {
            if (open.isEmpty()) rootClosed = true
            pendingEnd = XmlEvent.End(element)
        } else {
            open += name
            scopes += scope
        }
        return start
    }

    private fun text(): String {
        val end = source.indexOf('<', at).let { if (it < 0) source.length else it }
        val raw = source.substring(at, end)
        at = end
        return decode(raw)
    }

    private fun decode(raw: String): String {
        if ('&' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val semi = raw.indexOf(';', i + 1)
            if (semi < 0 || semi - i > 12) refuse(XmlProblem.Malformed)
            val entity = raw.substring(i + 1, semi)
            when {
                entity == "lt" -> out.append('<')
                entity == "gt" -> out.append('>')
                entity == "amp" -> out.append('&')
                entity == "apos" -> out.append('\'')
                entity == "quot" -> out.append('"')
                entity.startsWith("#x") -> appendCodePoint(out, entity.substring(2).toIntOrNull(16))
                entity.startsWith("#") -> appendCodePoint(out, entity.substring(1).toIntOrNull(10))
                else -> refuse(XmlProblem.Entity)
            }
            i = semi + 1
        }
        return out.toString()
    }

    private fun appendCodePoint(out: StringBuilder, code: Int?) {
        if (code == null || code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) refuse(XmlProblem.Malformed)
        if (code < 0x10000) {
            out.append(code.toChar())
        } else {
            val v = code - 0x10000
            out.append((0xD800 + (v shr 10)).toChar())
            out.append((0xDC00 + (v and 0x3FF)).toChar())
        }
    }

    private fun name(): String {
        val start = at
        while (at < source.length) {
            val c = source[at]
            if (c.isXmlSpace() || c == '>' || c == '/' || c == '=' || c == '<' || c == '"' || c == '\'') break
            at++
        }
        if (at == start) refuse(XmlProblem.Malformed)
        val name = source.substring(start, at)
        val first = name[0]
        if (first.isDigit() || first == '-' || first == '.' || name.count { it == ':' } > 1 || name.endsWith(':')) {
            refuse(XmlProblem.Malformed)
        }
        return name
    }

    private fun skipSpace(): Boolean {
        val start = at
        while (at < source.length && source[at].isXmlSpace()) at++
        return at > start
    }

    private fun expect(c: Char) {
        if (source.getOrNull(at) != c) refuse(XmlProblem.Malformed)
        at++
    }

    private companion object {
        val BASE_SCOPE: Map<String, String> = mapOf("xml" to "http://www.w3.org/XML/1998/namespace")
    }
}

private fun Char.isXmlSpace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'

private fun refuse(problem: XmlProblem): Nothing = throw XmlRefused(problem)

/** Bounds for one read. */
public data class XmlLimits(val maxDepth: Int, val maxElements: Int) {
    public companion object {
        /** Plan 10 §10.7: depth ≤ 64, element count ≤ 2,000,000. */
        public val Office: XmlLimits = XmlLimits(maxDepth = 64, maxElements = 2_000_000)
    }
}

public enum class XmlProblem { Malformed, Declaration, Entity, TooDeep, TooLarge }

/** The read stopped. Carries only the kind of problem, never document text. */
public class XmlRefused(public val problem: XmlProblem) : Exception("xml:${problem.name}")

public data class XmlName(val qualified: String, val prefix: String?, val local: String, val namespace: String?) {
    internal companion object {
        fun of(qualified: String, scope: Map<String, String>): XmlName {
            val prefix = qualified.substringBefore(':', "").ifEmpty { null }
            val local = if (prefix == null) qualified else qualified.substringAfter(':')
            val ns = scope[prefix ?: ""]?.ifEmpty { null }
            return XmlName(qualified, prefix, local, ns)
        }
    }
}

public data class XmlAttribute(val name: XmlName, val value: String)

public sealed interface XmlEvent {
    public data class Start(val name: XmlName, val attributes: List<XmlAttribute>, val selfClosing: Boolean) : XmlEvent {
        /** The value of the attribute whose qualified name is [qualified], or null. */
        public fun attr(qualified: String): String? = attributes.firstOrNull { it.name.qualified == qualified }?.value
    }

    public data class End(val name: XmlName) : XmlEvent

    public data class Text(val text: String) : XmlEvent

    /** A processing instruction (`<?target …?>`); the XML declaration is one with target `xml`. */
    public data class Instruction(val target: String) : XmlEvent
}
