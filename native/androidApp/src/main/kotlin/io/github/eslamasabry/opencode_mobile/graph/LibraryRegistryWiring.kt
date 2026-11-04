package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.HostClass
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.setupassist.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.*
import java.io.ByteArrayOutputStream
import kotlin.time.Clock
import kotlinx.coroutines.withTimeoutOrNull

/** A separate anonymous HTTPS client: server credentials never reach the public registry. */
@Inject
@SingleIn(AppScope::class)
class LibraryRegistryFactory(
    private val stores: PreviewStores,
    private val http: HttpClientFactory,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    suspend fun open(profile: ProfileId): SetupRegistryStore? {
        if (stores.warm() !is Outcome.Ok || stores.profiles.profile(profile) == null) return null
        val lane = stores.lanes.forProfile(profile)
        fun current() = lane.isAdmitting && stores.profiles.profile(profile) != null
        val prefs =
            object : SetupPrefs {
                override fun getString(key: String): String? =
                    if (current()) (stores.prefs.get(key) as? PrefValue.Str)?.value else null

                override fun containsKey(key: String): Boolean =
                    current() && stores.prefs.get(key) != null

                override fun keys(): Set<String> =
                    if (current()) stores.prefs.keys() else emptySet()

                override suspend fun setString(key: String, value: String): Boolean =
                    current() && stores.prefs.commit(lane) { putString(key, value) } is Outcome.Ok

                override suspend fun remove(key: String): Boolean =
                    current() && stores.prefs.commit(lane) { remove(key) } is Outcome.Ok
            }
        val client =
            SetupRegistryClient(
                RegistryHttp { request, cancellation ->
                    if (
                        !current() ||
                            cancellation.isCancelled ||
                            request.url != SetupRegistryClient.ENDPOINT ||
                            request.followRedirects ||
                            request.maxRedirects != 0
                    )
                        RegistryHttpReply.Failed
                    else {
                        val net =
                            http.create(
                                TransportSpec(
                                    ServerOrigin("https", "registry.modelcontextprotocol.io", 443),
                                    HostClass.Public,
                                    false,
                                    readTimeoutMs = 15_000,
                                )
                            )
                        try {
                            withTimeoutOrNull(20_000) {
                                val reply =
                                    net.exchange({
                                        method = io.ktor.http.HttpMethod.Get
                                        url(request.url)
                                        request.query.forEach { (key, value) ->
                                            url.parameters.append(key, value)
                                        }
                                        request.headers.forEach { (key, value) ->
                                            header(key, value)
                                        }
                                    }) { response ->
                                        val channel = response.bodyAsChannel()
                                        val out = ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        while (!channel.isClosedForRead) {
                                            if (cancellation.isCancelled || !current())
                                                return@exchange RegistryHttpReply.Failed
                                            val count =
                                                channel.readAvailable(
                                                    buffer,
                                                    0,
                                                    minOf(
                                                        buffer.size,
                                                        request.maxBytes - out.size() + 1,
                                                    ),
                                                )
                                            if (count < 0) break
                                            if (count > 0) out.write(buffer, 0, count)
                                            if (out.size() > request.maxBytes)
                                                return@exchange RegistryHttpReply.TooLarge
                                        }
                                        RegistryHttpReply.Body(
                                            response.status.value,
                                            out.toByteArray(),
                                        )
                                    }
                                (reply as? Outcome.Ok)?.value ?: RegistryHttpReply.Failed
                            } ?: RegistryHttpReply.Failed
                        } finally {
                            net.close()
                        }
                    }
                },
                redactor,
            )
        return SetupRegistryStore(prefs, profile.raw, client, redactor, clock)
    }
}
