package io.github.eslamasabry.oc.ui.gate

import androidx.compose.ui.semantics.SemanticsNode
import java.io.File
import java.io.RandomAccessFile

/**
 * The alignment gate, JVM layer (rules: native/docs/kit/ALIGN-RULES.md, [AlignRules]). [LayoutGuard.check] hands it
 * the phone window's light render of every gate shot, so every screen, sheet and kit scene with a golden is measured
 * on every desktopTest run, with no extra render.
 *
 * - Findings of each shot: `looks-out/align/<module>/<shot>.tsv` (`shot rule subject detail`), and the shot's start
 *   edges with their counts in `<shot>.edges.tsv`.
 * - Baseline: `native/config/align/baseline/<module>.tsv` (`shot rule subject`). A finding it does not list fails
 *   the test; the file may only shrink (`native/tool/qa/align_baseline.sh`).
 * - `OC_ALIGN_BASELINE=update` rewrites the baseline lines of the shots a run checked; `OC_ALIGN=report` writes the
 *   findings and never fails (for a sweep of a branch whose baseline is stale).
 */
object AlignGuard {
    private val root: File = File(System.getProperty("user.dir"), "../../..").canonicalFile
    private val native: File = File(root, "native")
    private val module: String =
        File(System.getProperty("user.dir")).canonicalFile.relativeToOrNull(native.canonicalFile)?.path
            ?.replace(File.separatorChar, '-')?.ifEmpty { null } ?: "unknown"
    private val baselineFile: File = File(native, "config/align/baseline/$module.tsv")
    private val out: File = File(root, "looks-out/align/$module")

    /** The findings and start edges of one rendered window, in dp. */
    class Result(val findings: List<AlignRules.Finding>, val edges: Map<Float, Int>)

    fun inspect(roots: List<SemanticsNode>, w: Int, h: Int, screen: Boolean): Result {
        val nodes = AlignRead.nodes(roots)
        val edges = if (screen) AlignRules.startEdges(nodes, w.toFloat(), h.toFloat()) else emptyMap()
        return Result(AlignRules.check(nodes, w.toFloat(), h.toFloat(), screen), edges)
    }

    /** Records [result] for [shot]; returns the failure message for findings the baseline does not list, or null. */
    fun verdict(shot: String, result: Result): String? {
        val found = result.findings
        val keys = found.map { "$shot\t${it.key}" }.toSortedSet()
        out.mkdirs()
        File(out, "$shot.tsv").writeText(found.joinToString("") { "$shot\t${it.key}\t${it.detail}\n" })
        val edges = result.edges.entries.joinToString("") { "${AlignRules.fmt(it.key)}\t${it.value}\n" }
        File(out, "$shot.edges.tsv").writeText(edges)
        if (System.getenv("OC_ALIGN_BASELINE") != "update") return judge(shot, found, keys)
        rewriteBaseline(shot, keys)
        return null
    }

    private fun judge(shot: String, found: List<AlignRules.Finding>, keys: Set<String>): String? {
        val listed = readBaseline().filter { it.startsWith("$shot\t") }.toSet()
        val stale = listed - keys
        if (stale.isNotEmpty()) {
            System.err.println("align gate: $shot no longer breaks ${stale.size} baselined rule(s); shrink it")
        }
        val fresh = found.filter { "$shot\t${it.key}" !in listed }.distinctBy { it.key }
        if (fresh.isEmpty() || System.getenv("OC_ALIGN") == "report") return null
        return "align gate: $shot breaks ${fresh.size} alignment rule(s) (native/docs/kit/ALIGN-RULES.md):\n" +
            fresh.joinToString("\n") { "  ${it.rule} ${it.subject}: ${it.detail}" } +
            "\nFix the screen (or the kit part when one cause breaks many screens). The baseline only shrinks."
    }

    private fun readBaseline(): List<String> =
        if (!baselineFile.isFile) emptyList()
        else baselineFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    /** Replaces [shot]'s lines with [keys] under a file lock (test classes of one module run in parallel forks). */
    private fun rewriteBaseline(shot: String, keys: Set<String>) {
        baselineFile.parentFile.mkdirs()
        RandomAccessFile(File(out.also { it.mkdirs() }, ".baseline.lock"), "rw").use { lock ->
            lock.channel.lock().use {
                val lines = (readBaseline().filterNot { it.startsWith("$shot\t") } + keys).toSortedSet()
                if (lines.isEmpty()) {
                    baselineFile.delete()
                } else {
                    baselineFile.writeText(
                        "# Alignment gate baseline of one module (native/docs/kit/ALIGN-RULES.md). Only shrinks.\n" +
                            "# shot\trule\tsubject\n" + lines.joinToString("\n", postfix = "\n"),
                    )
                }
            }
        }
    }
}
