package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason

/** A safe `https` link from release notes waiting for the person's confirmation before opening outside the app. */
public data class NoteLinkPrompt(val url: String, val host: String)

/** User actions for links inside What's new release notes (B-628). */
public sealed interface NoteLinkAction {
    public data class Open(val url: String) : NoteLinkAction

    public data object Confirm : NoteLinkAction

    public data object DismissPrompt : NoteLinkAction

    public data object DismissNoBrowser : NoteLinkAction
}

/** What the Updates page can do. Each name says its one target. */
public interface UpdatesActions {
    public fun onBack()

    /** Update: download, check, install. */
    public fun onUpdate()

    /** Stops the running download. */
    public fun onCancel()

    /** Install the file that is already downloaded and checked. */
    public fun onInstall()

    /** After a failure that another try can fix (network, storage, installer, checksum, no file). */
    public fun onTryAgain()

    /** After a refusal the same file would get again (signer, package, version): the release page on GitHub. */
    public fun onOpenRelease()

    public fun onCheckNow()

    /** The "Check GitHub for new versions" switch. */
    public fun onChecks(on: Boolean)

    /** Handles tapping, confirming, or dismissing What's new links and the missing-browser notice (B-628). */
    public fun onNoteLink(action: NoteLinkAction)
}

/**
 * Opens links from the Updates page. Addresses come from GitHub's reply, so the app did not author them:
 * :androidApp binds this to ExternalLinkPolicy → ExternalLinkOpener (native/AGENTS.md rule 8). A feature
 * may not depend on :core:security, hence this port (the :feature:agents SignInLinks pattern).
 */
public interface UpdateLinks {
    public suspend fun openReleasePage(url: String): Outcome<Unit>

    /** Checks a release-notes link (`https` only) through ExternalLinkPolicy; null when refused. */
    public fun inspectNoteLink(url: String): NoteLinkPrompt? = null

    /** Opens a release-notes link after the person confirmed the host, re-checking through the policy. */
    public suspend fun openNoteLink(url: String): Outcome<Unit> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}
