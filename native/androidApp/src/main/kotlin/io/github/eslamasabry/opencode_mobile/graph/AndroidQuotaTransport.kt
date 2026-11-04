package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.engine.integrations.quota.QuotaRequest
import io.github.eslamasabry.oc.engine.integrations.quota.QuotaResponse
import io.github.eslamasabry.oc.engine.integrations.quota.QuotaTransport
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * One private client per collector read: no shared cookies, interceptors, credentials or redirect
 * policy.
 */
internal class AndroidQuotaTransport(private val dispatchers: AppDispatchers) : QuotaTransport {
    private val calls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()
    private val responses = java.util.concurrent.ConcurrentHashMap.newKeySet<Response>()
    @Volatile private var closed = false
    private val client =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

    override suspend fun fetch(request: QuotaRequest): QuotaResponse {
        check(!closed)
        require(request.method == "GET" && !request.followRedirects && request.maxRedirects == 0)
        val bounded =
            client
                .newBuilder()
                .connectTimeout(request.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(request.sendTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(request.receiveTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(request.receiveTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
        val call =
            bounded.newCall(
                Request.Builder()
                    .url(request.url)
                    .apply { request.headers.forEach { (key, value) -> header(key, value) } }
                    .get()
                    .build()
            )
        calls += call
        if (closed) {
            call.cancel()
            throw IOException()
        }
        val response =
            suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            calls -= call
                            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (closed) {
                                response.close()
                                continuation.cancel()
                                return
                            }
                            responses += response
                            continuation.resume(response) { _, value, _ -> value.close() }
                        }
                    }
                )
            }
        return QuotaResponse(
            response.code,
            flow {
                    try {
                        response.use {
                            val stream = it.body.byteStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (count > 0) emit(buffer.copyOf(count))
                            }
                        }
                    } finally {
                        responses -= response
                        calls -= call
                        call.cancel()
                    }
                }
                .flowOn(dispatchers.io),
            response.isRedirect,
        )
    }

    override fun close(force: Boolean) {
        if (closed) return
        closed = true
        calls.forEach { it.cancel() }
        responses.forEach { it.close() }
        calls.clear()
        responses.clear()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}
