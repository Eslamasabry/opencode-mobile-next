package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.feature.search.web.WebLinkReview
import io.github.eslamasabry.oc.feature.search.web.WebResultOpener

/** One pending, user-confirmed search result. The approval cannot be reused for a different URL. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<WebResultOpener>())
@Inject
class ProtoSearchLinks(
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : WebResultOpener {
    private var generation = 0L
    private var held: Pair<Long, ApprovedLink>? = null

    override fun review(url: String): WebLinkReview? {
        held = null
        val decision =
            policy.check(url, LinkOrigin.ServerContent) as? LinkDecision.AskFirst ?: return null
        val token = ++generation
        held = token to decision.link
        return WebLinkReview(token, decision.shownHost, decision.insecure)
    }

    override suspend fun open(token: Long): Outcome<Unit> {
        val approved =
            held?.takeIf { it.first == token }?.second
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        held = null
        return opener.open(approved)
    }

    override fun discard(token: Long) {
        if (held?.first == token) held = null
    }
}
