package io.github.eslamasabry.oc.engine.cards

/**
 * The card normaliser (plan 09 §13.3), a line-for-line port of Flutter `gen_ui_validation.dart`: same limits, same
 * order of checks (so the same [CardProblem] wins), same output key order (so the same revision). Unknown keys fail,
 * wrong types and explicit nulls fail; nothing is coerced or repaired. Works on the [CardJson] tree.
 */
internal object CardValidator {
    class Invalid(val reason: CardProblem = CardProblem.BadValue) : Exception()

    fun fail(reason: CardProblem = CardProblem.BadValue): Nothing = throw Invalid(reason)

    const val MAX_SAFE: Long = 9007199254740991L

    /** Dart `_bounded`: the first value deeper than this is too large, whatever it is (checked before its type). */
    const val MAX_DEPTH: Int = 8

    /** Unicode scalar count; an unpaired surrogate fails (Dart `_scalars`). */
    fun scalars(value: String): Int {
        var count = 0
        var i = 0
        while (i < value.length) {
            val c = value[i].code
            if (c in 0xd800..0xdbff) {
                i++
                if (i >= value.length || value[i].code !in 0xdc00..0xdfff) fail()
            } else if (c in 0xdc00..0xdfff) {
                fail()
            }
            count++
            i++
        }
        return count
    }

    fun utf8Length(s: String): Int = s.encodeToByteArray().size

    /** Size, depth, cycle and type bounds over the raw input (Dart `_bounded`). */
    fun bounded(input: Any?) {
        var count = 0
        var stringBytes = 0
        val seen = ArrayList<Any>()
        fun string(s: String) {
            if (s.length > 65536 || scalars(s) > 32768) fail(CardProblem.TooLarge)
            stringBytes += utf8Length(s)
            if (stringBytes > 32768) fail(CardProblem.TooLarge)
        }
        fun visit(x: Any?, depth: Int) {
            if (++count > 4096 || depth > MAX_DEPTH) fail(CardProblem.TooLarge)
            when (x) {
                null, is Boolean -> return
                is String -> {
                    string(x)
                    return
                }
                is Long, is Int -> return
                is Double -> {
                    if (!x.isFinite()) fail()
                    return
                }
                is Map<*, *>, is List<*> -> Unit
                else -> fail()
            }
            if (seen.any { it === x }) fail()
            seen.add(x)
            if (x is Map<*, *>) {
                for ((k, v) in x) {
                    if (k !is String) fail()
                    string(k)
                    visit(v, depth + 1)
                }
            } else if (x is List<*>) {
                for (v in x) visit(v, depth + 1)
            }
            seen.removeAt(seen.lastIndex)
        }
        visit(input, 1)
        if (utf8Length(CardJson.encode(input)) > 32768) fail(CardProblem.TooLarge)
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?, allowed: List<String>, required: List<String> = emptyList()): LinkedHashMap<String, Any?> {
        if (value !is Map<*, *>) fail()
        if (value.keys.any { it !is String || it !in allowed }) fail(CardProblem.UnknownKey)
        if (required.any { !value.containsKey(it) } || value.values.any { it == null }) fail()
        return LinkedHashMap(value as Map<String, Any?>)
    }

    private val directional = setOf(0x061c, 0x200e, 0x200f, 0x202a, 0x202b, 0x202c, 0x202d, 0x202e, 0x2066, 0x2067, 0x2068, 0x2069)

    fun isControl(c: Char): Boolean {
        val code = c.code
        return code in 0x00..0x08 || code in 0x0b..0x1f || code in 0x7f..0x9f || code in directional
    }

    fun hasControl(s: String): Boolean = s.any(::isControl)

    fun stripControls(s: String): String = if (!hasControl(s)) s else s.filterNot(::isControl)

    /** Dart `String.trim()`'s whitespace set. */
    fun isDartSpace(c: Char): Boolean {
        val code = c.code
        return code in 0x09..0x0d || code == 0x20 || code == 0x85 || code == 0xa0 || code == 0x1680 ||
            code in 0x2000..0x200a || code == 0x2028 || code == 0x2029 || code == 0x202f || code == 0x205f ||
            code == 0x3000 || code == 0xfeff
    }

