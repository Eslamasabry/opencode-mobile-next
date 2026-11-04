package io.github.eslamasabry.oc.feature.search.web

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome

/** An opaque approval held by the platform after ExternalLinkPolicy accepts the server's URL. */
public data class WebLinkReview(val token: Long, val host: String, val insecure: Boolean = false)

public interface WebResultOpener {
    public fun review(url: String): WebLinkReview?

    public suspend fun open(token: Long): Outcome<Unit>

    public fun discard(token: Long)
}

@ContributesTo(AppScope::class)
public interface WebResultOpenerBindings {
    @Multibinds(allowEmpty = true) public val webResultOpeners: Set<WebResultOpener>
}
