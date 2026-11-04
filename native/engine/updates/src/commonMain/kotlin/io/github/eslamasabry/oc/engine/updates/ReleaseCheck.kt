package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The release-page check for builds that cannot install an update themselves (port of
 * `lib/update/desktop_release_check.dart`, B-BUILD-010..012): it lists the project's GitHub releases, picks the newest
 * valid non-draft build and points at its release page. Nothing downloads. Pre-releases count: the preview lineage is
 * this app's distribution channel.
 */
public const val RELEASES_API_URL: String = "https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases"
public const val RELEASES_PAGE_URL: String = "https://github.com/Eslamasabry/opencode-mobile-next/releases"
public val RELEASE_NETWORK_TIMEOUT: Duration = 10.seconds

/** The newest release: its tag and a release page that is safe to open. */
public data class ReleaseInfo(val tag: String, val pageUrl: String)

/** Build numbers are the project's monotonic release ordering. */
public object ReleaseTags {
    /** `v1.0.25+26-preview.5` → 26, `v1.0.19+20` → 20; null when the tag carries none (Flutter `buildNumberFromTag`). */
    public fun buildOf(tag: String): Int? = AppRelease.buildOf(tag)

    /** True only when [tag] names a strictly larger build than [currentBuild]; a tag without one is never newer. */
    public fun isNewer(currentBuild: Int, tag: String): Boolean {
        val build = buildOf(tag) ?: return false
        return build > currentBuild
    }

    /** The tag a release of [versionName] (`X.Y.Z` or `X.Y.Z-suffix`) and [versionCode] is published under. */
    public fun tagFor(versionName: String, versionCode: Int): String? {
        val match = VERSION_NAME.matchEntire(versionName.trim()) ?: return null
        val suffix = match.groupValues[2]
        return "v${match.groupValues[1]}+$versionCode" + (if (suffix.isEmpty()) "" else "-$suffix")
    }

    private val VERSION_NAME = Regex("""^(\d+\.\d+\.\d+)(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?$""")
}

/** The network half: GET [url] with [timeout] as connect / read / send timeout; the JSON body. Bound per platform. */
public fun interface ReleaseListFeed {
    public suspend fun get(url: String, timeout: Duration): Outcome<String>
}

/** Lists releases and picks the newest valid build. The whole wait is bounded by [timeout], even if [feed] hangs. */
public class ReleaseChecker(private val feed: ReleaseListFeed, private val timeout: Duration = RELEASE_NETWORK_TIMEOUT) {
    public suspend fun fetchLatest(): Outcome<ReleaseInfo?> {
        val body = withTimeoutOrNull(timeout) { feed.get("$RELEASES_API_URL?per_page=10", timeout) }
            ?: return Outcome.Failed(Failure.TimedOut())
        if (body is Outcome.Failed) return body
        return newest((body as Outcome.Ok).value).ok()
    }

    public companion object {
        private val lenient = Json { ignoreUnknownKeys = true }

        /**
         * Pure: the newest valid non-draft entry of a releases-API list. GitHub orders by publication time, not by
         * build number, so malformed and unversioned entries are skipped while the highest build is chosen.
         */
        public fun newest(json: String): ReleaseInfo? {
            val list = parseList(json) ?: return null
            var newest: ReleaseInfo? = null
            var newestBuild = -1
            for (entry in list) {
                val o = entry as? JsonObject ?: continue
                if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val tag = (o["tag_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                val build = ReleaseTags.buildOf(tag) ?: continue
                if (build <= newestBuild) continue
                val html = (o["html_url"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                newestBuild = build
                newest = ReleaseInfo(tag, trustedReleasePageUrl(html) ?: RELEASES_PAGE_URL)
            }
            return newest
        }

        // Network text is the one place a parse may throw; not suspend code.
        @Suppress("TooGenericExceptionCaught")
        private fun parseList(json: String): JsonArray? =
            try {
                lenient.parseToJsonElement(json) as? JsonArray
            } catch (_: Exception) {
                null
            }
    }
}

/**
 * [value] when it is this project's release list or one release's tag page on GitHub over https (no user info, port,
 * query or fragment); null otherwise, and the caller falls back to [RELEASES_PAGE_URL].
 */
public fun trustedReleasePageUrl(value: String?): String? {
    if (value == null) return null
    val m = PAGE.matchEntire(value) ?: return null
    if (!m.groupValues[1].equals("https", ignoreCase = true)) return null
    if (!m.groupValues[2].equals("github.com", ignoreCase = true)) return null
    val segments = m.groupValues[3].removePrefix("/").split('/')
    if (segments.size < 3) return null
    if (!segments[0].equals("Eslamasabry", ignoreCase = true)) return null
    if (!segments[1].equals("opencode-mobile-next", ignoreCase = true)) return null
    if (!segments[2].equals("releases", ignoreCase = true)) return null
    val ok = segments.size == 3 || (segments.size == 5 && segments[3].equals("tag", ignoreCase = true) && segments[4].isNotEmpty())
    return if (ok) value else null
}

/** scheme://host + path, with nothing else: no user info, no port, no query, no fragment, no spaces. */
private val PAGE = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([A-Za-z0-9.-]+)(/[^?#@\s]*)$""")