    fun dartTrim(s: String): String = s.trim(::isDartSpace)

    /** JS `\s` (Dart RegExp without the unicode flag differs only outside the BMP, where it never matters here). */
    fun isJsSpace(c: Char): Boolean = isDartSpace(c)

    fun text(value: Any?, max: Int = 2000, label: Boolean = false, min: Int = 0): String {
        if (value !is String || scalars(value) > max) fail()
        var result = stripControls(value)
        if (label) result = dartTrim(result)
        if (scalars(result) < min) fail()
        return result
    }

    fun arr(value: Any?, min: Int, max: Int): List<Any?> {
        if (value !is List<*> || value.size < min || value.size > max) fail()
        return value
    }

    /** Dart `_num`: integral values within 2^53 become ints (Long); everything else a double. */
    fun num(value: Any?): Any {
        val number =
            when (value) {
                is Long -> value.toDouble()
                is Int -> value.toDouble()
                is Double -> value
                else -> fail()
            }
        if (!number.isFinite()) fail()
        if (kotlin.math.abs(number) <= MAX_SAFE.toDouble() && number == kotlin.math.truncate(number)) {
            return number.toLong()
        }
        return number
    }

    fun asDouble(n: Any): Double = (n as? Long)?.toDouble() ?: (n as Double)

    fun int(value: Any?, min: Long, max: Long): Long {
        val n = asDouble(num(value))
        if (n != kotlin.math.truncate(n) || n < min.toDouble() || n > max.toDouble()) fail()
        return n.toLong()
    }

    fun bool(value: Any?): Boolean {
        if (value !is Boolean) fail()
        return value
    }

    fun pick(value: Any?, allowed: List<String>): String {
        if (value !is String || value !in allowed) fail()
        return value
    }

    val CARD_ID = Regex("[a-z0-9-]{1,48}")
    val OPTION_ID = Regex("[a-zA-Z0-9_\\-]{1,32}")
    val FIELD_ID = Regex("[a-zA-Z0-9_]{1,32}")
    private val CATALOG_ID = Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")

    fun id(value: Any?, pattern: Regex): String {
        if (value !is String || !pattern.matches(value)) fail()
        return value
    }

    private val SECRET = Regex("password|passwd|token|secret|apikey", RegexOption.IGNORE_CASE)

    fun secret(value: String) {
        val normalized = value.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        if (SECRET.containsMatchIn(normalized)) fail(CardProblem.SecretField)
    }

    fun unique(values: List<String>) {
        if (values.toSet().size != values.size) fail()
    }

    private fun optText(o: Map<String, Any?>, key: String, max: Int, label: Boolean = false, min: Int = 0): Map<String, Any?> =
        if (o.containsKey(key)) mapOf(key to text(o[key], max, label, min)) else emptyMap()

    private fun option(value: Any?): Map<String, Any?> {
        val o = obj(value, listOf("id", "label", "detail"), listOf("id", "label"))
        val id = id(o["id"], OPTION_ID)
        val label = text(o["label"], 120, true, 1)
        secret(id)
        secret(label)
        return linkedMapOf<String, Any?>("id" to id, "label" to label) + optText(o, "detail", 200)
    }

    private fun options(value: Any?, min: Int, max: Int): List<Map<String, Any?>> {
        val result = arr(value, min, max).map(::option)
        unique(result.map { it["id"] as String })
        return result
    }

