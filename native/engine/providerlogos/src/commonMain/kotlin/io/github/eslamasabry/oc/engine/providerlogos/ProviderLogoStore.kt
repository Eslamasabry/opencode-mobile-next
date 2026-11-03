package io.github.eslamasabry.oc.engine.providerlogos

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Port of lib/domain/provider_logo_store.dart (B-AGENT-205, B-AGENT-206, B-AGENT-207).
 */

/** One fetched logo response, reduced to what the store checks. */
public class ProviderLogoResponse(
    public val status: Int,
    public val contentType: String?,
    public val body: ByteArray,
)

/**
 * PORT (network). Fetches the app-authored models.dev logo [url] (always `https://models.dev/logos/<slug>.svg`).
 * Any network failure is `Outcome.Failed`; non-200 answers are returned as a response. The Android adapter uses an
 * 8 s connect/receive timeout, follows no redirects and stops reading past [ProviderLogoMaxBytes]. The store applies
 * the size, type and content checks again on whatever comes back, so a fake is held to the same rules.
 */
public fun interface ProviderLogoFetcher {
    public suspend fun fetch(url: String): Outcome<ProviderLogoResponse>
}

/** One cached file: its text (empty for a `.none` marker) and when it was last written. */
public data class CachedLogoFile(val text: String, val modifiedAtMs: Long)

/**
 * PORT (file system). The logo cache folder (Flutter: `<app support>/provider_logos`). Names are `<slug>.svg` or
 * `<slug>.none` and the slug is always already checked by [providerLogoSlug]. The store treats a Failed outcome
 * like the Dart catch-all (that provider counts as failed for the backoff, answer null). Pass no disk to the store
 * when the folder cannot be opened: logos then live in memory only.
 */
public interface ProviderLogoDisk {
    /** The file, or Ok(null) when it does not exist. Text is strict UTF-8. */
    public suspend fun read(name: String): Outcome<CachedLogoFile?>

    /** Writes [text] (creating or replacing the file); its modified time becomes "now". */
    public suspend fun write(name: String, text: String): Outcome<Unit>

    /** Deletes the file when present; absent is Ok. */
    public suspend fun delete(name: String): Outcome<Unit>
}

/** PORT (app bundle). Reads a bundled mark, e.g. `assets/marks/openai.svg` ([markAssetPath]); Failed if unreadable. */
public fun interface BundledMarkReader {
    public suspend fun read(assetPath: String): Outcome<String>
}

private val slugPattern = Regex("[a-z0-9][a-z0-9._-]{0,63}")

/**
 * A provider id reduced to a safe file/URL slug (`[a-z0-9._-]`, no `..`, at most 64 characters, not starting with
 * `.`), or null when the id cannot be one. The slug is the only part of the logo URL that comes from outside the
 * app, and it is only ever used after this check.
 */
public fun providerLogoSlug(providerId: String): String? {
    val id = providerId.trim().lowercase()
    if (!slugPattern.matches(id)) return null
    if (id.contains("..")) return null
    return id
}

/** The app-authored models.dev monochrome logo URL, or null for an id that is not a safe slug. */
public fun providerLogoUrl(providerId: String): String? {
    val slug = providerLogoSlug(providerId) ?: return null
    return "https://models.dev/logos/$slug.svg"
}

/**
 * models.dev answers an unknown provider id with one generic "sparkle" SVG rather than a 404. This is the MD5 of that
 * body as of 2026-10-13: a body with this digest means "no logo for this provider" and shows initials.
 */
public const val ModelsDevDefaultLogoMd5: String = "f14bc05bbdcf56f04949d1df83e67300"

/** Largest logo body accepted, in bytes. */
public const val ProviderLogoMaxBytes: Int = 64 * 1024

/** How long a cached logo is used before a refresh is attempted (7 days). */
public const val ProviderLogoRefreshAfterMs: Long = 7L * 24 * 60 * 60 * 1000

/** How long a failed fetch (offline, 5xx) is not retried in this store's lifetime (10 minutes). */
public const val ProviderLogoFailureBackoffMs: Long = 10L * 60 * 1000

/**
 * Provider logos (SVG text): a bundled mark first, the models.dev download as the fallback, null (initials) last.
 *
 * A provider with a bundled mark ([providerMarkSlug]) is answered from the bundle with no network request; a mark
 * that cannot be read falls through to the download. Downloads are fetched at most weekly per provider: a fresh cache
 * file is returned without the network, a stale one is returned if the refresh fails, and with nothing cached and no
 * network the answer is null. "No logo" (models.dev's sparkle) is remembered on disk as an empty `<slug>.none`
 * marker with the same weekly refresh. Concurrent callers for one provider share one fetch. One instance per app.
 */
