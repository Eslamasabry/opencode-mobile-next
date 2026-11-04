package io.github.eslamasabry.oc.feature.preview.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * What the app's link policy said about an address, in the preview's terms. [token] is opaque to
 * the feature.
 */
public sealed interface BrowserTicket {
    public data class Open(val token: Any) : BrowserTicket

    /** Ask "Open this link in your browser?" naming [host] first. */
    public data class AskFirst(val host: String, val token: Any, val insecure: Boolean = false) :
        BrowserTicket

    public data object Refused : BrowserTicket
}

/**
 * The preview's way out to the browser. The app implements it with `ExternalLinkPolicy` →
 * `ExternalLinkOpener` (AGENTS invariant 8); features may not reach `:core:security` themselves
 * (K1).
 */
public interface PreviewBrowser {
    /**
     * [appAuthored]: the address the app built for the preview (same origin), not one a page asked
     * for.
     */
    public fun check(url: String, appAuthored: Boolean): BrowserTicket

    /** Opens what [check] approved; false when nothing could open it. */
    public fun open(token: Any): Boolean
}

/** The default where no browser is wired: everything is refused, nothing opens. */
@ContributesBinding(AppScope::class)
@Inject
public class NoPreviewBrowser : PreviewBrowser {
    override fun check(url: String, appAuthored: Boolean): BrowserTicket = BrowserTicket.Refused

    override fun open(token: Any): Boolean = false
}
