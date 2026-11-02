package io.github.eslamasabry.oc.engine.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** The release feed: GitHub's latest release of this project (Flutter `appUpdateLatestUrl`). */
public const val APP_UPDATE_LATEST_URL: String =
    "https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases/latest"

/** One downloadable file of a release. */
public data class ReleaseAsset(val name: String, val url: String, val size: Long)

/**
 * A published release, as GitHub's releases API describes it (port of `lib/update/app_release.dart`). Everything here
 * is network data: the tag decides only "newer or not", and the file is checked (checksum, signer, package, version)
 * before anything installs.
 */
public data class AppRelease(
    /** `v2.1.0+57` */
    val tag: String,
    /** `2.1.0` */
    val version: String,
    /** 57, the versionCode. */
    val build: Int,
    /** Release notes (Markdown). */
    val notes: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
    /** The source JSON, kept so the finding survives a restart. */
    val json: String,
) {
    override fun toString(): String = "AppRelease($tag, ${assets.size} files)"

    public val checksums: ReleaseAsset? get() = assets.firstOrNull { it.name == "SHA256SUMS" }

    /** The file for this phone: the first of its [abis] a file exists for, else the universal one. */
    public fun apkFor(abis: List<String>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk") }
        for (abi in abis) {
            apks.firstOrNull { it.name.endsWith("-$abi.apk") }?.let { return it }
        }
        return apks.firstOrNull { !ABI_SUFFIX.containsMatchIn(it.name) }
    }

    /** The first sentence of the notes' first plain paragraph, without Markdown marks; empty when there is none. */
    public val summary: String
        get() {
            for (block in notes.split(PARAGRAPH)) {
                val text = block.trim()
                if (text.isEmpty() || SKIP_PREFIXES.any { text.startsWith(it) }) continue
                val plain =
                    text
                        .replace(MARKS, "")
                        .replace(LINK) { it.groupValues[1] }
                        .replace(SPACES, " ")
                val end = SENTENCE_END.find(plain)
                return if (end == null) plain else plain.substring(0, end.range.first + 1)
            }
            return ""
        }

    public companion object {
        private val TAG = Regex("""^v(\d+\.\d+\.\d+)\+(\d+)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$""")
        private val ABI_SUFFIX = Regex("""-[a-z0-9_]+-v\d+a\.apk$""")
        private val PARAGRAPH = Regex("""\n\s*\n""")
        private val SKIP_PREFIXES = listOf("#", "-", "|", ">", "```")
        private val MARKS = Regex("[*_`]")
        private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
        private val SPACES = Regex("""\s+""")
        private val SENTENCE_END = Regex("""[.!?](\s|$)""")
        private val lenient = Json { ignoreUnknownKeys = true }

        /** The versionCode in a `v<x.y.z>+<n>` tag, or null (Flutter `buildNumberFromTag`). */
        public fun buildOf(tag: String): Int? = TAG.find(tag)?.groupValues?.get(2)?.toIntOrNull()

        /** Null for unreadable JSON, a draft, a pre-release or a tag that is not `v<x.y.z>+<n>`. */
        public fun parse(json: String): AppRelease? {
            val root = (runCatchingParse(json) as? JsonObject) ?: return null
            if (root.bool("draft") == true || root.bool("prerelease") == true) return null
            val tag = root.string("tag_name") ?: return null
            val match = TAG.find(tag) ?: return null
            val build = match.groupValues[2].toIntOrNull() ?: return null
            val assets =
                (root["assets"] as? JsonArray).orEmpty().mapNotNull { entry ->
                    val o = entry as? JsonObject ?: return@mapNotNull null
                    val name = o.string("name") ?: return@mapNotNull null
                    val url = o.string("browser_download_url") ?: return@mapNotNull null
                    val size = (o["size"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return@mapNotNull null
                    ReleaseAsset(name, url, size)
                }
            return AppRelease(
                tag = tag,
                version = match.groupValues[1],
                build = build,
                notes = root.string("body").orEmpty(),
                pageUrl = root.string("html_url").orEmpty(),
                assets = assets,
                json = json,
            )
        }

        // Parsing network text is the one place a throw is expected; it is not suspend code (K3 bans runCatching
        // only around suspend calls).
        @Suppress("TooGenericExceptionCaught")
        private fun runCatchingParse(json: String) =
            try {
                lenient.parseToJsonElement(json)
            } catch (_: Exception) {
                null
            }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    }
}

/** `SHA256SUMS`: `<64 hex>  <file name>` per line (`*` before the name marks binary mode). Lower-case hex by name. */
public fun parseSha256Sums(text: String): Map<String, String> {
    val line = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$""")
    val out = LinkedHashMap<String, String>()
    for (raw in text.split('\n')) {
        val m = line.find(raw.trim()) ?: continue
        out[m.groupValues[2]] = m.groupValues[1].lowercase()
    }
    return out
}

/**
 * A file address this app downloads from: GitHub's release downloads for this project, or (proof builds only) the
 * feed override's own origin (Flutter `isTrustedReleaseFileUrl`).
 */
public fun isTrustedReleaseFileUrl(url: String, feedOverride: String = ""): Boolean {
    val u = SimpleUrl.parse(url) ?: return false
    if (u.host.isEmpty() || u.hasUserInfo) return false
    if (feedOverride.isNotEmpty()) {
        val feed = SimpleUrl.parse(feedOverride) ?: return false
        return feed.host == u.host && feed.port == u.port
    }
    return u.scheme == "https" && u.host == "github.com" && u.path.startsWith("/Eslamasabry/opencode-mobile-next/releases/")
}

/** Just enough URL reading for the trust check; anything odd is refused (null). */
internal data class SimpleUrl(val scheme: String, val host: String, val port: Int?, val path: String, val hasUserInfo: Boolean) {
    companion object {
        private val SHAPE = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)([^?#]*)""")

        fun parse(url: String): SimpleUrl? {
            val m = SHAPE.find(url.trim()) ?: return null
            val authority = m.groupValues[2]
            val userInfo = authority.contains('@')
            val hostPort = authority.substringAfterLast('@')
            if (hostPort.startsWith("[")) return null // IPv6 literals are never a release host
            val host = hostPort.substringBefore(':').lowercase()
            val portText = hostPort.substringAfter(':', "")
            val port = if (portText.isEmpty()) null else portText.toIntOrNull() ?: return null
            return SimpleUrl(m.groupValues[1].lowercase(), host, port, m.groupValues[3], userInfo)
        }
    }
}
