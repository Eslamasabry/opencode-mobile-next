package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome

/**
 * One HTTP GET for a model file (plan 10 §7.4). The fetcher NEVER follows redirects and never sends credentials or
 * cookies: [PackInstaller] follows at most 5 redirects itself, each hop `https`. Android: `HttpsModelFetcher`
 * (`HttpURLConnection`, connect and read timeouts 15 s / 20 s). Until `:core:net`'s `Downloader` gains the C10-7
 * options, this is the seam.
 *
 * Transport problems come back as `Failure.Media(DownloadNoResponse | DownloadTimedOut | DownloadClosed |
 * DownloadHttpsRequired …)` with only a `Redacted.shape` detail (never a URL).
 */
public interface ModelFetcher {
    public suspend fun open(request: FetchRequest): Outcome<FetchResponse>
}

public data class FetchRequest(val url: String, val rangeStart: Long?, val userAgent: String = USER_AGENT) {
    public companion object {
        public const val USER_AGENT: String = "OpenCode-Mobile/voice-model"
    }
}

public interface FetchResponse {
    public val status: Int

    /** null when the header is missing (chunked or HTTP/2 without it): allowed (plan 10 §7.4 "Length"). */
    public val contentLength: Long?

    public val contentRange: String?

    /** The redirect target for a 3xx, as sent. */
    public val location: String?

    /** Reads body bytes; -1 at the end. Suspends while the body stalls; cancellation aborts the read. */
    public suspend fun read(buffer: ByteArray): Int

    /** Aborts the connection; safe to call twice. */
    public fun close()
}

/** Thrown by [FetchResponse.read] for a transport failure mid-body; [PackInstaller] reports [problem]. */
public class FetchFailure(public val problem: MediaProblem) : RuntimeException(problem.name)
