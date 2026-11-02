package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CoroutineScope

/**
 * What the Git page needs from the app, in one place: the workspace resolver, one [GitHubAccount] per profile (kept for
 * the app's life so a sign-in survives leaving the page), Details text through the [Redactor], and GitHub pages opened
 * only through [ExternalLinkPolicy] → [ExternalLinkOpener] (AGENTS.md security invariant).
 */
public class GitServices(
    public val workspaces: GitWorkspaces,
    private val tokens: GitHubTokenStore,
    private val flow: GitHubDeviceFlow,
    private val redactor: Redactor,
    private val links: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val accounts = HashMap<ProfileId, GitHubAccount>()

    public fun page(ref: ConversationRef): GitPage = GitPage(ref, workspaces)

    public fun account(profile: ProfileId): GitHubAccount =
        accounts.getOrPut(profile) { GitHubAccount(profile, tokens, flow, scope, nowMs) }

    /** Git's own text, redacted, for the Details fold only; null when there is none. */
    public fun details(failure: GitFailure?): Redacted? =
        failure?.details?.takeIf { it.isNotBlank() }?.let { redactor.details(scrubGitText(it, emptyList())) }

    /** GitHub's sign-in words, redacted, for Details only. */
    public fun details(text: String): Redacted? = text.takeIf { it.isNotBlank() }?.let(redactor::details)

    /**
     * Opens the verification page GitHub named. It came from the network, so it is checked as server content; only an
     * `https://github.com/…` page is opened (the button already says "Open GitHub", which is the person's consent).
     */
    public fun openVerification(uri: String): Boolean =
        when (val decision = links.check(uri, LinkOrigin.ServerContent)) {
            is LinkDecision.AskFirst ->
                decision.shownHost == "github.com" && uri.startsWith("https://") && opener.open(decision.link) is Outcome.Ok
            is LinkDecision.Open -> opener.open(decision.link) is Outcome.Ok
            is LinkDecision.Refuse -> false
        }

    /** The app-authored page where people review or revoke the app's GitHub access. */
    public fun openManageAccess(): Boolean =
        when (val decision = links.check(GITHUB_AUTHORIZED_APPS_URL, LinkOrigin.AppAuthored)) {
            is LinkDecision.Open -> opener.open(decision.link) is Outcome.Ok
            else -> false
        }

    /** A removed server's GitHub sign-in goes with it (B-SEC-085). */
    public suspend fun forgetRemovedProfiles(existing: Set<ProfileId>) {
        tokens.sweep(existing)
        accounts.keys.retainAll(existing)
    }

    override fun toString(): String = "GitServices"
}
