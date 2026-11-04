package io.github.eslamasabry.oc.core.model.transcript

/** Flutter MessageErrorKind.fromName: server error names are data, never visible copy. */
public fun messageErrorKind(name: String?): MessageErrorKind? {
    val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val key = trimmed.lowercase().replace(Regex("[^a-z]"), "")
    return when {
        "contextoverflow" in key || "contextlength" in key -> MessageErrorKind.ContextOverflow
        "outputlength" in key || key == "length" -> MessageErrorKind.OutputLength
        "providerauth" in key || "needsauth" in key || "unauthorized" in key || "authentication" in key -> MessageErrorKind.ProviderAuth
        "contentfilter" in key -> MessageErrorKind.ContentFilter
        "abort" in key -> MessageErrorKind.Aborted
        "modelnotfound" in key -> MessageErrorKind.ModelNotFound
        else -> MessageErrorKind.Unknown
    }
}

/** A provider may put its class or expired-login notice in the message while leaving the name generic. */
public fun refineMessageErrorKind(kind: MessageErrorKind, text: String?): MessageErrorKind {
    if (kind != MessageErrorKind.Unknown || text == null) return kind
    if (SignInGone.containsMatchIn(text)) return MessageErrorKind.ProviderAuth
    return messageErrorKind(text.trimStart().split(Regex("[:\\s]")).first()) ?: MessageErrorKind.Unknown
}

private val SignInGone = Regex(
    "not logged in|please run /login|failed to authenticate|oauth (session|token) (has )?expired|" +
        "could not be refreshed|invalid api key|authentication_error",
    RegexOption.IGNORE_CASE,
)
