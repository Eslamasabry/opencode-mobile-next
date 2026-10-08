package io.github.eslamasabry.opencode_mobile

import java.security.MessageDigest

/** App-authored scripts only, independent of the generic setup output path. */
internal class PhoneAgentAuthRequest(
    val profileId: String,
    val agentId: String,
    val action: String,
    val script: String,
    val timeoutSeconds: Long,
) {
    companion object {
        private val executables = mapOf("claude" to "claude", "codex" to "codex",
            "gemini" to "gemini", "qwen" to "qwen", "goose" to "goose",
            "omp-acp" to "omp", "fx" to "fx")
        private const val SCRIPT_DIGEST = "86cca4026935daaf28c67c6f424e14c272f62a8f69b456d07a34a8c8a80d7c1c"
        private val fields = setOf("profileId", "agentId", "action", "script", "timeoutSeconds")

        private const val MAX_SCRIPT_CHARS = 16384
        private const val PROBE_SECONDS = 10L
        private const val LOGOUT_SECONDS = 20L

        fun parse(arguments: Map<*, *>): PhoneAgentAuthRequest? = try {
            check(arguments.keys == fields)
            val profile = arguments["profileId"] as String
            val agent = arguments["agentId"] as String
            val action = arguments["action"] as String
            val script = arguments["script"] as String
            val seconds = arguments["timeoutSeconds"] as Number
            val executable = checkNotNull(executables[agent])
            check(Regex("^[A-Za-z0-9_-]{1,80}$").matches(profile))
            check(seconds is Int || seconds is Long)
            val timeout = deadline(action, agent)
            check(seconds.toLong() == timeout)
            check(authored(script, profile, agent, executable, action))
            PhoneAgentAuthRequest(profile, agent, action, script, timeout)
        } catch (_: Exception) { null }

        private fun deadline(action: String, agent: String): Long = when (action) {
            "probe" -> PROBE_SECONDS
            "logout" -> {
                check(agent in setOf("claude", "fx"))
                LOGOUT_SECONDS
            }
            else -> error("Private action unavailable")
        }

        private fun authored(script: String, profile: String, agent: String,
            executable: String, action: String): Boolean {
            val home = "/home/oc/.oc-profiles/$profile"
            val prefix = "export HOME='$home' CLAUDE_CONFIG_DIR='$home/claude' CODEX_HOME='$home/codex' " +
                "DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1\n"
            check(script.length <= MAX_SCRIPT_CHARS && script.startsWith(prefix))
            val header = "python3 - '$agent' '$executable' $action <<'OC_AUTH_PROBE'\n"
            val body = script.removePrefix(prefix)
            check(body.contains(header))
            val normalized = body.replace(header, "python3 - 'AGENT' 'EXECUTABLE' ACTION <<'OC_AUTH_PROBE'\n")
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return digest == SCRIPT_DIGEST
        }
    }
}
