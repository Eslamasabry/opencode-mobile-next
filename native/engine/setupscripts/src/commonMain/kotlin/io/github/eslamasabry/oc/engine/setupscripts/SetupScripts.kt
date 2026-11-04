package io.github.eslamasabry.oc.engine.setupscripts

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

// Port of the functions in lib/builtin/setup/setup_scripts.dart, and SetupScripts.openCodeCheck
// (lib/builtin/setup/components.dart) for the Termux host.

/** [script] as the job runs it: the prelude, the component-update helpers, then the script. */
public fun withSetupPrelude(script: String): String = SETUP_PRELUDE + COMPONENT_UPDATE_PRELUDE + script

/**
 * One script that runs every check in [checks] (id → check script) in its own subshell and marks where each starts and
 * how it ended, so the engine pays for one proot start instead of one per component.
 */
public fun combinedCheckScript(checks: Map<String, String>): String =
    buildString {
        for ((id, check) in checks) {
            append("printf '\\n::oc-check-begin %s\\n' '").append(id).append("'\n")
            append("(\n")
            append(check.trimEnd()).append('\n')
            append(") 2>/dev/null </dev/null\n")
            append("printf '\\n::oc-check-end %s %s\\n' '").append(id).append("' \"$?\"\n")
        }
    }

/** The result of one check in [combinedCheckScript]'s output. */
public data class SetupCheckResult(val ok: Boolean, val version: String?)

/**
 * Reads [combinedCheckScript]'s output: which checks passed and the version each printed on its last non-empty line. A
 * check with no end marker (the script died) did not pass and is absent.
 */
public fun parseCombinedChecks(output: String): Map<String, SetupCheckResult> {
    val results = linkedMapOf<String, SetupCheckResult>()
    var current: String? = null
    var lines = mutableListOf<String>()
    for (raw in output.split('\n')) {
        val line = raw.trimEnd()
        if (line.startsWith(BEGIN)) {
            current = line.substring(BEGIN.length).trim()
            lines = mutableListOf()
            continue
        }
        if (line.startsWith(END)) {
            val parts = line.substring(END.length).trim().split(' ')
            if (parts.size == 2 && parts[0] == current) {
                val version = lines.lastOrNull { it.isNotBlank() }?.trim()
                results[current] = SetupCheckResult(ok = parts[1] == "0", version = version?.ifEmpty { null })
            }
            current = null
            continue
        }
        if (current != null) lines += line
    }
    return results
}

private const val BEGIN = "::oc-check-begin "
private const val END = "::oc-check-end "

/** The runtime of the one app-managed server (TermuxRuntime). OpenCode 1 is detection only. */
public enum class OpenCodeRuntime(public val wireName: String, public val pinnedVersion: String) {
    OpenCode1("opencode1", ""),
    OpenCode2("opencode2", "2.2.5"),
    ;

    public companion object {
        /** Fresh setup only; a missing record still describes an older install. */
        public val defaultForNewInstall: OpenCodeRuntime = OpenCode2

        /** null for an unsupported selection. */
        public fun parse(value: String?): OpenCodeRuntime? =
            when (value?.trim()) {
                null, "", "opencode1" -> OpenCode1
                "opencode2" -> OpenCode2
                else -> null
            }
    }
}

public object OpenCodeChecks {
    private val versionPattern = Regex("^[A-Za-z0-9._+-]+$")

    /** `opencode2 --version` (or `opencode --version`) only when the command exists. */
    public fun versionScript(runtime: OpenCodeRuntime): String =
        if (runtime == OpenCodeRuntime.OpenCode2) {
            "command -v opencode2 >/dev/null 2>&1 && opencode2 --version"
        } else {
            "command -v opencode >/dev/null 2>&1 && opencode --version"
        }

    /** The pinned or requested version; OpenCode 1 must move to OpenCode 2 first, a bad version never reaches a script. */
    public fun selectedVersion(runtime: OpenCodeRuntime, version: String?): Outcome<String> {
        if (runtime != OpenCodeRuntime.OpenCode2) return Failure.Incompatible(IncompatibleKind.LegacyOpenCode1).failed()
        val selected = version ?: runtime.pinnedVersion
        if (!versionPattern.matches(selected)) return Failure.Rejected(status = null).failed()
        return selected.ok()
    }

    /** The Termux host's OpenCode check: passes only on [runtime]'s command at the selected version, echoing it. */
    public fun openCodeCheck(runtime: OpenCodeRuntime, version: String? = null): Outcome<String> {
        val selected =
            when (val chosen = selectedVersion(runtime, version)) {
                is Outcome.Ok -> chosen.value
                is Outcome.Failed -> return chosen
            }
        return (
            "set -e\n" +
                "installed=$(${versionScript(runtime)})\n" +
                "installed=\${installed##* v}\n" +
                "[ \"\$installed\" = '$selected' ]\n" +
                "echo \"\$installed\"\n"
        ).ok()
    }

    /** The check for `params` as the setup engine receives them (`runtime`, `version`); refused when unsupported. */
    public fun openCodeCheck(params: Map<String, String>): Outcome<String> {
        val raw = params["runtime"]
        val runtime =
            if (raw == null) OpenCodeRuntime.defaultForNewInstall else OpenCodeRuntime.parse(raw)
                ?: return Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion).failed()
        return openCodeCheck(runtime, params["version"])
    }
}
