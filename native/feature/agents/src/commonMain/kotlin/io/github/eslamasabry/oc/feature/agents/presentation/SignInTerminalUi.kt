package io.github.eslamasabry.oc.feature.agents.presentation

import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.feature.agents.signin.SignInStartStep

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

    /** An agent set up at first use finished its set-up terminal: it is marked set up and the screen goes back. */
    SetUpDone,

    /** The agent itself said it is not signed in ("isn't signed in yet. Start the sign-in again…"). */
    NotYet,

    /**
     * The read gave no answer about the account (busy, timed out, no answer), even after its own bounded retries:
     * "Couldn't check…", one "Check sign-in again", the reason under Details. Never shown as "not signed in".
     */
    CouldNotCheck,

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
    /** Antigravity: the terminal shows a Google link and the person pastes the browser's code back. */
    val pastesBrowserCode: Boolean = false,
    /** Gemini CLI: its own sign-in may tell people to move to Antigravity; the screen says what to do. */
    val offersAntigravityNote: Boolean = false,
    /** Set up at first use: the screen says "Set up <agent>", and the end of the terminal goes back (no account read). */
    val firstUse: Boolean = false,
    /** Qwen Code: "Type /auth and follow the steps." above its terminal. */
    val typesAuth: Boolean = false,
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
    /** Where a failed start stopped; the screen says it in words above Details. */
    val startStep: SignInStartStep? = null,
    /**
     * Details' technical text: the code, the step and the redacted failure ([SignInDetails]). Never
     * CLI output.
     */
    val failureDetails: String? = null,
    /** The last attempt to stop the sign-in failed; it still runs and is still protected. */
    val stopFailed: Boolean = false,
    val linkPrompt: SignInPagePrompt? = null,
    /**
     * While [SignInStep.Checking]: the last read was held back because a conversation's program is busy, and the
     * check waits for it ("…is busy with a conversation. Checking when it's free…").
     */
    val checkBusy: Boolean = false,
    /** [SignInStep.CouldNotCheck]'s fixed reason for Details (`agentBusy`, `timedOut`, …); never CLI text. */
    val checkCode: String? = null,
    /**
     * The whole address "Open sign-in page" opens, for Details only (the page shows the action, never the address as
     * reading text). Never logged.
     */
    val pageAddress: String? = null,
) {
    /** The agent's program still runs and takes input: the only time the code field and "Paste code" exist. */
    val takesInput: Boolean
        get() = step == SignInStep.Running && inputEnabled

    override fun toString(): String =
        "SignInTerminalUi(step=$step, terminal=${terminal != null}, transcript=${transcript.length} chars, " +
            "canOpenPage=$canOpenPage, code=${code != null}, failureCode=$failureCode, startStep=$startStep, " +
            "stopFailed=$stopFailed, checkBusy=$checkBusy, checkCode=$checkCode, pageAddress=${pageAddress != null})"
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

    /**
     * "Paste": writes [text] (the clipboard, read by the screen when the person asks) into the
     * running sign-in as one bracketed paste, the way a desktop terminal pastes. Antigravity asks
     * for the browser's code this way. Does nothing once the sign-in ended.
     */
    public fun paste(text: String)

    /** "Send to terminal": [line] is exactly what to write (the field adds the carriage return). */
    public fun send(line: String)

    public fun setAccessibleMode(on: Boolean)

    /**
     * "Done": stops the sign-in if it still runs, then reads the account. After [SignInStep.CouldNotCheck] it is
     * "Check sign-in again": it reads the account again, without a new sign-in.
     */
    public fun finish()

    /** "Sign in again" after NotYet or a failure. */
    public fun startAgain()

    /**
     * Back: stops the sign-in (exactly this one), then leaves; stays when it could not be stopped.
     */
    public fun leave()
}
