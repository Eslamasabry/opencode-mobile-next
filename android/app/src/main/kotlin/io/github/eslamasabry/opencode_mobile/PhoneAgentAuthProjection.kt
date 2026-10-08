package io.github.eslamasabry.opencode_mobile

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict flat JSON and fixed errors; process text never escapes on rejection. */
internal object PhoneAgentAuthProjection {
    private val errors = setOf("probeUnsupported", "invalidResponse", "timedOut", "hostUnavailable",
        "notInstalled", "invalidContext", "signInExpired", "signOutFailed")
    private val fields = setOf("state", "accountDisplayName", "error")

    fun error(code: String): Map<String, Any?> = mapOf("state" to "error",
        "error" to if (code in errors) code else "invalidResponse")

    fun parse(output: ByteArray, exitCode: Int): Map<String, Any?> {
        if (exitCode != 0 || output.size > MAX_OUTPUT_BYTES) return error("invalidResponse")
        return try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output)).toString()
            project(PhoneAgentAuthJson(text).parse())
        } catch (_: Exception) { error("invalidResponse") }
    }

    private fun project(values: Map<String, String>): Map<String, Any?> {
        if (values.keys.any { it !in fields }) return error("invalidResponse")
        return when (values["state"]) {
            "signedOut" -> if (values.keys == setOf("state")) mapOf("state" to "signedOut")
                else error("invalidResponse")
            "error" -> if (values.keys == setOf("state", "error") && values["error"] in errors)
                error(values.getValue("error")) else error("invalidResponse")
            "signedIn" -> signedIn(values)
            else -> error("invalidResponse")
        }
    }

    private const val MAX_OUTPUT_BYTES = 65536
    private const val MAX_LABEL_CHARS = 160
    private const val FIRST_PRINTABLE = 32
    private const val DELETE = 127
    private const val SURROGATE_FIRST = 0xD800
    private const val SURROGATE_LAST = 0xDFFF

    private fun signedIn(values: Map<String, String>): Map<String, Any?> {
        check("error" !in values)
        val label = values["accountDisplayName"]
        if (label != null) check(validLabel(label))
        return if (label == null) mapOf("state" to "signedIn")
            else mapOf("state" to "signedIn", "accountDisplayName" to label)
    }

    private fun validLabel(label: String): Boolean = label.isNotBlank() && label.length <= MAX_LABEL_CHARS &&
        label.codePoints().noneMatch { forbiddenCharacter(it) }
    private fun forbiddenCharacter(value: Int): Boolean =
        value < FIRST_PRINTABLE || value == DELETE || value in SURROGATE_FIRST..SURROGATE_LAST
}
