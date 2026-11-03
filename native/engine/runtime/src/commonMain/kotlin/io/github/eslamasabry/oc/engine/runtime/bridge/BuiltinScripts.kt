package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

/** The runtime a saved in-app profile was set up with (port of `TermuxRuntime`). OpenCode 1 is detection only. */
public enum class OpenCodeRuntime(public val wireName: String, public val pinnedVersion: String) {
    /** Detection only: an old installation must migrate before it can start. */
    OpenCode1("opencode1", ""),
    OpenCode2("opencode2", "2.2.5"),
    ;

    public companion object {
        /** Fresh setup only. */
        public val DefaultForNewInstall: OpenCodeRuntime = OpenCode2
    }
}

/**
 * The shell scripts that run inside the built-in Ubuntu (port of the static half of lib/builtin/builtin_linux.dart;
 * B-LINUX-023, B-LINUX-024). Every value a script embeds is single-quoted; the server password only ever goes into
 * [writePasswordScript], never into the long-running server's command line or log.
 */
public object BuiltinScripts {
    /** The Ubuntu working directory the server starts in: never the home folder, which OpenCode would scan whole. */
    public const val PROJECTS_DIR: String = "/root/projects"

    /** Where the start script reads the server password from. */
    public const val PASSWORD_FILE: String = "/root/.oc-builtin/server.password"

    private val VERSION = Regex("[A-Za-z0-9._+-]+")

    /** `'…'` with every `'` closed, escaped and reopened. */
    public fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    /**
     * Installs OpenCode inside Ubuntu with the very text the Termux manager runs ([BuiltinScriptTexts.UBUNTU_SETUP]),
     * at the pinned version unless a caller asks for another on purpose. OpenCode 1 cannot be installed.
     */
    public fun installOpenCodeScript(
        runtime: OpenCodeRuntime = OpenCodeRuntime.DefaultForNewInstall,
        version: String? = null,
    ): Outcome<String> {
        if (runtime != OpenCodeRuntime.OpenCode2) return Failure.Incompatible(IncompatibleKind.LegacyOpenCode1).failed()
        val selected = version ?: runtime.pinnedVersion
        if (!VERSION.matches(selected)) return Failure.Unexpected().failed()
        return (
            "set -eu\n" +
                BuiltinScriptTexts.FAST_PREREQUISITES +
                "export OC_REQUESTED_VERSION=${quote(selected)}\n" +
                "export OC_RUNTIME=${quote(runtime.wireName)}\n" +
                "bash -s <<'OC_PROOT_SETUP'\n" +
                BuiltinScriptTexts.UBUNTU_SETUP + "OC_PROOT_SETUP\n"
            ).ok()
    }

    /** Prints the installed version of [runtime], or fails when it is missing. */
    public fun versionScript(runtime: OpenCodeRuntime): String =
        if (runtime == OpenCodeRuntime.OpenCode2) {
            "command -v opencode2 >/dev/null 2>&1 && opencode2 --version"
        } else {
            "command -v opencode >/dev/null 2>&1 && opencode --version"
        }

    /** The version out of `opencode --version` / `opencode2 --version`, trimmed as the Termux manager does. */
    public fun parseVersion(output: String): String? {
        for (raw in output.split('\n').asReversed()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            for (prefix in listOf("opencode2 v", "opencode v")) {
                if (line.startsWith(prefix)) line = line.substring(prefix.length)
            }
            return line.takeIf { VERSION.matches(it) }
        }
        return null
    }

    /** Lists the project folders under [PROJECTS_DIR], one name per line; a fresh install lists nothing. */
    public fun listProjectsScript(): String =
        "set -eu\n" +
            "mkdir -p $PROJECTS_DIR\n" +
            "find $PROJECTS_DIR -mindepth 1 -maxdepth 1 -type d -printf '%f\\n' " +
            "| sort\n"

