package io.github.eslamasabry.oc.engine.project.gitops

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlin.io.encoding.Base64

internal const val FAKE_GITHUB_TOKEN: String = "gho_FAKEtoken0123456789abcdefFAKE42"

internal const val HTTPS_AUTH_ERROR: String =
    "remote: Invalid username or token. Password authentication is not supported for Git operations.\n" +
        "fatal: Authentication failed for 'https://example.invalid/o/r.git/'\n"

internal const val NO_PROMPT_ERROR: String = "fatal: could not read Username for 'https://example.invalid': terminal prompts disabled\n"

internal data class StepAnswer(val exit: Int, val out: String, val err: String)

internal fun ok(out: String = ""): StepAnswer = StepAnswer(0, out, "")

internal fun failed(err: String, exit: Int = 1): StepAnswer = StepAnswer(exit, "", err)

/** The envelope a real script prints for [steps], in order. */
internal fun envelope(steps: Map<String, StepAnswer>): String {
    fun b64(s: String) = Base64.encode(s.encodeToByteArray())
    val b = StringBuilder("@@OCG begin\n")
    for ((name, a) in steps) {
        b.append("@@OCG out $name ${b64(a.out)}\n@@OCG err $name ${b64(a.err)}\n@@OCG exit $name ${a.exit}\n")
    }
    return b.append("@@OCG end\n").toString()
}

internal fun pushPlan(): String = envelope(mapOf("head" to ok("main\n"), "upstream" to ok("origin/main\n"), "remotes" to ok("origin\n")))

/** Answers each script with the next reply; records every script it was handed. */
internal class FakeOpsShell(replies: List<GitOpsReply>) : GitOpsShell {
    private val queue = ArrayDeque(replies)
    val scripts = ArrayList<String>()
    val timeouts = ArrayList<Long>()

    override suspend fun run(script: String, timeoutMs: Long): GitOpsReply {
        scripts += script
        timeouts += timeoutMs
        return queue.removeFirstOrNull() ?: GitOpsReply.Failed(GitOpsShellFailure.Unavailable)
    }

    companion object {
        fun of(vararg outputs: String): FakeOpsShell = FakeOpsShell(outputs.map { GitOpsReply.Output(it) })
    }
}

internal class FakeCredentialDrop(private val failPlace: Boolean = false) : GitCredentialDrop {
    val placed = ArrayList<String>()
    val removed = ArrayList<String>()
    val secrets = ArrayList<String>()
    private var n = 0

    val allRemoved: Boolean get() = placed.size == removed.size && placed.all { it in removed }

    override suspend fun place(secret: String): Outcome<GitSecretFile> {
        if (failPlace) return Failure.StorageRefused().failed()
        secrets += secret
        val path = "/root/.oc-git/c-test${n++}"
        placed += path
        return GitSecretFile(path, "/host$path").ok()
    }

    override suspend fun remove(file: GitSecretFile) {
        removed += file.shellPath
    }
}
