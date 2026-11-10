package io.github.eslamasabry.oc.quality

/**
 * Privacy leak guard for the whole tracked tree (public repository).
 *
 * Fails on private infrastructure details (the build VPS address, the owner's tailnet addresses, the owner's personal
 * mail, the owner's adb serial, absolute home paths of the owner) and on token-shaped strings outside an explicit
 * allowlist of test-fixture files. The infrastructure rules have no allowlist at all; only `TOKEN-*` rules do.
 *
 * Every pattern is assembled from parts at runtime, so this file and its test pass their own gate.
 */
object PrivacyLeakGuard {
    data class Finding(val path: String, val rule: String)

    private val dot = "\\."
    private fun lit(vararg parts: String) = Regex(parts.joinToString(""))

    /** Rules with no allowlist: private details must not appear in any tracked text file. */
    val hardRules: Map<String, Regex> = linkedMapOf(
        "PRIVATE-VPS" to lit("169", dot, "58", dot, "71", dot, "211"),
        "PRIVATE-TAILNET-PC" to lit("100", dot, "126", dot, "15", dot, "6\\b"),
        "PRIVATE-TAILNET-DESKTOP" to lit("100", dot, "120", dot, "55", dot, "43\\b"),
        "PRIVATE-MAIL" to lit("soma", dot, "eas"),
        "PRIVATE-ADB-SERIAL" to lit("320437", "155564"),
        "PRIVATE-HOME-PATH" to lit("/home/", "es", "lam\\b"),
    )

    /** Token shapes; a file may hold them only when it is listed in the fixture allowlist. */
    val tokenRules: Map<String, Regex> = linkedMapOf(
        "TOKEN-GITHUB" to lit("\\b(gh[pousr]", "_[A-Za-z0-9]{36,}|github", "_pat_[A-Za-z0-9_]{50,})"),
        "TOKEN-API-KEY" to lit("\\bs", "k-(proj-|ant-)?[A-Za-z0-9_-]{32,}"),
        "TOKEN-AWS" to lit("\\b(AK", "IA|AS", "IA)[0-9A-Z]{16}\\b"),
        "TOKEN-SLACK" to lit("\\bxo", "x[abposr]-[A-Za-z0-9-]{20,}"),
        "TOKEN-GOOGLE" to lit("\\bAI", "za[0-9A-Za-z_-]{35}\\b"),
        "TOKEN-JWT" to lit("\\bey", "J[A-Za-z0-9_-]{15,}", dot, "ey", "J[A-Za-z0-9_-]{15,}", dot, "[A-Za-z0-9_-]{10,}"),
        "TOKEN-PRIVATE-KEY" to lit("-----", "BEGIN [A-Z ]*PRIVATE", " KEY-----"),
    )

    /** Rule ids that [text] trips, regardless of any allowlist. */
    fun rulesIn(text: String): Set<String> =
        (hardRules + tokenRules).filterValues { it.containsMatchIn(text) }.keys

    /**
     * Scans [files] (path to text). Hard rules always count; token rules count unless the path is in [tokenAllow].
     * Also reports, as `STALE-ALLOWLIST`, each allowlisted path that no longer holds a token shape (the list only shrinks).
     */
    fun check(files: Map<String, String>, tokenAllow: Set<String>): List<Finding> {
        val out = mutableListOf<Finding>()
        for ((path, text) in files) {
            for (rule in rulesIn(text)) {
                if (rule.startsWith("TOKEN-") && path in tokenAllow) continue
                out += Finding(path, rule)
            }
        }
        for (path in tokenAllow) {
            val text = files[path]
            if (text == null || rulesIn(text).none { it.startsWith("TOKEN-") }) out += Finding(path, "STALE-ALLOWLIST")
        }
        return out
    }
}
