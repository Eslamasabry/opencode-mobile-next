package io.github.eslamasabry.oc.engine.agents.models

/**
 * The first meaningful line of a server error, without the stack trace or the `SomethingError: ` class prefix
 * (B-SEC-103). What the person reads; the full text stays behind Details.
 */
public fun errorHeadline(text: String): String {
    val lines = text.trim().split('\n')
    val first = (lines.firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("at ") } ?: lines.first()).trim()
    val colon = first.indexOf(": ")
    if (colon > 0 && ERROR_CLASS.matches(first.substring(0, colon))) return first.substring(colon + 2)
    return first
}

/** True when [text] carries more than its headline (a stack trace or a second paragraph). */
public fun errorHasDetails(text: String): Boolean = '\n' in text.trim()

private val ERROR_CLASS = Regex("^[A-Za-z]+Error$")

/** Typed classification of an assistant message error from the v1 `name` / v2 `type` (Flutter `MessageErrorKind`). */
public enum class MessageErrorKind {
    ContextOverflow,
    OutputLength,
    ProviderAuth,
    ContentFilter,
    Aborted,
    ModelNotFound,
    Unknown,
    ;

    public companion object {
        private val NON_LETTERS = Regex("[^a-z]")
        private val HEAD_SPLIT = Regex("[:\\s]")
        private val SIGN_IN_GONE = Regex(
            "not logged in|please run /login|failed to authenticate|" +
                "oauth (session|token) (has )?expired|could not be refreshed|" +
                "invalid api key|authentication_error",
            RegexOption.IGNORE_CASE,
        )

        /** Null when [name] is null or blank (no error); [Unknown] for any unrecognised name. */
        public fun fromName(name: String?): MessageErrorKind? {
            val trimmed = name?.trim()
            if (trimmed.isNullOrEmpty()) return null
            val key = trimmed.lowercase().replace(NON_LETTERS, "")
            return when {
                "contextoverflow" in key || "contextlength" in key -> ContextOverflow
                "outputlength" in key || key == "length" -> OutputLength
                "providerauth" in key || "needsauth" in key || "unauthorized" in key || "authentication" in key ->
                    ProviderAuth
                "contentfilter" in key -> ContentFilter
                "abort" in key -> Aborted
                "modelnotfound" in key -> ModelNotFound
                else -> Unknown
            }
        }

        /** Recovers the kind from the text's leading class name when the name gave nothing better. */
        public fun refineFromText(kind: MessageErrorKind, text: String?): MessageErrorKind {
            if (kind != Unknown || text == null) return kind
            if (SIGN_IN_GONE.containsMatchIn(text)) return ProviderAuth
            val head = text.trimStart().split(HEAD_SPLIT).first()
            return fromName(head) ?: Unknown
        }
    }
}
