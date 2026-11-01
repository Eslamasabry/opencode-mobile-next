package io.github.eslamasabry.oc.quality

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scope file gate (plan 02 §3.6, plan 14 §6.5 table "ScopeFileTest", T2).
 *
 * Inputs, all under `native/catalog/`:
 * - `behaviors.jsonl`: the append-only catalog copy (original 1,544 rows, then top-up rows);
 * - `behaviors.lock`: one `id<TAB>sha256(line)` per committed row, in file order (the append-only anchor);
 * - `scope.jsonl`: one line per row with scope, slice, module, decision, reason and amendments.
 * The first rows are also compared with the frozen plan copy `docs/design/native-rewrite-2026-11-01/catalog/`.
 *
 * Rule list (02 §3.6): every id once; `decision` when scope != switch or amended; `reason` when scope != switch;
 * `slice` when switch; ids match the 02 §6.1 form; no committed catalog line changed.
 */
class ScopeFileTest {
    private val root = File(System.getProperty("oc.root"))
    private val catalog = File(root, "catalog")
    private val frozen = File(root.parentFile, "docs/design/native-rewrite-2026-11-01/catalog/behaviors.jsonl")

    private fun real() = ScopeFile(
        behaviors = File(catalog, "behaviors.jsonl").readLines(),
        lock = File(catalog, "behaviors.lock").readLines(),
        scope = File(catalog, "scope.jsonl").readLines(),
        frozen = if (frozen.isFile) frozen.readLines() else null,
    )

    @Test
    fun `the committed scope file has no problems`() {
        val problems = real().problems()
        assertTrue(problems.isEmpty(), "scope.jsonl problems (${problems.size}):\n" + problems.take(40).joinToString("\n"))
    }

    @Test
    fun `the frozen plan catalog is present and the copy starts with it`() {
        assertTrue(frozen.isFile, "missing $frozen")
        assertEquals(1544, frozen.readLines().count { it.isNotBlank() })
    }

    // ---- planted errors: each rule fails on its own -----------------------------------------------------------------

    private fun planted(edit: (MutableList<String>) -> Unit, of: String): List<String> {
        val f = real()
        val behaviors = f.behaviors.toMutableList()
        val scope = f.scope.toMutableList()
        val lock = f.lock.toMutableList()
        when (of) {
            "scope" -> edit(scope)
            "behaviors" -> edit(behaviors)
            "lock" -> edit(lock)
        }
        return f.copy(behaviors = behaviors, scope = scope, lock = lock).problems()
    }

    private fun List<String>.mustName(fragment: String) =
        assertTrue(any { fragment in it }, "expected a problem naming '$fragment', got:\n" + take(10).joinToString("\n"))

    private fun firstLine(lines: List<String>, scope: String) = lines.indexOfFirst { "\"scope\":\"$scope\"" in it }

    @Test
    fun `a missing row is caught`() =
        planted({ it.removeAt(0) }, "scope").mustName("missing from scope.jsonl")

    @Test
    fun `a duplicated row is caught`() =
        planted({ it.add(it[0]) }, "scope").mustName("more than once")

    @Test
    fun `an unknown id is caught`() =
        planted({ it.add("""{"id":"B-NAV-999","scope":"switch","slice":1,"module":":ui:kit"}""") }, "scope")
            .mustName("not in behaviors.jsonl")

    @Test
    fun `a malformed id is caught`() =
        planted({ it.add("""{"id":"B-FOO-1","scope":"switch","slice":1,"module":":ui:kit"}""") }, "scope")
            .mustName("id does not match")

    @Test
    fun `a switch row without a slice is caught`() =
        planted({ val i = firstLine(it, "switch"); it[i] = it[i].replace(Regex("\"slice\":\\d"), "\"slice\":null") }, "scope")
            .mustName("needs a slice")

    @Test
    fun `a moved-out row without a decision or reason is caught`() {
        val p = planted({
            val i = firstLine(it, "after-switch")
            it[i] = it[i].replace(Regex(",\"decision\":\"[^\"]*\""), "").replace(Regex(",\"reason\":\"[^\"]*\""), "")
        }, "scope")
        p.mustName("needs a decision")
        p.mustName("needs a reason")
    }

    @Test
    fun `an amendment without a decision is caught`() =
        planted({
            val i = it.indexOfFirst { l -> "\"amended\"" in l && "\"scope\":\"switch\"" in l }
            it[i] = it[i].replace(Regex(",\"decision\":\"[^\"]*\""), "")
        }, "scope").mustName("needs a decision")

