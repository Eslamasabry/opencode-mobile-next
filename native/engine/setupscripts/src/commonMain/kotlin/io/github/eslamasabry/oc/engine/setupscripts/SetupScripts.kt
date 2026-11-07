package io.github.eslamasabry.oc.engine.setupscripts

// Port of the functions in lib/builtin/setup/setup_scripts.dart.

/** [script] as the job runs it: the prelude, the component-update helpers, then the script. */
public fun withSetupPrelude(script: String): String = SETUP_PRELUDE + COMPONENT_UPDATE_PRELUDE + script

/**
 * One script that runs every check in [checks] (id → check script) in its own subshell and marks where each starts and
 * how it ended, so the engine pays for one proot start instead of one per component.
 */
public fun combinedCheckScript(checks: Map<String, String>): String =
    buildString {
        for ((id, check) in checks) {
            append("printf '\\n::oc-check-begin %s\\n' '").append(id).append("'\n")
            append("(\n")
            append(check.trimEnd()).append('\n')
            append(") 2>/dev/null </dev/null\n")
            append("printf '\\n::oc-check-end %s %s\\n' '").append(id).append("' \"$?\"\n")
        }
    }

/** The result of one check in [combinedCheckScript]'s output. */
public data class SetupCheckResult(val ok: Boolean, val version: String?)

/**
 * Reads [combinedCheckScript]'s output: which checks passed and the version each printed on its last non-empty line. A
 * check with no end marker (the script died) did not pass and is absent.
 */
public fun parseCombinedChecks(output: String): Map<String, SetupCheckResult> {
    val results = linkedMapOf<String, SetupCheckResult>()
    var current: String? = null
    var lines = mutableListOf<String>()
    for (raw in output.split('\n')) {
        val line = raw.trimEnd()
        if (line.startsWith(BEGIN)) {
            current = line.substring(BEGIN.length).trim()
            lines = mutableListOf()
            continue
        }
        if (line.startsWith(END)) {
            val parts = line.substring(END.length).trim().split(' ')
            if (parts.size == 2 && parts[0] == current) {
                val version = lines.lastOrNull { it.isNotBlank() }?.trim()
                results[current] = SetupCheckResult(ok = parts[1] == "0", version = version?.ifEmpty { null })
            }
            current = null
            continue
        }
        if (current != null) lines += line
    }
    return results
}

private const val BEGIN = "::oc-check-begin "
private const val END = "::oc-check-end "