    /** The folder names out of [listProjectsScript]; hidden folders are tool state, not projects. */
    public fun parseProjectList(output: String): List<String> =
        output.split('\n').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('.') }

    /** Succeeds when [path] is a folder inside Ubuntu. */
    public fun folderExistsScript(path: String): String = "test -d ${quote(path)}"

    /**
     * Makes [path] a new git project with one empty first commit, or leaves an existing folder alone. Prints
     * `created <path>` or `exists <path>`. Null for a path that is not absolute or holds a newline or NUL.
     */
    public fun createFolderScript(path: String): String? {
        if (!absolute(path)) return null
        return "set -eu\n" +
            "dir=${quote(path)}\n" +
            "if [ -d \"\$dir\" ]; then printf 'exists %s\\n' \"\$dir\"; exit 0; fi\n" +
            "mkdir -p \"\$dir\"\n" +
            "git init -q \"\$dir\"\n" +
            "name=\$(git config user.name || echo \"AI Team\")\n" +
            "email=\$(git config user.email || echo \"aiteam@localhost\")\n" +
            "(cd \"\$dir\" && git -c user.name=\"\$name\" -c user.email=\"\$email\" " +
            "commit -q --allow-empty -m \"First commit\") || true\n" +
            "printf 'created %s\\n' \"\$dir\"\n"
    }

    /** Prints `empty` (a git folder without a commit), `ready` (it has one) or `notgit`. Null for a non-absolute path. */
    public fun repoStateScript(path: String): String? {
        if (!absolute(path)) return null
        return "dir=${quote(path)}\n" +
            "cd \"\$dir\" 2>/dev/null || { echo notgit; exit 0; }\n" +
            "git rev-parse --git-dir >/dev/null 2>&1 || { echo notgit; exit 0; }\n" +
            "if git rev-parse --verify -q HEAD >/dev/null 2>&1; then " +
            "echo ready; else echo empty; fi\n"
    }

    /** Gives a git folder with no commits an empty first one: prints `exists` or `committed`. */
    public fun firstCommitScript(path: String): String? {
        if (!absolute(path)) return null
        return "set -eu\n" +
            "dir=${quote(path)}\n" +
            "cd \"\$dir\"\n" +
            "if git rev-parse --verify -q HEAD >/dev/null 2>&1; then " +
            "echo exists; exit 0; fi\n" +
            "name=\$(git config user.name || echo \"AI Team\")\n" +
            "email=\$(git config user.email || echo \"aiteam@localhost\")\n" +
            "git -c user.name=\"\$name\" -c user.email=\"\$email\" " +
            "commit -q --allow-empty -m \"First commit\"\n" +
            "echo committed\n"
    }

    /** Saves the server password inside Ubuntu, readable by its root only. Null for an empty password. */
    public fun writePasswordScript(password: String): String? {
        if (password.isEmpty()) return null
        val dir = PASSWORD_FILE.substring(0, PASSWORD_FILE.lastIndexOf('/'))
        return "set -eu\n" +
            "umask 077\n" +
            "mkdir -p $dir\n" +
            "printf '%s' ${quote(password)} > $PASSWORD_FILE.tmp\n" +
            "mv $PASSWORD_FILE.tmp $PASSWORD_FILE\n"
    }

    /**
     * The long-running server: started in [PROJECTS_DIR], bound to 127.0.0.1 only, Basic auth `opencode` with the
     * password read from [PASSWORD_FILE]. OpenCode 2 gets isolated XDG folders, and OpenCode 1 chats come along before
     * the first start. OpenCode 1 cannot start; a port outside 1024…65535 is a bug path.
     */
    public fun serverScript(
        runtime: OpenCodeRuntime = OpenCodeRuntime.DefaultForNewInstall,
        port: Int = BuiltinLinuxBridge.SERVER_PORT,
    ): Outcome<String> {
        if (port !in 1024..65535) return Failure.Unexpected().failed()
        if (runtime != OpenCodeRuntime.OpenCode2) return Failure.Incompatible(IncompatibleKind.LegacyOpenCode1).failed()
        val isolated =
            "mkdir -p /root/.oc-opencode2/data/opencode /root/.oc-opencode2/cache " +
                "/root/.oc-opencode2/state /root/.oc-opencode2/config/opencode\n" +
                "unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT\n" +
                "export XDG_DATA_HOME=/root/.oc-opencode2/data\n" +
                "export XDG_CACHE_HOME=/root/.oc-opencode2/cache\n" +
                "export XDG_STATE_HOME=/root/.oc-opencode2/state\n" +
                "export XDG_CONFIG_HOME=/root/.oc-opencode2/config\n" +
                "export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode\n" +
                "export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db\n" +
                "(\n${BuiltinScriptTexts.HISTORY_COPY}) || exit 74\n"
        return (
            "set -eu\n" +
                "mkdir -p $PROJECTS_DIR\n" +
                "cd $PROJECTS_DIR\n" +
                "[ -s $PASSWORD_FILE ] || { echo \"[oc] The server password is missing\" >&2; exit 78; }\n" +
                isolated +
                "(\n${BuiltinScriptTexts.PHONE_CONTEXT}) >/dev/null 2>&1 || true\n" +
                "password=\$(cat $PASSWORD_FILE)\n" +
                "export OPENCODE_SERVER_USERNAME=${BuiltinLinuxBridge.SERVER_USERNAME}\n" +
                "export OPENCODE_SERVER_PASSWORD=\"\$password\"\n" +
                "export OPENCODE_PASSWORD=\"\$password\"\n" +
                "unset password\n" +
                "exec opencode2 serve --hostname 127.0.0.1 --port $port\n"
            ).ok()
    }

    /**
     * A short, shareable name for [script]: its first line that does real work, with quoted text blanked. The
     * password script is only ever `write-password`, because its body holds the password.
     */
    public fun scriptLabel(script: String): String {
        if (script.contains("$PASSWORD_FILE.tmp")) return "write-password"
        for (raw in script.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("set ") || line.startsWith("umask ") ||
                line.startsWith("export ")
            ) {
                continue
            }
            val blanked = line.replace(Regex("'[^']*'"), "'…'").replace(Regex("\"[^\"]*\""), "\"…\"")
            return if (blanked.length > 60) blanked.substring(0, 59) + "…" else blanked
        }
        return "script"
    }

    private fun absolute(path: String): Boolean = path.startsWith("/") && '\n' !in path && '\u0000' !in path
}
