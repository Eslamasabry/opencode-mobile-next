package io.github.eslamasabry.oc.engine.integrations.quota

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * One request the quota client sends: always one GET, no body, no cookies, redirects never followed. The transport
 * must honour every field; the Android binding is a dedicated OkHttp client (never the shared OpenCode one).
 */
public class QuotaRequest(
    public val method: String,
    public val url: String,
    public val headers: ImmutableMap<String, String>,
    public val followRedirects: Boolean,
    public val maxRedirects: Int,
    public val connectTimeoutMs: Long,
    public val sendTimeoutMs: Long,
    public val receiveTimeoutMs: Long,
) {
    /** The client never sends a body. */
    public val body: ByteArray? get() = null

    /** Never prints the URL or the Authorization header. */
    override fun toString(): String = "QuotaRequest($method)"
}

/** Status and the body as a cold chunk stream. The client collects [body] only for a 200 and stops it early. */
public class QuotaResponse(
    public val status: Int,
    public val body: Flow<ByteArray>,
    public val isRedirect: Boolean = false,
)

/**
 * The quota client's HTTP port. [fetch] suspends until headers arrive; cancelling the caller cancels the call. A
 * transport exception may carry anything (URL, credentials): the client never keeps or shows it.
 */
public interface QuotaTransport {
    public suspend fun fetch(request: QuotaRequest): QuotaResponse

    /** Called once, when the gateway closes; [force] cancels calls still running. */
    public fun close(force: Boolean)
}
