package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingSource
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalLinkHost
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalLinkReview
import kotlinx.coroutines.launch

@ContributesTo(AppScope::class)
interface TerminalLinksGraph {
    val terminalLinkHost: TerminalLinkHost
}

/** Approval stays in the visible terminal slot; only policy-approved URLs can reach Android. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidTerminalLinks(
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
    private val scope: AppCoroutineScope,
    private val teaching: TeachingSource,
) : TerminalLinkHost {
    override fun review(url: String): TerminalLinkReview? =
        when (val decision = policy.check(url, LinkOrigin.Terminal)) {
            is LinkDecision.AskFirst ->
                TerminalLinkReview(decision.shownHost, decision.insecure) {
                    scope.launch {
                        if (opener.open(decision.link) is Outcome.Ok)
                            teaching.controller()?.markUsed(TeachingIds.TerminalLinks)
                    }
                }
            is LinkDecision.Open -> {
                scope.launch {
                    if (opener.open(decision.link) is Outcome.Ok)
                        teaching.controller()?.markUsed(TeachingIds.TerminalLinks)
                }
                null
            }
            is LinkDecision.Refuse -> null
        }
}
