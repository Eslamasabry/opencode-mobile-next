package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Flutter lib/domain/provider_logo_store.dart: a bundled mark first, the models.dev download as the
// fallback,
// initials (null) last.

/** One fetched logo response, reduced to what the store checks. */
public class ProviderLogoResponse(
    public val status: Int,
    public val contentType: String?,
    public val body: ByteArray,
)

/** A cached file: its text (empty for a `.none` marker) and when it was written. */
public data class ProviderLogoFile(val text: String, val modifiedAtMs: Long)

/**
 * Where logos live (app support `provider_logos/`). Every call may fail; the store then treats it
 * as absent.
 */
public interface ProviderLogoFiles {
    public suspend fun read(name: String): ProviderLogoFile?

    public suspend fun write(name: String, text: String)

    public suspend fun delete(name: String)
}

/**
 * A safe slug (`[a-z0-9._-]`, no `..`, ≤ 64, not starting with `.`), or null. The only outside part
 * of the URL.
 */
public fun providerLogoSlug(providerId: String): String? {
    val id = providerId.trim().lowercase()
    if (!Regex("^[a-z0-9][a-z0-9._-]{0,63}$").matches(id)) return null
    if (".." in id) return null
    return id
}

/** The app-authored models.dev monochrome logo URL, or null for an id that is not a safe slug. */
public fun providerLogoUrl(providerId: String): String? =
    providerLogoSlug(providerId)?.let { "https://models.dev/logos/$it.svg" }

/**
 * MD5 of models.dev's generic "sparkle" (its answer for an unknown provider) as of 2026-10-13:
 * means "no logo".
 */
public const val MODELS_DEV_DEFAULT_LOGO_MD5: String = "f14bc05bbdcf56f04949d1df83e67300"

/** Largest logo body accepted, in bytes. */
public const val PROVIDER_LOGO_MAX_BYTES: Int = 64 * 1024

private const val REFRESH_AFTER_MS = 7L * 24 * 60 * 60 * 1000
private const val FAILURE_BACKOFF_MS = 10L * 60 * 1000

/**
 * Fetch-once, keep-on-disk logo store. A fresh cache is returned without the network; a stale one
 * if the refresh fails; nothing cached and no network is null (initials). "No logo" is remembered
 * as an empty `.none` marker with the same weekly refresh. Oversize, wrong-type, non-200 and
 * active-content bodies are refused and never written.
 */
