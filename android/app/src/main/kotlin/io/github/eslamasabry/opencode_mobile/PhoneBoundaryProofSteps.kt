package io.github.eslamasabry.opencode_mobile

/**
 * Names the one step a phone boundary proof stopped at (issue #111).
 *
 * Before this, every failure was the bare code `boundary_proof_failed`: the
 * probe's own report went to /dev/null, so a person (or a maintainer) could
 * not tell which check failed. The step is read only from the probe's static
 * control lines (`boundary-control:<id>:…`), the Ubuntu script's own
 * `proof-step:<name>` markers, exit codes and the sandbox's static error
 * code. The result is a short code of `[A-Za-z0-9._-]` only: never a path,
 * argument, environment value, sentinel content or raw OS message.
 */
internal object PhoneBoundaryProofSteps {
    const val FAILED = "boundary_proof_failed"
    private val SAFE = Regex("[A-Za-z0-9_-]{1,48}")

    /** `boundary_proof_failed`, or `boundary_proof_failed:<step>` when known. */
    fun code(step: String?): String =
        if (step.isNullOrEmpty() || !Regex("[A-Za-z0-9._-]{1,120}").matches(step)) FAILED else "$FAILED:$step"

    /** The step part of [code], or null for a bare or unrelated code. */
    fun stepOf(code: String?): String? =
        code?.takeIf { it.startsWith("$FAILED:") }?.substringAfter(':')?.takeIf { it.isNotEmpty() }

    /**
     * The first native control that failed. [exitCode] is Java's view of the
     * probe launch: 128+N when signal N ended it, 78 when the sandbox
     * launcher refused before the probe ran (its static code is the first
     * line of [stderr]), null when it never answered.
     */
    fun native(stdout: List<String>, stderr: List<String>, exitCode: Int?): String? {
        for (line in stdout) {
            val parts = line.trim().split(':')
            if (parts.size < 3 || parts[0] != "boundary-control") continue
            val id = parts[1].takeIf { SAFE.matches(it) } ?: continue
            val kind = parts[2]
            val number = parts.getOrNull(3)?.toIntOrNull()
            val shape = when {
                id == "descendant" && kind == "failed" -> null
                kind == "signal" && number != null -> "signal-$number"
                kind == "exit" && number != null -> "exit-$number"
                kind == "spawn" -> "spawn"
                kind == "no-answer" -> "no-answer"
                // result:<value>:errno:<errno>; -1 is a refusal with the wrong errno.
                kind == "result" && number != null -> parts.getOrNull(5)?.toIntOrNull()
                    ?.let { if (number == -1) "errno-$it" else "allowed" } ?: "allowed"
                kind == "errno" && number != null -> "errno-$number"
                SAFE.matches(kind) -> kind
                else -> null
            }
            return listOfNotNull("native", id, shape).joinToString(".")
        }
        return when {
            exitCode == null -> "native.no-answer"
            exitCode == 0 -> null
            exitCode == 78 -> stderr.firstOrNull()?.trim()?.takeIf { SAFE.matches(it) }
                ?.let { "native.sandbox.$it" } ?: "native.sandbox"
            // Ended by a signal before it reported, e.g. SIGSYS (31) from the
            // app's inherited seccomp policy.
            exitCode > 128 -> "native.signal-${exitCode - 128}"
            else -> "native.exit-$exitCode"
        }
    }

    /** The Ubuntu step the protected script reached last before it stopped. */
    fun ubuntu(stdout: List<String>, exitCode: Int?): String? {
        if (exitCode == 0) return null
        val last = stdout.mapNotNull { line ->
            line.trim().takeIf { it.startsWith("proof-step:") }?.substringAfter(':')
                ?.takeIf { SAFE.matches(it) }
        }.lastOrNull() ?: "start"
        val how = when {
            exitCode == null -> "no-answer"
            exitCode > 128 -> "signal-${exitCode - 128}"
            else -> "exit-$exitCode"
        }
        return "ubuntu.$last.$how"
    }

    /** The first proot-tier control that did not pass, in report order. */
    fun prootTier(labels: List<String>, controls: Map<String, Any?>, finished: Boolean, exitCode: Int?): String? {
        labels.firstOrNull { controls[it] != true }?.takeIf { SAFE.matches(it) }?.let { return "proot-tier.$it" }
        if (!finished) return "proot-tier.no-answer"
        if (exitCode != null && exitCode != 0) return "proot-tier.exit-$exitCode"
        return null
    }
}
