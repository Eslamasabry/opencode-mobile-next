// Copied from the Flutter app at origin/release/2.1.5,
// android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeCrashStore.kt (`object CrashSanitizer`),
// unchanged except the package line (B-180: Crashlytics records exception class names plus checked frames).
package io.github.eslamasabry.oc.android.platform

/**
 * A crash as other handlers may see it: the original exception class names
 * and code locations, with every message dropped. Frames whose class, method
 * or file name is not a plain identifier are left out, so a caller-supplied
 * stack cannot smuggle text through.
 */
object CrashSanitizer {
    private const val MAX_CAUSES = 4
    private const val MAX_FRAMES = 64
    private val identifier = Regex("^[A-Za-z0-9_.$<>-]{1,200}$")
    private val fileName = Regex("^[A-Za-z0-9_.$-]{1,100}$")

    /** Its message is the original class name only, e.g. "java.net.UnknownHostException". */
    class SanitizedCrash(originalClass: String) : RuntimeException(originalClass) {
        override fun fillInStackTrace(): Throwable = this
    }

    fun sanitize(error: Throwable): Throwable {
        val chain = mutableListOf<Throwable>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        while (current != null && chain.size < MAX_CAUSES && visited.add(current)) {
            chain.add(current)
            current = current.cause
        }
        var cause: Throwable? = null
        for (original in chain.asReversed()) {
            val name = original.javaClass.name.takeIf(identifier::matches) ?: "java.lang.Throwable"
            val safe = if (cause == null) SanitizedCrash(name) else SanitizedCrash(name).also { it.initCause(cause) }
            safe.stackTrace = frames(original.stackTrace)
            cause = safe
        }
        return cause ?: SanitizedCrash("java.lang.Throwable")
    }

    private fun frames(original: Array<StackTraceElement>?): Array<StackTraceElement> =
        (original ?: emptyArray()).asSequence()
            .filter { identifier.matches(it.className) && identifier.matches(it.methodName) }
            .take(MAX_FRAMES)
            .map {
                val file = it.fileName?.takeIf(fileName::matches)
                StackTraceElement(it.className, it.methodName, file, if (file == null) -1 else it.lineNumber)
            }
            .toList().toTypedArray()
}
