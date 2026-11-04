package io.github.eslamasabry.oc.core.net.okhttp

import java.io.IOException
import java.io.InterruptedIOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.Socket

/** Plain JVM types only: policy and Failure translation belong to core net. */
public object JvmWsUpgrade {
    public sealed interface Reply {
        public class Opened(public val socket: Socket, public val cancel: () -> Unit) : Reply

        public class Failed(
            public val status: Int? = null,
            public val invalid: Boolean = false,
            public val timeout: Boolean = false,
            public val cause: Throwable? = null,
        ) : Reply
    }

    public fun interface Handle {
        public fun cancel()
    }

    /** The client already contains the exact connection's trust gate and pin manager. */
    public fun open(
        client: OkHttpClient,
        url: String,
        headers: List<Pair<String, String>>,
        subprotocol: String?,
        handshakeTimeoutMs: Long,
        complete: (Reply) -> Unit,
    ): Handle {
        val done = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        val response = AtomicReference<Response?>(null)
        val active = AtomicReference<Call?>(null)
        val upgraded = AtomicReference<Socket?>(null)
        val derived =
            client
                .newBuilder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(handshakeTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
        val handle = Handle {
            done.set(true)
            active.getAndSet(null)?.cancel()
            upgraded.getAndSet(null)?.cancel()
            try {
                response.getAndSet(null)?.close()
            } catch (_: Exception) {}
        }
        try {
            require(
                headers.none { (name, _) ->
                    name.lowercase() in
                        setOf(
                            "connection",
                            "upgrade",
                            "sec-websocket-version",
                            "sec-websocket-key",
                            "sec-websocket-extensions",
                            "sec-websocket-protocol",
                        )
                }
            )
            require(
                subprotocol == null ||
                    (subprotocol.isNotEmpty() &&
                        subprotocol.none {
                            it.isWhitespace() || it.code < 32 || it.code >= 127 || it == ','
                        })
            )
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.toByteString().base64()
            val expected =
                (nonce + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encodeUtf8().sha1().base64()
            val request =
                Request.Builder()
                    .url(url)
                    .header("Connection", "Upgrade")
                    .header("Upgrade", "websocket")
                    .header("Sec-WebSocket-Version", "13")
                    .header("Sec-WebSocket-Key", nonce)
                    .apply {
                        for ((key, value) in headers) header(key, value)
                        if (subprotocol != null) header("Sec-WebSocket-Protocol", subprotocol)
                    }
                    .build()
            val call = derived.newCall(request)
            active.set(call)
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!done.get() && delivered.compareAndSet(false, true))
                            complete(Reply.Failed(timeout = e is InterruptedIOException, cause = e))
                    }

                    override fun onResponse(call: Call, reply: Response) {
                        response.set(reply)
                        if (done.get()) {
                            reply.close()
                            return
                        }
                        val socket = reply.socket
                        val valid =
                            reply.code == 101 &&
                                reply.header("Upgrade").equals("websocket", ignoreCase = true) &&
                                reply.headers
                                    .values("Connection")
                                    .flatMap { it.split(',') }
                                    .any { it.trim().equals("upgrade", true) } &&
                                reply.headers.values("Sec-WebSocket-Accept") == listOf(expected) &&
                                reply.headers.values("Sec-WebSocket-Extensions").isEmpty() &&
                                reply.headers.values("Sec-WebSocket-Protocol").let {
                                    if (subprotocol == null) it.isEmpty()
                                    else it.isEmpty() || it == listOf(subprotocol)
                                } &&
                                socket != null
                        if (!valid) {
                            if (!done.get() && delivered.compareAndSet(false, true))
                                complete(
                                    Reply.Failed(status = reply.code, invalid = reply.code == 101)
                                )
                            reply.close()
                            return
                        }
                        upgraded.set(socket)
                        if (done.get()) {
                            socket!!.cancel()
                            reply.close()
                            return
                        }
                        if (delivered.compareAndSet(false, true))
                            complete(Reply.Opened(socket!!, handle::cancel))
                        else {
                            socket!!.cancel()
                            reply.close()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            if (!done.get() && delivered.compareAndSet(false, true))
                complete(Reply.Failed(cause = e))
        }
        return handle
    }
}
