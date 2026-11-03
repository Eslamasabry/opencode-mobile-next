package io.github.eslamasabry.oc.engine.runtime.paths

/**
 * Which folders live in the phone's shared storage (the "internal storage" and SD cards other apps and the Files app
 * see), as opposed to the app's own project space (`/root/projects`, Termux's home). Port of
 * lib/domain/shared_storage_path.dart (B-ANDR-079, B-ANDR-080).
 *
 * Android 11+ shows an app such folders but hides other apps' non-media files unless the person allows "All files
 * access". Only a project here asks for it. The Android binding of the built-in Linux (`SharedStorageBinds` in the
 * Flutter app's BuiltinLinux.kt) must use [projectRoot] and [prootBinds] so both sides stay in step.
 */
public object SharedStoragePath {
    private val sharedPatterns =
        listOf(
            // The internal storage, by every name Android gives it.
            Regex("""^/sdcard(/|$)"""),
            Regex("""^/mnt/sdcard(/|$)"""),
            Regex("""^/mnt/user/\d+/(emulated/\d+|primary)(/|$)"""),
            Regex("""^/storage/emulated/\d+(/|$)"""),
            Regex("""^/storage/self/primary(/|$)"""),
            // An SD card or USB drive: /storage/1A2B-3C4D.
            Regex("""^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(/|$)"""),
            // Termux's shortcut after termux-setup-storage (its `~/storage/shared`).
            Regex("""^/data/data/com\.termux/files/home/storage(/|$)"""),
            Regex("""^~/storage(/|$)"""),
        )

    private val primaryAliases =
        listOf(
            Regex("""^/sdcard(?=/|$)"""),
            Regex("""^/mnt/sdcard(?=/|$)"""),
            Regex("""^/storage/self/primary(?=/|$)"""),
            Regex("""^/mnt/user/\d+/(?:emulated/(\d+)|primary)(?=/|$)"""),
        )

    private val volumeFolder = Regex("""^/storage/(?:emulated/\d+|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(/.+)$""")
    private val trailingSlashes = Regex("""/+$""")

    /** The primary volume's canonical spelling, the one `/sdcard` is an alias of. */
    public const val PRIMARY: String = "/storage/emulated/0"

    /** Whether [path] is in shared storage. `..` segments are resolved first, so `/root/projects/../../sdcard` counts. */
    public fun isShared(path: String?): Boolean {
        if (path == null) return false
        val value = resolve(path.trim().replace('\\', '/'))
        return sharedPatterns.any { it.containsMatchIn(value) }
    }

    /**
     * The folder a shared-storage project is opened from, in the one spelling the built-in Linux binds:
     * `/storage/emulated/N/<folder>[/...]` or `/storage/XXXX-XXXX/<folder>[/...]`. Null for anything else, for a whole
     * volume (never bound whole: only the folders a person opened), and for Termux's own shortcut (Termux reads it,
     * not proot).
     */
    public fun projectRoot(path: String?): String? {
        if (path == null) return null
        var value = resolve(path.trim().replace('\\', '/')).replace(trailingSlashes, "")
        for (alias in primaryAliases) {
            val match = alias.find(value) ?: continue
            val user = (if (match.groupValues.size > 1) match.groupValues[1] else "").ifEmpty { "0" }
            value = "/storage/emulated/$user" + value.substring(match.range.last + 1)
            break
        }
        return if (volumeFolder.matches(value)) value else null
    }

    /**
     * proot arguments for the shared-storage projects [roots]. [confined] is true once AI Team is on: then only each
     * opened folder is bound, at its own path and (primary volume) its `/sdcard` alias. Otherwise the whole `/storage`
     * and `/sdcard` are visible, as before AI Team.
     */
    public fun prootBinds(roots: Iterable<String>, confined: Boolean): List<String> {
        if (!confined) return listOf("--bind=/storage", "--bind=$PRIMARY:/sdcard")
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        for (raw in roots) {
            val root = projectRoot(raw) ?: continue
            if (!seen.add(root)) continue
            out += "--bind=$root"
            if (root.startsWith("$PRIMARY/")) out += "--bind=$root:/sdcard${root.substring(PRIMARY.length)}"
        }
        return out
    }

    private fun resolve(path: String): String {
        if (!path.contains("/.")) return path
        val out = ArrayList<String>()
        for (part in path.split('/')) {
            if (part.isEmpty() || part == ".") continue
            if (part == "..") {
                if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                continue
            }
            out += part
        }
        val joined = out.joinToString("/")
        return if (path.startsWith("/")) "/$joined" else joined
    }
}
