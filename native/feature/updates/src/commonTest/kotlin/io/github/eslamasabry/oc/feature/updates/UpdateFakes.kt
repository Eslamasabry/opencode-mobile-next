package io.github.eslamasabry.oc.feature.updates

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.updates.ApkInfo
import io.github.eslamasabry.oc.engine.updates.AppRelease
import io.github.eslamasabry.oc.engine.updates.AppUpdates
import io.github.eslamasabry.oc.engine.updates.InstallEvent
import io.github.eslamasabry.oc.engine.updates.InstallState
import io.github.eslamasabry.oc.engine.updates.InstalledApp
import io.github.eslamasabry.oc.engine.updates.UpdateFeed
import io.github.eslamasabry.oc.engine.updates.UpdatePlatform
import io.github.eslamasabry.oc.engine.updates.UpdateRecord
import io.github.eslamasabry.oc.engine.updates.UpdateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock

const val PACKAGE = "io.github.eslamasabry.opencode_mobile"
const val OWN_SIGNER = "AA11"
val GOOD_HASH = "a".repeat(64)
const val APK_SIZE = 40_000_000L
const val PAGE_URL = "https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v2.2.0+58"
private const val FILES = "https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.2.0+58"

/** GitHub's latest-release reply for 2.2.0+58: one universal APK and SHA256SUMS. */
val RELEASE_JSON =
    """
    {"tag_name":"v2.2.0+58","draft":false,"prerelease":false,
     "body":"This release is faster.\n\n- Smaller downloads",
     "html_url":"$PAGE_URL",
     "assets":[
       {"name":"app-release.apk","browser_download_url":"$FILES/app-release.apk","size":$APK_SIZE},
       {"name":"SHA256SUMS","browser_download_url":"$FILES/SHA256SUMS","size":100}
     ]}
    """.trimIndent()

val RELEASE: AppRelease = AppRelease.parse(RELEASE_JSON)!!

val INSTALLED = InstalledApp(PACKAGE, "2.1.0", 57)

/** The network half: answers from fields; a download can be held open with [gate]. */
class FakeUpdateFeed : UpdateFeed {
    /** Null: the feed says nothing newer (an unreadable reply parses to no release). */
    var latestJson: String? = RELEASE_JSON
    var latestCalls = 0
    var downloads = 0
    var gate: CompletableDeferred<Unit>? = null
    var downloadFailure: Failure? = null

    override suspend fun latest(): Outcome<String> {
        latestCalls++
        return Outcome.Ok(latestJson ?: "{}")
    }

    override suspend fun text(url: String): Outcome<String> = Outcome.Ok("$GOOD_HASH  app-release.apk\n")

    override suspend fun download(url: String, dest: String, onProgress: (received: Long, total: Long) -> Unit): Outcome<Unit> {
        downloads++
        onProgress(APK_SIZE * 42 / 100, APK_SIZE)
        gate?.await()
        downloadFailure?.let { return Outcome.Failed(it) }
        return Outcome.Ok(Unit)
    }
}

/** The device half: files by name, the APK reader and PackageInstaller, all from fields. */
class FakeUpdatePlatform : UpdatePlatform {
    var hash: String = GOOD_HASH
    var signer: String = OWN_SIGNER
    var apkVersion: Long = 58
    var allowed = true
    val deleted = mutableListOf<String>()
    val installs = mutableListOf<String>()
    var permissionAsks = 0

    override suspend fun installed(): InstalledApp = INSTALLED

    override suspend fun supportedAbis(): List<String> = listOf("arm64-v8a")

    override suspend fun updateDir(): String = "/cache/app-update"

    override suspend fun sha256(path: String): Outcome<String> = Outcome.Ok(hash)

    override suspend fun exists(path: String): Boolean = path !in deleted

    override suspend fun delete(path: String) {
        deleted += path
    }

    override suspend fun clearUpdateDir() = Unit

    override suspend fun inspectApk(path: String): ApkInfo = ApkInfo(PACKAGE, apkVersion, signer)

    override suspend fun ownSignerSha256(): String = OWN_SIGNER

    override suspend fun canInstall(): Boolean = allowed

    override fun openInstallPermission() {
        permissionAsks++
    }

    override fun install(path: String): Flow<InstallEvent> {
        installs += path
        return flowOf(InstallEvent(InstallState.PendingUser))
    }
}

class FakeUpdateStore(var record: UpdateRecord = UpdateRecord()) : UpdateStore {
    var writes = 0

    override suspend fun read(): UpdateRecord = record

    override suspend fun write(record: UpdateRecord): Outcome<Unit> {
        writes++
        this.record = record
        return Outcome.Ok(Unit)
    }
}

/** A real engine over the fakes. */
class UpdateRig(val clock: Clock, supported: Boolean = true) {
    val feed = FakeUpdateFeed()
    val platform = FakeUpdatePlatform()
    val store = FakeUpdateStore()
    val updates = AppUpdates(feed, platform, store, clock, supported)
}
