package io.github.eslamasabry.oc.engine.runtime.paths

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

/** One name directly inside a folder, as the filesystem reports it (links are not followed). */
public data class PhoneFolderChild(val name: String, val isDirectory: Boolean)

/**
 * The phone's filesystem as this app's own process sees it once "All files access" is on. The
 * Android binding uses java.io / NIO; tests use a map. [children] answers `Failure.Folder(Missing |
 * Denied)` for those two cases and any other failure (`Failure.Device`) for the rest.
 */
public interface PhoneFolderFiles {
    public suspend fun isDirectory(path: String): Boolean

    /** The names directly inside [path], unsorted. */
    public suspend fun children(path: String): Outcome<List<PhoneFolderChild>>
}

/** A folder the browser can show (port of the Dart `FolderEntry`). */
public data class PhoneFolderEntry(
    val name: String,
    val path: String,
    val isGit: Boolean = false,
    /**
     * A few visible names inside: the browser's one-line hint. Empty means empty; null means nobody
     * looked.
     */
    val inside: List<String>? = null,
    /** How many more names there are beyond [inside]. */
    val more: Int = 0,
)

/**
 * Lists folders in the phone's shared storage (port of lib/platform/phone_storage_folders.dart,
 * B-ANDR-051). Used by the folder browser's "This phone's storage" place for hosts that run on this
 * phone.
 *
 * Failures from [list]: a path outside the internal storage is `Failure.Refused(InvalidAddress)`
 * (Flutter `invalid`), a missing folder is `Failure.Folder(Missing)`, an unreadable one
 * `Failure.Folder(Denied)`, anything else what the [files] port answered.
 */
public class PhoneStorageFolders(
    private val files: PhoneFolderFiles,
    private val hintNames: Int = 2,
    private val hintLimit: Int = 40,
) {
    public suspend fun list(path: String): Outcome<List<PhoneFolderEntry>> {
        val folder =
            normalize(path) ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        if (!files.isDirectory(folder)) return Failure.Folder(FolderProblem.Missing).failed()
        val folders =
            when (val listed = files.children(folder)) {
                is Outcome.Failed -> return listed
                is Outcome.Ok -> listed.value.filter { it.isDirectory }.map { it.name }
            }.sortedBy { it.lowercase() }
        val entries = ArrayList<PhoneFolderEntry>(folders.size)
        var hinted = 0
        for (name in folders) {
            val childPath = "$folder/$name"
            if (name.startsWith(".") || hinted >= hintLimit) {
                entries += PhoneFolderEntry(name = name, path = childPath)
                continue
            }
            hinted++
            entries += entry(childPath, name)
        }
        return entries.ok()
    }

    private suspend fun entry(path: String, name: String): PhoneFolderEntry {
        val inner = (files.children(path) as? Outcome.Ok)?.value.orEmpty()
        // A folder that cannot be read still shows, with an empty hint (as in Flutter).
        val git = inner.any { it.name == ".git" }
        val visible = inner.filterNot { it.name.startsWith(".") }.take(HINT_SCAN)
        val folders = visible.filter { it.isDirectory }.map { it.name }.sortedBy { it.lowercase() }
        val plain = visible.filterNot { it.isDirectory }.map { it.name }.sortedBy { it.lowercase() }
        // Files that name a project come first: "package.json, src" says more than the first two
        // letters.
        val all = plain.filter { it in TELLING } + folders + plain.filterNot { it in TELLING }
        return PhoneFolderEntry(
            name = name,
            path = path,
            isGit = git,
            inside = all.take(hintNames),
            more = if (all.size > hintNames) all.size - hintNames else 0,
        )
    }

    public companion object {
        /** The internal storage, where everything the browser shows starts. */
        public const val ROOT: String = "/storage/emulated/0"

        private const val HINT_SCAN = 200
        private val TELLING =
            setOf(
                "package.json",
                "pubspec.yaml",
                "Cargo.toml",
                "pyproject.toml",
                "go.mod",
                "README.md",
            )
        private val trailingSlashes = Regex("""/+$""")

        /**
         * `/sdcard/x` and the like read as the one spelling the app binds; null for anything
         * outside [ROOT].
         */
        public fun normalize(path: String): String? {
            if ('\u0000' in path) return null
            var value = path.trim().replace(trailingSlashes, "")
            if (value == "/sdcard" || value.startsWith("/sdcard/"))
                value = ROOT + value.substring("/sdcard".length)
            if (value != ROOT && !value.startsWith("$ROOT/")) return null
            if (value.split('/').contains("..")) return null
            return value
        }

        /**
         * The folder above [path], or null at [ROOT]: the browser never climbs out of the internal
         * storage.
         */
        public fun parentOf(path: String): String? {
            if (path == ROOT) return null
            val cut = path.lastIndexOf('/')
            return if (cut <= 0) null else path.substring(0, cut)
        }
    }
}
