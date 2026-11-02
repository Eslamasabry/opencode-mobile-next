package io.github.eslamasabry.oc.core.markdown

/** What a Markdown picture points at (B-CHAT-059). */
public enum class PictureKind {
    /** A path on the connected server or phone, which the host may read through its own file transport. */
    File,

    /** An http(s) address. Never fetched by the app; a tap goes through the external-link policy. */
    Web,

    /** Nothing the app can open: `data:` URIs, other schemes, a stored attachment id, a bad target. */
    Inert,
}

/**
 * One `![alt](target)` (Flutter `KitMarkdownImage`). [alt] is trimmed; [target] is the destination as written
 * (without `<…>`); [path] is set for [PictureKind.File] (decoded, `./` added to a bare name); [url] for
 * [PictureKind.Web] (for a picture inside a link, the link's address).
 */
public data class MarkdownPicture(
    val alt: String,
    val target: String,
    val kind: PictureKind,
    val path: String? = null,
    val url: String? = null,
) {
    /** Empty and the bare words "Image"/"img" say nothing; those get the app's own word. */
    val hasDescription: Boolean
        get() = alt.isNotEmpty() && alt.lowercase() != "image" && alt.lowercase() != "img"

    /** A content hash (`079f63375d36….png`) names nothing. */
    val hasMeaningfulName: Boolean
        get() {
            val name = fileName ?: return false
            val dot = name.lastIndexOf('.')
            val stem = if (dot > 0) name.substring(0, dot) else name
            return !PictureScanner.attachmentId.matches(stem)
        }

    /** What identifies the picture for dedupe: where it lives. */
    val identity: String get() = path ?: url ?: target

    /** The file's name, for a [PictureKind.File] picture. */
    val fileName: String?
        get() {
            val value = path ?: return null
            val name = value.substring(value.lastIndexOf('/') + 1)
            return name.ifEmpty { null }
        }

    /** The address's host (with an explicit, non-default port), for a [PictureKind.Web] picture. */
    val host: String?
        get() {
            val uri = url?.let(SimpleUri::parse) ?: return null
            if (uri.host.isEmpty()) return null
            return if (uri.explicitPort != null) "${uri.host}:${uri.explicitPort}" else uri.host
        }

    /** Whether the platform image decoders can draw it as a thumbnail (not SVG, not HEIC). */
    val drawable: Boolean get() = PictureScanner.drawableExtension.containsMatchIn((fileName ?: "").lowercase())
}

/** One piece of a scanned line: words ([text]) or a [picture]; exactly one is set. */
public data class PicturePart(val text: String?, val picture: MarkdownPicture?)

/** The picture scanner (B-CHAT-059; Flutter `KitMarkdownImage.scan` and friends). Inline code is left alone. */
public object PictureScanner {
    internal val attachmentId = Regex("[0-9a-fA-F]{32,128}")
    internal val drawableExtension = Regex("""\.(png|jpe?g|gif|webp|bmp|wbmp)(?![\s\S])""")
    private val imageExtension =
        Regex("""\.(png|jpe?g|gif|webp|bmp|wbmp|svg|heic|heif|avif|tiff?|ico)(?![\s\S])""", RegexOption.IGNORE_CASE)
    private val scheme = Regex("""^([A-Za-z][A-Za-z0-9+.\-]+):""")
    private val unfinished = Regex("""!\[[^\]\n]*(?:\](?:\([^)\n]*)?)?""")
    private val forbiddenInPath = Regex("""[\x00-\x1f\\:?#]""")

    /** The picture that is exactly [source], or null. */
    public fun parse(source: String): MarkdownPicture? {
        val found = imageAt(source, 0) ?: return null
        return if (found.second == source.length) found.first else null
    }

