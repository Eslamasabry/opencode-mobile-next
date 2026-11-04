package io.github.eslamasabry.oc.engine.termuxhost

import io.github.eslamasabry.oc.engine.termuxhost.scripts.TermuxScriptText

/**
 * The shell the app hands Termux, rendered byte for byte as lib/termux/bridge.dart and lib/termux/scripts (Dart) do
 * (TermuxScriptHashesTest pins every rendering). Invalid arguments are programmer errors, as in Dart (ArgumentError):
 * they throw [IllegalArgumentException] before anything is rendered.
 */
public object TermuxScripts {
    public const val HOME: String = "/data/data/com.termux/files/home"
    public const val MANAGER_PATH: String = "$HOME/.oc/manager.sh"
    public const val MANAGED_SERVER_PORT: Int = 4096
    public const val MANAGED_SERVER_URL: String = "http://127.0.0.1:$MANAGED_SERVER_PORT"
    public const val MANAGED_PROJECTS_DIRECTORY: String = "/root/projects"

    /** The OpenCode server version a published build installs: pinned, never `latest`. */
    public const val DEFAULT_OPENCODE_VERSION: String = "2.2.5"

    /** The npm dist-tag, reachable only when a caller passes it on purpose. */
    public const val LATEST_OPENCODE_VERSION: String = "latest"

    public val setupBaseScript: String get() = TermuxScriptText.setupBase
    public val managerScript: String get() = TermuxScriptText.manager
    public val ensureWakeLockScript: String get() = TermuxScriptText.wakeLock
    public val unlockCommand: String get() = TermuxScriptText.unlock

    public fun storageScript(): String = TermuxScriptText.storage

    public fun installationScript(): String = TermuxScriptText.installation

    public fun statusScript(): String = TermuxScriptText.status

    public fun setupSnapshotScript(): String = TermuxScriptText.setupSnapshot

    public fun diagnosticsScript(): String = TermuxScriptText.diagnostics

    public fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private val VERSION = Regex("^[A-Za-z0-9._+-]+$")
    private val OPERATION = Regex("^[a-zA-Z0-9_-]{1,64}$")
    private val EXPECTED_OPERATION = Regex("^[a-zA-Z0-9_-]{0,64}$")
    private val LAUNCH = Regex("(^|\\n)manager-(started|already-running):[0-9]+($|\\n)")

    public fun managesServerUrl(value: String?): Boolean {
        if (value == null) return false
        val match = Regex("^http://([^/:?#]+)(?::(\\d+))?(?:[/?#].*)?$").find(value) ?: return false
        val port = match.groups[2]?.value?.toIntOrNull() ?: 80
        return match.groupValues[1] == "127.0.0.1" && port == MANAGED_SERVER_PORT
    }

    public fun isLaunchAcknowledged(output: String): Boolean = LAUNCH.containsMatchIn(output.trim())

    public fun installAndServeScript(
        password: String,
        port: Int = 4096,
        version: String? = null,
        runtime: TermuxRuntime = TermuxRuntime.DefaultForNewInstall,
    ): String {
        require(runtime.supportProblem() == null) { "OpenCode 1 must move to OpenCode 2 before starting." }
        val selectedVersion = version ?: runtime.pinnedVersion
        require(port in 1024..65535) { "port must be between 1024 and 65535" }
        require(VERSION.matches(selectedVersion)) { "Invalid package version." }
        require(password.isNotEmpty()) { "password must not be empty" }
        return TermuxScriptText.installAndServe(port, shellQuote(password), selectedVersion, runtime.wireName)
    }

    public fun restartScript(
        operationId: String,
        port: Int = MANAGED_SERVER_PORT,
        recoveryToken: String? = null,
        expectedOperationId: String? = null,
        switchTarget: TermuxRuntime? = null,
        switchPassword: String? = null,
    ): String {
        require(switchTarget?.supportProblem() == null) { "OpenCode 1 must move to OpenCode 2 before starting." }
        require(port in 1024..65535) { "port must be between 1024 and 65535" }
        require(OPERATION.matches(operationId)) { "Invalid restart ID" }
        require(
            recoveryToken == null ||
                (OPERATION.matches(recoveryToken) && expectedOperationId != null && EXPECTED_OPERATION.matches(expectedOperationId)),
        ) { "Invalid recovery identity" }
        require(switchTarget == null || (!switchPassword.isNullOrEmpty() && recoveryToken == null)) {
            "A runtime switch needs its saved credential"
        }
        val recoveryArgs =
            if (recoveryToken == null) "" else " ${shellQuote(expectedOperationId!!)} ${shellQuote(recoveryToken)}"
        val stagedPassword =
            if (switchTarget == null) {
                ""
            } else {
                "printf '%s' ${shellQuote(switchPassword!!)} > \"\$OC_DIR/switch-password-$operationId\""
            }
        val launchCommand =
            if (switchTarget == null) {
                "\"\$MANAGER\" restart '$port' '$operationId'$recoveryArgs &\n" +
                    "operation_pid=\$!\nwait \"\$operation_pid\""
            } else {
                "nohup \"\$MANAGER\" restart '$port' '$operationId' '' '' " +
                    "'${switchTarget.wireName}' >/dev/null 2>&1 </dev/null &\n" +
                    "operation_pid=\$!\nprintf \"manager-started:%s\\n\" \"\$operation_pid\""
            }
        val staleLockAction = if (switchTarget == null) TermuxScriptText.staleLockPlain else TermuxScriptText.staleLockSwitch
        return TermuxScriptText.restart(staleLockAction, stagedPassword, launchCommand)
    }

    public fun stopScript(port: Int = 4096): String = TermuxScriptText.stop(port)

    /** [name] must already pass the project-folder name rules: a single safe path segment. */
    public fun createProjectFolderScript(name: String): String = TermuxScriptText.createProjectFolder(name)

    /** Grant/revoke only this app's managed-server recovery permit. */
    public fun recoveryControlScript(token: String, enable: Boolean): String {
        require(OPERATION.matches(token)) { "token" }
        return TermuxScriptText.recoveryControl(token, enable)
    }
}
