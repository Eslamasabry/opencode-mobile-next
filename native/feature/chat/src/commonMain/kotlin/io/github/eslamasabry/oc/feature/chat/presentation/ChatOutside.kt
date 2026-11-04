package io.github.eslamasabry.oc.feature.chat.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * P12-CHAT-FEEL: what the chat hands to the platform — the clipboard, the share sheet and links.
 * Plain strings in, a plain answer out, so the presenter is tested with `kotlin.test`. The Android
 * app binds the real one (`graph/ChatFeelWiring.kt`), which checks every link with
 * `ExternalLinkPolicy` (origin Markdown) and opens it only through `ExternalLinkOpener` (AGENTS.md
 * invariant, R17). This module may not see `:core:security`, so the policy's decision arrives as a
 * [LinkCheck].
 */
public interface ChatOutside {
    /** A local file link stays in the app's file flow, never in the browser. */
    public fun fileLinkPath(url: String): String? = null

    /** Flutter phoneFileAction: a shared-storage binary/APK of a server on this phone is Android's to open. */
    public suspend fun phoneFileKind(ref: ConversationRef, path: String): PhoneFileKind = PhoneFileKind.Preview

    /** After the person said yes: open the shared-storage binary/APK, request access, or keep the file preview. */
    public suspend fun openPhoneFile(ref: ConversationRef, path: String): Outcome<PhoneFileOpen> = Outcome.Ok(PhoneFileOpen.Preview)

    /** Puts [text] on the clipboard verbatim (a reply is the person's to paste). False = it did not work. */
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

/** The permission round trip leaves a visible Retry action in chat. */
public enum class PhoneFileOpen { Preview, Opened, AccessRequested }

/** What a tap on a file path does: the in-app preview, Android's "Open with…", or Android's package installer. */
public enum class PhoneFileKind { Preview, Open, Install }
