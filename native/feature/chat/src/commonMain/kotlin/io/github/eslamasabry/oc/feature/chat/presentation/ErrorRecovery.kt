package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorCause
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorText
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel

/**
 * What a failed reply can do next (Flutter `assistant_error_row.dart`): a sign-in, no-credit or
 * context-full failure is never a dead end. Pure; the screen draws one button per fix and the
 * server's own text stays under Details.
 */
public enum class ErrorFix {
    /** "Use {model} and resend": the model the server named, one tap. */
    UseModel,
    ChooseModel,
    Compact,
    OpenProviders,
    OpenAccount,

    /** B-169: "Switch model": another model, and the stopped prompt sent again with it. */
    SwitchModel,

    /** B-169 §5: "Connect a provider…" once a stopped prompt was switched as often as it may be. */
    ConnectProvider,
}

public object ErrorRecovery {
    private val offer = Regex("""did you mean:?\s*([^?\n]+)""", RegexOption.IGNORE_CASE)
    private val failedModel =
        Regex("""not found:?\s*([^\s/]+)/(\S+?)\.?(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val pieces = Regex(""",|\s+or\s+""")

    /**
     * The model a "model not found" [raw] error suggests, when this server offers it enabled (Flutter
     * `_suggestedModel`): the first suggestion that resolves, never the model that just failed.
     */
    public fun suggestedModel(raw: String?, models: List<ComposerModel>): ComposerModel? {
        if (raw.isNullOrBlank() || models.isEmpty()) return null
        val wanted = offer.find(raw)?.groupValues?.get(1) ?: return null
        val failed = failedModel.find(raw)
        val failingProvider = failed?.groupValues?.get(1)
        val failingId = failed?.groupValues?.get(2)
        for (piece in wanted.split(pieces)) {
            val name = piece.trim().trim('`', '\'', '"', '.')
            if (name.isEmpty()) continue
            val slash = name.indexOf('/')
            val provider = if (slash > 0) name.substring(0, slash) else failingProvider
            val id = if (slash > 0) name.substring(slash + 1) else name
            if (provider == failingProvider && id == failingId) continue
            val usable = models.filter { it.enabled && it.modelId == id }
            val match =
                usable.firstOrNull { it.providerId == provider }
                    ?: if (slash > 0) null else usable.firstOrNull()
            if (match != null) return match
        }
        return null
    }

    /**
     * B-169 §2/§5: the fixes for a failed reply a lasting limit stopped ([limit] is not None): Switch model
     * leads, and the existing Compact and account fixes stay where they apply. One switch control per turn, so
     * neither Choose nor Use model joins it. A prompt switched as often as it may be offers only Connect a provider.
     */
    public fun limitFixes(
        limit: LimitSwitchOffer,
        reply: ReplyFailureKind,
        reason: String?,
        canCompact: Boolean,
    ): List<ErrorFix> =
        when (limit) {
            LimitSwitchOffer.None -> emptyList()
            LimitSwitchOffer.Spent -> listOf(ErrorFix.ConnectProvider)
            LimitSwitchOffer.Switch ->
                listOfNotNull(
                    ErrorFix.SwitchModel,
                    ErrorFix.Compact.takeIf {
                        canCompact && AgentErrorText.classify(reason.orEmpty()) == AgentErrorCause.FreeTierLimit
                    },
                    ErrorFix.OpenAccount.takeIf { reply == ReplyFailureKind.NoCredit },
                )
        }

    /** The fixes for a failed reply, most useful first. */
    public fun fixes(
        typed: MessageErrorKind?,
        reply: ReplyFailureKind,
        reason: String?,
        hasSuggestion: Boolean,
        canCompact: Boolean,
    ): List<ErrorFix> {
        val model = if (hasSuggestion) ErrorFix.UseModel else ErrorFix.ChooseModel
        val cause = AgentErrorText.classify(reason.orEmpty())
        return when {
            typed == MessageErrorKind.ModelNotFound -> listOf(model)
            typed == MessageErrorKind.ContextOverflow ->
                if (canCompact) listOf(ErrorFix.Compact) else emptyList()
            typed == MessageErrorKind.ProviderAuth ->
                listOf(ErrorFix.OpenProviders, ErrorFix.ChooseModel)
            cause == AgentErrorCause.FreeTierLimit ->
                listOfNotNull(ErrorFix.Compact.takeIf { canCompact }, ErrorFix.ChooseModel)
            reply == ReplyFailureKind.SignIn -> listOf(ErrorFix.OpenProviders, ErrorFix.ChooseModel)
            reply == ReplyFailureKind.NoCredit -> listOf(ErrorFix.OpenAccount, ErrorFix.ChooseModel)
            reply == ReplyFailureKind.Model -> listOf(model)
            reply == ReplyFailureKind.Busy -> listOf(ErrorFix.ChooseModel)
            else -> emptyList()
        }
    }
}
