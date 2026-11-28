package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.core.common.Outcome

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
}

/**
 * Opens the release page. Its address comes from GitHub's reply, so the app did not author it: :androidApp binds this
 * to ExternalLinkPolicy.check(url, LinkOrigin.ServerContent) → ExternalLinkOpener (native/AGENTS.md rule 8). A feature
 * may not depend on :core:security, hence this port (the :feature:agents SignInLinks pattern).
 */
public fun interface UpdateLinks {
    public suspend fun openReleasePage(url: String): Outcome<Unit>
}
