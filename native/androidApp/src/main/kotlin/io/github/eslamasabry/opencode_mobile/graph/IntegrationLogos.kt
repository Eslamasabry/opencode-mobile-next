package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import java.io.File
import kotlin.time.Clock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray

/**
 * Logo-only, unauthenticated, fixed models.dev origin. No shared credentials, cookies, redirects or
 * unbounded body.
 */
@SingleIn(AppScope::class)
@Inject
class IntegrationLogos(
    application: Application,
    clients: HttpClientFactory,
    dispatchers: AppDispatchers,
    clock: Clock,
) {
    private val directory = File(application.cacheDir, "provider_logos")
    private val store =
        ProviderLogoStore(
            fetch = { address ->
                if (!address.startsWith("https://models.dev/logos/") || !address.endsWith(".svg"))
                    null
                else {
                    val origin = ServerOrigin("https", "models.dev", 443)
                    val net =
                        clients.create(
                            TransportSpec(
                                origin,
                                AddressRules.classify(origin.host),
                                false,
                                readTimeoutMs = 8_000,
                            )
                        )
                    try {
                        when (
                            val result =
                                net.exchange({ url(address) }) { reply ->
                                    val bytes =
                                        reply
                                            .bodyAsChannel()
                                            .readRemaining(PROVIDER_LOGO_MAX_BYTES.toLong() + 1)
                                            .readByteArray()
                                    ProviderLogoResponse(
                                        reply.status.value,
                                        reply.headers["Content-Type"],
                                        bytes,
                                    )
                                }
                        ) {
                            is Outcome.Failed -> null
                            is Outcome.Ok -> result.value
                        }
                    } finally {
                        net.close()
                    }
                }
            },
            files =
                object : ProviderLogoFiles {
                    private fun file(name: String): File {
                        require(Regex("^[a-z0-9][a-z0-9._-]{0,68}$").matches(name) && ".." !in name)
                        return File(directory, name)
                    }

                    override suspend fun read(name: String): ProviderLogoFile? =
                        withContext(dispatchers.io) {
                            val f = file(name)
                            if (!f.isFile || f.length() > PROVIDER_LOGO_MAX_BYTES) null
                            else ProviderLogoFile(f.readText(), f.lastModified())
                        }

                    override suspend fun write(name: String, text: String): Unit =
                        withContext(dispatchers.io) {
                            require(text.toByteArray().size <= PROVIDER_LOGO_MAX_BYTES)
                            check(directory.isDirectory || directory.mkdirs())
                            val target = file(name)
                            val staged = File(directory, "$name.part")
                            staged.writeText(text)
                            check(staged.renameTo(target))
                        }

                    override suspend fun delete(name: String): Unit =
                        withContext(dispatchers.io) {
                            val f = file(name)
                            check(!f.exists() || f.delete())
                        }
                },
            nowMs = { clock.now().toEpochMilliseconds() },
        )

    suspend fun svg(provider: String): String? =
        (attempt({ Failure.Device() }) { store.svgFor(provider) } as? Outcome.Ok)?.value
}