    /** Splits one line into the words around its pictures and the pictures. A line with none is one text part. */
    public fun scan(source: String): List<PicturePart> {
        if (!source.contains("![")) return listOf(PicturePart(source, null))
        val parts = ArrayList<PicturePart>()
        var textStart = 0
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '`') {
                // `code`: a picture inside it is code, not a picture.
                val close = source.indexOf('`', i + 1)
                i = if (close < 0) i + 1 else close + 1
                continue
            }
            val found =
                when {
                    c == '!' && source.startsWith("![", i) -> imageAt(source, i)
                    c == '[' && source.startsWith("[![", i) -> linkedImageAt(source, i)
                    else -> null
                }
            if (found == null) {
                i++
                continue
            }
            if (i > textStart) parts += PicturePart(source.substring(textStart, i), null)
            parts += PicturePart(null, found.first)
            i = found.second
            textStart = i
        }
        if (textStart < source.length) parts += PicturePart(source.substring(textStart), null)
        return parts
    }

    /** The pictures of a line that holds nothing else (spaces aside), or null. */
    public fun only(line: String): List<MarkdownPicture>? {
        if (!line.contains("![")) return null
        val pictures = ArrayList<MarkdownPicture>()
        for (part in scan(line)) {
            val picture = part.picture
            if (picture != null) {
                pictures += picture
            } else if (part.text!!.isNotBlank()) {
                return null
            }
        }
        return pictures.ifEmpty { null }
    }

    /** [source] with every picture replaced by its description: what is spoken, measured or copied. */
    public fun withAltText(source: String): String {
        if (!source.contains("![")) return source
        val out = StringBuilder()
        for (part in scan(source)) out.append(part.picture?.alt ?: part.text)
        return out.toString()
    }

    /** [line] without a picture that is still being written (`![Image](/tmp/sho`); finished ones stay. */
    public fun withoutUnfinished(line: String): String {
        val at = line.lastIndexOf("![")
        if (at < 0) return line
        return if (unfinished.matchEntire(line.substring(at)) != null) line.substring(0, at) else line
    }

    // -- Parsing -----------------------------------------------------------------------------------------------

    private fun imageAt(s: String, start: Int): Pair<MarkdownPicture, Int>? {
        if (!s.startsWith("![", start)) return null
        val altEnd = closingBracket(s, start + 1)
        if (altEnd < 0 || altEnd + 1 >= s.length || s[altEnd + 1] != '(') return null
        val destination = destinationAt(s, altEnd + 1) ?: return null
        return classify(alt = s.substring(start + 2, altEnd).trim(), target = destination.first) to destination.second
    }

    /** `[![alt](src)](href)`: a picture that is also a link. */
    private fun linkedImageAt(s: String, start: Int): Pair<MarkdownPicture, Int>? {
        val inner = imageAt(s, start + 1) ?: return null
        val close = inner.second
        if (close + 1 >= s.length || s[close] != ']' || s[close + 1] != '(') return null
        val link = destinationAt(s, close + 1) ?: return null
        return classify(alt = inner.first.alt, target = inner.first.target, href = link.first) to link.second
    }

    private fun closingBracket(s: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '\n' -> return -1
                '\\' -> i++
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun destinationAt(s: String, open: Int): Pair<String, Int>? {
        var i = open + 1
        fun space(at: Int) = s[at] == ' ' || s[at] == '\t'
        while (i < s.length && space(i)) i++
        val target: String
        if (i < s.length && s[i] == '<') {
            val close = s.indexOf('>', i + 1)
            if (close < 0 || s.substring(i, close).contains('\n')) return null
            target = s.substring(i + 1, close)
            i = close + 1
        } else {
            val begin = i
            var depth = 0
            while (i < s.length && !space(i) && s[i] != '\n') {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    i++
                } else if (c == '(') {
                    depth++
                } else if (c == ')') {
                    if (depth == 0) break
                    depth--
                }
                i++
            }
            target = s.substring(begin, i)
        }
        while (i < s.length && space(i)) i++
        if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
            val close = s.indexOf(s[i], i + 1)
            if (close < 0) return null
            i = close + 1
            while (i < s.length && space(i)) i++
        }
        if (i >= s.length || s[i] != ')') return null
        return target.trim() to i + 1
    }

    private fun classify(alt: String, target: String, href: String? = null): MarkdownPicture {
        // A bare content id ("![Image](<64 hex>)") is a stored attachment, not a file name: "Image" only.
        val isAttachment = attachmentId.matches(target)
        val path = if (isAttachment) null else pathOf(target)
        val web = if (isAttachment) null else webAddress(href) ?: webAddress(target)
        val kind =
            when {
                path != null -> PictureKind.File
                web != null -> PictureKind.Web
                else -> PictureKind.Inert
            }
        return MarkdownPicture(
            alt = alt,
            target = target,
            kind = kind,
            path = path,
            url = if (kind == PictureKind.Web) web else null,
        )
    }

    private fun webAddress(value: String?): String? {
        val text = value?.trim()
        if (text.isNullOrEmpty() || text.length > 2048) return null
        val uri = SimpleUri.parse(text) ?: return null
        if (uri.host.isEmpty()) return null
        val s = uri.scheme.lowercase()
        return if (s == "http" || s == "https") text else null
    }

    private fun pathOf(target: String): String? {
        if (target.isEmpty() || target.length > 1024) return null
        val found = scheme.find(target)?.groupValues?.get(1)?.lowercase()
        var path: String
        if (found == null) {
            if (target.startsWith("//")) return null
            path = target
        } else if (found == "file") {
            val uri = SimpleUri.parse(target) ?: return null
            if ((uri.host.isNotEmpty() && uri.host != "localhost") || uri.path.isEmpty()) return null
            path = uri.path
        } else {
            return null
        }
        path = SimpleUri.decodeFull(path) ?: return null
        if (forbiddenInPath.containsMatchIn(path)) return null
        if (!imageExtension.containsMatchIn(path) && !PathChipScanner.looksLikeFilePath(path)) return null
        return if (path.contains('/')) path else "./$path"
    }
}

