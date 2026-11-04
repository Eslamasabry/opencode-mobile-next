package io.github.eslamasabry.oc.feature.linux.manager

/*
 * Where the space of the app's own Linux goes, and the cleanups that give it back (port of
 * lib/builtin/linux_manager/linux_storage.dart, B-LINUX-089). A cleanup may only name a path under an allowed root;
 * [LinuxStorage.cleanupPathIsSafe] refuses anything that is, or holds, a protected path (projects, sign-ins, settings).
 */

/** A slice of the disk, in the words the page uses. The figures add up: the paths are disjoint. */
public enum class LinuxStorageCategory {
    Base,
    Packages,
    Tools,
    Agents,
    Projects,
    Temp,
}

/** A safe way to give space back. */
public enum class LinuxCleanupId {
    AptCache,
    ToolCaches,
    TempFiles,
    OldAgentVersions,
}

public class LinuxCleanupAction
internal constructor(
    public val id: LinuxCleanupId,
    /** Every path the script removes things under. */
    public val paths: List<String>,
    /** Removes. Run alone by the app, never built from text a person typed. */
    public val script: String,
    /**
     * Prints `::du <kb> <path>` for what [script] would remove; the space freed is the drop between
     * two readings.
     */
    public val measureScript: String,
    /** Paths the script only reads (to decide what to keep). */
    public val reads: List<String> = emptyList(),
)

/** How a storage reading adds up. */
public data class LinuxStorageReport(val bytes: Map<LinuxStorageCategory, Long>) {
    val total: Long
        get() = bytes.values.sum()

    public fun of(category: LinuxStorageCategory): Long = bytes[category] ?: 0L
}

public enum class LinuxStorageScanState {
    Idle,
    Running,
    Done,
    Failed,
}

/** Only authored stage markers enter the log; paths and shell errors never become labels. */
public data class LinuxStorageScan(
    val state: LinuxStorageScanState = LinuxStorageScanState.Idle,
    val headings: List<LinuxStorageCategory> = emptyList(),
) {
    val stage: LinuxStorageCategory?
        get() = headings.lastOrNull()

    public companion object {
        public fun parse(output: String): LinuxStorageScan {
            val state =
                output
                    .lineSequence()
                    .mapNotNull { line ->
                        if (!line.startsWith("::storage-state ")) null
                        else
                            LinuxStorageScanState.entries.firstOrNull {
                                it.name == line.removePrefix("::storage-state ")
                            }
                    }
                    .lastOrNull() ?: LinuxStorageScanState.Idle
            val headings =
                output
                    .lineSequence()
                    .mapNotNull { line ->
                        if (!line.startsWith("::storage-stage ")) null
                        else
                            LinuxStorageCategory.entries.firstOrNull {
                                it.name == line.removePrefix("::storage-stage ")
                            }
                    }
                    .toList()
                    .takeLast(32)
            return LinuxStorageScan(state, headings)
        }
    }
}

public object LinuxStorage {
    public val categoryPaths: Map<LinuxStorageCategory, List<String>> =
        linkedMapOf(
            LinuxStorageCategory.Base to listOf("/usr", "/etc", "/var/lib/dpkg"),
            LinuxStorageCategory.Packages to listOf("/var/cache/apt", "/var/lib/apt"),
            LinuxStorageCategory.Tools to
                listOf(
                    "/opt/node",
                    "/root/.npm",
                    "/root/.cache",
                    "/var/cache/oc-setup",
                    "/home/oc/.cache",
                ),
            LinuxStorageCategory.Agents to
                listOf(
                    "/opt/opencode",
                    "/opt/opencode2",
                    "/opt/oc2",
                    "/opt/oc-claude",
                    "/opt/aiteam",
                    "/root/aiteam",
                    "/root/.gc",
                    "/root/.oc-opencode2",
                    "/home/oc/.local",
                    "/home/oc/.oc-profiles",
                ),
            LinuxStorageCategory.Projects to listOf("/root/projects"),
            LinuxStorageCategory.Temp to listOf("/tmp", "/var/tmp", "/var/log"),
        )