public class ProviderLogoStore(
    private val fetch: suspend (url: String) -> ProviderLogoResponse?,
    private val files: ProviderLogoFiles?,
    private val nowMs: () -> Long,
    private val defaultLogoMd5: String = MODELS_DEV_DEFAULT_LOGO_MD5,
    /**
     * The bundled mark's SVG for a slug, or null when there is none (then the download is used).
     */
    private val loadBundled: suspend (slug: String) -> String? = { null },
) {
    private val resolved = HashMap<String, String?>()
    private val resolvedUntil = HashMap<String, Long>()
    private val failedAt = HashMap<String, Long>()
    private val locks = HashMap<String, Mutex>()

    /**
     * The logo's SVG text, or null when the provider has none, the id is unsafe, or nothing could
     * be read.
     */
    public suspend fun svgFor(providerId: String): String? {
        val slug = providerLogoSlug(providerId) ?: return null
        if (nowMs() < (resolvedUntil[slug] ?: Long.MIN_VALUE)) return resolved[slug]
        val lock = locks.getOrPut(slug) { Mutex() }
        return lock.withLock {
            if (nowMs() < (resolvedUntil[slug] ?: Long.MIN_VALUE)) resolved[slug]
            else bundledOrFetched(slug)
        }
    }

    private suspend fun bundledOrFetched(slug: String): String? {
        val mark = (attempt({ Failure.Device() }) { loadBundled(slug) } as? Outcome.Ok)?.value
        if (
            mark != null &&
                mark.encodeToByteArray().size <= PROVIDER_LOGO_MAX_BYTES &&
                looksLikePlainSvg(mark)
        )
            return remember(slug, mark, Long.MAX_VALUE)
        val result = attempt({ Failure.Device() }) { resolve(slug) }
        return when (result) {
            is Outcome.Ok -> result.value
            is Outcome.Failed -> {
                failedAt[slug] = nowMs()
                remember(slug, resolved[slug], nowMs() + FAILURE_BACKOFF_MS)
            }
        }
    }

    private suspend fun resolve(slug: String): String? {
        val svgName = "$slug.svg"
        val noneName = "$slug.none"
        val svg = files?.let { f ->
            (attempt({ Failure.Device() }) { f.read(svgName) } as? Outcome.Ok)?.value
        }
        val none =
            if (svg == null)
                files?.let { f ->
                    (attempt({ Failure.Device() }) { f.read(noneName) } as? Outcome.Ok)?.value
                }
            else null
        val cached =
            svg?.text?.takeIf {
                it.encodeToByteArray().size <= PROVIDER_LOGO_MAX_BYTES && looksLikePlainSvg(it)
            }
        val cachedAt = (if (cached != null) svg else none)?.modifiedAtMs
        val fresh = cachedAt != null && nowMs() - cachedAt in 0 until REFRESH_AFTER_MS
        if (fresh) return remember(slug, cached, cachedAt!! + REFRESH_AFTER_MS)
        val failed = failedAt[slug]
        if (failed != null && nowMs() - failed in 0 until FAILURE_BACKOFF_MS)
            return remember(slug, cached, failed + FAILURE_BACKOFF_MS)

        val response = fetchSafely(slug)
        if (response == null) {
            failedAt[slug] = nowMs()
            return remember(slug, cached, nowMs() + FAILURE_BACKOFF_MS)
        }
        failedAt.remove(slug)
        if (Md5.hex(response.body) == defaultLogoMd5) {
            attempt({ Failure.Device() }) {
                files?.delete(svgName)
                files?.write(noneName, "")
            }
            return remember(slug, null)
        }
        val text = response.body.decodeToString(throwOnInvalidSequence = true)
        attempt({ Failure.Device() }) {
            files?.let {
                it.write(svgName, text)
                it.delete(noneName)
            }
        }
        return remember(slug, text)
    }

    private fun remember(
        slug: String,
        svg: String?,
        until: Long = nowMs() + REFRESH_AFTER_MS,
    ): String? {
        resolved[slug] = svg
        resolvedUntil[slug] = until
        return svg
    }

    private suspend fun fetchSafely(slug: String): ProviderLogoResponse? {
        val url = providerLogoUrl(slug) ?: return null
        val response =
            (attempt({ Failure.Network() }) { fetch(url) } as? Outcome.Ok)?.value ?: return null
        if (response.status != 200) return null
        if (response.body.isEmpty() || response.body.size > PROVIDER_LOGO_MAX_BYTES) return null
        if ("image/svg" !in (response.contentType ?: "").lowercase()) return null
        if (Md5.hex(response.body) == defaultLogoMd5) return response
        val text =
            try {
                response.body.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                return null
            }
        return if (looksLikePlainSvg(text)) response else null
    }
}

private val BANNED_SVG =
    listOf(
        "<script",
        "<foreignobject",
        "<image",
        "<use",
        "javascript:",
        "onload=",
        "onclick=",
        "<!entity",
        "<!doctype",
        "@import",
    )

/**
 * A logo is a picture only: anything carrying script, embedded HTML or an image reference is
 * refused.
 */
internal fun looksLikePlainSvg(text: String): Boolean {
    val lower = text.lowercase()
    val withoutLocalReferences =
        lower.replace(Regex("""url\(\s*["']?#[a-z0-9_.:-]+["']?\s*\)"""), "")
    return "<svg" in lower &&
        BANNED_SVG.none { it in lower } &&
        !Regex("""\bon[a-z]+\s*=""").containsMatchIn(lower) &&
        !Regex("""(?:\bhref\s*=|\burl\s*\()""").containsMatchIn(withoutLocalReferences)
}
