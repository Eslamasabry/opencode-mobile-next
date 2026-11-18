package io.github.eslamasabry.oc.engine.analytics.umami

import java.io.File
import java.io.InterruptedIOException
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.net.StatusMapping
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The app's existing HTTP stack, with no auth, cookies, redirect, automatic retry or SDK. */
public class UmamiHttp(
    private val endpoint: String,
    private val userAgent: () -> String,
) : UmamiTransport {
    private val log = AppLog.tagged("UmamiGuard")
    private val http = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override fun prepare(events: List<UmamiEvent>): UmamiCall {
        // The guard reads the JSON that will really be posted; a key or word it does not know is dropped,
        // and only its name is logged.
        val guarded = UmamiBodyGuard.check(events)
        if (guarded.dropped.isNotEmpty()) {
            log.w { "usage report: dropped ${guarded.dropped.sorted().joinToString(", ")}" }
        }
        val kept = guarded.kept
        // An event the guard withheld is never sent; it leaves the queue as if delivered so it cannot be retried.
        val withheld = events.indices.toSet() - kept.toSet()
        val body = guarded.body
        if (body == null) {
            return object : UmamiCall {
                override fun cancel() = Unit
                override suspend fun execute(): Outcome<Set<Int>> = Outcome.Ok(withheld)
            }
        }
        val batch = kept.size != 1
        val call = http.newCall(Request.Builder()
            .url(endpoint.trimEnd('/') + if (batch) "/api/batch" else "/api/send")
            .header("User-Agent", userAgent())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build())
        return object : UmamiCall {
            override fun cancel() = call.cancel()
            override suspend fun execute(): Outcome<Set<Int>> {
                val sent = attempt({ error ->
                    if (error is InterruptedIOException) Failure.TimedOut() else Failure.Network(NetworkWhy.Unknown)
                }) { call.execute() }
                val reply = when (sent) {
                    is Outcome.Failed -> return sent
                    is Outcome.Ok -> sent.value
                }
                return reply.use { response ->
                    if (!response.isSuccessful) return@use Outcome.Failed(StatusMapping.failureFor(response.code, credentialsSent = false))
                    val decoded = attempt({ Failure.UnreadableReply() }) { receipt(response.body, batch, kept.size) }
                    if (decoded is Outcome.Failed) decoded
                    else decoded.getOrNull()
                        ?.let { accepted -> Outcome.Ok(accepted.map { kept[it] }.toSet() + withheld) }
                        ?: Outcome.Failed(Failure.UnreadableReply())
                }
            }
        }
    }

    private fun receipt(body: okhttp3.ResponseBody?, batch: Boolean, count: Int): Set<Int>? {
        val responseBody = body ?: return null
        val source = responseBody.source()
        source.request(MAX_RESPONSE_BYTES + 1)
        if (source.buffer.size > MAX_RESPONSE_BYTES) return null
        val json = UmamiCodec.json.parseToJsonElement(source.readUtf8()) as? JsonObject ?: return null
        // A bot-filtered HTTP 200 is not a delivery receipt.
        if ("beep" in json) return null
        if (!batch) return if ("sessionId" in json) setOf(0) else null
        val size = json["size"]?.jsonPrimitive?.intOrNull ?: return null
        val errors = json["errors"]?.jsonPrimitive?.intOrNull ?: return null
        val processed = json["processed"]?.jsonPrimitive?.intOrNull ?: return null
        val details = json["details"] as? JsonArray ?: return null
        val failed = details.mapNotNull { it.jsonObject["index"]?.jsonPrimitive?.intOrNull }.toSet()
        if (size != count || errors != failed.size || processed + errors != size || failed.any { it !in 0 until count }) return null
        return (0 until count).toSet() - failed
    }

    public companion object {
        public const val CONNECT_TIMEOUT_MS: Long = 1_500
        public const val CALL_TIMEOUT_MS: Long = 3_000
        private const val MAX_RESPONSE_BYTES: Long = 32 * 1024

        public fun configured(endpoint: String, website: String): Boolean {
            val url = endpoint.toHttpUrlOrNull() ?: return false
            return url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null &&
                url.fragment == null && url.encodedPath == "/" &&
                website.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        }
    }
}

/** Atomic replacement in private app storage; no stored id and no logs. The sender enforces the read/write bound. */
public class FileUmamiStore(private val file: File) : UmamiStore {
    override suspend fun read(): Outcome<String?> = attempt({ Failure.StorageRefused() }) {
        if (file.isFile && file.length() <= UmamiSender.MAX_STORE_BYTES) file.readText() else null
    }

    override suspend fun replace(value: String?): Outcome<Unit> = attempt({ Failure.StorageRefused() }) {
        val pending = File(file.parentFile, file.name + ".new")
        if (value == null) {
            check(!pending.exists() || pending.delete())
            check(!file.exists() || file.delete())
        } else {
            check(value.toByteArray(Charsets.UTF_8).size <= UmamiSender.MAX_STORE_BYTES)
            file.parentFile?.mkdirs()
            pending.writeText(value)
            check(pending.renameTo(file))
        }
    }
}
