package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Where the app's own update is (Flutter `AppUpdatePhase`, without the code-push patch phase: it ends at the switch). */
public enum class UpdatePhase { None, Checking, Available, Downloading, Verifying, ReadyToInstall, Installing, Failed }

/** Why an update stopped. The words people read come from the UI; the generated technical text is [AppUpdateState.detail]. */
public enum class UpdateFailure { Network, NoFile, Checksum, Signer, Package, Version, Storage, Installer }

/** Everything the notice, the gear badge, the offer sheet and the Updates page read. */
public data class AppUpdateState(
    /** False where the app cannot install its own update: nothing is ever claimed (B-ANDR-094). */
    val supported: Boolean = true,
    val phase: UpdatePhase = UpdatePhase.None,
    val release: AppRelease? = null,
    val failure: UpdateFailure? = null,
    /** Generated technical text for the Details fold (hashes, package names, Android's installer words); never copy. */
    val detail: String? = null,
    /** 0..1 while downloading; null when the size is unknown. */
    val progress: Float? = null,
    val receivedBytes: Long? = null,
    /** Bytes of the file this phone would download, once known. */
    val downloadSize: Long? = null,
    val enabled: Boolean = true,
    val needsPermission: Boolean = false,
    val checkedAt: Instant? = null,
    val installed: InstalledApp? = null,
    val notNow: Boolean = false,
    val pageOpen: Boolean = false,
) {
    /** A newer release is known and not installed: drives the gear badge and stays on after Not now. */
    public val hasPendingRelease: Boolean get() = supported && release != null

    public val busy: Boolean
        get() =
            phase == UpdatePhase.Checking || phase == UpdatePhase.Downloading ||
                phase == UpdatePhase.Verifying || phase == UpdatePhase.Installing

    /**
     * The status line offers or tracks the update. Not now silences only the offer; work the person started keeps its
     * line. While the Updates page is open its own row is the one place it shows.
     */
    public val noticeVisible: Boolean
        get() {
            if (!supported || pageOpen) return false
            return when (phase) {
                UpdatePhase.Available, UpdatePhase.ReadyToInstall -> !notNow
                UpdatePhase.Downloading, UpdatePhase.Verifying, UpdatePhase.Installing -> true
                else -> false
            }
        }

    /** The same file would be refused again: send the person to the release page instead of Try again. */
    public val retryHelps: Boolean
        get() = failure != UpdateFailure.Signer && failure != UpdateFailure.Package && failure != UpdateFailure.Version
}

/**
 * The one app-wide holder of the app's own update (port of Flutter `AppUpdateController`,
 * docs/design/app-updates-2026-10-31.md). Checks GitHub at most once every six hours (start-up and resume), offers the
 * newer release, downloads the file for this phone with resume, verifies SHA256SUMS, package, signer and versionCode,
 * then hands it to Android's installer, which always asks the person. A file that does not match is deleted and never
 * installed (B-MIGR-012, B-ANDR-094).
 */
