package io.github.eslamasabry.oc.android.migration.readers.xml

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** What Android's `SharedPreferencesImpl.loadFromDisk()` would end up with for these bytes. */
internal enum class AndroidLoad {
    /** `XmlUtils.readMapXml` succeeds: the app sees [PrefsXmlRead.entries]. */
    Loaded,

    /**
     * `readMapXml` throws, `loadFromDisk` catches it and installs an EMPTY map. The first commit after that
     * overwrites the file, so every key is lost. A migrator must block all writes in this case.
     */
    LoadsEmpty,
}

/** Problems found while reading. [fatal] problems are the ones that make Android load an empty map. */
internal enum class XmlProblem(public val fatal: Boolean) {
    EmptyFile(true),
    /** Android Binary XML (`ABX\0`). SharedPreferences never writes it today; reported, never guessed at. */
    BinaryXml(true),
    NoMapRoot(true),
    Truncated(true),
    Malformed(true),
    UnknownEntity(true),
    UnknownTag(true),
    BadPrimitive(true),
    TagInsideString(true),
    /** Bytes that are not UTF-8. Android's reader replaces them with U+FFFD and loads the rest. */
    MalformedUtf8(false),
    /** An element without `name=`. Android stores it under a null key; we skip it. */
    MissingName(false),
    /** The same name twice. Android keeps the last one, and so do we. */
    DuplicateName(false),
    /** Content after `</map>`. Android stops reading at `</map>`. */
    TrailingContent(false),
}

/**
 * The parsed file.
 *
 * [entries] is a lenient salvage in file order: on a [AndroidLoad.LoadsEmpty] file it holds what could still
 * be read, which is what an integrity check compares against the live (empty) map. Never use salvaged
 * entries as the adopted data: Android would not.
 */
internal class PrefsXmlRead(
    val entries: LinkedHashMap<String, RawPref>,
    val load: AndroidLoad,
    val problems: List<XmlProblem>,
    /** Character offset of the first fatal problem, for diagnostics (never the content around it). */
    val firstFatalOffset: Int?,
) {
    /** The map Android would hand to the app. */
    val androidMap: Map<String, RawPref> get() = if (load == AndroidLoad.Loaded) entries else emptyMap()

    override fun toString(): String =
        "PrefsXmlRead(load=$load, entries=${entries.size}, problems=$problems, firstFatalOffset=$firstFatalOffset)"
}

/**
 * Reader for Android `shared_prefs/<name>.xml` files, written to match
 * `SharedPreferencesImpl.loadFromDisk` + `XmlUtils.readMapXml`/`readThisValueXml`/`readThisPrimitiveValueXml`
 * (AOSP `frameworks/base`, read 2026-11-01).
 *
 * It is a small hand-written parser on purpose: Android's text serializer (`FastXmlSerializer`) writes control
 * characters as `&#1;` … `&#31;`, which a strict XML 1.0 parser such as JAXP rejects but Android's KXmlParser
 * accepts. Using JAXP here would call a valid file corrupt.
 */
internal object AndroidPrefsXml {

    private val PRIMITIVES = setOf("int", "long", "float", "double", "boolean")

    /** Tags `XmlUtils.readThisValueXml` understands that the Editor never writes. Kept as [RawPref.Other]. */
    private val OTHER_KNOWN = setOf(
        "byte-array", "int-array", "long-array", "double-array", "boolean-array", "string-array", "map", "list",
    )

    private val ABX_MAGIC = byteArrayOf(0x41, 0x42, 0x58, 0x00)

