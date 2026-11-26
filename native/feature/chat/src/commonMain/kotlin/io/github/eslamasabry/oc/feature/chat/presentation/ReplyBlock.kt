package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorCause
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorText

/** Distinguishes a limit or regional refusal from a removed model on a failed reply card. */
public enum class ReplyBlock {
    /** OpenCode's shared free daily allowance is used up. */
    FreeDailyLimit,

    /** Another usage or free-tier limit stopped this reply. */
    Limit,

    /** This specific model is not offered in the user's country or region. */
    CountryRefusal,
    ;

    public companion object {
        private val freeDaily =
            Regex(
                "free.?usage.?(?:limit|exceeded|reached)|free limit (?:reached|exceeded)",
                RegexOption.IGNORE_CASE,
            )
        private val countryRefusal =
            Regex(
                "model\\b[^.\\n]{0,40}\\bnot (?:available|offered) in your (?:country|region)",
                RegexOption.IGNORE_CASE,
            )

        /** Classifies [reason] into a [ReplyBlock], or null when none applies. */
        public fun of(reason: String?): ReplyBlock? =
            if (reason.isNullOrBlank()) null
            else {
                val cause = AgentErrorText.classify(reason)
                when {
                    freeDaily.containsMatchIn(reason) -> FreeDailyLimit
                    cause == AgentErrorCause.UsageLimit || cause == AgentErrorCause.FreeTierLimit -> Limit
                    countryRefusal.containsMatchIn(reason) -> CountryRefusal
                    else -> null
                }
            }
    }
}
