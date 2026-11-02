package io.github.eslamasabry.oc.feature.linux.manager

/*
 * Where the space of the app's own Linux goes, and the cleanups that give it back (port of
 * lib/builtin/linux_manager/linux_storage.dart, B-LINUX-089). A cleanup may only name a path under an allowed root;
 * [LinuxStorage.cleanupPathIsSafe] refuses anything that is, or holds, a protected path (projects, sign-ins, settings).
 */

/** A slice of the disk, in the words the page uses. The figures add up: the paths are disjoint. */
public enum class LinuxStorageCategory { Base, Packages, Tools, Agents, Projects, Temp }

/** A safe way to give space back. */
public enum class LinuxCleanupId { AptCache, ToolCaches, TempFiles, OldAgentVersions }

public class LinuxCleanupAction internal constructor(
    public val id: LinuxCleanupId,
    /** Every path the script removes things under. */
    public val paths: List<String>,
    /** Removes. Run alone by the app, never built from text a person typed. */
    public val script: String,
    /** Prints `::du <kb> <path>` for what [script] would remove; the space freed is the drop between two readings. */
    public val measureScript: String,
    /** Paths the script only reads (to decide what to keep). */
    public val reads: List<String> = emptyList(),
)

/** How a storage reading adds up. */
public data class LinuxStorageReport(val bytes: Map<LinuxStorageCategory, Long>) {
    val total: Long get() = bytes.values.sum()

    public fun of(category: LinuxStorageCategory): Long = bytes[category] ?: 0L
}

public object LinuxStorage {
    public val categoryPaths: Map<LinuxStorageCategory, List<String>> =
        linkedMapOf(
            LinuxStorageCategory.Base to listOf("/usr", "/etc", "/var/lib/dpkg"),
            LinuxStorageCategory.Packages to listOf("/var/cache/apt", "/var/lib/apt"),
            LinuxStorageCategory.Tools to listOf("/opt/node", "/root/.npm", "/root/.cache", "/var/cache/oc-setup", "/home/oc/.cache"),
            LinuxStorageCategory.Agents to
                listOf(
                    "/opt/opencode", "/opt/opencode2", "/opt/oc2", "/opt/oc-claude", "/opt/aiteam", "/root/aiteam", "/root/.gc",
                    "/root/.oc-opencode2", "/home/oc/.local", "/home/oc/.oc-profiles",
                ),
            LinuxStorageCategory.Projects to listOf("/root/projects"),
            LinuxStorageCategory.Temp to listOf("/tmp", "/var/tmp", "/var/log"),
        )

    /** The only places a cleanup may touch. */
    private val allowedRoots =
        listOf(
            "/var/cache/apt", "/root/.npm/_cacache", "/root/.npm/_logs", "/root/.cache", "/var/cache/oc-setup",
            "/home/oc/.cache", "/tmp", "/var/tmp", "/var/log", "/home/oc/.local/share/oc-paseo",
        )

    /** Never touched, nor any folder that holds one of these. */
    public val protectedPaths: List<String> =
        listOf(
            "/root/projects", "/root/.oc-builtin", "/root/.oc-opencode2", "/root/.claude", "/root/.codex", "/root/.config",
            "/root/.ssh", "/root/.gc", "/root/aiteam", "/root/.local", "/home/oc/.oc-profiles", "/home/oc/.claude",
            "/home/oc/.codex", "/home/oc/.config", "/home/oc/.ssh", "/home/oc/.paseo", "/opt", "/etc", "/usr",
        )

    private val plainPath = Regex("^/[A-Za-z0-9_./-]+$")

    private fun within(path: String, root: String) = path == root || path.startsWith("$root/")

    /** An absolute plain path (no spaces, quotes, globs or `..`), under an allowed root, neither in nor holding a protected one. */
    public fun cleanupPathIsSafe(path: String): Boolean {
        if (!plainPath.matches(path) || path.contains("..") || path.endsWith("/")) return false
        if (allowedRoots.none { within(path, it) }) return false
        return protectedPaths.none { within(path, it) || within(it, path) }
    }

    private const val DU_LINE = $$"""
oc_du() { for p in "$@"; do [ -e "$p" ] && printf '::du %s %s\n' "$(du -sk -x "$p" 2>/dev/null | cut -f1)" "$p"; done; }
"""

    private fun measure(paths: List<String>) = "$DU_LINE\noc_du ${paths.joinToString(" ")}\nexit 0\n"

    private val toolCachePaths = listOf("/root/.npm/_cacache", "/root/.npm/_logs", "/root/.cache", "/var/cache/oc-setup", "/home/oc/.cache")

    private const val PASEO_DIR = "/home/oc/.local/share/oc-paseo"
    private const val PASEO_LINK = "/home/oc/.local/bin/paseo"
    private const val OLD_PASEO = $$"""
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
            script = (toolCachePaths.map { "[ -d $it ] && find $it -mindepth 1 -delete 2>/dev/null" } + "exit 0").joinToString("\n"),
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
                ).joinToString("\n"),
            measureScript =
                DU_LINE + "\n" +
                    $$"""for d in /tmp /var/tmp; do [ -d "$d" ] && printf "::du %s %s\n" "$(find "$d" -mindepth 1 -mtime +1 -type f -printf "%k\n" 2>/dev/null | awk '{s+=$1} END {print s+0}')" "$d"; done""" +
                    "\n" +
                    $$"""printf "::du %s /var/log\n" "$(find /var/log -type f -mtime +7 -printf "%k\n" 2>/dev/null | awk '{s+=$1} END {print s+0}')"""" +
                    "\nexit 0\n",
        )

    /** Paseo versions the current link does not point at. Nothing is removed when the current version is unreadable. */
    public val oldAgentVersions: LinuxCleanupAction =
        LinuxCleanupAction(
            id = LinuxCleanupId.OldAgentVersions,
            paths = listOf(PASEO_DIR),
            reads = listOf(PASEO_LINK),
            script = OLD_PASEO + $$"""  rm -rf -- "$d"""" + "\ndone\nexit 0\n",
            measureScript = OLD_PASEO + $$"""  printf "::du %s %s\n" "$(du -sk -x "$d" 2>/dev/null | cut -f1)" "$d"""" + "\ndone\nexit 0\n",
        )

    public val cleanups: List<LinuxCleanupAction> = listOf(aptCache, toolCaches, tempFiles, oldAgentVersions)

    /** The measure script for every category path. */
    public val script: String = measure(categoryPaths.values.flatten())

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

    /** Bytes removed between two readings. Never negative: files written meanwhile are not "freed space". */
    public fun freedBytes(before: Map<String, Long>, after: Map<String, Long>): Long =
        before.entries.sumOf { (path, kb) -> (kb - (after[path] ?: 0L)).coerceAtLeast(0L) } * 1024L

    public fun report(output: String): LinuxStorageReport {
        val du = parseDu(output)
        return LinuxStorageReport(categoryPaths.mapValues { (_, paths) -> paths.sumOf { du[it] ?: 0L } * 1024L })
    }
}