public class AppUpdates(
    private val feed: UpdateFeed,
    private val platform: UpdatePlatform,
    private val store: UpdateStore,
    private val clock: Clock,
    supported: Boolean = true,
    private val feedOverride: String = "",
) {
    private val mutable = MutableStateFlow(AppUpdateState(supported = supported))
    public val state: StateFlow<AppUpdateState> = mutable.asStateFlow()

    private var record = UpdateRecord()
    private var started = false
    private var abis: List<String> = emptyList()
    private var verifiedPath: String? = null
    private var download: Job? = null

    private val s: AppUpdateState get() = mutable.value
    private val log = AppLog.tagged("Updates")

    private inline fun set(change: (AppUpdateState) -> AppUpdateState) = mutable.update(change)

    /** Loads the saved choice and the release found last time, then checks when a check is due. */
    public suspend fun start(): Outcome<Unit> {
        if (!s.supported || started) return Unit.ok()
        started = true
        record = store.read()
        val app = platform.installed()
        var release: AppRelease? = null
        val saved = record.releaseJson
        if (saved != null) {
            val parsed = AppRelease.parse(saved)
            if (parsed != null && parsed.build > app.build) {
                release = parsed
            } else {
                // Installed (or older, or unreadable): the finding and its download are done.
                record = record.copy(releaseJson = null)
                store.write(record)
                platform.clearUpdateDir()
            }
        }
        if (release != null) abis = platform.supportedAbis()
        set {
            it.copy(
                enabled = record.enabled,
                checkedAt = record.lastCheckMs?.let(Instant::fromEpochMilliseconds),
                installed = app,
                release = release,
                downloadSize = release?.apkFor(abis)?.size,
                phase = if (release != null) UpdatePhase.Available else UpdatePhase.None,
            )
        }
        return checkIfDue()
    }

    public suspend fun setEnabled(on: Boolean): Outcome<Unit> {
        record = record.copy(enabled = on)
        set { it.copy(enabled = on) }
        val saved = store.write(record)
        if (saved is Outcome.Failed) return saved
        return if (on) checkIfDue() else Unit.ok()
    }

    /** Start-up and resume: asks GitHub at most once every six hours. */
    public suspend fun checkIfDue(): Outcome<Unit> {
        if (!s.supported || !s.enabled || s.busy) return Unit.ok()
        val last = s.checkedAt
        if (last != null && clock.now() - last < CHECK_INTERVAL) return Unit.ok()
        return check()
    }

    /** Check now: asks regardless of the clock. */
    public suspend fun checkNow(): Outcome<Unit> {
        if (!s.supported || s.busy) return Unit.ok()
        return check()
    }

    private suspend fun check(): Outcome<Unit> {
        val before = s.phase
        val app = s.installed ?: platform.installed()
        set { it.copy(phase = UpdatePhase.Checking, failure = null, detail = null, installed = app) }
        val body = feed.latest()
        if (body is Outcome.Failed) {
            // Look again sooner than six hours, but not on every resume. A failed check is not a failed update.
            stamp(clock.now() - (CHECK_INTERVAL - RETRY_AFTER_FAILURE))
            set {
                it.copy(
                    phase = if (before == UpdatePhase.Checking) UpdatePhase.None else before,
                    detail = shape(body.failure),
                )
            }
            return body
        }
        stamp(clock.now())
        abis = platform.supportedAbis()
        val found = AppRelease.parse((body as Outcome.Ok).value)
        if (found != null && found.build > app.build) {
            val sameTag = s.release?.tag == found.tag
            if (!sameTag) verifiedPath = null
            record = record.copy(releaseJson = found.json)
            store.write(record)
            set {
                it.copy(
                    release = found,
                    notNow = if (sameTag) it.notNow else false,
                    downloadSize = found.apkFor(abis)?.size,
                    phase = UpdatePhase.Available,
                )
            }
        } else {
            record = record.copy(releaseJson = null)
            store.write(record)
            set { it.copy(release = null, downloadSize = null, phase = UpdatePhase.None) }
        }
        return Unit.ok()
    }

    private suspend fun stamp(at: Instant) {
        record = record.copy(lastCheckMs = at.toEpochMilliseconds())
        set { it.copy(checkedAt = at) }
        store.write(record)
    }

    /** Not now: hides the offer for this version until the next app start. The gear badge stays. */
    public fun notNow() {
        set { it.copy(notNow = true) }
    }

    /** The Updates page is open: the app-wide notice steps aside. */
    public fun setPageOpen(open: Boolean) {
        set { it.copy(pageOpen = open) }
    }

    /** Stops a running download; the phase goes back to Available. */
    public fun cancel() {
        download?.cancel()
    }

    /**
     * Update now: download, check, install. Every failing step says why and stops; a file that does not match is
     * deleted and never installed. A file already checked is reused (Try again after the dialog was dismissed).
     */
    public suspend fun updateNow(): Outcome<Unit> {
        val release = s.release
        if (!s.supported || release == null || s.busy) return Unit.ok()
        set { it.copy(failure = null, detail = null, progress = null, receivedBytes = null) }
        val kept = verifiedPath
        if (kept != null && platform.exists(kept)) return install(kept)
        verifiedPath = null
        var outcome: Outcome<Unit> = Unit.ok()
        coroutineScope {
            val job = launch { outcome = fetchAndVerify(release) }
            download = job
            job.join()
            download = null
            if (job.isCancelled && s.phase != UpdatePhase.Failed) {
                set { it.copy(phase = UpdatePhase.Available, progress = null, receivedBytes = null) }
            }
        }
        return outcome
    }

    private suspend fun fetchAndVerify(release: AppRelease): Outcome<Unit> {
        abis = platform.supportedAbis()
        val asset = release.apkFor(abis)
        val sums = release.checksums
        if (asset == null || sums == null) return fail(UpdateFailure.NoFile, "no file for this phone")
        if (!isTrustedReleaseFileUrl(asset.url, feedOverride) || !isTrustedReleaseFileUrl(sums.url, feedOverride)) {
            return fail(UpdateFailure.NoFile, "untrusted release file address")
        }
        set { it.copy(phase = UpdatePhase.Downloading, downloadSize = asset.size) }
        val sumsText =
            when (val text = feed.text(sums.url)) {
                is Outcome.Failed -> return fail(UpdateFailure.Network, shape(text.failure))
                is Outcome.Ok -> text.value
            }
        val expected = parseSha256Sums(sumsText)[asset.name] ?: return fail(UpdateFailure.Checksum, "no checksum for ${asset.name}")
        val file = platform.updateDir().trimEnd('/') + "/" + asset.name
        val got =
            feed.download(asset.url, file) { received, total ->
                val size = if (total > 0) total else asset.size
                set {
                    it.copy(
                        receivedBytes = received,
                        progress = if (size > 0) (received.toFloat() / size).coerceIn(0f, 1f) else null,
                    )
                }
            }
        when (got) {
            is Outcome.Failed ->
                return fail(
                    if (got.failure is Failure.StorageRefused) UpdateFailure.Storage else UpdateFailure.Network,
                    shape(got.failure),
                )
            is Outcome.Ok -> Unit
        }
        set { it.copy(phase = UpdatePhase.Verifying) }
        val actual =
            when (val hash = platform.sha256(file)) {
                is Outcome.Failed -> return fail(UpdateFailure.Storage, shape(hash.failure))
                is Outcome.Ok -> hash.value.lowercase()
            }
        if (actual != expected) {
            platform.delete(file)
            return fail(UpdateFailure.Checksum, "sha256 $actual, expected $expected")
        }
        val info = platform.inspectApk(file)
        if (info == null) {
            platform.delete(file)
            return fail(UpdateFailure.Package, "Android cannot read this file as an app")
        }
        val app = s.installed ?: platform.installed()
        if (info.packageName != app.packageName) {
            platform.delete(file)
            return fail(UpdateFailure.Package, "package ${info.packageName}, expected ${app.packageName}")
        }
        val own = platform.ownSignerSha256()
        if (own.isEmpty() || !info.signerSha256.equals(own, ignoreCase = true)) {
            platform.delete(file)
            return fail(UpdateFailure.Signer, "signer ${info.signerSha256}, expected $own")
        }
        if (info.versionCode <= app.build) {
            platform.delete(file)
            return fail(UpdateFailure.Version, "versionCode ${info.versionCode} is not above ${app.build}")
        }
        verifiedPath = file
        return install(file)
    }

    private suspend fun install(path: String): Outcome<Unit> {
        if (!platform.canInstall()) {
            // Ask once, plainly; back in the app, onResumed carries on.
            set { it.copy(needsPermission = true, phase = UpdatePhase.ReadyToInstall) }
            platform.openInstallPermission()
            return Unit.ok()
        }
        set { it.copy(needsPermission = false, phase = UpdatePhase.Installing) }
        var result: Outcome<Unit> = Unit.ok()
        platform.install(path).collect { event ->
            when (event.state) {
                InstallState.PendingUser -> Unit
                InstallState.Success -> set { it.copy(phase = UpdatePhase.Installing) }
                InstallState.Aborted -> set { it.copy(phase = UpdatePhase.ReadyToInstall) }
                InstallState.Failure -> result = fail(UpdateFailure.Installer, event.message)
            }
        }
        return result
    }

    /** Back in the app: continues an install that waited for the permission, then checks when due. */
    public suspend fun onResumed(): Outcome<Unit> {
        if (!s.supported) return Unit.ok()
        val path = verifiedPath
        if (s.phase == UpdatePhase.ReadyToInstall && s.needsPermission && path != null && platform.canInstall()) {
            return install(path)
        }
        return checkIfDue()
    }

    private fun fail(why: UpdateFailure, detail: String?): Outcome<Unit> {
        log.i { "update stopped: $why" }
        set { it.copy(phase = UpdatePhase.Failed, failure = why, detail = detail, progress = null) }
        return Outcome.Failed(Failure.Unexpected())
    }

    /** Failure kind and status only: never raw exception text (rule 5). */
    private fun shape(failure: Failure): String = failure.details?.text ?: (failure::class.simpleName ?: "Failure")

    public companion object {
        public val CHECK_INTERVAL: Duration = 6.hours
        public val RETRY_AFTER_FAILURE: Duration = 30.minutes
    }
}