    /** The only places a cleanup may touch. */
    private val allowedRoots =
        listOf(
            "/var/cache/apt",
            "/root/.npm/_cacache",
            "/root/.npm/_logs",
            "/root/.cache",
            "/var/cache/oc-setup",
            "/home/oc/.cache",
            "/tmp",
            "/var/tmp",
            "/var/log",
            "/home/oc/.local/share/oc-paseo",
        )

    /** Never touched, nor any folder that holds one of these. */
    public val protectedPaths: List<String> =
        listOf(
            "/root/projects",
            "/root/.oc-builtin",
            "/root/.oc-opencode2",
            "/root/.claude",
            "/root/.codex",
            "/root/.config",
            "/root/.ssh",
            "/root/.gc",
            "/root/aiteam",
            "/root/.local",
            "/home/oc/.oc-profiles",
            "/home/oc/.claude",
            "/home/oc/.codex",
            "/home/oc/.config",
            "/home/oc/.ssh",
            "/home/oc/.paseo",
            "/opt",
            "/etc",
            "/usr",
        )

    private val plainPath = Regex("^/[A-Za-z0-9_./-]+$")

    private fun within(path: String, root: String) = path == root || path.startsWith("$root/")

    /**
     * An absolute plain path (no spaces, quotes, globs or `..`), under an allowed root, neither in
     * nor holding a protected one.
     */
    public fun cleanupPathIsSafe(path: String): Boolean {
        if (!plainPath.matches(path) || path.contains("..") || path.endsWith("/")) return false
        if (allowedRoots.none { within(path, it) }) return false
        return protectedPaths.none { within(path, it) || within(it, path) }
    }

    private const val DU_LINE =
        $$"""
oc_du() { for p in "$@"; do [ -e "$p" ] && printf '::du %s %s\n' "$(du -sk -x "$p" 2>/dev/null | cut -f1)" "$p"; done; }
"""

    private fun measure(paths: List<String>) =
        "$DU_LINE\noc_du ${paths.joinToString(" ")}\nexit 0\n"

    private val toolCachePaths =
        listOf(
            "/root/.npm/_cacache",
            "/root/.npm/_logs",
            "/root/.cache/pip",
            "/root/.cache/uv",
            "/home/oc/.cache/pip",
            "/home/oc/.cache/uv",
        )

    private const val PASEO_DIR = "/home/oc/.local/share/oc-paseo"
    private const val PASEO_LINK = "/home/oc/.local/bin/paseo"
    private const val OLD_PASEO =
        $$"""
cur=$(readlink -f /home/oc/.local/bin/paseo 2>/dev/null)
[ -n "$cur" ] && [ -d /home/oc/.local/share/oc-paseo ] || exit 0
case "$cur" in /home/oc/.local/share/oc-paseo/*) ;; *) exit 0 ;; esac
for d in /home/oc/.local/share/oc-paseo/*; do
  [ -d "$d" ] || continue
  case "$cur" in "$d"/*) continue ;; esac
"""

    /** `apt-get clean`: the downloaded .deb files. Package lists stay. */
    public val aptCache: LinuxCleanupAction =
        LinuxCleanupAction(
            id = LinuxCleanupId.AptCache,
            paths = listOf("/var/cache/apt"),
            script = "DEBIAN_FRONTEND=noninteractive apt-get clean\nexit 0\n",
            measureScript = measure(listOf("/var/cache/apt")),
        )

    /** npm's download cache and the setup downloads (fetched again if ever needed). */
    public val toolCaches: LinuxCleanupAction =
        LinuxCleanupAction(
            id = LinuxCleanupId.ToolCaches,
            paths = toolCachePaths,
            script =
                (toolCachePaths.map { "[ -d $it ] && find $it -mindepth 1 -delete 2>/dev/null" } +
                        "exit 0")
                    .joinToString("\n"),
            measureScript = measure(toolCachePaths),
        )

