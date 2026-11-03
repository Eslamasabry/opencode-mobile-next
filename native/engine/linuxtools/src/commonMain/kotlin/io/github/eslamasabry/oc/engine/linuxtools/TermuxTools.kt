package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Termux's home folder (lib/termux/scripts/script_support.dart). */
public const val TERMUX_HOME: String = "/data/data/com.termux/files/home"

/** Termux's install prefix. */
public const val TERMUX_PREFIX: String = "/data/data/com.termux/files/usr"

/** Where the bridge writes [TERMUX_TOOLS_SCRIPT]. */
public const val TERMUX_TOOLS_PATH: String = "$TERMUX_HOME/.oc/tools.sh"

/** The verbs of `~/.oc/tools.sh` (lib/termux/scripts/tools_script.dart). */
public val TermuxToolVerbs: Set<String> =
    setOf(
        "storage-scan", "storage-status", "storage-summary", "storage-cancel", "storage-clean", "procs-scan",
        "procs-stop",
    )

private val toolArgument = Regex("^[A-Za-z0-9_,]{0,400}$")

/** Why a tools command could not be built: the verb or the argument is not one the script knows. */
public class InvalidToolCommand(message: String) : IllegalArgumentException(message)

/**
 * Shell that installs the current tools script and runs [verb] (port of `termuxToolsCommandScript`). A `storage-scan`
 * is dispatched into the background and acknowledged with `tools-started:<pid>`; every other verb runs in the
 * foreground and prints its answer. The argument is a name, a number or a comma-separated pid list, single-quoted,
 * so nothing in it can escape. Throws [InvalidToolCommand] for an unknown verb or unsafe argument (a programming
 * error, never a user input: the callers build the argument from typed values).
 */
public fun termuxToolsCommandScript(verb: String, argument: String = ""): String {
    if (verb !in TermuxToolVerbs) throw InvalidToolCommand("Unknown tools verb.")
    if (!toolArgument.matches(argument)) throw InvalidToolCommand("Invalid argument.")
    val launch =
        if (verb == "storage-scan") {
            $$"""rm -f "$OC_DIR/storage-scan.cancel"
scan_pid=$(cat "$OC_DIR/storage-scan.pid" 2>/dev/null || true)
case "$scan_pid" in
  ''|*[!0-9]*) ;;
  *) if kill -0 "$scan_pid" 2>/dev/null; then
       echo "tools-already-running:$scan_pid"
       exit 0
     fi ;;
esac
set -m
nohup "$TOOLS" storage-scan >/dev/null 2>&1 </dev/null &
echo "tools-started:$!"
"""
        } else {
            "exec \"\$TOOLS\" $verb" + (if (argument.isEmpty()) "" else " '$argument'") + "\n"
        }
    return $$"""set -eu
OC_DIR="$$TERMUX_HOME/.oc"
TOOLS="$$TERMUX_TOOLS_PATH"
mkdir -p "$OC_DIR"
umask 077
tools_tmp="$TOOLS.tmp.$$"
cat > "$tools_tmp" <<'OC_TOOLS_EOF'
$${TERMUX_TOOLS_SCRIPT}
OC_TOOLS_EOF
chmod 700 "$tools_tmp"
mv "$tools_tmp" "$TOOLS"
[ -x "$TOOLS" ] || { echo 'tools-install-failed' >&2; exit 74; }
""" + launch
}

/**
 * Runs one tools verb in Termux and answers its stdout (port of `TermuxBridge.runTool`). The Android binding runs
 * [termuxToolsCommandScript] over the `oc/termux` channel; a bridge problem is a Failed outcome.
 */
public fun interface TermuxToolRunner {
    public suspend fun runTool(verb: String, argument: String, timeout: Duration): Outcome<String>
}

/** The default bound of a tools verb (the Flutter `runTool` default). */
public val DefaultToolTimeout: Duration = 30.seconds
