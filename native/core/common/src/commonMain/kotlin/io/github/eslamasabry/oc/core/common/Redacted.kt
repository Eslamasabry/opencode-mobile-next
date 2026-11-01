package io.github.eslamasabry.oc.core.common

import kotlin.jvm.JvmInline

@RequiresOptIn("Only the Redactor in :core:security constructs Redacted text.", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
public annotation class RedactorApi

/** Technical text that already went through the Redactor (plan 03 S6). P-03-3: opt-in on a value-class constructor compiles. */
@JvmInline
public value class Redacted @RedactorApi constructor(public val text: String) {
    public companion object {
        /** Exception class name + optional HTTP status only. A class name cannot carry a secret; a message can. */
        @OptIn(RedactorApi::class)
        public fun shape(error: Throwable?, status: Int? = null): Redacted =
            Redacted(
                listOfNotNull(error?.let { it::class.simpleName }, status?.let { "HTTP $it" })
                    .joinToString(" · ")
                    .ifEmpty { "no message" },
            )
    }
}
