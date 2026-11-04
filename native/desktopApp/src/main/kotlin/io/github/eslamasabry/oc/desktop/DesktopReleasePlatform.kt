package io.github.eslamasabry.oc.desktop

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.updates.RELEASES_API_URL
import io.github.eslamasabry.oc.engine.updates.ReleaseListFeed
import io.github.eslamasabry.oc.engine.updates.trustedReleasePageUrl
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopReleaseLinks
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration as JavaDuration
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Public GitHub metadata only: no profile credentials, no redirects, a cancellable request and a
 * bounded check.
 */
internal class DesktopReleaseFeed : ReleaseListFeed {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    override suspend fun get(url: String, timeout: Duration): Outcome<String> {
        if (url != "$RELEASES_API_URL?per_page=10") return Outcome.Failed(Failure.UnreadableReply())
        return suspendCancellableCoroutine { continuation ->
            val request =
                HttpRequest.newBuilder(URI(url))
                    .timeout(JavaDuration.ofMillis(timeout.inWholeMilliseconds))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "OpenCode-Mobile-Desktop")
                    .GET()
                    .build()
            val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            continuation.invokeOnCancellation { future.cancel(true) }
            future.whenComplete { response, error ->
                val result =
                    when {
                        error != null -> Outcome.Failed(Failure.Network())
                        response.statusCode() !in 200..299 ->
                            Outcome.Failed(Failure.Server(response.statusCode()))
                        response.body().length > 2 * 1024 * 1024 ->
                            Outcome.Failed(Failure.UnreadableReply())
                        else -> response.body().ok()
                    }
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }
}

internal class DesktopReleaseOpener : ExternalLinkOpener {
    override fun open(link: ApprovedLink): Outcome<Unit> {
        if (
            !Desktop.isDesktopSupported() ||
                !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        ) {
            return Outcome.Failed(Failure.Unavailable(FeatureId("browser")))
        }
        return try {
            Desktop.getDesktop().browse(URI(link.uri))
            Unit.ok()
        } catch (_: Exception) {
            Outcome.Failed(Failure.Unavailable(FeatureId("browser")))
        }
    }
}

/**
 * Called only after the on-screen GitHub confirmation; any untrusted target is refused before the
 * OS sees it.
 */
internal class DesktopReleaseLinkPolicy(
    private val dispatchers: AppDispatchers,
    private val policy: ExternalLinkPolicy = ExternalLinkPolicy(),
    private val opener: ExternalLinkOpener = DesktopReleaseOpener(),
) : DesktopReleaseLinks {
    override suspend fun open(url: String): Outcome<Unit> =
        withContext(dispatchers.io) {
            val trusted =
                trustedReleasePageUrl(url)
                    ?: return@withContext Outcome.Failed(
                        Failure.Unavailable(FeatureId("release-page"))
                    )
            when (val decision = policy.check(trusted, LinkOrigin.ServerContent)) {
                is LinkDecision.Open -> opener.open(decision.link)
                is LinkDecision.AskFirst -> opener.open(decision.link)
                is LinkDecision.Refuse ->
                    Outcome.Failed(Failure.Unavailable(FeatureId("release-page")))
            }
        }
}
