package io.github.eslamasabry.oc.engine.diagnostics.crash

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/** One bounded nested cause of a [NativeCrashRecord]. */
public data class NativeCrashCause(
    val exceptionClass: String,
    val message: String,
    val frames: ImmutableList<String>,
)

/**
 * The latest JVM crash summary the platform's crash handler kept (port of Dart `NativeCrashRecord`, B-ANDR-036).
 *
 * The handler already drops raw exception messages; this repeats the boundary: only whitelisted Java/Kotlin symbols,
 * structured frames and fixed messages are accepted. Arbitrary strings, URLs, sign-in codes and file paths never
 * become exception text or frames. Read independently of the exit history.
 */
public class NativeCrashRecord private constructor(
    public val timestamp: Instant,
    public val exceptionClass: String,
    public val message: String,
    public val frames: ImmutableList<String>,
    /** Bounded nested causes: the actionable exception Android often wraps in a RuntimeException. */
    public val causes: ImmutableList<NativeCrashCause>,
) {
    public val timestampMs: Long get() = timestamp.toEpochMilliseconds()

    /** `OutOfMemoryError` anywhere in the chain. */
    public val isOutOfMemory: Boolean
        get() = exceptionClass.endsWith(OUT_OF_MEMORY) || causes.any { it.exceptionClass.endsWith(OUT_OF_MEMORY) }

    // Package parts are separated so the generic token scrubber never mistakes a long class name for a credential.
    public val diagnosticMessage: String get() = "${exceptionClass.replace(".", " > ")}: $message"

    public val diagnosticStack: String
        get() =
            buildList {
                add(renderFrames(frames))
                for (cause in causes) {
                    add("Caused by ${cause.exceptionClass.replace(".", " > ")}: ${cause.message}\n${renderFrames(cause.frames)}")
                }
            }.filter { it.isNotEmpty() }.joinToString("\n")

    /** Bounded individual attributes keep the top symbols in the performance trace (each value ≤ 80 chars there). */
    public val traceAttrs: Map<String, Any?>
        get() =
            buildMap {
                put("class", exceptionClass.substringAfterLast('.'))
                put("message", message)
                put("at", timestamp.toString())
                for (i in 0 until minOf(frames.size, MAX_TRACE_FRAMES)) put("frame$i", shortFrame(frames[i]))
                causes.forEachIndexed { i, cause ->
                    put("cause$i", cause.exceptionClass.substringAfterLast('.'))
                    put("causeMessage$i", cause.message)
                    if (cause.frames.isNotEmpty()) put("causeFrame$i", shortFrame(cause.frames.first()))
                }
            }

    override fun toString(): String = "NativeCrashRecord(class=$exceptionClass, message=$message, at=$timestamp)"

    public companion object {
        public const val MAX_FRAMES: Int = 24
        public const val MAX_CAUSES: Int = 3
        public const val MAX_CAUSE_FRAMES: Int = 8
        private const val MAX_TRACE_FRAMES = 6
        private const val MAX_TIMESTAMP = 253_402_300_799_999L
        private const val MAX_CLASS = 256
        private const val MAX_FRAME = 512
        private const val OUT_OF_MEMORY = "OutOfMemoryError"
        public const val REDACTED: String = "[redacted]"

        /** The only messages a record may carry; anything else becomes [REDACTED]. */
        public val MESSAGES: Set<String> =
            setOf(
                "Input/output failure", "Permission denied", "Invalid state", "Invalid argument", "Missing value",
                "Channel reply failure", "Interrupted operation", REDACTED,
            )

        private val SYMBOL =
            Regex(
                """^(?:java\.|javax\.|android\.|kotlin\.|kotlinx\.|io\.flutter\.|io\.github\.eslamasabry\.opencode_mobile\.|io\.github\.eslamasabry\.oc\.)[A-Za-z_$][A-Za-z0-9_$.]*$""",
            )
        private val FRAME =
            Regex(
                """^([A-Za-z_$][A-Za-z0-9_$.]*)\.([A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>)\(([A-Za-z_$][A-Za-z0-9_$]*\.(?:kt|java):[0-9]{1,7}|Native Method|Unknown Source)\)$""",
            )

        /** Parses the platform's untyped record; null when it is not a valid crash (fails closed). */
        public fun from(raw: Any?): NativeCrashRecord? {
            if (raw !is Map<*, *>) return null
            val millis = (raw["timestamp"] as? Number)?.takeIf { it is Int || it is Long }?.toLong() ?: return null
            if (millis <= 0 || millis > MAX_TIMESTAMP) return null
            val details = readDetails(raw, MAX_FRAMES) ?: return null
            val causes = (raw["causes"] as? List<*>).orEmpty().take(MAX_CAUSES).mapNotNull { readDetails(it, MAX_CAUSE_FRAMES) }
            return NativeCrashRecord(
                timestamp = Instant.fromEpochMilliseconds(millis),
                exceptionClass = details.exceptionClass,
                message = details.message,
                frames = details.frames,
                causes = causes.toImmutableList(),
            )
        }

        private fun readDetails(raw: Any?, frameLimit: Int): NativeCrashCause? {
            if (raw !is Map<*, *>) return null
            val exception = raw["exceptionClass"] as? String ?: return null
            if (exception.length > MAX_CLASS || !SYMBOL.matches(exception)) return null
            val frames =
                (raw["frames"] as? List<*>).orEmpty().take(frameLimit).mapNotNull { value ->
                    if (value !is String || value.length > MAX_FRAME) return@mapNotNull null
                    val match = FRAME.matchEntire(value) ?: return@mapNotNull null
                    value.takeIf { SYMBOL.matches(match.groupValues[1]) }
                }
            val message = (raw["message"] as? String)?.takeIf { it in MESSAGES } ?: REDACTED
            return NativeCrashCause(exception, message, frames.toImmutableList())
        }

        private fun renderFrames(frames: List<String>): String =
            frames.joinToString("\n") { frame ->
                val match = FRAME.matchEntire(frame)!!
                val className = match.groupValues[1]
                val method = match.groupValues[2]
                val separator = if ("${className.substringAfterLast('.')}.$method".length >= 32) ". " else "."
                "at ${className.replace(".", " > ")}$separator$method (${match.groupValues[3]})"
            }

        private fun shortFrame(frame: String): String {
            val match = FRAME.matchEntire(frame)!!
            val symbol = "${match.groupValues[1].substringAfterLast('.')}.${match.groupValues[2]}"
            val display = if (symbol.length >= 32) symbol.replaceFirst(".", ". ") else symbol
            return "$display (${match.groupValues[3]})"
        }
    }
}
