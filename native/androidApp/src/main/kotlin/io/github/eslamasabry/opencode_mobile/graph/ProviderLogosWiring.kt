package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.integrations.catalog.PROVIDER_LOGO_MAX_BYTES
import io.github.eslamasabry.oc.engine.integrations.catalog.ProviderLogoFile
import io.github.eslamasabry.oc.engine.integrations.catalog.ProviderLogoHost
import io.github.eslamasabry.oc.engine.integrations.catalog.ProviderLogoResponse
import io.github.eslamasabry.oc.engine.integrations.catalog.ProviderLogos
import io.github.eslamasabry.oc.engine.integrations.catalog.ProviderMarks
import io.github.eslamasabry.oc.engine.integrations.catalog.providerLogoSlug
import io.github.eslamasabry.oc.engine.integrations.catalog.providerLogoUrl
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The image adapter is app owned. Coil receives these bounded bytes, never a URL or authenticated
 * transport.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppProviderLogoHost(
    private val application: Application,
    private val http: HttpClientFactory,
    private val dispatchers: AppDispatchers,
) : ProviderLogoHost {
    private val directory: File
        get() = File(application.filesDir, "provider_logos")

    private fun cacheFile(name: String): File {
        val suffix =
            when {
                name.endsWith(".svg") -> ".svg"
                name.endsWith(".none") -> ".none"
                else -> error("Invalid logo cache name")
            }
        val slug = name.removeSuffix(suffix)
        require(providerLogoSlug(slug) == slug)
        val root = directory
        require(!Files.isSymbolicLink(root.toPath()))
        return File(root, name).also { require(!Files.isSymbolicLink(it.toPath())) }
    }

    override suspend fun bundled(slug: String): String? =
        withContext(dispatchers.io) {
            if (slug !in ProviderMarks.slugs) return@withContext null
            application.assets.open("marks/$slug.svg").use { input ->
                val bytes = input.logoBytes()
                if (bytes.size > PROVIDER_LOGO_MAX_BYTES) null
                else bytes.decodeToString(throwOnInvalidSequence = true)
            }
        }

    override suspend fun read(name: String): ProviderLogoFile? =
        withContext(dispatchers.io) {
            val file = cacheFile(name)
            if (!file.isFile || file.length() > PROVIDER_LOGO_MAX_BYTES) return@withContext null
            val bytes = file.inputStream().use { it.logoBytes() }
            if (bytes.size > PROVIDER_LOGO_MAX_BYTES) null
            else
                ProviderLogoFile(
                    bytes.decodeToString(throwOnInvalidSequence = true),
                    file.lastModified(),
                )
        }

    override suspend fun write(name: String, text: String) {
        withContext(dispatchers.io) {
            val bytes = text.encodeToByteArray()
            require(bytes.size <= PROVIDER_LOGO_MAX_BYTES)
            val target = cacheFile(name)
            val root = target.parentFile!!
            check(root.isDirectory || root.mkdirs())
            val temporary = Files.createTempFile(root.toPath(), ".logo-", ".tmp")
            try {
                Files.write(temporary, bytes)
                Files.move(
                    temporary,
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    override suspend fun delete(name: String) {
        withContext(dispatchers.io) {
            Files.deleteIfExists(cacheFile(name).toPath())
            Unit
        }
    }

    override suspend fun fetch(url: String): ProviderLogoResponse? {
        val slug = url.removePrefix("https://models.dev/logos/").removeSuffix(".svg")
        if (providerLogoUrl(slug) != url) return null
        val origin = ServerOrigin("https", "models.dev", 443)
        val client =
            http.create(
                TransportSpec(
                    origin,
                    AddressRules.classify(origin.host),
                    false,
                    readTimeoutMs = 5_000,
                )
            )
        return try {
            withTimeoutOrNull(5_000) {
                val result =
                    client.exchange({
                        method = HttpMethod.Get
                        url(url)
                        header(HttpHeaders.Accept, "image/svg+xml")
                    }) { response ->
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(PROVIDER_LOGO_MAX_BYTES + 1)
                        var size = 0
                        while (size < buffer.size) {
                            val read = channel.readAvailable(buffer, size, buffer.size - size)
                            if (read < 0) break
                            size += read
                        }
                        if (size > PROVIDER_LOGO_MAX_BYTES) null
                        else
                            ProviderLogoResponse(
                                response.status.value,
                                response.headers[HttpHeaders.ContentType],
                                buffer.copyOf(size),
                            )
                    }
                when (result) {
                    is Outcome.Ok -> result.value
                    is Outcome.Failed -> null
                }
            }
        } finally {
            client.close()
        }
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppKitLogoLoader(private val logos: ProviderLogos) : KitLogoLoader {
    override suspend fun svg(id: String, agent: Boolean): String? =
        if (agent) logos.agentSvg(id) else logos.svgFor(id)
}

@ContributesTo(AppScope::class)
interface ProviderLogoGraph {
    val kitLogoLoader: KitLogoLoader
}

/** API-26-safe bounded read; neither asset nor file reads can allocate an unbounded logo. */
private fun InputStream.logoBytes(): ByteArray {
    val buffer = ByteArray(PROVIDER_LOGO_MAX_BYTES + 1)
    var count = 0
    while (count < buffer.size) {
        val read = read(buffer, count, buffer.size - count)
        if (read < 0) break
        count += read
    }
    return buffer.copyOf(count)
}
