package io.github.eslamasabry.oc.ui.gate.flicker

import java.io.File

/** A test the recorder fails: the findings that are neither allowed by name nor held by the module's baseline. */
public class FlickerFailure(public val test: String, public val findings: List<Finding>, mode: FlickerMode) :
    AssertionError(message(test, findings, mode)) {
    private companion object {
        const val LISTED = 20

        fun message(test: String, findings: List<Finding>, mode: FlickerMode): String {
            val held = if (mode == FlickerMode.FailNew) " that the module's flicker-baseline.tsv does not hold" else ""
            val shown = findings.take(LISTED).joinToString("\n") { "  $it" }
            val rest = if (findings.size > LISTED) "\n  … and ${findings.size - LISTED} more" else ""
            return "FLICKER $test: ${findings.size} finding(s)$held\n$shown$rest\n" +
                "Fix the cause. Intended motion is allowed by name in the test, with its reason: " +
                "kitUiTest(allow = setOf(Allow.blink(\"<words or tag>\", \"<why it is intended>\"))). " +
                "A baseline line is only for a finding that was there before the recorder; it is removed by its fix."
        }
    }
}

/**
 * Where the recorder's findings go and how a module judges them. Settings come from the test task
 * (gradle/flicker.gradle): `oc.flicker.mode` (report, fail-new, fail, off; auto or unset: fail-new when the
 * baseline file exists, else report), `oc.flicker.baseline` (the module's flicker-baseline.tsv), `oc.flicker.dir`
 * (build/flicker) and, on CI, `oc.flicker.out` (native/reports, printed into the job log as
 * `OC-VERDICT flicker-*.json`).
 */
internal object FlickerVerdict {
    private const val UNKNOWN_TEST = "unknown"
    private val baseline: Set<String> by lazy { readBaseline() }

    private fun baselineFile(): File = File(System.getProperty("oc.flicker.baseline") ?: "flicker-baseline.tsv")

    fun mode(asked: FlickerMode?): FlickerMode =
        asked ?: when (System.getProperty("oc.flicker.mode")) {
            "off" -> FlickerMode.Off
            "report" -> FlickerMode.Report
            "fail" -> FlickerMode.Fail
            "fail-new" -> FlickerMode.FailNew
            else -> if (baselineFile().isFile) FlickerMode.FailNew else FlickerMode.Report
        }

    /** The baseline line of [finding] in [test]: `<Class.test>\t<RULE>\t<key>`. */
    fun line(test: String, finding: Finding): String = "$test\t${finding.rule}\t${finding.key}"

    fun judge(test: String, found: List<Finding>, allow: Set<Allow>, mode: FlickerMode) {
        if (mode == FlickerMode.Off) return
        val open = found.filter { f -> allow.none { it.covers(f) } }
        FlickerLog.add(test, open, found.size - open.size, mode)
        val failing =
            when (mode) {
                FlickerMode.Fail -> open
                FlickerMode.FailNew -> open.filter { line(test, it) !in baseline }
                else -> emptyList()
            }
        if (failing.isNotEmpty()) throw FlickerFailure(test, failing, mode)
    }

    private fun readBaseline(): Set<String> =
        baselineFile().takeIf { it.isFile }?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t').take(BASELINE_FIELDS).joinToString("\t") }
            .toSet()

    private const val BASELINE_FIELDS = 3

    /** `<Class>.<test method>` of the test that called [kitUiTest]: the nearest caller annotated as a test. */
    fun callingTest(): String {
        val call = Thread.currentThread().stackTrace.firstOrNull(::isTest)
        val type = call?.className?.substringAfterLast('.')?.substringBefore('$')
        return call?.let { "$type.${it.methodName}" } ?: UNKNOWN_TEST
    }

