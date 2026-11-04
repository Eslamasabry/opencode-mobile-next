package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.feature.files.presentation.FileLinks
import io.github.eslamasabry.oc.feature.files.presentation.NoFileLinks

/** File Markdown links take the same policy and explicit confirmation path as chat links. */
@Inject
@ContributesBinding(AppScope::class, replaces = [NoFileLinks::class])
class AndroidFileLinks(
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : FileLinks {
    override fun host(url: String): String? =
        when (val decision = policy.check(url, LinkOrigin.Markdown)) {
            is LinkDecision.AskFirst -> decision.shownHost
            else -> null
        }

    override fun openConfirmed(url: String): Outcome<Unit> =
        when (val decision = policy.check(url, LinkOrigin.Markdown)) {
            is LinkDecision.AskFirst -> opener.open(decision.link)
            else -> Outcome.Failed(Failure.Unavailable(FeatureId("external-links")))
        }
}
