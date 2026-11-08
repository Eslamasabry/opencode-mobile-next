package io.github.eslamasabry.oc.android.platform

import java.util.IdentityHashMap

/**
 * What an uncaught-exception handler hands down the chain (Android's logger and crash dropbox, Crashlytics' own
 * handler): a copy of the crash that keeps every exception class and every stack frame, causes and suppressed ones
 * included, but no message (B-SEC-043: messages can carry chat text, login codes and tokens that redaction cannot
 * reliably find, and Crashlytics sends what it gets off the phone).
 *
 * The copy is the same class when the class has a public no-argument constructor that leaves the message empty
 * (IllegalStateException, NullPointerException, IOException, ...); otherwise it is a [SanitizedThrowable] that prints
 * as the original class name. Never a cause-less wrapper with no frames: that made every crash undiagnosable
 * (2026-11-08, the prompt editor crash read only "RuntimeException: Native application error").
 */
public object CrashSanitizer {
    private const val MAX_THROWABLES = 16

    /** Never throws. Already sanitized throwables are returned as they are. */
    public fun sanitize(error: Throwable): Throwable =
        try {
            copy(error, IdentityHashMap(), intArrayOf(0))
        } catch (_: Throwable) {
            fallback(error)
        }

    private fun copy(error: Throwable, seen: IdentityHashMap<Throwable, Throwable>, budget: IntArray): Throwable =
        error as? SanitizedThrowable ?: seen[error] ?: fresh(error, seen, budget)

    private fun fresh(error: Throwable, seen: IdentityHashMap<Throwable, Throwable>, budget: IntArray): Throwable {
        budget[0]++
        val frames = framesOf(error)
        var copy = sameClass(error) ?: SanitizedThrowable(error.javaClass.name)
        copy.stackTrace = frames
        seen[error] = copy
        val cause = error.cause?.takeIf { it !== error && budget[0] < MAX_THROWABLES }
        if (cause != null) {
            val safeCause = copy(cause, seen, budget)
            if (!attach(copy, safeCause)) {
                // The constructor already set a cause (or forbids one): the named copy carries it instead.
                copy = SanitizedThrowable(error.javaClass.name)
                copy.stackTrace = frames
                attach(copy, safeCause)
                seen[error] = copy
            }
        }
        addSuppressed(error, copy, seen, budget)
        return copy
    }

    private fun attach(copy: Throwable, cause: Throwable): Boolean =
        try {
            copy.initCause(cause)
            true
        } catch (_: Throwable) {
            false
        }

    private fun addSuppressed(
        error: Throwable,
        copy: Throwable,
        seen: IdentityHashMap<Throwable, Throwable>,
        budget: IntArray,
    ) {
        for (suppressed in error.suppressed) {
            if (budget[0] >= MAX_THROWABLES) return
            if (suppressed === error || seen.containsKey(suppressed)) continue
            try {
                copy.addSuppressed(copy(suppressed, seen, budget))
            } catch (_: Throwable) {}
        }
    }

    /** A fresh instance of the same class, only when its public no-argument constructor leaves it message-free. */
    private fun sameClass(error: Throwable): Throwable? =
        try {
            val fresh = error.javaClass.getConstructor().newInstance()
            if (fresh.message == null && fresh.localizedMessage == null && fresh.cause == null) fresh else null
        } catch (_: Throwable) {
            null
        }

    private fun framesOf(error: Throwable): Array<StackTraceElement> =
        try {
            error.stackTrace
        } catch (_: Throwable) {
            emptyArray()
        }

    private fun fallback(error: Throwable): Throwable =
        SanitizedThrowable(
            try {
                error.javaClass.name
            } catch (_: Throwable) {
                "java.lang.Throwable"
            }
        ).also {
            try {
                it.stackTrace = error.stackTrace
            } catch (_: Throwable) {}
        }
}

/**
 * A message-free stand-in for an exception that could not be copied as its own class: it prints as [originalClass],
 * so logcat, the crash dropbox and Crashlytics' frames still read like the original.
 */
public class SanitizedThrowable(public val originalClass: String) : RuntimeException() {
    override val message: String?
        get() = null

    override fun toString(): String = originalClass

    // The frames are the original's, set by the sanitizer; never this handler's own.
    override fun fillInStackTrace(): Throwable = this
}