    private fun isTest(element: StackTraceElement): Boolean {
        val loader = FlickerVerdict::class.java.classLoader
        val type = runCatching { Class.forName(element.className, false, loader) }.getOrNull()
        return type?.declaredMethods.orEmpty().any { m ->
            m.name == element.methodName && m.annotations.any { it.annotationClass.java.name.endsWith(".Test") }
        }
    }
}

/**
 * Everything the recorder found in this test process, grouped by test, rule and words, written after each test:
 * `findings-<pid>.tsv` (and a baseline candidate) under `oc.flicker.dir`, and on CI `flicker-<module>-<pid>.json` under
 * `oc.flicker.out`.
 */
internal object FlickerLog {
    private class Entry(val test: String, val finding: Finding) {
        var n = 1
    }

    private val entries = LinkedHashMap<String, Entry>()
    private var tests = 0
    private var allowed = 0
    private const val DETAIL_CHARS = 200
    private const val PART_LIMIT = 150_000

    @Synchronized
    fun add(test: String, open: List<Finding>, allowedNow: Int, mode: FlickerMode) {
        tests++
        allowed += allowedNow
        open.forEach { f ->
            val key = FlickerVerdict.line(test, f)
            val entry = entries[key]
            if (entry == null) entries[key] = Entry(test, f) else entry.n++
        }
        write(mode)
    }

    private fun write(mode: FlickerMode) {
        val pid = ProcessHandle.current().pid()
        val dir = File(System.getProperty("oc.flicker.dir") ?: "build/flicker").apply { mkdirs() }
        val rows = entries.map { (key, e) -> "$key\t${e.n}\t${e.finding.detail.replace('\t', ' ').take(DETAIL_CHARS)}" }
        val head = "# test\trule\tkey\tcount\tdetail (mode ${modeName(mode)})\n"
        File(dir, "findings-$pid.tsv").writeText(head + rows.joinToString("") { "$it\n" })
        File(dir, "baseline-candidate-$pid.tsv").writeText(entries.keys.joinToString("") { "$it\n" })
        val out = System.getProperty("oc.flicker.out") ?: return
        val module = (System.getProperty("oc.flicker.module") ?: "module").trim(':').replace(':', '-')
        // A CI job prints each file into its log only up to 200 KB, so the findings go out in parts.
        json(module, mode).forEachIndexed { i, part ->
            File(out).apply { mkdirs() }.resolve("flicker-$module-$pid-$i.json").writeText(part)
        }
    }

    private fun modeName(mode: FlickerMode): String =
        when (mode) {
            FlickerMode.Off -> "off"
            FlickerMode.Report -> "report"
            FlickerMode.FailNew -> "fail-new"
            FlickerMode.Fail -> "fail"
        }

    private fun json(module: String, mode: FlickerMode): List<String> {
        val rows =
            entries.values.map { e ->
                val f = e.finding
                val detail = quote(f.detail.take(DETAIL_CHARS))
                "{\"test\":${quote(e.test)},\"rule\":\"${f.rule}\",\"key\":${quote(f.key)}," +
                    "\"n\":${e.n},\"detail\":$detail}"
            }
        val parts = mutableListOf(mutableListOf<String>())
        var size = 0
        rows.forEach { row ->
            if (size + row.length > PART_LIMIT && parts.last().isNotEmpty()) {
                parts += mutableListOf<String>()
                size = 0
            }
            parts.last() += row
            size += row.length + 1
        }
        val head =
            "{\"module\":${quote(module)},\"mode\":\"${modeName(mode)}\"," +
                "\"tests\":$tests,\"allowed\":$allowed,\"findings\":["
        return parts.map { head + it.joinToString(",") + "]}" }
    }

    private fun quote(s: String): String {
        val b = StringBuilder("\"")
        s.forEach { c ->
            when {
                c == '"' || c == '\\' -> b.append('\\').append(c)
                c < ' ' -> b.append("\\u%04x".format(c.code))
                else -> b.append(c)
            }
        }
        return b.append('"').toString()
    }
}