    private val DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    /** Dart `_fieldValue`: a value for a field description (`type`, `required`, `min`, `max`, `options`). */
    fun fieldValue(value: Any?, field: Map<String, Any?>): Any {
        when (field["type"]) {
            "text", "multiline" -> {
                if (value !is String || scalars(value) > 2000 || (field["required"] == true && dartTrim(value).isEmpty())) fail()
                return value
            }
            "number" -> {
                val n = num(value)
                val d = asDouble(n)
                val min = field["min"]?.let { asDouble(it) }
                val max = field["max"]?.let { asDouble(it) }
                if ((min != null && d < min) || (max != null && d > max)) fail()
                return n
            }
            "toggle" -> return bool(value)
            "select" -> {
                @Suppress("UNCHECKED_CAST")
                val opts = field["options"] as? List<Map<String, Any?>> ?: emptyList()
                if (value !is String || opts.none { it["id"] == value }) fail()
                return value
            }
            "date" -> {
                if (value !is String || value.length != 10 || !DATE.matches(value)) fail()
                val parts = value.split('-').map { it.toInt() }
                val (y, m, d) = parts
                val leap = y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)
                val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
                if (y < 1 || m < 1 || m > 12 || d < 1 || d > days[m - 1]) fail()
                return value
            }
        }
        fail()
    }

    private fun field(value: Any?): Map<String, Any?> {
        val o =
            obj(
                value,
                listOf("id", "label", "type", "required", "placeholder", "default", "options", "min", "max"),
                listOf("id", "label", "type"),
            )
        val result = LinkedHashMap<String, Any?>()
        result["id"] = id(o["id"], FIELD_ID)
        result["label"] = text(o["label"], 120, true, 1)
        result["type"] = pick(o["type"], listOf("text", "multiline", "number", "toggle", "select", "date"))
        result["required"] = if (o.containsKey("required")) bool(o["required"]) else false
        result.putAll(optText(o, "placeholder", 120))
        secret(result["id"] as String)
        secret(result["label"] as String)
        if (result["type"] == "select") {
            result["options"] = options(o["options"], 2, 20)
        } else if (o.containsKey("options")) {
            fail()
        }
        for (key in listOf("min", "max")) {
            if (o.containsKey(key)) {
                if (result["type"] != "number") fail()
                result[key] = num(o[key])
            }
        }
        if (result.containsKey("min") && result.containsKey("max") && asDouble(result["min"]!!) > asDouble(result["max"]!!)) fail()
        if (o.containsKey("default")) result["default"] = fieldValue(o["default"], result)
        return result
    }

    private fun ask(value: Any?): Map<String, Any?> {
        val o =
            obj(
                value,
                listOf("kind", "options", "multi", "fields", "submitLabel", "confirmLabel", "cancelLabel", "tone", "purpose", "max"),
                listOf("kind"),
            )
        return when (o["kind"]) {
            "choice" -> {
                obj(o, listOf("kind", "options", "multi"), listOf("kind", "options"))
                val opts = options(o["options"], 2, 8)
                linkedMapOf("kind" to "choice", "options" to opts, "multi" to (if (o.containsKey("multi")) bool(o["multi"]) else false))
            }
            "form" -> {
                obj(o, listOf("kind", "fields", "submitLabel"), listOf("kind", "fields"))
                val fields = arr(o["fields"], 1, 12).map(::field)
                unique(fields.map { it["id"] as String })
                linkedMapOf<String, Any?>("kind" to "form", "fields" to fields) + optText(o, "submitLabel", 24, true, 1)
            }
            "confirm" -> {
                obj(o, listOf("kind", "confirmLabel", "cancelLabel", "tone"), listOf("kind"))
                val result = linkedMapOf<String, Any?>("kind" to "confirm")
                result.putAll(optText(o, "confirmLabel", 24, true, 1))
                result.putAll(optText(o, "cancelLabel", 24, true, 1))
                result["tone"] = if (o.containsKey("tone")) pick(o["tone"], listOf("normal", "danger")) else "normal"
                result
            }
            "photo" -> {
                obj(o, listOf("kind", "purpose", "max"), listOf("kind", "purpose"))
                val purpose = text(o["purpose"], 200, true, 1)
                linkedMapOf("kind" to "photo", "purpose" to purpose, "max" to (if (o.containsKey("max")) int(o["max"], 1, 4) else 1L))
            }
            "file", "voice" -> fail(CardProblem.UnsupportedAsk)
            else -> fail()
        }
    }

    private fun node(value: Any?): Map<String, Any?> {
        if (value !is Map<*, *> || value["type"] !is String) fail()
        return when (value["type"]) {
            "text" -> {
                val o = obj(value, listOf("type", "text"), listOf("type", "text"))
                linkedMapOf("type" to "text", "text" to text(o["text"]))
            }
            "keyValue" -> {
                val o = obj(value, listOf("type", "rows"), listOf("type", "rows"))
                linkedMapOf(
                    "type" to "keyValue",
                    "rows" to
                        arr(o["rows"], 0, 20).map {
                            val r = obj(it, listOf("key", "value"), listOf("key", "value"))
                            val k = text(r["key"], 60, true, 1)
                            linkedMapOf("key" to k, "value" to text(r["value"], 200))
                        },
                )
            }
            "list" -> {
                val o = obj(value, listOf("type", "items", "style"), listOf("type", "items", "style"))
                val items =
                    arr(o["items"], 0, 30).map {
                        val r = obj(it, listOf("text", "done"), listOf("text"))
                        val t = text(r["text"])
                        linkedMapOf("text" to t, "done" to (if (r.containsKey("done")) bool(r["done"]) else false))
                    }
                linkedMapOf("type" to "list", "items" to items, "style" to pick(o["style"], listOf("bullet", "check")))
            }
            "table" -> {
                val o = obj(value, listOf("type", "columns", "rows"), listOf("type", "columns", "rows"))
                val columns = arr(o["columns"], 1, 6).map { text(it, 80, true) }
                linkedMapOf(
                    "type" to "table",
                    "columns" to columns,
                    "rows" to arr(o["rows"], 0, 20).map { row -> arr(row, columns.size, columns.size).map { text(it, 80) } },
                )
            }
            "chart" -> {
                val o = obj(value, listOf("type", "kind", "unit", "labels", "series"), listOf("type", "kind", "labels", "series"))
                val labels = arr(o["labels"], 1, 30).map { text(it, 80, true) }
                val result = linkedMapOf<String, Any?>("type" to "chart", "kind" to pick(o["kind"], listOf("bar", "line")))
                result.putAll(optText(o, "unit", 24, true))
                result["labels"] = labels
                result["series"] =
                    arr(o["series"], 1, 3).map {
                        val r = obj(it, listOf("name", "values"), listOf("name", "values"))
                        val name = text(r["name"], 80, true)
                        linkedMapOf("name" to name, "values" to arr(r["values"], labels.size, labels.size).map(::num))
                    }
                result
            }
            "code" -> {
                val o = obj(value, listOf("type", "language", "text"), listOf("type", "text"))
                val result = linkedMapOf<String, Any?>("type" to "code")
                result.putAll(optText(o, "language", 32, true))
                result["text"] = text(o["text"], 4000)
                result
            }
            "diffStat" -> {
                val o = obj(value, listOf("type", "files"), listOf("type", "files"))
                linkedMapOf(
                    "type" to "diffStat",
                    "files" to
                        arr(o["files"], 0, 30).map {
                            val r = obj(it, listOf("path", "added", "removed"), listOf("path", "added", "removed"))
                            val path = text(r["path"], 256)
                            val added = int(r["added"], 0, MAX_SAFE)
                            linkedMapOf("path" to path, "added" to added, "removed" to int(r["removed"], 0, MAX_SAFE))
                        },
                )
            }
            "progress" -> {
                val o = obj(value, listOf("type", "label", "value"), listOf("type", "label", "value"))
                val n = num(o["value"])
                if (asDouble(n) < 0 || asDouble(n) > 1) fail()
                linkedMapOf("type" to "progress", "label" to text(o["label"], 120, true), "value" to n)
            }
            "callout" -> {
                val o = obj(value, listOf("type", "tone", "text"), listOf("type", "tone", "text"))
                val tone = pick(o["tone"], listOf("info", "warning", "success"))
                linkedMapOf("type" to "callout", "tone" to tone, "text" to text(o["text"], 500))
            }
            "link" -> {
                val o = obj(value, listOf("type", "label", "url"), listOf("type", "label", "url"))
                val url = o["url"]
                if (url !is String || scalars(url) > 2048 || url.any(::unsafeUrlChar) || !url.startsWith("https://", ignoreCase = true)) fail()
                if (hasControl(url) || !SafeHttps.isSafe(url)) fail()
                linkedMapOf("type" to "link", "label" to text(o["label"], 120, true, 1), "url" to url)
            }
            else -> fail()
        }
    }

    /** `[\s\x00-\x20\x7f-\x9f\\]` with the unicode flag. */
    private fun unsafeUrlChar(c: Char): Boolean = isJsSpace(c) || c.code <= 0x20 || c.code in 0x7f..0x9f || c == '\\'

    private fun connector(value: Any?): Map<String, Any?> {
        val o = obj(value, listOf("catalogId", "reason"), listOf("catalogId", "reason"))
        val catalogId = id(o["catalogId"], CATALOG_ID)
        if (catalogId.length > 256) fail()
        val reason = text(o["reason"], 32768, true, 1)
        if (scalars(reason) > 500) fail()
        return linkedMapOf("catalogId" to catalogId, "reason" to reason)
    }

    /** Dart `_normalize`: the canonical card map, keys in schema order. */
    fun normalize(input: Any?): LinkedHashMap<String, Any?> {
        bounded(input)
        val o = obj(input, listOf("v", "id", "title", "body", "ask", "connector"), listOf("v", "id", "title", "body"))
        val v = o["v"]
        if (!((v is Long && v == 1L) || (v is Int && v == 1) || (v is Double && v == 1.0))) fail(CardProblem.Version)
        if (o.containsKey("connector") && o.containsKey("ask")) fail()
        val result = LinkedHashMap<String, Any?>()
        result["v"] = 1L
        result["id"] = id(o["id"], CARD_ID)
        result["title"] = text(o["title"], 120, true, 1)
        result["body"] = arr(o["body"], 0, 40).map(::node)
        if (o.containsKey("ask")) result["ask"] = ask(o["ask"])
        if (o.containsKey("connector")) result["connector"] = connector(o["connector"])
        return result
    }
}

