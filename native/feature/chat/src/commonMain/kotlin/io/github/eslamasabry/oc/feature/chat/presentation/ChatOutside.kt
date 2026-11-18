package io.github.eslamasabry.oc.feature.chat.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

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

    /**
     * E15-S05: [text] through the app's one redactor (`Redactor.redact`, Flutter `KitRedact.text`), so a key, token or
     * password a server, a tool or the person typed never reaches the clipboard or another app. The presenter masks
     * every text before [copy] and [share], the person's own prompt included (E15-S06). No default on purpose.
     */
    public fun mask(text: String): String

    /** B-AGENT-189: the conversation's server is this phone's own (its agents sign in here). */
    public fun isPhoneServer(ref: ConversationRef): Boolean = false

    /**
     * Set up at first use: the phone owner whose agents this conversation runs (the owner the Agents page and its set-up
     * terminal use); null where its agents are not this phone's.
     */
    public fun phoneAgentOwner(ref: ConversationRef): ProfileId? = null

    /**
     * The agent ids of [owner] the app has seen set up (their set-up terminal ended, or a conversation with them got an
     * answer: the phone's agent registry), as they are now.
     */
    public fun agentsSetUp(owner: ProfileId): Set<String> = emptySet()

    /** Emits when [owner]'s set-up list may have changed (a set-up terminal ended), so the chat reads it again. */
    public fun agentsSetUpChanges(owner: ProfileId): Flow<Unit> = emptyFlow()

    /**
     * A conversation with [owner]'s set-up-at-first-use [agent] got an answer: the app has now seen it set up, so the
     * Agents page and New conversation offer it as a normal choice. Nothing of the answer is passed.
     */
    public fun agentAnswered(owner: ProfileId, agent: String) {}

    /**
     * B-458: the sign-in of [owner]'s phone agents as the Agents page reads it right now, by provider id (true: signed
     * in, false: needs a sign-in; absent: not known yet). Emits again on every change. A stopped turn's "Sign in again"
     * follows it, never a flag of the chat's own, so it holds after the app is closed and opened again.
     */
    public fun agentSignIns(owner: ProfileId): Flow<Map<String, Boolean>> = flowOf(emptyMap())

    /**
     * Issue 147: a reply of [owner]'s phone agent behind [provider] ended on its sign-in at [atMs] (wall clock): the
     * Agents page, the chooser and New conversation read it as Sign in needed until a sign-in completes.
     */
    public fun agentSignInLapsed(owner: ProfileId, provider: String, atMs: Long) {}

    /**
     * signin-truth: a reply of [owner]'s phone agent behind [provider] at [atMs] was its plan's usage limit; [words] are
     * the agent's own line, read only for the reset it names (never kept or shown). Every place then reads "Plan limit
     * reached", never signed out.
     */
    public fun agentLimitReached(owner: ProfileId, provider: String, atMs: Long, words: String) {}

    /**
     * Puts [text] on the clipboard as given: the presenter has already [mask]ed it. False = it did not work.
     */
    public fun copy(text: String): Boolean

    /** Opens the system share sheet with [text], already [mask]ed (this app is not offered as a target). */
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

    /**
     * Opens an address the app itself authored (the GitHub star ask), through the same link policy and opener, with no
     * question first. Never pass a value from a server or a form. False = refused or the platform failed.
     */
    public fun openOwnLink(url: String): Boolean = false
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
    override fun mask(text: String): String = text // nothing leaves the app: copy and share refuse

    override fun copy(text: String): Boolean = false

    override fun share(text: String): Boolean = false

    override fun checkLink(url: String): LinkCheck = LinkCheck.Refused

    override fun openLink(url: String): Boolean = false

    override fun openOwnLink(url: String): Boolean = false
}

/** The permission round trip leaves a visible Retry action in chat. */
public enum class PhoneFileOpen { Preview, Opened, AccessRequested }

/** What a tap on a file path does: the in-app preview, Android's "Open with…", or Android's package installer. */
public enum class PhoneFileKind { Preview, Open, Install }
