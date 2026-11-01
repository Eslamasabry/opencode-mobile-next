package io.github.eslamasabry.opencode_mobile

/** PRoot can exit zero when its guest was signalled. Require the guest's result too. */
internal class SetupScriptCompletion(token: String = java.util.UUID.randomUUID().toString()) {
    private val marker = "::oc-setup-result $token "
    var exitCode: Int? = null
        private set
    private var seen = false

    fun wrap(script: String): String =
        "/bin/sh -c '" + script.replace("'", "'\"'\"'") + "'\n" +
            "oc_setup_result=\$?\n" +
            "printf '\\n${marker}%s\\n' \"\$oc_setup_result\"\n" +
            "exit \"\$oc_setup_result\"\n"

    /** Consume only this invocation's terminal record; never publish it as user output. */
    fun accept(line: String): Boolean {
        if (!line.startsWith(marker)) return false
        val code = line.removePrefix(marker).toIntOrNull()?.takeIf { it in 0..MAX_EXIT_CODE }
        exitCode = if (seen) null else code
        seen = true
        return true
    }

    companion object { private const val MAX_EXIT_CODE = 255 }
}
