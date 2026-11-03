package io.github.eslamasabry.oc.feature.about.report

/**
 * Text made safe for a public issue (Flutter `problemReportScrub` over `KitRedact`): provider keys, tokens, passwords
 * and URL user-info are masked, then server addresses and long opaque tokens. The report becomes public on GitHub, so
 * a server's host or address never goes in it. [extra] runs first: the app passes `:core:security` Redactor there
 * (registered secrets); a feature may not depend on it, so the fixed rules below are this module's own floor.
 */
public class ReportScrub(private val extra: (String) -> String = { it }) {
    public fun scrub(text: String): String {
        var safe = extra(text)
        safe = PROVIDER_KEY.replace(safe, MASK)
        safe = BEARER.replace(safe) { "${it.groupValues[1]}$MASK" }
        safe = NAMED_SECRET.replace(safe) { "${it.groupValues[1]}${it.groupValues[2]}$MASK" }
        safe = USER_INFO.replace(safe) { it.groupValues[1] }
        // scheme://host:port → scheme://[server]; the path stays (it says which call failed).
        safe = ORIGIN.replace(safe) { "${it.groupValues[1]}[server]" }
        safe = IPV4.replace(safe, "[address]")
        safe = OPAQUE.replace(safe, MASK)
        return safe
    }

    /** Terminal colour and title escapes and CR line ends, removed before a log is kept (Flutter `_clean`). */
    public fun clean(text: String): String =
        scrub(text.replace(OSC, "").replace(CSI, "").replace("\r\n", "\n").replace('\r', '\n'))

    public companion object {
        public const val MASK: String = "••••"
        private val PROVIDER_KEY = Regex("""\b(?:sk|pk|rk)-[A-Za-z0-9_-]{8,}""")
        private val BEARER = Regex("""(?i)\b(bearer\s+)[A-Za-z0-9._~+/=-]+""")
        private val NAMED_SECRET =
            Regex("""(?i)\b(token|access_token|password|passwd|secret|api[_-]?key|auth|authorization)(\s*[=:]\s*)[^\s&"']+""")
        private val USER_INFO = Regex("""\b([a-zA-Z][a-zA-Z0-9+.-]*://)[^/\s?#@"<>]*@""")
        private val ORIGIN = Regex("""\b([a-zA-Z][a-zA-Z0-9+.-]*://)([^/\s?#"<>]+)""")
        private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b""")
        private val OPAQUE = Regex("""\b[A-Za-z0-9_-]{32,}\b""")
        private val OSC = Regex("\u001B\\][^\u0007]*(?:\u0007|\u001B\\\\)")
        private val CSI = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    }
}