    /** Parse bytes as Android would. */
    fun parse(bytes: ByteArray): PrefsXmlRead {
        if (bytes.isEmpty()) return PrefsXmlRead(LinkedHashMap(), AndroidLoad.LoadsEmpty, listOf(XmlProblem.EmptyFile), 0)
        if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(ABX_MAGIC)) {
            return PrefsXmlRead(LinkedHashMap(), AndroidLoad.LoadsEmpty, listOf(XmlProblem.BinaryXml), 0)
        }
        val problems = mutableListOf<XmlProblem>()
        val text = decodeUtf8(bytes, problems)
        return Parser(text, problems).run()
    }

    fun parse(file: File): PrefsXmlRead = parse(file.readBytes())

    private fun decodeUtf8(bytes: ByteArray, problems: MutableList<XmlProblem>): String {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            problems += XmlProblem.MalformedUtf8
            String(bytes, Charsets.UTF_8) // replaces malformed input with U+FFFD, as InputStreamReader does
        }
    }

    private class Fatal(val problem: XmlProblem, val offset: Int) : Exception(null, null, false, false)

    private sealed interface Token {
        data class Start(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean, val offset: Int) : Token
        data class End(val name: String, val offset: Int) : Token
        data class Text(val text: String, val offset: Int) : Token
        data object Eof : Token
    }

    private class Parser(private val s: String, private val problems: MutableList<XmlProblem>) {
        private var i = 0
        private val entries = LinkedHashMap<String, RawPref>()
        private var firstFatal: Pair<XmlProblem, Int>? = null

        fun run(): PrefsXmlRead {
            try {
                if (s.startsWith("\uFEFF")) i = 1
                readDocument()
            } catch (f: Fatal) {
                fatal(f.problem, f.offset)
            }
            val load = if (firstFatal == null) AndroidLoad.Loaded else AndroidLoad.LoadsEmpty
            return PrefsXmlRead(entries, load, problems.distinct(), firstFatal?.second)
        }

        private fun fatal(p: XmlProblem, offset: Int) {
            if (firstFatal == null) firstFatal = p to offset
            problems += p
        }

        private fun readDocument() {
            // Prolog: everything up to the first start tag must be whitespace, PIs or comments.
            val root = when (val t = next()) {
                is Token.Start -> t
                is Token.Text -> if (t.text.isBlank()) nextStartOrFail() else throw Fatal(XmlProblem.NoMapRoot, t.offset)
                is Token.End -> throw Fatal(XmlProblem.NoMapRoot, t.offset)
                Token.Eof -> throw Fatal(XmlProblem.NoMapRoot, i)
            }
            if (root.name != "map") throw Fatal(XmlProblem.NoMapRoot, root.offset)
            if (!root.selfClosing) readMapBody()
            // Android returns at </map>; anything after it is never read.
            val rest = s.substring(i)
            if (rest.isNotBlank()) problems += XmlProblem.TrailingContent
        }

        private fun nextStartOrFail(): Token.Start = when (val t = next()) {
            is Token.Start -> t
            is Token.Text -> if (t.text.isBlank()) nextStartOrFail() else throw Fatal(XmlProblem.NoMapRoot, t.offset)
            is Token.End -> throw Fatal(XmlProblem.NoMapRoot, t.offset)
            Token.Eof -> throw Fatal(XmlProblem.NoMapRoot, i)
        }

        private fun readMapBody() {
            while (true) {
                when (val t = next()) {
                    Token.Eof -> throw Fatal(XmlProblem.Truncated, i)
                    is Token.End -> if (t.name == "map") return else throw Fatal(XmlProblem.Malformed, t.offset)
                    is Token.Text -> Unit // XmlUtils ignores text between values (whitespace in practice)
                    is Token.Start -> readValue(t)
                }
            }
        }

        private fun put(name: String?, value: RawPref) {
            if (name == null) {
                problems += XmlProblem.MissingName
                return
            }
            if (entries.containsKey(name)) {
                problems += XmlProblem.DuplicateName
                entries.remove(name) // keep "last wins" and move it to its last position
            }
            entries[name] = value
        }

        private fun readValue(start: Token.Start) {
            val name = start.attrs["name"]
            when (start.name) {
                "null" -> {
                    if (!start.selfClosing) expectEnd("null")
                    put(name, RawPref.Null)
                }
                "string" -> {
                    val text = if (start.selfClosing) "" else readStringText()
                    put(name, RawPref.Str(text))
                }
                in PRIMITIVES -> {
                    val value = start.attrs["value"]
                    val parsed = parsePrimitive(start.name, value)
                    if (!start.selfClosing) skipToEnd(start.name)
                    if (parsed == null) {
                        // Android throws for the whole file; keep going only to count what is salvageable.
                        fatal(XmlProblem.BadPrimitive, start.offset)
                    } else {
                        put(name, parsed)
                    }
                }
                "set" -> {
                    val set = if (start.selfClosing) LinkedHashSet() else readSet()
                    put(name, RawPref.StrSet(set))
                }
                in OTHER_KNOWN -> {
                    val from = start.offset
                    if (!start.selfClosing) skipToEnd(start.name)
                    put(name, RawPref.Other(start.name, s.substring(from, i)))
                }
                else -> {
                    fatal(XmlProblem.UnknownTag, start.offset)
                    if (!start.selfClosing) skipToEnd(start.name)
                }
            }
        }

        private fun parsePrimitive(tag: String, v: String?): RawPref? {
            if (v == null) return null
            return try {
                when (tag) {
                    "int" -> RawPref.Int32(v.toInt())
                    "long" -> RawPref.Int64(v.toLong())
                    "float" -> RawPref.F32(java.lang.Float.parseFloat(v))
                    "double" -> RawPref.F64(java.lang.Double.parseDouble(v))
                    // TypedXmlPullParser.getAttributeBoolean: "true"/"false" (any case), anything else throws.
                    "boolean" -> when {
                        v.equals("true", ignoreCase = true) -> RawPref.Bool(true)
                        v.equals("false", ignoreCase = true) -> RawPref.Bool(false)
                        else -> null
                    }
                    else -> null
                }
            } catch (e: NumberFormatException) {
                null
            }
        }

        private fun readStringText(): String {
            val sb = StringBuilder()
            while (true) {
                when (val t = next()) {
                    is Token.Text -> sb.append(t.text)
                    is Token.End -> if (t.name == "string") return sb.toString() else throw Fatal(XmlProblem.Malformed, t.offset)
                    is Token.Start -> throw Fatal(XmlProblem.TagInsideString, t.offset)
                    Token.Eof -> throw Fatal(XmlProblem.Truncated, i)
                }
            }
        }

        private fun readSet(): LinkedHashSet<String> {
            val set = LinkedHashSet<String>()
            while (true) {
                when (val t = next()) {
                    is Token.Text -> Unit
                    is Token.End -> if (t.name == "set") return set else throw Fatal(XmlProblem.Malformed, t.offset)
                    is Token.Start -> {
                        if (t.name != "string") throw Fatal(XmlProblem.UnknownTag, t.offset)
                        set += if (t.selfClosing) "" else readStringText()
                    }
                    Token.Eof -> throw Fatal(XmlProblem.Truncated, i)
                }
            }
        }

        private fun expectEnd(name: String) {
            while (true) {
                when (val t = next()) {
                    is Token.Text -> if (t.text.isNotBlank()) throw Fatal(XmlProblem.Malformed, t.offset)
                    is Token.End -> if (t.name == name) return else throw Fatal(XmlProblem.Malformed, t.offset)
                    is Token.Start -> throw Fatal(XmlProblem.Malformed, t.offset)
                    Token.Eof -> throw Fatal(XmlProblem.Truncated, i)
                }
            }
        }

        private fun skipToEnd(name: String) {
            var depth = 1
            while (depth > 0) {
                when (val t = next()) {
                    is Token.Start -> if (!t.selfClosing) depth++
                    is Token.End -> {
                        depth--
                        if (depth == 0 && t.name != name) throw Fatal(XmlProblem.Malformed, t.offset)
                    }
                    is Token.Text -> Unit
                    Token.Eof -> throw Fatal(XmlProblem.Truncated, i)
                }
            }
        }

        // ---- tokenizer ----

        private fun next(): Token {
            while (true) {
                if (i >= s.length) return Token.Eof
                val c = s[i]
                if (c != '<') return readText()
                when {
                    s.startsWith("<!--", i) -> {
                        val end = s.indexOf("-->", i + 4)
                        if (end < 0) throw Fatal(XmlProblem.Truncated, i)
                        i = end + 3
                    }
                    s.startsWith("<?", i) -> {
                        val end = s.indexOf("?>", i + 2)
                        if (end < 0) throw Fatal(XmlProblem.Truncated, i)
                        i = end + 2
                    }
                    s.startsWith("<![CDATA[", i) -> {
                        val start = i
                        val end = s.indexOf("]]>", i + 9)
                        if (end < 0) throw Fatal(XmlProblem.Truncated, i)
                        val text = s.substring(i + 9, end)
                        i = end + 3
                        return Token.Text(text, start)
                    }
                    s.startsWith("<!", i) -> { // DOCTYPE: never written by Android; skip it like a declaration
                        val end = s.indexOf('>', i + 2)
                        if (end < 0) throw Fatal(XmlProblem.Truncated, i)
                        i = end + 1
                    }
                    s.startsWith("</", i) -> return readEndTag()
                    else -> return readStartTag()
                }
            }
        }

        private fun readText(): Token.Text {
            val start = i
            val sb = StringBuilder()
            while (i < s.length && s[i] != '<') {
                val c = s[i]
                if (c == '&') sb.append(readEntity()) else {
                    sb.append(c)
                    i++
                }
            }
            return Token.Text(sb.toString(), start)
        }

        private fun readEntity(): String {
            val start = i
            val semi = s.indexOf(';', i)
            if (semi < 0 || semi - i > 12) throw Fatal(if (semi < 0) XmlProblem.Truncated else XmlProblem.UnknownEntity, start)
            val body = s.substring(i + 1, semi)
            i = semi + 1
            return when {
                body == "amp" -> "&"
                body == "lt" -> "<"
                body == "gt" -> ">"
                body == "quot" -> "\""
                body == "apos" -> "'"
                body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.substring(2), 16, start)
                body.startsWith("#") -> codePoint(body.substring(1), 10, start)
                else -> throw Fatal(XmlProblem.UnknownEntity, start)
            }
        }

        private fun codePoint(digits: String, radix: Int, at: Int): String {
            val cp = digits.toIntOrNull(radix) ?: throw Fatal(XmlProblem.UnknownEntity, at)
            if (cp < 0 || cp > 0x10FFFF) throw Fatal(XmlProblem.UnknownEntity, at)
            return String(Character.toChars(cp))
        }

        private fun readName(): String {
            val start = i
            while (i < s.length) {
                val c = s[i]
                if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == ':') i++ else break
            }
            if (i == start) throw Fatal(if (i >= s.length) XmlProblem.Truncated else XmlProblem.Malformed, start)
            return s.substring(start, i)
        }

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun readStartTag(): Token.Start {
            val start = i
            i++ // '<'
            val name = readName()
            val attrs = LinkedHashMap<String, String>()
            while (true) {
                skipSpace()
                if (i >= s.length) throw Fatal(XmlProblem.Truncated, start)
                when (s[i]) {
                    '>' -> {
                        i++
                        return Token.Start(name, attrs, false, start)
                    }
                    '/' -> {
                        if (i + 1 >= s.length) throw Fatal(XmlProblem.Truncated, start)
                        if (s[i + 1] != '>') throw Fatal(XmlProblem.Malformed, i)
                        i += 2
                        return Token.Start(name, attrs, true, start)
                    }
                    else -> {
                        val key = readName()
                        skipSpace()
                        if (i >= s.length) throw Fatal(XmlProblem.Truncated, start)
                        if (s[i] != '=') throw Fatal(XmlProblem.Malformed, i)
                        i++
                        skipSpace()
                        if (i >= s.length) throw Fatal(XmlProblem.Truncated, start)
                        val quote = s[i]
                        if (quote != '"' && quote != '\'') throw Fatal(XmlProblem.Malformed, i)
                        i++
                        val sb = StringBuilder()
                        while (true) {
                            if (i >= s.length) throw Fatal(XmlProblem.Truncated, start)
                            val c = s[i]
                            if (c == quote) {
                                i++
                                break
                            }
                            if (c == '<') throw Fatal(XmlProblem.Malformed, i)
                            if (c == '&') sb.append(readEntity()) else {
                                sb.append(c)
                                i++
                            }
                        }
                        attrs[key] = sb.toString()
                    }
                }
            }
        }

        private fun readEndTag(): Token.End {
            val start = i
            i += 2
            val name = readName()
            skipSpace()
            if (i >= s.length) throw Fatal(XmlProblem.Truncated, start)
            if (s[i] != '>') throw Fatal(XmlProblem.Malformed, i)
            i++
            return Token.End(name, start)
        }
    }
}

/**
 * One SharedPreferences file name on disk, with the AOSP `.bak` rule applied: when `<name>.xml.bak` exists,
 * `loadFromDisk` deletes `<name>.xml` and renames the backup over it, so the backup is what the app sees.
 */
internal class SharedPrefsFile(public val dir: File, public val name: String) {
    val xml: File get() = File(dir, "$name.xml")
    val bak: File get() = File(dir, "$name.xml.bak")

    /** The file Android would load, or null when neither exists. */
    fun authoritative(): File? = when {
        bak.exists() -> bak
        xml.exists() -> xml
        else -> null
    }

    fun exists(): Boolean = authoritative() != null

    /** Parse the authoritative file; null when the store does not exist (Android: empty map, no file). */
    fun read(): PrefsXmlRead? = authoritative()?.let { AndroidPrefsXml.parse(it) }
}
