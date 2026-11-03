package io.github.eslamasabry.oc.feature.chat.presentation

/**
 * What a failed reply tells the person (QA wave 1 B5): a plain reason with the next step. The server's technical
 * text is never the line itself; it stays under Details.
 */
public enum class ReplyFailureKind {
    /** The provider refused the key or sign-in: sign in to that provider again, or pick another model. */
    SignIn,

    /** The model is unknown or not offered any more: pick another model. */
    Model,

    /** Rate limit, quota or an overloaded provider: wait, or pick another model. */
    Busy,

    /** Anything else: send again, or pick another model. */
    Other,
    ;

    public companion object {
        /** Reads the server's error text; never shows it. */
        public fun of(reason: String?): ReplyFailureKind {
            val t = reason?.lowercase() ?: return Other
            fun any(vararg words: String) = words.any { it in t }
            return when {
                any("401", "403", "unauthor", "forbidden", "api key", "api_key", "apikey", "expired", "invalid key",
                    "authentication", "not authenticated", "credential", "sign in", "login") -> SignIn
                any("429", "rate limit", "ratelimit", "too many requests", "quota", "overloaded", "insufficient",
                    "billing", "credit", "capacity", "503") -> Busy
                any("model not found", "unknown model", "model_not_found", "no such model", "not supported",
                    "does not exist", "404") -> Model
                else -> Other
            }
        }
    }
}
