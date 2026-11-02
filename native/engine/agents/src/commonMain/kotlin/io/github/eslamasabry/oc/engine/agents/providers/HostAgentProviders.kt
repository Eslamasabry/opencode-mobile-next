package io.github.eslamasabry.oc.engine.agents.providers

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// Port of lib/domain/host_agent_providers.dart (AGENTS-BACKENDS-38). Protocol-neutral host truth: host availability
// controls new chats, restoration proof controls reopening old chats, never visibility or admission to a new chat.
// The Paseo snapshot projection (lib/paseo/host_agent_providers.dart `paseoHostAgentCatalog`) belongs to
// :protocol:paseo (S3-PASEO) and produces these values. Copy is words keys only.

public enum class HostAgentProviderAvailability { Ready, NeedsHostSignIn, Checking, Hidden }

public enum class HostAgentProviderHiddenReason { ResumeUnverified, ResumeUnsupported, NotPilot, Unavailable, Disabled }

public enum class HostAgentLoginState { Unknown, Ready, NeedsHostSignIn }

public enum class HostAgentResumeSupport { Unknown, Unsupported, LoadSession, ListAndLoad }

/** The provider's app-authored reason line (words keys; never a raw host error or agent output). */
public enum class HostAgentReasonKey {
    /** "Ready" */
    HostAgentReady,

    /** "Sign in on your computer first." */
    HostAgentSignInFirst,

    /** "Checking your computer." */
    HostAgentChecking,

    /** "Conversation restoration has not been checked." */
    HostAgentResumeUnverified,

    /** "This agent cannot restore a conversation." */
    HostAgentResumeUnsupported,

    /** "This agent is not available in this preview." */
    HostAgentNotPilot,

    /** "This agent is not available on your computer." */
    HostAgentUnavailable,

    /** "This agent is turned off on your computer." */
    HostAgentDisabled,
}

/** The sign-in hint (words keys). */
public enum class HostAgentSignInMessageKey {
    /** "Sign in on your computer: run gemini there, then check again." */
    HostAgentSignInGemini,

    /** "Run omp on your computer, use /login to sign in, then check again." */
    HostAgentSignInOmp,

    /** "Sign in to the agent on your computer, then check again." */
    HostAgentSignInGeneric,
}

public data class HostAgentModel(val id: String, val name: String)

public class HostAgentProvider(
    public val id: String,
    public val displayName: String,
    public val availability: HostAgentProviderAvailability,
    public val hiddenReason: HostAgentProviderHiddenReason? = null,
    public val loginState: HostAgentLoginState,
    public val resumeSupport: HostAgentResumeSupport,
    models: List<HostAgentModel> = emptyList(),
) {
    public val models: ImmutableList<HostAgentModel> = models.toImmutableList()

    init {
        require(
            hiddenReason != HostAgentProviderHiddenReason.ResumeUnverified &&
                hiddenReason != HostAgentProviderHiddenReason.ResumeUnsupported,
        ) { "Restoration alone cannot hide a host agent." }
        if (availability == HostAgentProviderAvailability.Hidden) {
            require(hiddenReason != null) { "A hidden host agent needs a reason." }
        } else {
            require(hiddenReason == null) { "Only a hidden host agent can have a hidden reason." }
        }
        require(
            !(availability == HostAgentProviderAvailability.Ready && loginState == HostAgentLoginState.NeedsHostSignIn),
        ) { "A signed-out host agent cannot be ready." }
        require(
            availability != HostAgentProviderAvailability.NeedsHostSignIn ||
                loginState == HostAgentLoginState.NeedsHostSignIn,
        ) { "A host sign-in request needs host sign-in state." }
    }

    public val resumeVerified: Boolean
        get() = resumeSupport == HostAgentResumeSupport.LoadSession ||
            resumeSupport == HostAgentResumeSupport.ListAndLoad

    public val selectable: Boolean
        get() = availability == HostAgentProviderAvailability.Ready && loginState != HostAgentLoginState.NeedsHostSignIn

    /** True when the row shows "Can't reopen old chats" with "Starts a new chat". */
    public val resumeUnverified: Boolean get() = !resumeVerified

    public val reason: HostAgentReasonKey
        get() = when (availability) {
            HostAgentProviderAvailability.Ready -> HostAgentReasonKey.HostAgentReady
            HostAgentProviderAvailability.NeedsHostSignIn -> HostAgentReasonKey.HostAgentSignInFirst
            HostAgentProviderAvailability.Checking -> HostAgentReasonKey.HostAgentChecking
            HostAgentProviderAvailability.Hidden -> when (hiddenReason!!) {
                HostAgentProviderHiddenReason.ResumeUnverified -> HostAgentReasonKey.HostAgentResumeUnverified
                HostAgentProviderHiddenReason.ResumeUnsupported -> HostAgentReasonKey.HostAgentResumeUnsupported
                HostAgentProviderHiddenReason.NotPilot -> HostAgentReasonKey.HostAgentNotPilot
                HostAgentProviderHiddenReason.Unavailable -> HostAgentReasonKey.HostAgentUnavailable
                HostAgentProviderHiddenReason.Disabled -> HostAgentReasonKey.HostAgentDisabled
            }
        }

    /** Fixed commands authored by the app (shown under Details), never commands supplied by the host or agent. */
    public val hostSignInCommand: String?
        get() = when (id) {
            "gemini" -> "gemini"
            "omp", "omp-acp" -> "omp"
            "fx" -> "fx login"
            else -> null
        }

    public val signInMessage: HostAgentSignInMessageKey
        get() = when (id) {
            "gemini" -> HostAgentSignInMessageKey.HostAgentSignInGemini
            "omp", "omp-acp" -> HostAgentSignInMessageKey.HostAgentSignInOmp
            else -> HostAgentSignInMessageKey.HostAgentSignInGeneric
        }

    override fun toString(): String = "HostAgentProvider($id, $availability)"
}

public class HostAgentProviderCatalog(providers: List<HostAgentProvider>) {
    public val providers: ImmutableList<HostAgentProvider> = providers.toImmutableList()
    public val selectable: ImmutableList<HostAgentProvider> get() = providers.filter { it.selectable }.toImmutableList()
}

/** Old chats with unknown restoration need an explicit new-chat action; a live chat does not need restoration. */
public enum class HostAgentContinuationState { ExistingRoute, LiveSession, ResumeUnverified, MissingHandle }

public data class HostAgentContinuation(
    val sessionId: String,
    val providerId: String,
    val state: HostAgentContinuationState,
) {
    public val requiresNewChat: Boolean
        get() = state == HostAgentContinuationState.ResumeUnverified ||
            state == HostAgentContinuationState.MissingHandle
    public val blocked: Boolean get() = requiresNewChat
}

/** Words: "Allow once" / "Reject once". Never "always". */
public enum class HostAgentPermissionBehavior { AllowOnce, RejectOnce }

/** The action id is opaque and only sent back to the same host; arbitrary agent permission copy is not exposed. */
public data class HostAgentPermissionChoice(val actionId: String, val behavior: HostAgentPermissionBehavior)

public class HostAgentPermissionRequest(
    public val requestId: String,
    public val sessionId: String,
    choices: List<HostAgentPermissionChoice>,
) {
    public val choices: ImmutableList<HostAgentPermissionChoice> = choices.toImmutableList()

    init {
        val ids = mutableSetOf<String>()
        for (choice in this.choices) {
            require(choice.actionId.isNotBlank() && ids.add(choice.actionId)) {
                "Permission choices need unique, nonempty IDs."
            }
        }
    }
}
