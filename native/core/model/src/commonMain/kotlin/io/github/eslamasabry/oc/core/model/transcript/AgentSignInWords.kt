package io.github.eslamasabry.oc.core.model.transcript

/**
 * The one rule for "the agent's sign-in is gone", read from the words an agent or its helper gave back. It lives here,
 * the lowest module every reader depends on, so the error kind, the headline, the retry card, the run result, the
 * phrase chips, the notices and the Paseo mapper all agree (they used to hold seven drifting copies; a Claude Code
 * "OAuth refresh token is no longer valid; run /login to re-authenticate" slipped past all of them).
 *
 * Two meanings, kept apart because the words to the person differ:
 * - [expired]: the agent was signed in and the sign-in ran out or was revoked. The words are "sign in again".
 * - [neverSignedIn]: the agent was never signed in ("Not logged in. Please run /login"). The words are "sign in".
 * [gone] is either of them, or any other sign-in refusal (an unusable key, "failed to authenticate").
 *
 * The text is only matched, never shown.
 */
public object AgentSignInWords {
    private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    /**
     * A sign-in the agent had and can no longer use: Claude Code ("OAuth session expired and could not be refreshed",
     * "OAuth refresh token is no longer valid; run /login to re-authenticate"), Codex ("Your access token could not be
     * refreshed because your refresh token was already used. Please log out and sign in again."), a revoked token.
     * Antigravity and Gemini have no wording of their own in this code's fixtures, so none is claimed for them.
     */
    private val EXPIRED =
        ci(
            "(?:oauth|auth|login|sign.?in)? ?(?:session|token|credentials?) (?:has |have )?(?:expired|been revoked)" +
                "|could not be refreshed|failed to refresh",
        )

    /** Never signed in. Not an expiry: "Please run /login" alone is how Claude Code asks a first sign-in. */
    private val NEVER = ci("not logged in|not signed in")

    private val LOGIN_ASK = ci("please run /login")

    private val REFUSED = ci("failed to authenticate|invalid api key|authentication_error")

    /** The agent was signed in once and that sign-in is no longer usable. */
    public fun expired(text: String): Boolean = EXPIRED.containsMatchIn(text)

    /** The agent says it was never signed in. Not true when the same text also says the sign-in expired. */
    public fun neverSignedIn(text: String): Boolean = NEVER.containsMatchIn(text) && !expired(text)

    /** Any of the agent's own words that its sign-in cannot be used: [expired], [neverSignedIn], a login ask, a refusal. */
    public fun gone(text: String): Boolean =
        expired(text) || NEVER.containsMatchIn(text) || LOGIN_ASK.containsMatchIn(text) || REFUSED.containsMatchIn(text)
}