/**
 * The few URI facts the scanners need, without a platform URI class (common code has none): scheme, host
 * (lower-cased, userinfo and port removed), the explicit non-default port, and the still-encoded path.
 */
internal class SimpleUri private constructor(
    val scheme: String,
    val host: String,
    val explicitPort: Int?,
    val path: String,
) {
    companion object {
        private val schemeRe = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):""")

        fun parse(text: String): SimpleUri? {
            var rest = text
            val s = schemeRe.find(rest)
            val scheme = s?.groupValues?.get(1) ?: ""
            if (s != null) rest = rest.substring(s.range.last + 1)
            rest = rest.substringBefore('#').substringBefore('?')
            var host = ""
            var port: Int? = null
            if (rest.startsWith("//")) {
                val authorityEnd = rest.indexOf('/', 2).let { if (it < 0) rest.length else it }
                var authority = rest.substring(2, authorityEnd)
                rest = rest.substring(authorityEnd)
                authority = authority.substringAfterLast('@')
                val portAt = if (authority.startsWith("[")) authority.indexOf("]:").let { if (it < 0) -1 else it + 1 }
                else authority.lastIndexOf(':')
                if (portAt >= 0) {
                    val digits = authority.substring(portAt + 1)
                    if (digits.isNotEmpty()) {
                        port = digits.toIntOrNull() ?: return null
                    }
                    authority = authority.substring(0, portAt)
                }
                host = authority.lowercase()
            }
            val default =
                when (scheme.lowercase()) {
                    "http" -> 80
                    "https" -> 443
                    else -> 0
                }
            return SimpleUri(scheme, host, port?.takeIf { it != default }, rest)
        }

        /** Percent-decoding as Dart's `Uri.decodeFull`: null on a bad escape or bad UTF-8. */
        fun decodeFull(text: String): String? {
            if (!text.contains('%')) return text
            val bytes = ArrayList<Byte>()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c.code > 127) return null
                if (c == '%') {
                    if (i + 3 > text.length) return null
                    val pair = text.substring(i + 1, i + 3)
                    if (!pair.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
                    val hex = pair.toInt(16)
                    bytes += hex.toByte()
                    i += 3
                } else {
                    bytes += c.code.toByte()
                    i++
                }
            }
            return try {
                bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                null
            }
        }
    }
}
