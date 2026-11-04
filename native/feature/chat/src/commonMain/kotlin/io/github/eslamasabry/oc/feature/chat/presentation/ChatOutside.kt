package io.github.eslamasabry.oc.feature.chat.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * P12-CHAT-FEEL: what the chat hands to the platform — the clipboard, the share sheet and links.
 * Plain strings in, a plain answer out, so the presenter is tested with `kotlin.test`. The Android
 * app binds the real one (`graph/ChatFeelWiring.kt`), which checks every link with
 * `ExternalLinkPolicy` (origin Markdown) and opens it only through `ExternalLinkOpener` (AGENTS.md
 * invariant, R17). This module may not see `:core:security`, so the policy's decision arrives as a
 * [LinkCheck].
 */
public interface ChatOutside {
    /**
     * Puts [text] on the clipboard verbatim (a reply is the person's to paste). False = it did not
     * work.
     */
    public fun copy(text: String): Boolean

    /** Opens the system share sheet with [text] (this app is not offered as a target). */
    public fun share(text: String): Boolean

    /**
     * Runs [url] through the link policy. An approved app-authored link opens at once; reply links
     * ask first.
     */
    public fun checkLink(url: String): LinkCheck

    /**
     * Opens a link [checkLink] approved and the person confirmed. False = refused or the platform
     * failed.
     */
    public fun openLink(url: String): Boolean
}

/** The link policy's answer, in this module's words. */
public sealed interface LinkCheck {
    /** Opened already. */
    public data object Opened : LinkCheck

    /** Approved, but the person confirms first; [host] is shown in the question. */
    public data class Ask(val host: String, val insecure: Boolean = false) : LinkCheck

    /** Refused (bad scheme, credentials in the address, too long, not an address). */
    public data object Refused : LinkCheck
}

/**
 * The default for graphs without a platform (desktop, `:quality`'s smoke graph): nothing leaves the
 * app. The Android graph replaces it.
 */
@Inject
@ContributesBinding(AppScope::class)
public class NoChatOutside : ChatOutside {
    override fun copy(text: String): Boolean = false

    override fun share(text: String): Boolean = false

    override fun checkLink(url: String): LinkCheck = LinkCheck.Refused

    override fun openLink(url: String): Boolean = false
}
