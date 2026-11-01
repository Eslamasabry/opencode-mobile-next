package io.github.eslamasabry.oc.quality.sec

import java.io.File

/**
 * One scanned file of the native tree. [rel] is the path relative to the scanned root with `/` separators
 * (`core/security/src/commonMain/kotlin/.../Links.kt`), so a planted tree and the real tree look the same to a rule.
 */
data class SecFile(val rel: String, val text: String) {
    val name: String get() = rel.substringAfterLast('/')

    /** The module folder, `core/security` or `androidApp`; empty for a file outside a module. */
    val module: String
        get() {
            val idx = rel.indexOf("/src/")
            return if (idx < 0) "" else rel.substring(0, idx)
        }

    /** The source set folder (`commonMain`, `main`, `test`, `qa`, …), empty outside `src/`. */
    val sourceSet: String get() = rel.substringAfter("/src/", "").substringBefore('/')

    val isKotlin: Boolean get() = rel.endsWith(".kt") || rel.endsWith(".kts")
    val isXml: Boolean get() = rel.endsWith(".xml")
    val isTestSource: Boolean get() = sourceSet.contains("test", ignoreCase = true) || sourceSet == "benchmark"
    val isQaSource: Boolean get() = sourceSet == "qa" || sourceSet.startsWith("qa") || sourceSet.endsWith("Qa")

    /** Production code (plan 16 §2.1 "production code"): a product module's non-test, non-qa source set. */
    val isProduction: Boolean get() = isProductModule && rel.contains("/src/") && !isTestSource && !isQaSource

    /** Product modules: everything that can reach an APK or the desktop app. Not tools, spikes, quality or e2e. */
    val isProductModule: Boolean
        get() = PRODUCT_LAYERS.any { rel.startsWith("$it/") } || rel.startsWith("androidApp/") || rel.startsWith("desktopApp/")

    /** Kotlin text with comments removed; string literals kept (K-SEC-10 reads them). */
    val code: String by lazy { if (isKotlin) KotlinText.stripComments(text) else XmlText.stripComments(text) }

    /** Line of an offset into [code] (comment stripping keeps every line break, so it is the source line). */
    fun lineOf(offset: Int): Int = code.substring(0, offset.coerceIn(0, code.length)).count { it == '\n' } + 1

    companion object {
        val PRODUCT_LAYERS = listOf("core", "protocol", "engine", "ui", "feature", "android")
    }
}

/** A rule hit: rule id, file, line (in [SecFile.code], which keeps line breaks) and what was found. */
data class Violation(val rule: String, val file: String, val line: Int, val what: String) {
    /** The key `ksec-pending.txt` uses: rule, file and the subject before the first colon (no line number). */
    val key: String get() = "$rule $file ${what.substringBefore(':')}"

    override fun toString(): String = "$rule $file:$line $what"
}

/** Loads the scanned tree: every `.kt` and `.xml` under `src/` of the product modules (plus build files on request). */
object SecTree {
    private val skipDirs = setOf("build", ".gradle", ".kotlin", ".idea", "node_modules", ".git")