public class ProviderLogoStore(
    private val fetcher: ProviderLogoFetcher,
    private val disk: ProviderLogoDisk?,
    private val bundled: BundledMarkReader,
    private val nowMs: () -> Long,
    private val defaultLogoMd5: String = ModelsDevDefaultLogoMd5,
) {
    private val guard = Mutex()
    private val slugLocks = mutableMapOf<String, Mutex>()
    private val resolved = mutableMapOf<String, String?>()
    private val failedAt = mutableMapOf<String, Long>()

    /** The logo's SVG text, or null when the provider has none, the id is not a safe slug, or nothing was readable. */
    public suspend fun svgFor(providerId: String): String? {
        val slug = providerLogoSlug(providerId) ?: return null
        val lock = guard.withLock {
            if (resolved.containsKey(slug)) return resolved[slug]
            slugLocks.getOrPut(slug) { Mutex() }
        }
        return lock.withLock {
            val known = guard.withLock { resolved.containsKey(slug) to resolved[slug] }
            if (known.first) known.second else bundledOrFetched(slug)
        }
    }

    private suspend fun bundledOrFetched(slug: String): String? {
        val mark = providerMarkSlug(slug)
        if (mark != null) {
            val text = bundled.read(markAssetPath(mark))
            if (text is Outcome.Ok) return remember(slug, text.value)
            // A mark that cannot be read falls through to the download.
        }
        return resolve(slug)
    }

    private suspend fun resolve(slug: String): String? {
        val outcome = resolveOrFail(slug)
        if (outcome is Resolution.Broken) {
            guard.withLock { failedAt[slug] = nowMs() }
            return null
        }
        return (outcome as Resolution.Done).svg
    }

    private sealed interface Resolution {
        data class Done(val svg: String?) : Resolution

        data object Broken : Resolution
    }

    private suspend fun resolveOrFail(slug: String): Resolution {
        val lastFailure = guard.withLock { failedAt[slug] }
        val svgName = "$slug.svg"
        val noneName = "$slug.none"

        var cached: String? = null
        var fresh = false
        if (disk != null) {
            val svgFile = when (val read = disk.read(svgName)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return Resolution.Broken
            }
            if (svgFile != null) {
                cached = svgFile.text
                fresh = isFresh(svgFile.modifiedAtMs)
            } else {
                val noneFile = when (val read = disk.read(noneName)) {
                    is Outcome.Ok -> read.value
                    is Outcome.Failed -> return Resolution.Broken
                }
                if (noneFile != null) fresh = isFresh(noneFile.modifiedAtMs)
            }
        }
        if (fresh) return Resolution.Done(remember(slug, cached))
        if (lastFailure != null && nowMs() - lastFailure < ProviderLogoFailureBackoffMs) {
            return Resolution.Done(cached)
        }

        val response = fetchSafely(slug)
        if (response == null) {
            guard.withLock { failedAt[slug] = nowMs() }
            return Resolution.Done(cached)
        }
        guard.withLock { failedAt.remove(slug) }
        val body = response.body
        if (Md5.hex(body) == defaultLogoMd5) {
            if (disk != null) {
                if (disk.delete(svgName) is Outcome.Failed) return Resolution.Broken
                if (disk.write(noneName, "") is Outcome.Failed) return Resolution.Broken
            }
            return Resolution.Done(remember(slug, null))
        }
        val text = strictUtf8(body) ?: return Resolution.Broken
        if (disk != null) {
            if (disk.write(svgName, text) is Outcome.Failed) return Resolution.Broken
            if (disk.delete(noneName) is Outcome.Failed) return Resolution.Broken
        }
        return Resolution.Done(remember(slug, text))
    }

    private suspend fun remember(slug: String, svg: String?): String? {
        guard.withLock { resolved[slug] = svg }
        return svg
    }

    private fun isFresh(modifiedAtMs: Long): Boolean = nowMs() - modifiedAtMs < ProviderLogoRefreshAfterMs

    private suspend fun fetchSafely(slug: String): ProviderLogoResponse? {
        val url = providerLogoUrl(slug) ?: return null
        val response = (fetcher.fetch(url) as? Outcome.Ok)?.value ?: return null
        if (response.status != 200) return null
        if (response.body.isEmpty() || response.body.size > ProviderLogoMaxBytes) return null
        val type = (response.contentType ?: "").lowercase()
        if (!type.contains("image/svg")) return null
        if (Md5.hex(response.body) == defaultLogoMd5) return response
        val text = strictUtf8(response.body) ?: return null
        if (!looksLikePlainSvg(text)) return null
        return response
    }
}

private fun strictUtf8(bytes: ByteArray): String? =
    try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        null
    }

private val bannedSvgContent = listOf(
    "<script",
    "<foreignobject",
    "<image",
    "<use",
    "javascript:",
    "onload=",
    "onclick=",
    "<!entity",
)

/**
 * A logo is drawn as a picture only: anything that carries script, embedded HTML or an image reference is refused
 * before it reaches the renderer.
 */
internal fun looksLikePlainSvg(text: String): Boolean {
    val lower = text.lowercase()
    if (!lower.contains("<svg")) return false
    return bannedSvgContent.none { lower.contains(it) }
}
