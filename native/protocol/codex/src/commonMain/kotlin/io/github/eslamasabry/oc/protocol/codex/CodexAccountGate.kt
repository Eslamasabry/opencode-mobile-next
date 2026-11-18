package io.github.eslamasabry.oc.protocol.codex

/**
 * Whether a Codex app-server may be asked its account questions (sign-in, account read, rate limits): ONE rule for both
 * connections ([io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport] and [CodexReads]), read from the
 * `userAgent` of `initialize` (`<originator>/<version> (<os> ...)`).
 *
 * ON when the version after the first `/` is a plain `X.Y.Z` that is at least [FLOOR_TEXT]. OFF for an older version,
 * a prerelease (`0.171.0-alpha.1`), an unreadable string and no version at all. The floor is the version whose account
 * schema was verified (0.153.4); Codex's own stable schema grew only additively up to 0.170.0 (versions-301 report),
 * so a newer server keeps the same eight account names. There is no capability list to ask instead: `initialize`
 * answers no feature flags, and an error from an account read does not mean "unsupported".
 */
internal object CodexAccountGate {
    const val FLOOR_TEXT: String = "0.153.4"
    private val FLOOR = intArrayOf(0, 153, 4)
    private val PLAIN = Regex("""^(\d{1,4})\.(\d{1,4})\.(\d{1,4})$""")

    fun supported(userAgent: String?): Boolean {
        if (userAgent == null) return false
        val rest = if ('/' in userAgent) userAgent.substringAfter('/') else userAgent
        val token = rest.trimStart().substringBefore(' ').substringBefore('\t')
        val parts = PLAIN.matchEntire(token)?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
        for (i in FLOOR.indices) {
            if (parts[i] != FLOOR[i]) return parts[i] > FLOOR[i]
        }
        return true
    }

    /** The plan names Codex's stable schema has used; any other value (a plan newer than this app) is not shown. */
    val KNOWN_PLANS: Set<String> = setOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")

    /** The plan as the person may be shown it, or null when the server's value is one this app does not know. */
    fun shownPlan(planType: String?): String? = planType?.takeIf { it.lowercase() in KNOWN_PLANS }
}
