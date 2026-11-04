package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One finished Termux command (the bridge's `TermuxCommandResult`, without the platform error fields). */
public data class TermuxCommandResult(val stdout: String, val stderr: String = "", val exitCode: Int = 0)

/**
 * Runs one script in Termux (`bash -s`) within [timeout]. A bridge problem is a Failed outcome; a timeout is
 * [Failure.TimedOut].
 */
public fun interface TermuxScriptRunner {
    public suspend fun run(script: String, timeout: Duration): Outcome<TermuxCommandResult>
}

/** Whether a made folder was new. */
public data class CreatedFolder(val path: String, val created: Boolean)

/**
 * Folders of the OpenCode server this app runs in Termux, for the folder browser (port of lib/termux/termux_folders.dart):
 * a short read-only script runs inside Termux's Ubuntu (`proot-distro login opencode-ubuntu`). It never changes
 * anything, never follows a link, and prints one line per child folder. The path reaches the script base64-encoded as
 * an argument, never as shell text: whatever a folder is called (quotes, spaces, `$(...)`, newlines), it cannot become
 * part of a command.
 */
public class TermuxFolders(private val runner: TermuxScriptRunner, private val timeout: Duration = DEFAULT_TIMEOUT) {
    public suspend fun list(path: String): FolderResult<List<FolderEntry>> {
        val ubuntuPath = checked(path) ?: return FolderResult.Problem(FolderListProblem.Invalid, path)
        return when (val result = runner.run(listScript(ubuntuPath), timeout)) {
            is Outcome.Failed -> bridgeProblem(result.failure)
            is Outcome.Ok -> parseListing(ubuntuPath, result.value.stdout)
        }
    }

    /** Makes [path] (and any missing folder above it) inside Termux's Ubuntu; `created` is false when it was there. */
    public suspend fun create(path: String): FolderResult<CreatedFolder> {
        val ubuntuPath = checked(path) ?: return FolderResult.Problem(FolderListProblem.Invalid, path)
        workspaceDirectoryProblem(ubuntuPath)?.let { return FolderResult.Problem(FolderListProblem.Invalid, it) }
        val result =
            when (val ran = runner.run(createScript(ubuntuPath), timeout)) {
                is Outcome.Failed -> return bridgeProblem(ran.failure)
                is Outcome.Ok -> ran.value
            }
        val lines = splitLines(result.stdout)
        return when {
            "oc-folder-created" in lines -> FolderResult.Ok(CreatedFolder(ubuntuPath, true))
            "oc-folder-exists" in lines -> FolderResult.Ok(CreatedFolder(ubuntuPath, false))
            "oc-folder-linked" in lines -> FolderResult.Problem(FolderListProblem.Linked, ubuntuPath)
            "oc-folders-timeout" in lines -> FolderResult.Problem(FolderListProblem.TimedOut)
            else -> FolderResult.Problem(FolderListProblem.Failed, describe(result, "The folder was not made."))
        }
    }

    public companion object {
        /** How long a listing may take in all, Termux's start included. */
        public val DEFAULT_TIMEOUT: Duration = 25.seconds

        /** The script's own bound inside Termux, shorter than [DEFAULT_TIMEOUT]. */
        public const val SCRIPT_SECONDS: Int = 15

        private fun bridgeProblem(failure: Failure): FolderResult.Problem =
            if (failure is Failure.TimedOut) {
                FolderResult.Problem(FolderListProblem.TimedOut)
            } else {
                FolderResult.Problem(FolderListProblem.Failed)
            }

        private fun checked(path: String): String? {
            val value = BuiltinRootfsFolders.normalize(path) ?: return null
            return if (value.any { it.code < 0x20 }) null else value
        }

        private fun encode(path: String): String = Base64.encode(path.encodeToByteArray())

        /**
         * Decodes `$1` into `$path` exactly (the `x` keeps a trailing newline that command substitution would drop),
         * then refuses anything but a plain absolute path.
         */
        private const val DECODE_PATH: String = $$"""path=$(printf %s "$1" | base64 -d 2>/dev/null; printf x) || { echo oc-folders-invalid; exit 0; }
path=${path%x}
nl="
"
tab=$(printf "\t")
case "$path" in /*) ;; *) echo oc-folders-invalid; exit 0 ;; esac
case "$path" in *"$nl"*|*"$tab"*) echo oc-folders-invalid; exit 0 ;; esac
"""

        /** Runs inside Ubuntu as `sh -c '<this>' -- <base64 path>`. It holds no single quote. */
        public const val LIST_INNER_SCRIPT: String = "set -u\n" + DECODE_PATH +
            $$"""cur=""
rest=${path#/}
while [ -n "$rest" ]; do
  part=${rest%%/*}
  case "$rest" in */*) rest=${rest#*/} ;; *) rest="" ;; esac
  case "$part" in ""|.) continue ;; ..) echo oc-folders-invalid; exit 0 ;; esac
  cur="$cur/$part"
  if [ -L "$cur" ]; then echo oc-folders-linked; exit 0; fi
  if [ ! -d "$cur" ]; then echo oc-folders-missing; exit 0; fi
done
dir=${cur:-/}
if [ ! -r "$dir" ] || [ ! -x "$dir" ]; then echo oc-folders-denied; exit 0; fi
echo oc-folders-ok
prefix=${dir%/}
for entry in "$prefix"/*; do
  if [ -L "$entry" ] || [ ! -d "$entry" ]; then continue; fi
  name=${entry##*/}
  case "$name" in *"$nl"*|*"$tab"*) continue ;; esac
  if [ "$dir" = / ]; then
    case "$name" in dev|proc|sys) continue ;; esac
  fi
  git=-
  if [ -e "$entry/.git" ] || [ -L "$entry/.git" ]; then git=g; fi
  printf "oc-dir\t%s\t%s\n" "$git" "$name"
done
"""

        /** Makes the folder with `mkdir -p`, refusing a link in its place. */
        public const val CREATE_INNER_SCRIPT: String = "set -u\n" + DECODE_PATH +
            $$"""if [ -L "$path" ]; then echo oc-folder-linked; exit 0; fi
if [ -d "$path" ]; then echo oc-folder-exists; exit 0; fi
mkdir -p -- "$path" && [ -d "$path" ] && echo oc-folder-created
"""

        /** The script Termux runs (`bash -s`) to list [path]'s folders. */
        public fun listScript(path: String): String = outer(LIST_INNER_SCRIPT, path)

        /** The script Termux runs (`bash -s`) to make [path]. */
        public fun createScript(path: String): String = outer(CREATE_INNER_SCRIPT, path)

        private fun outer(inner: String, path: String): String {
            check(!inner.contains('\'')) { "the inner script must hold no single quote" }
            // Base64 is letters, digits, + / and =: safe inside single quotes.
            val encoded = encode(path)
            return $$"""set -u
status=0
timeout -k 2s $${SCRIPT_SECONDS}s proot-distro login opencode-ubuntu -- sh -c '
$$inner' -- '$$encoded' || status=$?
case "$status" in
  0) ;;
  124|137) echo oc-folders-timeout ;;
  *) exit "$status" ;;
esac
"""
        }

        private fun splitLines(text: String): List<String> {
            // Dart LineSplitter: \n, \r\n or \r end a line; no trailing empty line.
            val lines = text.split(Regex("\r\n|\r|\n"))
            return if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
        }

        /**
         * The folders in [stdout] of [listScript] for [path]. Anything that is not one of the script's own lines (a
         * proot warning) is ignored; a listing without its `oc-folders-ok` line is an error. A missing projects folder
         * lists as empty (it appears with the first project).
         */
        public fun parseListing(path: String, stdout: String): FolderResult<List<FolderEntry>> {
            var problem: FolderListProblem? = null
            var ok = false
            val entries = ArrayList<FolderEntry>()
            for (line in splitLines(stdout)) {
                val marker =
                    when (line.trim()) {
                        "oc-folders-ok" -> {
                            ok = true
                            continue
                        }
                        "oc-folders-missing" -> FolderListProblem.Missing
                        "oc-folders-linked" -> FolderListProblem.Linked
                        "oc-folders-denied" -> FolderListProblem.Denied
                        "oc-folders-invalid" -> FolderListProblem.Invalid
                        "oc-folders-timeout" -> FolderListProblem.TimedOut
                        else -> null
                    }
                if (marker != null) {
                    if (problem == null) problem = marker
                    continue
                }
                if (!ok || !line.startsWith("oc-dir\t")) continue
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val flag = parts[1]
                val name = parts[2]
                if (flag != "g" && flag != "-") continue
                if (name.isEmpty() || name == "." || name == ".." || name.startsWith(".") || name.contains('/') ||
                    name.any { it.code < 0x20 || it.code == 0x7f }
                ) {
                    continue
                }
                if (path == "/" && name in setOf("dev", "proc", "sys")) continue
                entries.add(FolderEntry(name = name, path = if (path == "/") "/$name" else "$path/$name", isGit = flag == "g"))
            }
            if (problem == FolderListProblem.Missing && path == MANAGED_PROJECTS_DIRECTORY) return FolderResult.Ok(emptyList())
            problem?.let { return FolderResult.Problem(it, path) }
            if (!ok) {
                return FolderResult.Problem(
                    FolderListProblem.Failed,
                    if (stdout.trim().isEmpty()) "Termux gave no answer." else stdout.trim(),
                )
            }
            entries.sortWith(compareBy { it.name.lowercase() })
            return FolderResult.Ok(entries)
        }

        private fun describe(result: TermuxCommandResult, fallback: String): String {
            val text = listOf(result.stdout.trim(), result.stderr.trim()).filter { it.isNotEmpty() }.joinToString("\n")
            return text.ifEmpty { fallback }
        }
    }
}

/** Why a new project folder name is unusable, or null when it is a single safe path segment. */
public fun projectFolderNameProblem(name: String): String? {
    val value = name.trim()
    if (value.isEmpty()) return "Enter a folder name."
    if (value == "." || value == ".." || value.contains('/') || value.contains('\\')) {
        return "Use a single folder name without slashes."
    }
    if (!Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$").matches(value)) {
        return "Use letters, digits, dots, dashes or underscores, starting with a letter or digit (up to 64 characters)."
    }
    return null
}

/**
 * The script that makes one project folder under `/root/projects` in the managed Termux container (port of
 * `termuxCreateProjectFolderScript`). The script itself refuses anything but one plain name.
 */
public fun termuxCreateProjectFolderScript(name: String): String =
    $$"""set -eu
timeout -k 2s 30s proot-distro login opencode-ubuntu -- sh -c '
set -eu
name="$1"
case "$name" in ""|.|..|*/*|.*) echo "invalid-folder-name" >&2; exit 64 ;; esac
dir="$$MANAGED_PROJECTS_DIRECTORY/$name"
mkdir -p "$dir"
test -d "$dir"
printf "%s\n" "$dir"
' -- '$$name'
"""

/** Why [createProjectFolder] did not make a folder: `invalid_folder_name` or `folder_not_created` (the Dart codes). */
public data class ProjectFolderRefusal(val code: String, val message: String)

/**
 * Creates a project folder under `/root/projects` in the managed container and answers its absolute path (port of
 * `TermuxBridge.createProjectFolder`). A name that is not one safe segment is refused before anything runs.
 */
public suspend fun createProjectFolder(
    runner: TermuxScriptRunner,
    name: String,
): Outcome<String> {
    projectFolderNameProblem(name)?.let { return Outcome.Failed(Failure.Rejected(status = null)) }
    val folder = name.trim()
    val result =
        when (val ran = runner.run(termuxCreateProjectFolderScript(folder), 45.seconds)) {
            is Outcome.Failed -> return ran
            is Outcome.Ok -> ran.value
        }
    val path = result.stdout.trim().split('\n').last().trim()
    if (path != "$MANAGED_PROJECTS_DIRECTORY/$folder") return Outcome.Failed(Failure.Termux())
    return Outcome.Ok(path)
}

/** The plain-words reason a name cannot be a project folder, with its Dart error code (for the create row). */
public fun projectFolderRefusal(name: String): ProjectFolderRefusal? =
    projectFolderNameProblem(name)?.let { ProjectFolderRefusal("invalid_folder_name", it) }

// ---- lib/domain/workspace_paths.dart (the two checks the folder tools need)

private fun normalizeWorkspace(path: String): String {
    var value = path.trim().replace('\\', '/')
    while (value.length > 1 && value.endsWith("/")) {
        val trimmed = value.substring(0, value.length - 1)
        if (trimmed.endsWith(":")) break // Keep a Windows drive root intact.
        value = trimmed
    }
    return value
}

/** True for `/`, a Windows drive root and every home folder shape the supported servers use (and empty paths). */
public fun isProtectedWorkspaceDirectory(path: String?): Boolean {
    if (path == null) return true
    val value = normalizeWorkspace(path)
    if (value.isEmpty() || value == "/" || value == "~") return true
    if (Regex("^[A-Za-z]:/?$").matches(value) || Regex("^[A-Za-z]:/Users/[^/]+$", RegexOption.IGNORE_CASE).matches(value)) {
        return true
    }
    if (value == "/root" || value == "/home" || value == "/Users") return true
    if (value == TERMUX_HOME) return true
    val segments = value.split('/').filter { it.isNotEmpty() }
    return segments.size == 2 && (segments.first() == "home" || segments.first() == "Users")
}

/** Plain-sentence reason a typed or restored [path] cannot be a workspace, or null when it is acceptable. */
public fun workspaceDirectoryProblem(path: String?): String? {
    val value = if (path == null) "" else normalizeWorkspace(path)
    if (value.isEmpty()) return "Enter the full path of a project folder."
    if (value.any { it.code < 0x20 }) return "The folder path contains characters that cannot be used."
    if (value.length > 4096) return "The folder path is too long."
    val absolute = value.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(value)
    if (!absolute) return "Enter an absolute path, starting with /."
    if (isProtectedWorkspaceDirectory(value)) {
        return "The home folder and the filesystem root cannot be used as a workspace. " +
            "Choose a project folder inside them instead."
    }
    return null
}