/**
 * The part of Dart `Uri.tryParse` the link rule needs: an absolute https URL with a host, no userinfo, a port up to
 * 65,535 and valid percent escapes. Anything this cannot read is refused.
 */
internal object SafeHttps {
    fun isSafe(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val rest = url.substring(8)
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, end)
        if ('@' in authority) return false
        if (!validEscapes(url)) return false
        val hostPort =
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return false
                val v6 = authority.substring(1, close)
                if (v6.isEmpty() || v6.any { !(it.isLetterOrDigit() && it.code < 128) && it != ':' && it != '.' }) return false
                v6 to authority.substring(close + 1).removePrefix(":").takeIf { authority.length > close + 1 }
            } else {
                val colon = authority.lastIndexOf(':')
                if (colon >= 0) authority.substring(0, colon) to authority.substring(colon + 1) else authority to null
            }
        val (host, port) = hostPort
        if (host.isEmpty()) return false
        if (!authority.startsWith("[") && host.any { !regNameChar(it) }) return false
        if (port != null && port.isNotEmpty()) {
            if (port.any { it !in '0'..'9' }) return false
            val n = port.toBigIntegerOrNull() ?: return false
            if (n > 65535) return false
        }
        return true
    }

    private fun String.toBigIntegerOrNull(): Long? = if (length > 18) Long.MAX_VALUE else toLongOrNull()

    private fun regNameChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~!$&'()*+,;=%" || c.code >= 0x80

    private fun validEscapes(url: String): Boolean {
        var i = url.indexOf('%')
        while (i >= 0) {
            if (i + 2 >= url.length || !hex(url[i + 1]) || !hex(url[i + 2])) return false
            i = url.indexOf('%', i + 3)
        }
        return true
    }

    private fun hex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
