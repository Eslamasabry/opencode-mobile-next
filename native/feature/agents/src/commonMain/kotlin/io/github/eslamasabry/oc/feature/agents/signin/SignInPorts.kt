package io.github.eslamasabry.oc.feature.agents.signin

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// The sign-in screen's ports are bound to the agents engine, native terminal and foreground
// owner by AgentTerminalWiring. Every page handoff passes the external-link policy.

/** The phone's terminal sessions (plan 17 §2.6). Sessions belong to the host, not a screen. */
public interface TerminalHost {
    /**
     * Opens [target] on a new PTY. Returns once the native side named the session; a failure
     * carries no CLI text.
     */
    public suspend fun open(target: TerminalTarget): Outcome<TerminalModel>
}

/** What a terminal runs (plan 17 §2.6; only the sign-in target is in slice 3). */
public sealed interface TerminalTarget {
    /** An agent's own sign-in, exactly [command] and nothing else (B-AGENT-129). */
    public data class SignIn(val command: SignInCommand) : TerminalTarget
}

/** One live terminal (plan 17 §2.6, plus the event stream of plan 11 W11-08a). */
public interface TerminalModel {
    /**
     * The live grid (a Termux `TerminalView` on Android); only kit `androidMain` touches its
     * `native` value.
     */
    public val view: PlatformViewHandle
    public val title: StateFlow<String>

    /** Accessible-mode text (plan 06 §7.7): ANSI/OSC stripped, at most 100,000 characters. */
    public val transcript: StateFlow<String>

    /** The open-URL watcher's requests and the program's exit. */
    public val events: Flow<TerminalEvent>

    public suspend fun write(bytes: ByteArray)

    public suspend fun resize(cols: Int, rows: Int)

    /**
     * Stops the program and removes this exact session. A failed drain keeps it; calling again
     * retries the same id.
     */
    public suspend fun close(): Outcome<Unit>
}

public sealed interface TerminalEvent {
    /**
     * The guest's stand-in `xdg-open` asked for [url] (`<home>/.oc-open-url`). Opened only through
     * the link policy.
     */
    public class OpenUrl(public val url: String) : TerminalEvent {
        override fun toString(): String = "OpenUrl(•••)"
    }

    /** The program ended with [code] (minus the signal when one ended it). */
    public data class Exited(val code: Int) : TerminalEvent
}

/**
 * The guest command a sign-in terminal runs (mirror of `:engine:agents` `GuestCommand`): an
 * allowlisted [program] with at most five short arguments, a private [home] and its environment.
 * Built only by the agents engine (`AgentHost.signInCommand`); `toString` never prints the
 * environment.
 */
public class SignInCommand(
    public val program: String,
    public val args: List<String>,
    public val env: Map<String, String> = emptyMap(),
    public val home: String = "",
) {
    override fun equals(other: Any?): Boolean =
        other is SignInCommand &&
            other.program == program &&
            other.args == args &&
            other.env == env &&
            other.home == home

    override fun hashCode(): Int =
        ((program.hashCode() * 31 + args.hashCode()) * 31 + env.hashCode()) * 31 + home.hashCode()

    override fun toString(): String = "SignInCommand($program, ${args.size} args)"
}

/** An agent of the catalog, by id (`claude`, `codex`, …). */
public data class AgentRef(val id: String)

/** How an agent signs in (mirror of `:engine:agents` `SignInPlan`). */
public sealed interface SignInPlan {
    /**
     * The agent's own login runs in a terminal. [usesClaudeAccount]: words name the Claude account.
     */
    public data class Terminal(val agentName: String, val usesClaudeAccount: Boolean) : SignInPlan

    /**
     * API-key agents (Qwen, Goose): "Set up a provider key on the host"; the app has no key field.
     */
    public data class ProviderKeyOnHost(val agentName: String) : SignInPlan

    /** Not a phone agent, not in the catalog, or no sign-in at all. */
    public data class NotAvailable(val agentName: String) : SignInPlan
}

/**
 * What the sign-in printed: the page to sign in on and the one-time code. Never logged, stored or
 * sent.
 */
public class SignInFound(public val page: String? = null, public val code: String? = null) {
    override fun equals(other: Any?): Boolean =
        other is SignInFound && other.page == page && other.code == code

    override fun hashCode(): Int = (page?.hashCode() ?: 0) * 31 + (code?.hashCode() ?: 0)

    override fun toString(): String = "SignInFound(page=${page != null}, code=${code != null})"
}

/** The agents engine's sign-in model, as the screen needs it. */
public interface SignInAgents {
    public fun plan(agent: AgentRef): SignInPlan

    /**
     * The guest command (allowlist checked before the host sees anything). A refusal carries no CLI
     * text.
     */
    public fun command(owner: ProfileId, agent: AgentRef): Outcome<SignInCommand>

    /**
     * The terminal ended: re-read the account; true only on a `SignedIn` projection (exit code 0 is
     * never proof).
     */
    public suspend fun confirmSignedIn(owner: ProfileId, agent: AgentRef): Boolean

    /**
     * `SignInOutputReader` over the transcript: the newest https page not on this phone, and a
     * device code.
     */
    public fun read(transcript: String): SignInFound
}

/** The sign-in foreground claim (plan 11 §9.1 `SignIn` row). */
public interface SignInForeground {
    /**
     * Reserves the claim at once (before anything suspends); the lease says when Android admitted
     * it.
     */
    public fun reserve(): SignInForegroundLease
}

public interface SignInForegroundLease {
    /** Suspends until Android's start receipt (5 s at most, plan 17 §1.5) or a refusal. */
    public suspend fun admitted(): SignInAdmission

    /** Whether protection holds right now (rechecked before every URL handoff). */
    public val active: Boolean

    /** Loss reasons (`android-time-limit`, `paused-by-you`, `service-gone`, …) after admission. */
    public val lost: Flow<String>

    /** True only after the platform confirmed releasing this particular claim. */
    public val releaseConfirmed: Boolean
        get() = true

    public suspend fun release()
}

public sealed interface SignInAdmission {
    public data object Ready : SignInAdmission

    /** Notifications are off, so Android would not show the foreground notice. */
    public data object NotificationsBlocked : SignInAdmission

    /** Refused with a short lowercase code (`service-refused`, `service-receipt-unsaved`, …). */
    public data class Refused(val code: String) : SignInAdmission
}

/**
 * Opens a page the sign-in asked for, only through ExternalLinkPolicy → ExternalLinkOpener (16
 * SI-1).
 */
public fun interface SignInLinks {
    public suspend fun open(url: String): Outcome<Unit>

    /** A platform can require a visible host confirmation before opening an untrusted page. */
    public fun check(url: String): SignInLinkCheck = SignInLinkCheck.Direct

    /** Called only after the person confirms the exact page named by [check]. */
    public suspend fun openConfirmed(url: String): Outcome<Unit> = open(url)
}

public sealed interface SignInLinkCheck {
    public data object Direct : SignInLinkCheck

    public data object Refused : SignInLinkCheck

    public data class Ask(val host: String, val insecure: Boolean) : SignInLinkCheck
}

/**
 * Copies the one-time code as sensitive (`KitCopy(sensitive = true)`: hidden from clipboard
 * previews).
 */
public fun interface SignInClipboard {
    public fun copySensitive(text: String)
}
