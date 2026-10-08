package io.github.eslamasabry.opencode_mobile

/** A cold-start recipe contains identity and compatibility metadata, never launch input. */
internal data class NativeServerRecipe(
    val profileId: String,
    val runtime: String,
    val packageVersion: Long,
    val rootfsGeneration: String,
) {
    init {
        require(PROFILE.matches(profileId)) { SAFE_ERROR }
        require(runtime == "openCode1" || runtime == "openCode2") { SAFE_ERROR }
        require(packageVersion > 0L) { SAFE_ERROR }
        require(ROOTFS.matches(rootfsGeneration)) { SAFE_ERROR }
    }

    fun map(): Map<String, Any?> = mapOf(
        "version" to 1,
        "profileId" to profileId,
        "runtime" to runtime,
        "packageVersion" to packageVersion,
        "rootfsGeneration" to rootfsGeneration,
    )

    fun compatible(packageVersion: Long, rootfsGeneration: String): Boolean =
        this.packageVersion == packageVersion && this.rootfsGeneration == rootfsGeneration

    /** Only app-authored commands are reconstructed; the private password stays in Ubuntu. */
    fun restorationScript(): String {
        val isolated = if (runtime == "openCode2") {
            "mkdir -p /root/.oc-opencode2/data/opencode /root/.oc-opencode2/cache " +
                "/root/.oc-opencode2/state /root/.oc-opencode2/config/opencode\n" +
                "unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT\n" +
                "export XDG_DATA_HOME=/root/.oc-opencode2/data\n" +
                "export XDG_CACHE_HOME=/root/.oc-opencode2/cache\n" +
                "export XDG_STATE_HOME=/root/.oc-opencode2/state\n" +
                "export XDG_CONFIG_HOME=/root/.oc-opencode2/config\n" +
                "export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode\n" +
                "export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db\n"
        } else ""
        val binary = if (runtime == "openCode2") "opencode2" else "opencode"
        return "set -eu\n" +
            "mkdir -p /root/projects\n" +
            "cd /root/projects\n" +
            "[ -s /root/.oc-builtin/server.password ] || { " +
            "echo '$SAFE_ERROR' >&2; exit 78; }\n" +
            isolated +
            "password=\$(cat /root/.oc-builtin/server.password)\n" +
            "export OPENCODE_SERVER_USERNAME=opencode\n" +
            "export OPENCODE_SERVER_PASSWORD=\"\$password\"\n" +
            "export OPENCODE_PASSWORD=\"\$password\"\n" +
            "unset password\n" +
            "exec $binary serve --hostname 127.0.0.1 --port 4097\n"
    }

    companion object {
        private const val SAFE_ERROR = "The phone server could not restart."
        private val PROFILE = Regex("[A-Za-z0-9_-]{1,80}")
        private val ROOTFS = Regex("[0-9a-f]{64}")
        private val REQUEST_KEYS = setOf("version", "profileId", "runtime")
        private val STORED_KEYS = REQUEST_KEYS + setOf("packageVersion", "rootfsGeneration")

        fun fromRequest(
            request: Map<*, *>,
            boundProfile: String?,
            packageVersion: Long,
            rootfsGeneration: String,
        ): NativeServerRecipe {
            require(request.keys == REQUEST_KEYS) { SAFE_ERROR }
            require(integer(request["version"]) == 1L) { SAFE_ERROR }
            val profile = string(request["profileId"])
            require(boundProfile != null && boundProfile == profile) { SAFE_ERROR }
            return NativeServerRecipe(profile, string(request["runtime"]), packageVersion, rootfsGeneration)
        }

        fun read(value: Map<*, *>): NativeServerRecipe {
            require(value.keys == STORED_KEYS) { SAFE_ERROR }
            require(integer(value["version"]) == 1L) { SAFE_ERROR }
            return NativeServerRecipe(
                string(value["profileId"]),
                string(value["runtime"]),
                integer(value["packageVersion"]),
                string(value["rootfsGeneration"]),
            )
        }

        private fun integer(value: Any?): Long {
            require(value is Int || value is Long) { SAFE_ERROR }
            return (value as Number).toLong()
        }

        private fun string(value: Any?): String {
            require(value is String) { SAFE_ERROR }
            return value
        }
    }
}
