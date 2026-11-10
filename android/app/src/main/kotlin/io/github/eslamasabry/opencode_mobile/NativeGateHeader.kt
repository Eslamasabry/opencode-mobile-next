package io.github.eslamasabry.opencode_mobile

import java.io.InputStream

/**
 * The first line a permit gate prints (`OC-INSTALL-1 <nonce> <pid>` or
 * `OC-GATE-1 …`), read from the gate's merged stdout/stderr.
 *
 * Issue #111: under the Landlock tier, the launchers (the sandbox, proot,
 * bionic's linker) can print warnings to stderr before the gate runs. The
 * old reader required the header to be the very first line, so one linker
 * warning made every confined install and server start fail. Lines before
 * the header can only come from those launchers: no workload runs until the
 * app writes the permit, which happens after this header is verified. The
 * caller still proves the nonce, the leader's ancestry, session and
 * environment exactly as before, so skipping launcher noise grants nothing.
 *
 * Bounded: at most [MAX_LINES] lines of [MAX_LINE_BYTES] bytes each. A missing
 * header, an early end of output or an over-long line is a failure. Skipped
 * text is never returned, logged or stored.
 */
internal object NativeGateHeader {
    const val MAX_LINES = 16
    const val MAX_LINE_BYTES = 512
    private val SANDBOX_CODE = Regex("[a-z][a-z_]{2,39}")

    /** The header line, or a static reason why it never came (see [Missing]). */
    sealed class Outcome {
        data class Found(val line: String) : Outcome()
        /**
         * [reason] is fixed text: `eof`, `line-too-long` or `too-many-lines`,
         * the count of lines skipped, and the sandbox's own stable stderr code
         * when one of those lines was exactly such a code (for example
         * `invalid_policy`). Never any other output text.
         */
        data class Missing(val reason: String) : Outcome()
    }

    fun read(stream: InputStream, prefix: String): String? =
        (readOutcome(stream, prefix) as? Outcome.Found)?.line

    fun readOutcome(stream: InputStream, prefix: String): Outcome {
        require(prefix.isNotEmpty() && !prefix.contains(' ') && !prefix.contains('\n'))
        var sandboxCode: String? = null
        fun missing(why: String, lines: Int) = Outcome.Missing(
            "gate_header_missing:$why:lines-$lines" + (sandboxCode?.let { ":sandbox-$it" } ?: ""))
        for (index in 0 until MAX_LINES) {
            val line = when (val next = readLine(stream)) {
                is Line.Text -> next.text
                Line.End -> return missing("eof", index)
                Line.TooLong -> return missing("line-too-long", index)
            }
            if (line.startsWith("$prefix ")) return Outcome.Found(line)
            if (sandboxCode == null && SANDBOX_CODE.matches(line)) sandboxCode = line
        }
        return missing("too-many-lines", MAX_LINES)
    }

    private sealed class Line {
        data class Text(val text: String) : Line()
        object End : Line()
        object TooLong : Line()
    }

    private fun readLine(stream: InputStream): Line {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < MAX_LINE_BYTES) {
            val value = stream.read()
            if (value < 0) return Line.End
            if (value == '\n'.code) return Line.Text(bytes.toString("US-ASCII"))
            bytes.write(value)
        }
        return Line.TooLong
    }
}

/**
 * Read-only system roots of the Landlock sandbox around proot.
 *
 * Never add `/linkerconfig` (issue #111): an app may not even stat that
 * directory (SELinux denies getattr), so the sandbox cannot resolve the rule
 * and refuses the whole policy (`invalid_policy`, exit 78). Without it,
 * bionic's linker may warn on stderr that it cannot read `ld.config.txt` and
 * uses its default config; [NativeGateHeader] tolerates that warning.
 */
internal object PhoneSandboxPolicy {
    val readOnlySystemRoots: List<String> = listOf("/system", "/apex", "/vendor", "/proc", "/sys")
}