    fun load(root: File): List<SecFile> =
        root.walkTopDown()
            .onEnter { it.name !in skipDirs }
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".xml")) }
            .map { SecFile(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
            .filter { it.isProductModule && it.rel.contains("/src/") }
            .toList()

    /** Every `build.gradle.kts` of the tree and the convention sources (QaHooksAbsent reads them). */
    fun buildScripts(root: File): List<SecFile> =
        root.walkTopDown()
            .onEnter { it.name !in skipDirs }
            .filter { it.isFile && (it.name == "build.gradle.kts" || (it.name.endsWith(".kt") && "build-logic/" in it.invariantSeparatorsPath)) }
            .map { SecFile(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
            .toList()
}

/** Comment stripping that keeps offsets' line numbers (newlines survive) and leaves string literals intact. */
object KotlinText {
    fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var inString = false
        var raw = false
        while (i < src.length) {
            val c = src[i]
            val next = if (i + 1 < src.length) src[i + 1] else '\u0000'
            when {
                inString && raw -> {
                    if (src.startsWith("\"\"\"", i)) { out.append("\"\"\""); i += 3; inString = false; continue }
                    out.append(c)
                }
                inString -> {
                    if (c == '\\' && i + 1 < src.length) { out.append(c).append(next); i += 2; continue }
                    if (c == '"') inString = false
                    out.append(c)
                }
                src.startsWith("\"\"\"", i) -> { inString = true; raw = true; out.append("\"\"\""); i += 3; continue }
                c == '"' -> { inString = true; raw = false; out.append(c) }
                c == '\'' && i + 2 < src.length && (src[i + 2] == '\'' || (next == '\\' && i + 3 < src.length)) -> {
                    // a char literal: copy through its closing quote so '"' does not open a string
                    val end = src.indexOf('\'', i + if (next == '\\') 3 else 2)
                    if (end < 0) { out.append(c) } else { out.append(src, i, end + 1); i = end + 1; continue }
                }
                c == '/' && next == '/' -> {
                    while (i < src.length && src[i] != '\n') i++
                    continue
                }
                c == '/' && next == '*' -> {
                    var depth = 1
                    i += 2
                    while (i < src.length && depth > 0) {
                        if (src.startsWith("/*", i)) { depth++; i += 2; continue }
                        if (src.startsWith("*/", i)) { depth--; i += 2; continue }
                        if (src[i] == '\n') out.append('\n')
                        i++
                    }
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** The text inside the parentheses that open at [openParen] (balanced; strings skipped). */
    fun balancedArgs(code: String, openParen: Int): String {
        var depth = 0
        var i = openParen
        var inString = false
        while (i < code.length) {
            val c = code[i]
            if (inString) {
                if (c == '\\') { i += 2; continue }
                if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return code.substring(openParen + 1, i) }
            }
            i++
        }
        return code.substring(openParen + 1)
    }
}

object XmlText {
    fun stripComments(src: String): String =
        Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).replace(src) { m -> "\n".repeat(m.value.count { it == '\n' }) }
}

/** The K-SEC allowlists (`native/quality/config/sec/`). Lines are trimmed; `#` starts a comment. */
class SecConfig(private val dir: File) {
    private fun list(name: String): List<String> =
        File(dir, name).takeIf { it.isFile }?.readLines().orEmpty()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }

    /** Package prefixes of wire DTOs whose secret-named `String`s are decoded only to become a `Secret` at once. */
    val secretDtoPackages: List<String> by lazy { list("secret-dto-allowlist.txt") }

    /** Property names that keep a `String` type with a written reason (`monitorToken`). */
    val secretNames: Set<String> by lazy { list("secret-name-allowlist.txt").toSet() }

    /** File names that may call `Secret.reveal()`. */
    val secretRevealFiles: Set<String> by lazy { list("secret-reveal-allowlist.txt").toSet() }

    /** File names whose PendingIntents may be mutable (§1.8.2; 3 entries). */
    val mutablePendingIntentFiles: Set<String> by lazy { list("mutable-pendingintent-allowlist.txt").toSet() }

    /** Hosts allowed in URL literals until `KnownHosts` lists them (shrink-only). */
    val pendingKnownHosts: Set<String> by lazy { list("knownhosts-pending.txt").toSet() }

    /** Gate items not built yet (shrink-only; a listed item that now holds fails until it is removed). */
    val pending: Set<String> by lazy { list("sec-pending.txt").map { it.substringBefore(' ') }.toSet() }

    val exportedSurface: List<String> by lazy { list("exported-surface.txt") }

    /** K-SEC hits in code this unit does not own, each reported to its owner: `<rule> <file> <subject>`; shrink-only. */
    val ksecPending: Set<String> by lazy { list("ksec-pending.txt").toSet() }

    companion object {
        fun of(root: File): SecConfig = SecConfig(File(root, "quality/config/sec"))
    }
}
