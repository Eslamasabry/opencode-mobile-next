package io.github.eslamasabry.oc.feature.agents.presentation

import io.github.eslamasabry.oc.core.common.PlatformViewHandle

/** Where the sign-in terminal is (plan 07 G03). Each step has fixed words in `:ui:words`. */
public enum class SignInStep {
    /** Reading the agent's plan. */
    Preparing,

    /** Waiting for the foreground claim and the native start. */
    Starting,

    /** The agent's own sign-in runs in the terminal. */
    Running,

    /** The sign-in ended; the account is being read. */
    Checking,
    SignedIn,

    /** The read did not say signed in ("isn't signed in yet. Start the sign-in again…"). */
    NotYet,

    /** Could not keep the sign-in running; [SignInTerminalUi.failureCode] under Details. */
    Failed,

    /** Notifications are off, so the sign-in cannot be protected. */
    NotificationsBlocked,

    /** An API-key agent: "Set up a provider key on the host"; no key field. */
    ProviderKeyOnHost,

    /** Not a phone agent, or its sign-in is not ready on this phone. */
    Unavailable,
}

/** The sign-in terminal screen's state (plan 07 G03). No copy here: the screen maps it to words. */
public data class SignInTerminalUi(
    val agentName: String = "",
    val usesClaudeAccount: Boolean = false,
    val step: SignInStep = SignInStep.Preparing,
    /** The live grid while the terminal exists. */
    val terminal: PlatformViewHandle? = null,
    val title: String = "",
    /** Accessible-mode text (06 §7.7). */
    val transcript: String = "",
    /** The person's accessible-mode choice; null follows TalkBack / Switch Access. */
    val accessibleMode: Boolean? = null,
    val inputEnabled: Boolean = false,
    /** The sign-in printed a page to open (17 §1.5 "Open sign-in page"). */
    val canOpenPage: Boolean = false,
    /** The one-time code the sign-in printed ("Copy code"); never stored or sent. */
    val code: String? = null,
    /** Fixed failure code for Details (`sign-in-start`, `sign-in-lost/…`); never CLI text. */
    val failureCode: String? = null,
    /** The last attempt to stop the sign-in failed; it still runs and is still protected. */
    val stopFailed: Boolean = false,
    val linkPrompt: SignInPagePrompt? = null,
) {
    override fun toString(): String =
        "SignInTerminalUi(step=$step, terminal=${terminal != null}, transcript=${transcript.length} chars, " +
            "canOpenPage=$canOpenPage, code=${code != null}, failureCode=$failureCode, stopFailed=$stopFailed)"
}

public data class SignInPagePrompt(val host: String, val insecure: Boolean)

/** What the person can do on the sign-in terminal. Each action names its target. */
public interface SignInTerminalActions {
    /** "Open sign-in page": the page the sign-in printed, through the link policy. */
    public fun openSignInPage()

    public fun confirmSignInPage()

    public fun dismissSignInPage()

    /** "Copy code": the one-time code, copied as sensitive. */
    public fun copyCode()

    /** "Send to terminal": [line] is exactly what to write (the field adds the carriage return). */
    public fun send(line: String)

    public fun setAccessibleMode(on: Boolean)

    /** "Done": stops the sign-in if it still runs, then reads the account. */
    public fun finish()

    /** "Sign in again" after NotYet or a failure. */
    public fun startAgain()

    /**
     * Back: stops the sign-in (exactly this one), then leaves; stays when it could not be stopped.
     */
    public fun leave()
}
