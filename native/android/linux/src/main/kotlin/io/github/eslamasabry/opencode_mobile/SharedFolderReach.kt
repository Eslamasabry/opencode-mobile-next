package io.github.eslamasabry.opencode_mobile

/**
 * What one start of the phone's Linux binds of shared storage: the whole of `/storage` ([broad]) or exactly [roots]
 * (each at its own path and, on the primary volume, its `/sdcard` alias; see [SharedStorageBinds]). A process keeps the
 * binds it was started with, so a folder chosen after its start is not there for it.
 */
class SharedStorageView(val broad: Boolean, roots: List<String>) {
    val roots: List<String> = roots.mapNotNull { SharedStorageBinds.canonicalRoot(it) }.distinct()

    /** Whether a process started with this view sees [path] (any spelling: `/sdcard/X` is `/storage/emulated/0/X`). */
    fun covers(path: String): Boolean {
        val folder = SharedStorageBinds.canonicalRoot(path) ?: return false
        return broad || roots.any { folder == it || folder.startsWith("$it/") }
    }
}

/**
 * Issue 147 (a folder chosen in the phone's storage must be the agent's real working folder): what that folder is to
 * the Linux identity that will work in it, checked before anything starts. The phone's own server runs as root, the
 * phone agents' helper and its agents as uid 1000 (`--change-id=1000:1000`); both reach shared storage only through
 * the binds of their own start and Android's All files access.
 */
enum class SharedFolderReach(val code: String) {
    /** The identity lists and writes the folder, and the running process (if any) has it bound. */
    Ready("ready"),

    /** Android's All files access is off: nothing in the phone's Linux can open the folder. */
    AppAccess("app_access"),

    /** The identity reaches the folder, but its process now running was started before the folder was chosen. */
    Restart("restart"),

    /** The folder is not on the phone (deleted, renamed, an SD card taken out). */
    Missing("missing"),

    /** The app sees the folder, but the identity cannot list or write it. */
    Denied("denied"),

    /** The check could not run (the phone's Linux is not ready); the agent's own start answers. */
    Unknown("unknown");

    companion object {
        /**
         * The identity's own check, run inside proot with the folder as `$1`: 0 listed and writable, 3 not there for
         * the identity (not bound), 4 not a folder, 5 not listable, 6 not writable. Writing is tried for real with an
         * empty file removed at once: under proot's emulated ids `test -w` answers yes where a write fails.
         */
        const val PROBE: String =
            "d=\"\$1\"\n" +
                "[ -e \"\$d\" ] || exit 3\n" +
                "[ -d \"\$d\" ] || exit 4\n" +
                "cd -- \"\$d\" 2>/dev/null || exit 5\n" +
                "ls -A >/dev/null 2>&1 || exit 5\n" +
                "t=\".oc-folder-check.\$\$\"\n" +
                "(: > \"\$t\") 2>/dev/null || exit 6\n" +
                "rm -f -- \"\$t\" 2>/dev/null || exit 6\n" +
                "exit 0\n"

        /**
         * [appAccess]: Android's All files access; [appSeesFolder]: the app itself finds the folder; [running]: the
         * binds of the identity's process running now (null when none runs: the next start binds what [probeExit]
         * saw); [probeExit]: the [PROBE]'s exit status, null when it could not run.
         */
        fun decide(
            appAccess: Boolean,
            appSeesFolder: Boolean,
            path: String,
            running: SharedStorageView?,
            probeExit: Int?,
        ): SharedFolderReach =
            when {
                !appAccess -> AppAccess
                !appSeesFolder -> Missing
                probeExit == null -> Unknown
                probeExit != 0 -> Denied
                running != null && !running.covers(path) -> Restart
                else -> Ready
            }
    }
}