    /** Files in /tmp and /var/tmp untouched for a day, and logs older than a week. */
    public val tempFiles: LinuxCleanupAction =
        LinuxCleanupAction(
            id = LinuxCleanupId.TempFiles,
            paths = listOf("/tmp", "/var/tmp", "/var/log"),
            script =
                listOf(
                        "find /tmp /var/tmp -mindepth 1 -mtime +1 -delete 2>/dev/null",
                        "find /var/log -type f -mtime +7 -delete 2>/dev/null",
                        "exit 0",
                    )
                    .joinToString("\n"),
            measureScript =
                DU_LINE +
                    "\n" +
                    $$"""for d in /tmp /var/tmp; do [ -d "$d" ] && printf "::du %s %s\n" "$(find "$d" -mindepth 1 -mtime +1 -type f -printf "%k\n" 2>/dev/null | awk '{s+=$1} END {print s+0}')" "$d"; done""" +
                    "\n" +
                    $$"""printf "::du %s /var/log\n" "$(find /var/log -type f -mtime +7 -printf "%k\n" 2>/dev/null | awk '{s+=$1} END {print s+0}')"""" +
                    "\nexit 0\n",
        )

    /**
     * Paseo versions the current link does not point at. Nothing is removed when the current
     * version is unreadable.
     */
    public val oldAgentVersions: LinuxCleanupAction =
        LinuxCleanupAction(
            id = LinuxCleanupId.OldAgentVersions,
            paths = listOf(PASEO_DIR),
            reads = listOf(PASEO_LINK),
            script = OLD_PASEO + $$"""  rm -rf -- "$d"""" + "\ndone\nexit 0\n",
            measureScript =
                OLD_PASEO +
                    $$"""  printf "::du %s %s\n" "$(du -sk -x "$d" 2>/dev/null | cut -f1)" "$d"""" +
                    "\ndone\nexit 0\n",
        )

    public val cleanups: List<LinuxCleanupAction> =
        listOf(aptCache, toolCaches, tempFiles, oldAgentVersions)

    /** A one-use private receipt binds a cleanup to the regular files an explicit scan found. */
    internal val receiptName: Regex = Regex("^storage-cleanup\\.[A-Za-z0-9]{10}$")

    private const val CLEANUP_GUARD =
        $$"""
set -eu
cleanup_home=/root/.oc-builtin
safe_parents() {
  checked="$1"
  while [ "$checked" != / ]; do
    [ ! -L "$checked" ] || return 1
    checked=${checked%/*}
    [ -n "$checked" ] || checked=/
  done
}
safe_parents "$cleanup_home" || exit 74
[ ! -e "$cleanup_home" ] || [ -d "$cleanup_home" ] || exit 74
mkdir -p "$cleanup_home"
[ "$(stat -c %u "$cleanup_home")" = "$(id -u)" ] || exit 74
chmod 700 "$cleanup_home"
# A missing or failed process listing is unknown, never an idle phone.
process_table=$(ps -eo pid=,ppid=,args=) || exit 74
[ -n "$process_table" ] || exit 74
# The host launches this script with sh -c: its own argv and every proot ancestor contain the
# expressions below. Exclude only the exact live ancestor chain established by this reading.
processes=$(printf '%s\n' "$process_table" | awk -v self="$$" '
  { parent[$1] = $2; row[$1] = $0; order[NR] = $1 }
  END {
    if (!(self in parent)) exit 74
    pid = self
    while (pid > 1 && !hidden[pid]) {
      hidden[pid] = 1
      if (!(pid in parent)) break
      pid = parent[pid]
    }
    for (i = 1; i <= NR; i++) if (!hidden[order[i]]) print row[order[i]]
  }') || exit 74
if printf '%s\n' "$processes" | grep -Eiq '(GradleDaemon|gradle-launcher|[f]lutter (build|test)|[d]art (compile|test)|[n]pm (install|ci|run)|[p]npm (install|run)|[y]arn (install|run)|[a]pt(-get)? .*install|[d]pkg .*--|[p]ip[0-9]* install|[c]argo (build|run|test)|[m]ake( |$)|[n]inja( |$))'; then exit 75; fi
safe_path() {
  case "$1" in *[!A-Za-z0-9_./-]*|*..*|*/|/) return 1 ;; esac
  case "$1" in
    /var/cache/apt/archives/*.deb|/root/.npm/_cacache/*|/root/.npm/_logs/*|/root/.cache/pip/*|/root/.cache/uv/*|/home/oc/.cache/pip/*|/home/oc/.cache/uv/*|/tmp/*|/var/tmp/*|/var/log/*|/home/oc/.local/share/oc-paseo/*) ;;
    *) return 1 ;;
  esac
  safe_parents "$1"
}
"""

