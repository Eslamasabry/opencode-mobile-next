package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.Flow

/** What is installed now (package, versionName, versionCode). */
public data class InstalledApp(val packageName: String, val version: String, val build: Int)

/** What Android says about a downloaded APK. */
public data class ApkInfo(val packageName: String, val versionCode: Long, val signerSha256: String)

public enum class InstallState { PendingUser, Success, Failure, Aborted }

/** One PackageInstaller answer; [message] is Android's own words, shown only under Details. */
public data class InstallEvent(val state: InstallState, val message: String? = null)

/**
 * The network half (bound by :androidApp). Every call is HTTPS to an address the engine already checked with
 * [isTrustedReleaseFileUrl] (except [latest], the fixed feed address).
 */
public interface UpdateFeed {
    /** The JSON body of the newest release (the feed address). */
    public suspend fun latest(): Outcome<String>

    /** A small text file of a release (`SHA256SUMS`). */
    public suspend fun text(url: String): Outcome<String>

    /**
     * Fetches [url] into [dest], resuming `"$dest.part"` from where it stopped (HTTP Range; 416 starts over).
     * [onProgress] gets (received, total) with total -1 when unknown. Stopped by coroutine cancellation.
     */
    public suspend fun download(url: String, dest: String, onProgress: (received: Long, total: Long) -> Unit): Outcome<Unit>
}

/** The device half (bound by :androidApp): files, the APK reader and PackageInstaller. */
public interface UpdatePlatform {
    public suspend fun installed(): InstalledApp

    public suspend fun supportedAbis(): List<String>

    /** The folder the update file goes in (app cache, `app-update`). */
    public suspend fun updateDir(): String

    /** Lower-case hex SHA-256 of the file, or a failure when it cannot be read. */
    public suspend fun sha256(path: String): Outcome<String>

    public suspend fun exists(path: String): Boolean

    public suspend fun delete(path: String)

    /** Drops the whole update folder (cache only). */
    public suspend fun clearUpdateDir()

    /** Null when Android cannot read the file as an app. */
    public suspend fun inspectApk(path: String): ApkInfo?

    /** This app's signer, upper-case hex SHA-256; several signers sorted and joined with a comma. */
    public suspend fun ownSignerSha256(): String

    public suspend fun canInstall(): Boolean

    /** Opens Android's "Install unknown apps" page for this app. */
    public fun openInstallPermission()

    /** Starts the install; Android shows its own confirm dialog. The flow completes after its last event. */
    public fun install(path: String): Flow<InstallEvent>
}

/** What the engine keeps across restarts (`oc.appUpdate.*`, app-wide, not per profile). */
public data class UpdateRecord(val enabled: Boolean = true, val lastCheckMs: Long? = null, val releaseJson: String? = null)

public interface UpdateStore {
    public suspend fun read(): UpdateRecord

    public suspend fun write(record: UpdateRecord): Outcome<Unit>
}
