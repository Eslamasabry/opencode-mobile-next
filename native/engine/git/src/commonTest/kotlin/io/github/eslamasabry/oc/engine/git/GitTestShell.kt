package io.github.eslamasabry.oc.engine.git

import kotlin.io.encoding.Base64

/** One step's answer inside a fake envelope. */
internal data class StepReply(val name: String, val exit: Int = 0, val out: String = "", val err: String = "")

/** The text a real bridge would print for these step results. */
internal fun envelopeOf(vararg steps: StepReply, noise: Boolean = true): String =
    buildString {
        if (noise) append("proot warning: can't sanitize binding \"/proc/self/fd/1\"\n")
        append("@@OCG begin\n")
        for (s in steps) {
            append("@@OCG out ${s.name} ${Base64.encode(s.out.encodeToByteArray())}\n")
            append("@@OCG err ${s.name} ${Base64.encode(s.err.encodeToByteArray())}\n")
            append("@@OCG exit ${s.name} ${s.exit}\n")
        }
        append("@@OCG end\n")
    }

/** Answers each script by the step names it runs; records every script it was given. */
internal class FakeShell(private val answer: (String) -> GitShellReply) : GitShell {
    val scripts = mutableListOf<String>()

    override suspend fun run(script: String, timeoutMs: Long): GitShellReply {
        scripts += script
        return answer(script)
    }

    companion object {
        /** Replies with the given steps that appear in the script (`ocg_s <name> `). */
        fun steps(vararg replies: StepReply): FakeShell =
            FakeShell { script ->
                GitShellReply.Output(envelopeOf(*replies.filter { "ocg_s ${it.name} " in script }.toTypedArray()))
            }
    }
}

internal fun runs(script: String, name: String): Boolean = "ocg_s $name " in script
