package io.github.eslamasabry.oc.core.security

import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.LogSink
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.RedactingLogSink
import kotlin.concurrent.atomics.AtomicReference

/**
 * The one redactor (plan 03 §10.2). Created in OcApplication.onCreate before the graph and handed to the graph
 * factory; not @Inject. Slice 0 carries the registered-secret and URL-credential rules; the full KitRedact port
 * (redaction profiles, header and token patterns) is slice 1.
 */
public class Redactor {
    private val secrets = AtomicReference<Set<String>>(emptySet())

    public fun registerSecret(value: String) {
        if (value.length < 4) return // too short to redact without destroying ordinary words
        while (true) {
            val current = secrets.load()
            if (secrets.compareAndSet(current, current + value)) return
        }
    }

    public fun forget(value: String) {
        while (true) {
            val current = secrets.load()
            if (secrets.compareAndSet(current, current - value)) return
        }
    }

    public fun redact(text: String): String {
        var out = text
        secrets.load().sortedByDescending { it.length }.forEach { out = out.replace(it, MASK) }
        out = USER_INFO.replace(out) { "${it.groupValues[1]}$MASK@" }
        out = TOKEN_QUERY.replace(out) { "${it.groupValues[1]}=$MASK" }
        return out
    }

    @OptIn(RedactorApi::class)
    public fun details(text: String): Redacted = Redacted(redact(text).take(DETAILS_LIMIT))

    private companion object {
        const val MASK = "•••"
        const val DETAILS_LIMIT = 12_000 // Flutter sanitize limit
        val USER_INFO = Regex("""([a-zA-Z][a-zA-Z0-9+.-]*://)[^/\s@]+@""")
        val TOKEN_QUERY = Regex("""(?i)([?&](?:token|key|password|secret|auth))=[^&\s]+""")
    }
}

/** The root log sink; AppLog.install accepts only a RedactingLogSink. */
public class RedactingWriter(private val redactor: Redactor, private val delegates: List<LogSink>) : RedactingLogSink {
    override fun write(entry: LogEntry) {
        val clean = entry.copy(message = redactor.redact(entry.message))
        delegates.forEach { it.write(clean) }
    }
}
