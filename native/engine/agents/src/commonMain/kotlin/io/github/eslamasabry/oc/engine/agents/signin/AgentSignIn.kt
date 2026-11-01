package io.github.eslamasabry.oc.engine.agents.signin

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.auth.AgentAuth
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.host.AgentHost
import io.github.eslamasabry.oc.engine.agents.host.GuestCommand
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How a person signs an agent in (plan 17 §1.5 "Sign in", AGENTS-BACKENDS-13/14). */
public sealed interface SignInPlan {
    /** The agent's own login runs in a terminal: [program] with exactly [args] (from the catalog's signInArgs). */
    public data class Terminal(val agent: AgentId, val program: String, val args: ImmutableList<String>) : SignInPlan

    /** API-key agents (Qwen, Goose): words key [SignInWords.ProviderKeyOnHost]; the app has no key field. */
    public data class ProviderKeyOnHost(val agent: AgentId) : SignInPlan

    /** Not a phone agent, not in the catalog, or no sign-in at all. */
    public data class NotAvailable(val agent: AgentId) : SignInPlan
}

/** Words keys this slice names (`:ui:words` owns the copy; English in the S3-AGENTSE report). */
public enum class SignInWords {
    /** "Set up a provider key on the host to use this agent." */
    ProviderKeyOnHost,

    /** "Open sign-in page" */
    OpenSignInPage,

    /** "Copy code" */
    CopyCode,
}

/**
 * The sign-in model. The terminal runs [AgentHost.signInCommand] through `TerminalHost` (17 §2.6); the app never
 * reads, stores or relays a code (owner terms decision 2026-10-05). When the terminal ends, [finish] re-probes and
 * returns signed-in only on a `SignedIn` projection.
 */
public class AgentSignIn(
    private val host: AgentHost,
    private val auth: AgentAuth,
    private val catalog: AgentCatalog = AgentCatalog.builtIn,
) {
    public fun plan(agent: AgentId): SignInPlan {
        val descriptor = catalog.byId(agent) ?: return SignInPlan.NotAvailable(agent)
        val recipe = descriptor.recipe
        if (!descriptor.runsOnPhoneHost || recipe == null) return SignInPlan.NotAvailable(agent)
        return when (descriptor.signInMethod) {
            AgentSignInMethod.ApiKeyHost -> SignInPlan.ProviderKeyOnHost(agent)
            AgentSignInMethod.None -> SignInPlan.NotAvailable(agent)
            AgentSignInMethod.BrowserOAuthHost ->
                if (SignInCommandPolicy.allows(recipe.executable, recipe.signInArgs)) {
                    SignInPlan.Terminal(agent, recipe.executable, recipe.signInArgs)
                } else {
                    SignInPlan.NotAvailable(agent)
                }
        }
    }

    /** The guest command for the sign-in terminal. Refused before the host sees anything outside the allowlist. */
    public fun command(owner: OwnerId, agent: AgentId): Outcome<GuestCommand> {
        val plan = plan(agent) as? SignInPlan.Terminal ?: return Failure.Unavailable(FeatureId("agentSignIn")).failed()
        return host.signInCommand(owner, agent, plan.args)
    }

    /** The terminal ended: re-read, signed in only on a `SignedIn` projection. */
    public suspend fun finish(agent: AgentId): Boolean = auth.confirmSignIn(agent)
}

/** The allowlist the kept Kotlin enforces too (`agentSignInCommand`); checked here first so a bad call never leaves. */
public object SignInCommandPolicy {
    public val PROGRAMS: Set<String> = setOf("claude", "codex", "gemini", "qwen", "goose", "omp", "fx", "kilo", "cline")
    public const val MAX_ARGS: Int = 5
    private val ARG = Regex("^[A-Za-z0-9-]{1,32}$")

    public fun allows(program: String, args: List<String>): Boolean =
        program in PROGRAMS && args.size <= MAX_ARGS && args.all { ARG.matches(it) }

    /** For callers that build args themselves (a terminal launched with the CLI and no args). */
    public fun checked(program: String, args: List<String>): ImmutableList<String>? =
        if (allows(program, args)) args.toImmutableList() else null
}

/**
 * Why the sign-in foreground claim failed (11 §9.1 `SignIn` row, 17 §1.5). Shown only under Details, as the code.
 * The engine waits [START_RECEIPT_WAIT] for Android's start receipt.
 */
public sealed interface SignInForegroundFailure {
    public val code: String

    public data class Admission(val admission: String) : SignInForegroundFailure {
        init {
            require(ADMISSION.matches(admission)) { "Admission codes are short lowercase words." }
        }

        override val code: String get() = "sign-in-admission/$admission"
    }

    public data class Lost(val reason: LostReason) : SignInForegroundFailure {
        override val code: String get() = "sign-in-lost/${reason.code}"
    }

    public data object ServiceRefused : SignInForegroundFailure {
        override val code: String get() = "service-refused"
    }

    public data object ServiceReceiptUnsaved : SignInForegroundFailure {
        override val code: String get() = "service-receipt-unsaved"
    }

    public enum class LostReason(public val code: String) {
        AndroidTimeLimit("android-time-limit"),
        PausedByYou("paused-by-you"),
        ServiceGone("service-gone"),
        LiveTurnedOff("live-turned-off"),
        AppClosed("app-closed"),
        OwnerClosed("owner-closed"),
    }

    public companion object {
        public val START_RECEIPT_WAIT: Duration = 5.seconds
        private val ADMISSION = Regex("^[a-z0-9-]{1,40}$")

        /** The code as stored or reported, back to a value; unknown text is never echoed. */
        public fun fromCode(code: String): SignInForegroundFailure? =
            when {
                code == ServiceRefused.code -> ServiceRefused
                code == ServiceReceiptUnsaved.code -> ServiceReceiptUnsaved
                code.startsWith("sign-in-lost/") ->
                    LostReason.entries.firstOrNull { it.code == code.removePrefix("sign-in-lost/") }?.let(::Lost)
                code.startsWith("sign-in-admission/") ->
                    code.removePrefix("sign-in-admission/").takeIf { ADMISSION.matches(it) }?.let(::Admission)
                else -> null
            }
    }
}
