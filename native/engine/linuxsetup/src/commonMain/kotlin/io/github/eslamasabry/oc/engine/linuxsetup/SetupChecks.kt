package io.github.eslamasabry.oc.engine.linuxsetup

/** The result of one check in [combinedCheckScript]'s output. */
public data class SetupCheckResult(val ok: Boolean, val version: String?)

/**
 * Every check in one shell run (port of `combinedCheckScript` in setup_scripts.dart): each runs alone in a subshell
 * with no input, between begin/end markers that carry its id and exit status.
 */
public fun combinedCheckScript(checks: Map<String, String>): String =
    buildString {
        for ((id, script) in checks) {
            append("printf '\\n::oc-check-begin %s\\n' '").append(id).append("'\n")
            append("(\n")
            append(script.trimEnd()).append('\n')
            append(") 2>/dev/null </dev/null\n")
            append("printf '\\n::oc-check-end %s %s\\n' '").append(id).append("' \"\$?\"\n")
        }
    }

/**
 * Reads [combinedCheckScript]'s output: which checks passed and the version each printed on its last non-blank line.
 * A check with no end marker (the script died) is absent: it did not pass.
 */
public fun parseCombinedChecks(output: String): Map<String, SetupCheckResult> {
    val results = linkedMapOf<String, SetupCheckResult>()
    var current: String? = null
    var lines = mutableListOf<String>()
    for (raw in output.split('\n')) {
        val line = raw.trimEnd()
        if (line.startsWith("::oc-check-begin ")) {
            current = line.removePrefix("::oc-check-begin ").trim()
            lines = mutableListOf()
            continue
        }
        if (line.startsWith("::oc-check-end ")) {
            val parts = line.removePrefix("::oc-check-end ").trim().split(' ')
            val id = current
            if (parts.size == 2 && id != null && parts[0] == id) {
                val version = lines.lastOrNull { it.isNotBlank() }?.trim()
                results[id] = SetupCheckResult(ok = parts[1] == "0", version = version?.ifEmpty { null })
            }
            current = null
            continue
        }
        if (current != null) lines.add(line)
    }
    return results
}
