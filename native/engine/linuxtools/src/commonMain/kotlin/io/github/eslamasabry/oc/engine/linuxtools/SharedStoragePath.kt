package io.github.eslamasabry.oc.engine.linuxtools

// Which folders live in the phone's shared storage (the "internal storage" and SD cards other apps and the Files app
// see), as opposed to the app's own project space (/root/projects, Termux's home). Port of
// lib/domain/shared_storage_path.dart; the Android side mirrors it in `SharedStorageBinds` (BuiltinLinux.kt).

private val sharedPatterns =
    listOf(
        // The internal storage, by every name Android gives it.
        Regex("^/sdcard(/|$)"),
        Regex("^/mnt/sdcard(/|$)"),
        Regex("^/mnt/user/\\d+/(emulated/\\d+|primary)(/|$)"),
        Regex("^/storage/emulated/\\d+(/|$)"),
        Regex("^/storage/self/primary(/|$)"),
        // An SD card or USB drive: /storage/1A2B-3C4D.
        Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(/|$)"),
        // Termux's shortcut after termux-setup-storage (its ~/storage/shared).
        Regex("^/data/data/com\\.termux/files/home/storage(/|$)"),
        Regex("^~/storage(/|$)"),
    )

/** Whether [path] is in shared storage. `..` segments are resolved first, so `/root/projects/../../sdcard` counts. */
public fun isSharedStoragePath(path: String?): Boolean {
    if (path == null) return false
    val value = resolveDots(path.trim().replace('\\', '/'))
    return sharedPatterns.any { it.containsMatchIn(value) }
}

private fun resolveDots(path: String): String {
    if (!path.contains("/.")) return path
    val out = ArrayList<String>()
    for (part in path.split('/')) {
        if (part.isEmpty() || part == ".") continue
        if (part == "..") {
            if (out.isNotEmpty()) out.removeAt(out.lastIndex)
            continue
        }
        out.add(part)
    }
    val joined = out.joinToString("/")
    return if (path.startsWith("/")) "/$joined" else joined
}

private val primaryAliases =
    listOf(
        Regex("^/sdcard(?=/|$)"),
        Regex("^/mnt/sdcard(?=/|$)"),
        Regex("^/storage/self/primary(?=/|$)"),
        Regex("^/mnt/user/\\d+/(?:emulated/(\\d+)|primary)(?=/|$)"),
    )

private val sharedVolume = Regex("^/storage/(?:emulated/\\d+|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(/.+)$")

/**
 * The folder a shared-storage project is opened from, in the one spelling the built-in Linux binds:
 * `/storage/emulated/N/<folder>[/...]` or `/storage/XXXX-XXXX/<folder>[/...]`. Null for anything else, for a whole
 * volume (never bound whole: only the folders a person opened), and for Termux's own shortcut.
 */
public fun sharedProjectRoot(path: String?): String? {
    if (path == null) return null
    var value = resolveDots(path.trim().replace('\\', '/')).replace(Regex("/+$"), "")
    for (alias in primaryAliases) {
        val match = alias.find(value) ?: continue
        val user = (if (match.groups.size > 1) match.groups[1]?.value else null) ?: "0"
        value = "/storage/emulated/$user" + value.substring(match.range.last + 1)
        break
    }
    return if (sharedVolume.matches(value)) value else null
}

/**
 * proot arguments for the shared-storage project [roots]. [confined] is true once AI Team is on: then only each opened
 * folder is bound, at its own path and (primary volume) its `/sdcard` alias. Otherwise the whole `/storage` and
 * `/sdcard` are visible, as before AI Team.
 */
public fun sharedStorageProotBinds(roots: Iterable<String>, confined: Boolean): List<String> {
    if (!confined) return listOf("--bind=/storage", "--bind=/storage/emulated/0:/sdcard")
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    val primary = "/storage/emulated/0"
    for (raw in roots) {
        val root = sharedProjectRoot(raw) ?: continue
        if (!seen.add(root)) continue
        out.add("--bind=$root")
        if (root.startsWith("$primary/")) out.add("--bind=$root:/sdcard" + root.substring(primary.length))
    }
    return out
}