    private fun categoryGuard(id: LinuxCleanupId): String {
        val patterns =
            when (id) {
                LinuxCleanupId.AptCache -> "/var/cache/apt/archives/*.deb"
                LinuxCleanupId.ToolCaches -> toolCachePaths.joinToString("|") { "$it/*" }
                LinuxCleanupId.TempFiles -> "/tmp/*|/var/tmp/*|/var/log/*"
                LinuxCleanupId.OldAgentVersions -> "/home/oc/.local/share/oc-paseo/*"
            }
        return "\ncategory_path() { case \"${'$'}1\" in $patterns) safe_path \"${'$'}1\" ;; *) return 1 ;; esac; }\n"
    }

    /**
     * The scan stores only safe file paths and inode/device/owner identities, never wildcard
     * removals.
     */
    internal fun scanCleanup(action: LinuxCleanupAction): String {
        val discover =
            when (action.id) {
                LinuxCleanupId.AptCache -> "collect /var/cache/apt/archives -name '*.deb'"
                LinuxCleanupId.ToolCaches -> toolCachePaths.joinToString("\n") { "collect $it" }
                LinuxCleanupId.TempFiles ->
                    "collect /tmp -mtime +1\ncollect /var/tmp -mtime +1\ncollect /var/log -mtime +7"
                LinuxCleanupId.OldAgentVersions ->
                    $$"""
current=$(readlink -f /home/oc/.local/bin/paseo 2>/dev/null || true)
case "$current" in
  /home/oc/.local/share/oc-paseo/*)
    for d in /home/oc/.local/share/oc-paseo/*; do
      [ -d "$d" ] || continue
      case "$current" in "$d"/*) continue ;; esac
      collect "$d"
    done ;;
esac
"""
            }
        return CLEANUP_GUARD +
            categoryGuard(action.id) +
            $$"""
umask 077
receipt=$(mktemp "$cleanup_home/storage-cleanup.XXXXXXXXXX")
trap 'rm -f -- "$receipt" "$receipt.paths"' EXIT
printf 'v1\n' > "$receipt"
collect() {
  root="$1"; shift
  safe_parents "$root" || exit 74
  [ -e "$root" ] || return 0
  [ -d "$root" ] && [ ! -L "$root" ] || exit 74
  find "$root" -type f "$@" -print > "$receipt.paths" || exit 74
  while IFS= read -r path; do
    category_path "$path" || continue
    identity=$(stat -c '%d:%i:%u' "$path") || exit 74
    [ "${identity##*:}" = "$(id -u)" ] || exit 74
    size=$(du -k -- "$path" | cut -f1) || exit 74
    case "$size" in ''|*[!0-9]*) exit 74 ;; esac
    printf '%s %s %s\n' "$identity" "$size" "$path" >> "$receipt"
  done < "$receipt.paths"
}
""" +
            discover +
            $$"""

total=$(awk 'NR > 1 { sum += $2 } END { printf "%.0f", sum + 0 }' "$receipt")
printf '::cleanup %s %s\n' "${receipt##*/}" "$total"
rm -f -- "$receipt.paths"
trap - EXIT
"""
    }

