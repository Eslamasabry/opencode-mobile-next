package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

internal const val PKG = "io.github.eslamasabry.opencode_mobile"
internal const val SIGNER = "AA11"
internal const val GOOD_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
private const val DL = "https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v2.2.0%2B58"

internal fun releaseJson(
    tag: String = "v2.2.0+58",
    draft: Boolean = false,
    prerelease: Boolean = false,
    body: String = "Faster chats. Also fixes.\n\n- item",
): String =
    """
    {"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"body":${quote(body)},
     "html_url":"https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/$tag",
     "assets":[
       {"name":"opencode-mobile-2.2.0-arm64-v8a.apk","browser_download_url":"$DL/opencode-mobile-2.2.0-arm64-v8a.apk","size":2000},
       {"name":"opencode-mobile-2.2.0-armeabi-v7a.apk","browser_download_url":"$DL/opencode-mobile-2.2.0-armeabi-v7a.apk","size":1900},
       {"name":"opencode-mobile-2.2.0.apk","browser_download_url":"$DL/opencode-mobile-2.2.0.apk","size":4000},
       {"name":"SHA256SUMS","browser_download_url":"$DL/SHA256SUMS","size":300}
     ]}
    """.trimIndent()

private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

internal class FakeFeed : UpdateFeed {
    var latestBody: Outcome<String> = releaseJson().ok()
    var sumsHash = GOOD_HASH
    var latestCalls = 0
    var downloads = 0
    var downloadedUrl: String? = null
    var hold: CompletableDeferred<Unit>? = null
    val written = mutableSetOf<String>()

    override suspend fun latest(): Outcome<String> {
        latestCalls++
        return latestBody
    }

    override suspend fun text(url: String): Outcome<String> =
        ("$sumsHash  opencode-mobile-2.2.0-arm64-v8a.apk\n" +
            "$sumsHash *opencode-mobile-2.2.0-armeabi-v7a.apk\n" +
            "$sumsHash  opencode-mobile-2.2.0.apk\n").ok()

    override suspend fun download(url: String, dest: String, onProgress: (Long, Long) -> Unit): Outcome<Unit> {
        downloads++
        downloadedUrl = url
        onProgress(1000, 2000)
        hold?.await()
        onProgress(2000, 2000)
        written += dest
        return Unit.ok()
    }
}

internal class FakePlatform : UpdatePlatform {
    var build = 57
    var abis = listOf("arm64-v8a", "armeabi-v7a")
    var hash = GOOD_HASH
    var signer = SIGNER
    var pkg = PKG
    var versionCode = 58L
    var canInstallNow = true
    var permissionOpened = 0
    val installs = mutableListOf<String>()
    var events = listOf(InstallEvent(InstallState.PendingUser), InstallEvent(InstallState.Success))
    val deleted = mutableListOf<String>()
    var cleared = 0
    val files = mutableSetOf<String>()

    override suspend fun installed() = InstalledApp(PKG, "2.1.0", build)

    override suspend fun supportedAbis() = abis

    override suspend fun updateDir() = "/cache/app-update"

    override suspend fun sha256(path: String): Outcome<String> {
        files += path
        return hash.ok()
    }

    override suspend fun exists(path: String) = path in files

    override suspend fun delete(path: String) {
        deleted += path
        files -= path
    }

    override suspend fun clearUpdateDir() {
        cleared++
        files.clear()
    }

    override suspend fun inspectApk(path: String) = ApkInfo(pkg, versionCode, signer)

    override suspend fun ownSignerSha256() = SIGNER

    override suspend fun canInstall() = canInstallNow

    override fun openInstallPermission() {
        permissionOpened++
    }

    override fun install(path: String): Flow<InstallEvent> {
        installs += path
        return events.asFlow()
    }
}

internal class FakeStore(var record: UpdateRecord = UpdateRecord()) : UpdateStore {
    override suspend fun read() = record

    override suspend fun write(record: UpdateRecord): Outcome<Unit> {
        this.record = record
        return Unit.ok()
    }
}

internal val offline: Outcome<String> = Outcome.Failed(Failure.Network(NetworkWhy.Offline))
