package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * S24-CHAT story 1 (Flutter `connectionKitStatus` `serverOnThisPhone` / `onRestartServer`): the
 * OpenCode server that runs on this phone, as the chat's connection line needs it. The Android app
 * binds it over the phone server's one start path; without a binding (desktop, tests) no
 * conversation's server is on this phone and Restart is never offered.
 */
public interface ChatPhoneServer {
    /** True when [profile] is the server this phone runs (the built-in Linux one). */
    public fun runsHere(profile: ProfileId): Boolean

    /** Stop then start it again; a person's restart spends no recovery attempt. */
    public suspend fun restart(profile: ProfileId): Outcome<Unit>

    /** True when [profile] is the saved "Phone agents" server, whose helper the app starts (Flutter `recoverAgentBackend`). */
    public fun isAgentHelper(profile: ProfileId): Boolean = false

    /** Makes sure that helper runs and answers, starting it when it does not. Ok for any other server. */
    public suspend fun startAgentHelper(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

    /**
     * Issue 147: a conversation of that helper opened while it is not connected. The app's own start of it runs now
     * (the one the home uses, never a second one), with a new start window; returns at once.
     */
    public fun wantAgentHelper(profile: ProfileId) {}

    /**
     * What this phone's own server is doing for [profile] that a conversation should wait for: starting, or stopped
     * for the OpenCode update (the automatic one included). [PhoneServerWait.None] for any other server, and while
     * nothing is in progress.
     */
    public fun waiting(profile: ProfileId): StateFlow<PhoneServerWait> = NotWaiting

    /**
     * The real current stage of getting [profile]'s server ready (This phone's OpenCode, or the agents' helper and its
     * agents), from the app's own signals; null while none is current (readers then use their generic words). Each
     * stage already holds at least [STAGE_HOLD_MS]. Read by the chat page's waiting line and by the home.
     */
    public fun stage(profile: ProfileId): StateFlow<LaunchStage?> = NoStage

    /**
     * Issue 147: true when [ref] is a phone agent's conversation the agents helper no longer holds and cannot take up
     * again where it stopped (after a restart, for an agent whose own resume is not proven, or one that already failed
     * to resume): it opens to read what it had, and continuing starts a new conversation. False when it continues in
     * place; null when unknown (another server, or no record of it yet).
     */
    public fun needsNewSession(ref: ConversationRef): Boolean? = null

    /**
     * Issue 147: what [directory], a folder in this phone's storage, is now to the side of the phone that works in
     * [ref]'s conversation (this phone's OpenCode, or the agents' helper). A [thorough] check lists and writes it from
     * that side (when the page comes back); otherwise only the access, the folder and the running binds are read (before
     * each message). Null when the folder is not in this phone's storage, the server is another one, or unknown.
     */
    public suspend fun phoneFolder(ref: ConversationRef, directory: String?, thorough: Boolean): ChatPhoneFolder? = null

    /** The way forward for [reach]: Android's All files access page, or a new start of the side that can't see it. */
    public suspend fun fixPhoneFolder(ref: ConversationRef, reach: ChatPhoneFolder): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneFolder")))
}

/** Issue 147: a conversation's folder in this phone's storage, as the side that works in it finds it now. */
public enum class ChatPhoneFolder {
    Ready,

    /** Android's All files access is off (taken back since the folder was chosen). */
    AppAccess,

    /** That side was started without the folder and does not see it until it starts again. */
    Restart,

    /** The folder is not on the phone any more. */
    Missing,

    /** That side cannot open or change files in it. */
    Denied,
}

/**
 * Issue 147: a message is never sent into a folder the agent cannot reach. Null when it may go; otherwise the failure
 * the send answers (words: `chatSendPhoneFolder`) and the reason, which the page's notice then shows.
 */
public suspend fun ChatPhoneServer?.phoneFolderRefusal(
    ref: ConversationRef,
    directory: String?,
): Pair<Failure, ChatPhoneFolder>? {
    val reach = this?.phoneFolder(ref, directory, thorough = false) ?: return null
    if (reach == ChatPhoneFolder.Ready) return null
    return Failure.Unavailable(FeatureId(PHONE_FOLDER_FEATURE)) to reach
}

/** The feature name of a send refused because the conversation's folder in phone storage can't be reached. */
public const val PHONE_FOLDER_FEATURE: String = "phoneFolder"

/** Why this phone's server does not answer yet, as far as the app that runs it knows. */
public enum class PhoneServerWait {
    None,
    Starting,
    Updating,

    /** The phone's agents (Claude Code and the others) are being updated; their helper is down meanwhile. */
    UpdatingAgents,

    /** A phone agent needs the person to sign in; its conversations wait for that and say so. */
    AgentNeedsSignIn,

    /** STUB (red proof): never produced on this branch. */
    StoppedByPerson,
}

private val NotWaiting: StateFlow<PhoneServerWait> = MutableStateFlow(PhoneServerWait.None)

@ContributesTo(AppScope::class)
public interface ChatPhoneServerBindings {
    @Multibinds(allowEmpty = true) public val chatPhoneServers: Set<ChatPhoneServer>
}
