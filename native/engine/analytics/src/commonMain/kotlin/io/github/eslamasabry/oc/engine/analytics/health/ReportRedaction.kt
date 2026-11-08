package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.core.security.Redactor

/**
 * The redaction every "Send a problem report" text goes through before the person sees it (section E of
 * docs/telemetry/health-and-diagnostics.md; Flutter 2.1.5 `lib/telemetry/report_redaction.dart`, rule for rule).
 *
 * It starts from the app's [Redactor] (provider keys, tokens, Bearer values, secret assignments, user-info, JWTs and
 * the secrets registered at run time) and then blanks what a log line can still carry about the person's setup:
 * - URLs, whole: `https://host:port/path?query` becomes `[url]`;
 * - e-mail addresses;
 * - IPv4 and IPv6 addresses;
 * - file paths: absolute (`/data/user/0/...`), home (`~/...`) and Windows (`C:\...`);
 * - host names: a dotted name ending in a common top-level domain, or in `.local`/`.lan`/`.internal`, or any
 *   `*.ts.net` name;
 * - long opaque tokens (32 or more letters and digits in one run).
 *
 * Code locations are kept, since a crash is read by them: Dart `package:`/`dart:` libraries (lines a Flutter log
 * carried over) and Kotlin frames (`Foo.kt:12`).
 */
public class ReportRedaction(private val redactor: Redactor) {
    /** [text] with everything listed above blanked. Idempotent. */
    public fun redact(text: String): String {
        // Park the code locations so the host and path rules cannot touch them.
        val parked = mutableListOf<String>()
        var safe =
            CODE_LOCATION.replace(text) { m ->
                parked += m.value
                "\u0000${parked.size - 1}\u0000"
            }
        safe = redactor.redact(safe)
        safe = URL.replace(safe, URL_MARK)
        safe = EMAIL.replace(safe, EMAIL_MARK)
        safe = IPV4.replace(safe, ADDRESS_MARK)
        safe = IPV6.replace(safe, ADDRESS_MARK)
        safe = WINDOWS_PATH.replace(safe, PATH_MARK)
        safe = UNIX_PATH.replace(safe, PATH_MARK)
        safe = HOST.replace(safe, HOST_MARK)
        safe = OPAQUE.replace(safe, Redactor.MASK)
        return PARKED.replace(safe) { m -> parked[m.groupValues[1].toInt()] }
    }

    public companion object {
        public const val URL_MARK: String = "[url]"
        public const val PATH_MARK: String = "[path]"
        public const val HOST_MARK: String = "[host]"
        public const val ADDRESS_MARK: String = "[address]"
        public const val EMAIL_MARK: String = "[email]"

        private val CODE_LOCATION =
            Regex("""(?:package|dart):[A-Za-z0-9_./\-]+(?::\d+){0,2}|\b[A-Za-z0-9_$]+\.(?:kt|java):\d{1,6}\b""")
        private val PARKED = Regex("\u0000(\\d+)\u0000")
        private val URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s"'<>)\]]+""")
        private val EMAIL = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)+\b""")
        private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b""")
        // Four or more groups, or a `::` form. Unlike Flutter's rule (two or more groups), a log line's `12:00:01` time
        // is not an address and stays readable.
        private val IPV6 =
            Regex(
                """(?<![\w:])(?:(?:[0-9A-Fa-f]{1,4}:){3,7}[0-9A-Fa-f]{1,4}|(?:[0-9A-Fa-f]{1,4}:){0,6}[0-9A-Fa-f]{0,4}::(?:[0-9A-Fa-f]{1,4}:){0,6}[0-9A-Fa-f]{0,4})(?![\w:])"""
            )
        private val WINDOWS_PATH = Regex("""\b[A-Za-z]:\\[^\s"'<>|]*""")
        private val UNIX_PATH = Regex("""(?<![\w:/.\-])(?:~|\.{1,2})?/[\w.\-@+%~=,]+(?:/[\w.\-@+%~=,]*)*""")
        private val HOST =
            Regex(
                """\b(?:[A-Za-z0-9\-]+\.)+(?:ts\.net|com|net|org|io|dev|app|xyz|local|lan|internal|home|me|co|ai|cloud|info|tech|online|site|us|uk|de|fr|eu)\b""",
                RegexOption.IGNORE_CASE,
            )
        private val OPAQUE = Regex("""\b[A-Za-z0-9_-]{32,}\b""")
    }
}