    /**
     * Revalidates the entire receipt before the first removal; partial failures report measured
     * shrinkage only.
     */
    internal fun cleanReceipt(action: LinuxCleanupAction, name: String): String {
        require(receiptName.matches(name))
        return CLEANUP_GUARD +
            categoryGuard(action.id) +
            "\nreceipt=\"${'$'}cleanup_home/$name\"\n" +
            $$"""
[ -f "$receipt" ] && [ ! -L "$receipt" ] || exit 74
[ "$(stat -c %u "$receipt")" = "$(id -u)" ] || exit 74
[ "$(head -n 1 "$receipt")" = v1 ] || exit 74
trap 'rm -f -- "$receipt"' EXIT
# Replaced ancestors or files refuse the whole operation before anything is deleted.
while IFS=' ' read -r identity old_size path; do
  [ "$identity" != v1 ] || continue
  [ "${identity##*:}" = "$(id -u)" ] || exit 74
  category_path "$path" || exit 74
  [ -f "$path" ] && [ ! -L "$path" ] || exit 74
  [ "$(stat -c '%d:%i:%u' "$path")" = "$identity" ] || exit 74
  case "$path" in
    /home/oc/.local/share/oc-paseo/*)
      current=$(readlink -f /home/oc/.local/bin/paseo) || exit 74
      case "$current" in /home/oc/.local/share/oc-paseo/*) ;; *) exit 74 ;; esac
      version=${path#/home/oc/.local/share/oc-paseo/}; version=${version%%/*}
      case "$current" in "/home/oc/.local/share/oc-paseo/$version"/*) exit 74 ;; esac
      ;;
  esac
done < "$receipt"
freed=0
while IFS=' ' read -r identity old_size path; do
  [ "$identity" != v1 ] || continue
  [ "${identity##*:}" = "$(id -u)" ] || exit 74
  category_path "$path" || exit 74
  [ "$(stat -c '%d:%i:%u' "$path")" = "$identity" ] || exit 74
  before=$(du -k -- "$path" | cut -f1) || exit 74
  case "$before" in ''|*[!0-9]*) exit 74 ;; esac
  rm -f -- "$path" 2>/dev/null || true
  if [ -e "$path" ] || [ -L "$path" ]; then
    category_path "$path" || exit 74
    after=$(du -k -- "$path" | cut -f1) || exit 74
    case "$after" in ''|*[!0-9]*) exit 74 ;; esac
  else
    after=0
  fi
  [ "$before" -le "$after" ] || freed=$((freed + before - after))
done < "$receipt"
printf '::freed %s\n' "$freed"
"""
    }

    /** The measure script for every category path. */
    public fun stageScript(category: LinuxStorageCategory): String =
        "printf '::storage-stage ${category.name}\\n'\n" + measure(categoryPaths.getValue(category))

    public val script: String = measure(categoryPaths.values.flatten())

    /**
     * A new scan voids every earlier receipt: removes the receipt files a previous scan left unused
     * (only the exact mktemp names, never a link target), so they cannot pile up.
     */
    internal const val DISCARD_RECEIPTS: String =
        "rm -f -- /root/.oc-builtin/storage-cleanup.?????????? " +
            "/root/.oc-builtin/storage-cleanup.??????????.paths 2>/dev/null || true\n"

    /** Kilobytes by path from `::du` lines. */
    public fun parseDu(output: String): Map<String, Long> {
        val out = linkedMapOf<String, Long>()
        for (line in output.split('\n')) {
            if (!line.startsWith("::du ")) continue
            val rest = line.substring(5).trim()
            val space = rest.indexOf(' ')
            if (space < 0) continue
            val kb = rest.substring(0, space).toLongOrNull() ?: continue
            val path = rest.substring(space + 1).trim()
            if (kb < 0 || path.isEmpty()) continue
            out[path] = kb
        }
        return out
    }

    /**
     * Bytes removed between two readings. Never negative: files written meanwhile are not "freed
     * space".
     */
    public fun freedBytes(before: Map<String, Long>, after: Map<String, Long>): Long =
        before.entries.sumOf { (path, kb) -> (kb - (after[path] ?: 0L)).coerceAtLeast(0L) } * 1024L

    public fun report(output: String): LinuxStorageReport {
        val du = parseDu(output)
        return LinuxStorageReport(
            categoryPaths.mapValues { (_, paths) -> paths.sumOf { du[it] ?: 0L } * 1024L }
        )
    }
}
