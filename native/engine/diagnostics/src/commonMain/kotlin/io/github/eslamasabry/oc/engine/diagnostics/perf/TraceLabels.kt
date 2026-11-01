package io.github.eslamasabry.oc.engine.diagnostics.perf

/** Shareable labels for traced work: no ids, no query values, no script bodies (B-SEC-117). */
public object TraceLabels {
    /** The phone's server password file; a script that writes it is only ever "write-password". */
    public const val PASSWORD_FILE: String = "/root/.oc-builtin/server.password"
    public const val PASSWORD_SCRIPT_LABEL: String = "write-password"

    private val PREFIXED_ID = Regex("^[a-z]{2,5}_[A-Za-z0-9]{6,}$")
    private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    private val DIGITS = Regex("^\\d+$")
    private val HEXISH = Regex("^[0-9a-f]{12,}$", RegexOption.IGNORE_CASE)
    private val DIGIT = Regex("\\d")
    private val QUERY = Regex("[?#]")
    private val SINGLE_QUOTED = Regex("'[^']*'")
    private val DOUBLE_QUOTED = Regex("\"[^\"]*\"")

    /**
     * The route-shaped template of a request path: ids and other opaque segments become `:id`, the query is dropped.
     * `/session/ses_4f…/message?limit=1` → `/session/:id/message`.
     */
    public fun pathTemplate(path: String): String {
        var raw = path
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd != -1) {
            val pathStart = raw.indexOf('/', schemeEnd + 3)
            raw = if (pathStart == -1) "/" else raw.substring(pathStart)
        }
        QUERY.find(raw)?.let { raw = raw.substring(0, it.range.first) }
        val template = raw.split('/').joinToString("/") { if (it.isEmpty() || !opaque(it)) it else ":id" }
        return template.ifEmpty { "/" }
    }

    private fun opaque(segment: String): Boolean =
        PREFIXED_ID.matches(segment) || UUID.matches(segment) || DIGITS.matches(segment) || HEXISH.matches(segment) ||
            // Percent-encoded paths and anything long and mixed are data, not route words.
            '%' in segment || (segment.length >= 20 && DIGIT.containsMatchIn(segment))

    /**
     * A short name for a shell script: its first line that does real work, quoted text blanked. A script that touches
     * [passwordFile]'s temp file is only ever [PASSWORD_SCRIPT_LABEL]: its body holds the password.
     */
    public fun scriptLabel(script: String, passwordFile: String = PASSWORD_FILE): String {
        if ("$passwordFile.tmp" in script) return PASSWORD_SCRIPT_LABEL
        for (raw in script.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("set ") || line.startsWith("umask ") ||
                line.startsWith("export ")
            ) {
                continue
            }
            val blanked = line.replace(SINGLE_QUOTED, "'…'").replace(DOUBLE_QUOTED, "\"…\"")
            return if (blanked.length > 60) blanked.substring(0, 59) + "…" else blanked
        }
        return "script"
    }
}