    @Test
    fun `an unknown scope value is caught`() =
        planted({ it[0] = it[0].replace("\"scope\":\"switch\"", "\"scope\":\"later\"") }, "scope")
            .mustName("unknown scope")

    @Test
    fun `an edited committed catalog line is caught`() =
        planted({ it[10] = it[10].replace("\"behavior\": \"", "\"behavior\": \"X") }, "behaviors")
            .mustName("committed line changed")

    @Test
    fun `a removed committed catalog line is caught`() =
        planted({ it.removeAt(it.size - 1) }, "behaviors").mustName("committed line")

    @Test
    fun `an appended row without a lock entry is caught`() {
        val p = planted({
            it.add("""{"id": "B-NAV-999", "area": "NAV", "behavior": "x", "given_when_then": "x", "native_level": "unit", "portable_e2e": false, "sources": ["test/x_test.dart:1"], "sources_commit": "fc5c1e78f"}""")
        }, "behaviors")
        p.mustName("not in behaviors.lock")
    }
}

/** The pure checker, so planted errors can be tested without touching the files. */
data class ScopeFile(
    val behaviors: List<String>,
    val lock: List<String>,
    val scope: List<String>,
    val frozen: List<String>?,
) {
    fun problems(): List<String> {
        val out = mutableListOf<String>()
        val catalogLines = behaviors.filter { it.isNotBlank() }
        val rows = catalogLines.mapIndexedNotNull { n, line ->
            runCatching { MiniJson.obj(line) }.getOrElse { out += "behaviors.jsonl:${n + 1}: not JSON (${it.message})"; null }
        }
        val ids = rows.map { it.str("id") ?: "" }

        // Catalog rows: well-formed, unique, area matches id, top-up rows name their commit.
        rows.forEachIndexed { n, r ->
            val id = r.str("id") ?: ""
            if (!ID.matches(id)) out += "behaviors.jsonl:${n + 1}: id does not match B-<AREA>-<NNN>: '$id'"
            else if (r.str("area") != id.split('-')[1]) out += "$id: area '${r.str("area")}' does not match the id"
            if (r.str("native_level") !in LEVELS) out += "$id: unknown native_level '${r.str("native_level")}'"
            listOf("behavior", "given_when_then").forEach { if (r.str(it).isNullOrBlank()) out += "$id: empty $it" }
            if ((r["sources"] as? List<*>).isNullOrEmpty()) out += "$id: no sources"
            if (frozen != null && n >= frozen.count { it.isNotBlank() } && r.str("sources_commit").isNullOrBlank()) {
                out += "$id: a top-up row needs sources_commit (02 §3.5)"
            }
        }
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { out += "behaviors.jsonl: id $it more than once" }

        // Append-only: the frozen plan copy is a prefix, and every committed line matches its lock hash in order.
        if (frozen != null) {
            val f = frozen.filter { it.isNotBlank() }
            f.forEachIndexed { i, line ->
                if (i >= catalogLines.size) { out += "behaviors.jsonl: committed line ${i + 1} removed (frozen plan copy)"; return@forEachIndexed }
                if (catalogLines[i] != line) out += "behaviors.jsonl:${i + 1}: committed line changed (differs from the frozen plan copy)"
            }
        }
        val locked = lock.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        locked.forEachIndexed { i, parts ->
            if (parts.size != 2) { out += "behaviors.lock:${i + 1}: expected id<TAB>sha256"; return@forEachIndexed }
            val line = catalogLines.getOrNull(i)
            when {
                line == null -> out += "behaviors.jsonl: committed line ${i + 1} (${parts[0]}) removed"
                ids.getOrNull(i) != parts[0] -> out += "behaviors.jsonl:${i + 1}: committed line changed (expected ${parts[0]}, found ${ids.getOrNull(i)})"
                sha256(line) != parts[1] -> out += "behaviors.jsonl:${i + 1}: committed line changed (${parts[0]} hash differs from behaviors.lock)"
            }
        }
        ids.drop(locked.size).forEach { out += "$it: not in behaviors.lock (append it with seed_scope.py --lock)" }

        // Scope rows.
        val idSet = ids.toSet()
        val seen = mutableMapOf<String, Int>()
        scope.filter { it.isNotBlank() }.forEachIndexed { n, line ->
            val s = runCatching { MiniJson.obj(line) }.getOrElse { out += "scope.jsonl:${n + 1}: not JSON (${it.message})"; return@forEachIndexed }
            val id = s.str("id") ?: ""
            seen[id] = (seen[id] ?: 0) + 1
            if (!ID.matches(id)) out += "scope.jsonl:${n + 1}: id does not match B-<AREA>-<NNN>: '$id'"
            else if (id !in idSet) out += "scope.jsonl:${n + 1}: $id not in behaviors.jsonl"
            s.keys.filter { it !in SCOPE_KEYS }.forEach { out += "$id: unknown key '$it'" }
            val scopeValue = s.str("scope")
            if (scopeValue !in SCOPES) out += "$id: unknown scope '$scopeValue'"
            val slice = s["slice"]
            val amended = s.str("amended")
            if (scopeValue == "switch" && slice == null) out += "$id: a switch row needs a slice"
            if (slice != null && (slice !is Long || slice !in 0L..6L)) out += "$id: slice must be 0..6, got $slice"
            if (scopeValue == "switch" && s.str("module").isNullOrBlank()) out += "$id: a switch row needs a module"
            s.str("module")?.let { if (!MODULE.matches(it)) out += "$id: module '$it' is not a Gradle path" }
            if ((scopeValue != "switch" || amended != null) && s.str("decision").isNullOrBlank()) out += "$id: needs a decision"
            if (scopeValue != "switch" && s.str("reason").isNullOrBlank()) out += "$id: needs a reason"
            if (amended != null && amended.isBlank()) out += "$id: empty amendment"
            if (amended != null && s.str("amended_at")?.let(DATE::matches) != true) out += "$id: an amendment needs amended_at (YYYY-MM-DD)"
            s.str("split_from")?.let { if (it !in idSet) out += "$id: split_from $it is not a row" }
        }
        seen.filterValues { it > 1 }.keys.forEach { out += "scope.jsonl: $it more than once" }
        ids.filter { it !in seen }.forEach { out += "$it: missing from scope.jsonl" }
        return out
    }

    private fun Map<String, Any?>.str(key: String) = this[key] as? String

    companion object {
        val ID = Regex("B-(NAV|CHAT|AGENT|PROTO|STATE|SEC|ANDR|LINUX|MEDIA|KIT|L10N|XPLAT|BUILD|MIGR)-\\d{3}")
        val MODULE = Regex(":[A-Za-z][A-Za-z0-9-]*(:[a-z][a-z0-9-]*)?")
        val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        val SCOPES = setOf("switch", "after-switch", "desktop-phase", "ios-phase", "dropped")
        val LEVELS = setOf("unit", "contract", "integration", "screenshot", "migration", "e2e")
        val SCOPE_KEYS = setOf("id", "scope", "slice", "module", "decision", "reason", "amended", "amended_at", "split_from")

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** A small strict JSON reader (objects, arrays, strings, integers, booleans, null): :quality has no JSON library. */
object MiniJson {
    @Suppress("UNCHECKED_CAST")
    fun obj(text: String): Map<String, Any?> = Reader(text).run { val v = value(); end(); v as? Map<String, Any?> ?: error("not an object") }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }
        fun end() { ws(); if (i != s.length) error("trailing text at $i") }
        fun value(): Any? {
            ws()
            if (i >= s.length) error("unexpected end")
            return when (s[i]) {
                '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws()
                    if (s[i] == '}') { i++; return m }
                    while (true) {
                        ws()
                        val k = value() as? String ?: error("key at $i")
                        if (m.containsKey(k)) error("duplicate key '$k'")
                        ws(); expect(':')
                        m[k] = value()
                        ws(); if (s[i] == ',') { i++; continue }; expect('}'); return m
                    } }
                '[' -> { i++; val l = mutableListOf<Any?>(); ws()
                    if (s[i] == ']') { i++; return l }
                    while (true) { l += value(); ws(); if (s[i] == ',') { i++; continue }; expect(']'); return l } }
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> number()
            }
        }
        fun expect(c: Char) { if (i >= s.length || s[i] != c) error("expected '$c' at $i"); i++ }
        fun word(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) error("bad literal at $i"); i += w.length; return v }
        fun number(): Long {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] in ".eE") error("only integers are expected, at $start")
            return s.substring(start, i).toLongOrNull() ?: error("bad number at $start")
        }
        fun string(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) error("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        '"', '\\', '/' -> b.append(e)
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000C')
                        'n' -> b.append('\n')
                        'r' -> b.append('\r')
                        't' -> b.append('\t')
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> error("bad escape at $i")
                    }
                    else -> if (c < ' ') error("control character in string at $i") else b.append(c)
                }
            }
        }
    }
}
