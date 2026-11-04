package io.github.eslamasabry.oc.engine.runtime.components

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.time.Duration

/**
 * The ports of [ComponentRemoval] (port of the `BuiltinLinux`/`BuiltinTeam` surface lib/builtin/setup/
 * component_removal.dart uses). Small on purpose: the setup engine has its own ports.
 */

/** One finished script: its exit code and stdout (never shown to the person; size scripts are parsed). */
public data class ShellResult(val exitCode: Int, val output: String = "") {
    val ok: Boolean get() = exitCode == 0
}

/** Runs one app-authored script in the app's own Ubuntu (proot). A failed launch is `Outcome.Failed`. */
public fun interface LinuxShell {
    public suspend fun run(script: String, timeout: Duration): Outcome<ShellResult>
}

/** The native runtime's own state: whether Ubuntu is installed, and whether it is being installed right now. */
public data class LinuxInstallState(val installed: Boolean, val installing: Boolean = false)

/** Reads the native runtime state and the setup job's raw status record (`setup.json`; null when there is none). */
public interface SetupStatusReader {
    public suspend fun linuxState(): Outcome<LinuxInstallState>

    public suspend fun setupRecord(): Outcome<String?>
}

/** AI Team's lifecycle API: stops the team's service, then runs the component's remove script (BuiltinTeam.remove). */
public fun interface TeamRemover {
    public suspend fun remove(): Outcome<Unit>
}

/**
 * Port of `setupToolsChanged` (a global counter in Flutter): told after any removal that may have changed what is
 * installed, after [ComponentRemoval.busy] is released, so listeners refresh their inventory.
 */
public fun interface ToolsChangedListener {
    public fun toolsChanged()
}
