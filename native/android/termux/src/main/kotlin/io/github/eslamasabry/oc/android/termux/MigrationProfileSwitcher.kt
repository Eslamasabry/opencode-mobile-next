package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CancellationException
import java.net.URI

/** A saved server as the move sees it: no password, no workspace, no session state. */
public data class MigrationProfile(
    val id: String,
    /** The backend is OpenCode (Paseo and other backends never move). */
    val openCode: Boolean,
    val baseUrl: String,
    /** The OpenCode generation (`v1` / `v2`); the destination keeps the source's. */
    val flavor: String,
)

/**
 * The profile store and the connection, narrowed to the switch (the app binds ProfileStore + ConnectionController +
 * the in-app server start). Any exception is replaced by a fixed code: connection and storage errors can hold
 * credentials.
 */
public interface MigrationProfiles {
    public fun profile(id: String): MigrationProfile?

    /** The saved project directory of [profileId], or null. */
    public fun locationDirectory(profileId: String): String?

    /** Saves [directory] as [profileId]'s location with no workspace (workspace IDs belong to the old server). */
    public suspend fun setLocationDirectory(profileId: String, directory: String)

    /** The in-app server's profile for [flavor], created when missing. */
    public suspend fun ensureBuiltinProfile(flavor: String): MigrationProfile

    /** Starts the in-app server; false when it did not start. */
    public suspend fun startBuiltin(profile: MigrationProfile, stillWanted: () -> Boolean): Boolean

    public suspend fun connect(profile: MigrationProfile)

    public fun isConnectedTo(profile: MigrationProfile): Boolean

    public fun selectedProfileId(): String?
}

/** The Termux-managed OpenCode server's URL (Flutter `TermuxBridge.managedServerUrl`). */
public const val TERMUX_MANAGED_SERVER_URL: String = "http://127.0.0.1:4096"

/** The in-app server's port (Flutter `BuiltinLinux.serverPort`); Termux's is 4096, so both can run at once. */
public const val BUILTIN_SERVER_PORT: Int = 4097

/** Flutter `BuiltinLinux.managesServerUrl`: plain http to 127.0.0.1 on the in-app port. */
public fun builtinManagesServerUrl(value: String?): Boolean {
    val uri = try {
        value?.let(::URI)
    } catch (_: Exception) {
        null
    } ?: return false
    if (uri.scheme != "http") return false
    val port = if (uri.port == -1) 80 else uri.port
    return uri.host == "127.0.0.1" && port == BUILTIN_SERVER_PORT
}

/** Flutter `TermuxMigrationService.eligible`: the Termux-managed OpenCode server. */
public fun termuxMigrationEligible(profile: MigrationProfile): Boolean = profile.openCode && profile.baseUrl == TERMUX_MANAGED_SERVER_URL

/**
 * Finishes an already verified import without retiring its Termux profile (Flutter TermuxMigrationProfileSwitcher).
 * Session IDs are not portable: pins, drafts and queued prompts stay with their original profile.
 */
public class TermuxMigrationProfileSwitcher(private val profiles: MigrationProfiles) : MigrationProfileSwitch {
    private var switching = false

    /**
     * [verifiedProjectPaths] maps imported source roots to their verified `/root/projects` destinations; the caller
     * finished archive verification first. No other source location follows the switch.
     */
    override suspend fun switchProfile(
        sourceProfileId: String,
        stillWanted: () -> Boolean,
        verifiedProjectPaths: Map<String, String>,
    ): String {
        if (switching) fail(TermuxMigrationFailure.profileSwitch)
        switching = true
        var source: MigrationProfile? = null
        var destination: MigrationProfile? = null
        val selectedBefore = profiles.selectedProfileId()
        try {
            wanted(stillWanted)
            source = profiles.profile(sourceProfileId)
            if (source == null || !termuxMigrationEligible(source)) fail(TermuxMigrationFailure.invalidSelection)
            val mapped = mappedDirectory(profiles.locationDirectory(source.id), verifiedProjectPaths)
            destination = profiles.ensureBuiltinProfile(source.flavor)
            wanted(stillWanted)
            if (destination.id == source.id ||
                !destination.openCode ||
                destination.flavor != source.flavor ||
                !builtinManagesServerUrl(destination.baseUrl) ||
                profiles.profile(source.id) == null ||
                profiles.profile(destination.id) == null
            ) {
                fail(TermuxMigrationFailure.profileSwitch)
            }
            if (mapped != null) profiles.setLocationDirectory(destination.id, mapped)
            wanted(stillWanted)
            if (!profiles.startBuiltin(destination, stillWanted)) {
                wanted(stillWanted)
                fail(TermuxMigrationFailure.profileSwitch)
            }
            wanted(stillWanted)
            if (profiles.selectedProfileId() != selectedBefore) fail(TermuxMigrationFailure.profileSwitch)
            profiles.connect(destination)
            wanted(stillWanted)
            if (!profiles.isConnectedTo(destination)) fail(TermuxMigrationFailure.profileSwitch)
            return destination.id
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            // Restore only a selection this operation displaced. A simultaneous user selection wins. Never remove either
            // profile on a partial switch.
            val from = source
            if (from != null && destination != null && selectedBefore == from.id &&
                profiles.selectedProfileId() == destination.id && profiles.profile(from.id) != null
            ) {
                try {
                    profiles.connect(from)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The saved source stays available even if it cannot reconnect.
                }
            }
            if (error is TermuxMigrationException) throw error
            fail(TermuxMigrationFailure.profileSwitch)
        } finally {
            switching = false
        }
    }

    private fun wanted(stillWanted: () -> Boolean) {
        if (!stillWanted()) fail(TermuxMigrationFailure.cancelled)
    }

    private fun absolutePath(path: String): Boolean =
        path.startsWith("/") && '\u0000' !in path && '\\' !in path &&
            path.split('/').drop(1).none { it.isEmpty() || it == "." || it == ".." }

    private fun mappedDirectory(directory: String?, mappings: Map<String, String>): String? {
        for ((root, target) in mappings) {
            if (!absolutePath(root) || !absolutePath(target) || !(target == "/root/projects" || target.startsWith("/root/projects/"))) {
                fail(TermuxMigrationFailure.invalidSelection)
            }
        }
        if (directory == null || !absolutePath(directory)) return null
        for (root in mappings.keys.sortedByDescending { it.length }) {
            if (directory == root || directory.startsWith("$root/")) return mappings.getValue(root) + directory.substring(root.length)
        }
        return null
    }
}
