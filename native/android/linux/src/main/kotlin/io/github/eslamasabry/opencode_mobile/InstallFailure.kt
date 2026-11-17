package io.github.eslamasabry.opencode_mobile

/**
 * What a failed install leaves for the rest of the app: a fixed word in `message` (never the exception's text, which is
 * not copy) and the technical text apart, for the Details page only.
 */
internal object InstallFailure {
    /** The fixed word the install message holds after a failure. */
    const val CODE: String = "installFailed"

    /** What [BuiltinLinux.message] holds after [error]: the fixed word, never the exception's text. */
    @Suppress("UNUSED_PARAMETER")
    fun shown(error: Throwable): String = error.message ?: error.javaClass.simpleName

    /** The technical text for Details. */
    fun details(error: Throwable): String = error.message ?: error.javaClass.simpleName
}
