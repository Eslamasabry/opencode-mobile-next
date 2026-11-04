package io.github.eslamasabry.oc.core.model

/**
 * Flutter `WebSourceSelection` (lib/domain/web_source_selection.dart): one source a person
 * reviewed. Its text is unverified, untrusted material; no consumer may fetch a page because it
 * holds one. Build with [of].
 */
public class WebSourceSelection
private constructor(
    public val title: String,
    public val url: String,
    public val excerpt: String?,
) {
    override fun equals(other: Any?): Boolean =
        other is WebSourceSelection &&
            other.title == title &&
            other.url == url &&
            other.excerpt == excerpt

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = "WebSourceSelection(${title.length} chars)"

    /** Why a source was refused; :ui:words owns the sentence for each. */
    public enum class Problem {
        Url,
        Title,
        ExcerptTooLong,
        ExcerptControl,
    }

    public sealed interface Built {
        public data class Ok(val source: WebSourceSelection) : Built

        public data class Refused(val problem: Problem) : Built
    }

    public companion object {
        public const val MAX_TITLE: Int = 200
        public const val MAX_URL: Int = 2048
        public const val MAX_EXCERPT: Int = 2000
        private val Http = Regex("^(https?)://([^/?#]*)(.*)$", RegexOption.IGNORE_CASE)
        private val UrlForbidden = Regex("[\\s\\u0000-\\u001f\\u007f\\\\]")
        private val TitleControl = Regex("[\\u0000-\\u001f\\u007f]")
        private val ExcerptControl = Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]")
        private val Tld = Regex("^[a-z]{2,63}$")
        private val Label = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
        private val PrivateTld =
            setOf("localhost", "local", "internal", "lan", "home", "test", "invalid")

        public fun of(title: String, url: String, excerpt: String? = null): Built {
            val normalized = url.trim()
            val match = Http.matchEntire(normalized)
            if (
                normalized.length > MAX_URL ||
                    UrlForbidden.containsMatchIn(normalized) ||
                    match == null
            )
                return Built.Refused(Problem.Url)
            val authority = match.groupValues[2]
            if ('@' in authority) return Built.Refused(Problem.Url)
            val host = authority.substringBefore(':').lowercase()
            val port = authority.substringAfter(':', "")
            if (
                (':' in authority && (port.isEmpty() || port.any { !it.isDigit() })) ||
                    !publicDnsHost(host)
            )
                return Built.Refused(Problem.Url)
            val label = title.trim().ifEmpty { host }
            if (label.length > MAX_TITLE || TitleControl.containsMatchIn(label))
                return Built.Refused(Problem.Title)
            val text = excerpt?.trim()
            if (text != null && text.length > MAX_EXCERPT)
                return Built.Refused(Problem.ExcerptTooLong)
            if (text != null && ExcerptControl.containsMatchIn(text))
                return Built.Refused(Problem.ExcerptControl)
            val canonical =
                match.groupValues[1].lowercase() +
                    "://" +
                    host +
                    (if (port.isNotEmpty()) ":$port" else "") +
                    match.groupValues[3]
            return Built.Ok(WebSourceSelection(label, canonical, text?.ifEmpty { null }))
        }

        /** Conservative offline input policy, not DNS resolution or an SSRF guard. */
        private fun publicDnsHost(host: String): Boolean {
            val name = host.removeSuffix(".")
            val labels = name.split('.')
            if (labels.size < 2 || name.length > 253) return false
            if (labels.last() in PrivateTld) return false
            if (name == "home.arpa" || name.endsWith(".home.arpa")) return false
            if (!Tld.matches(labels.last())) return false
            return labels.all { Label.matches(it) }
        }
    }
}
