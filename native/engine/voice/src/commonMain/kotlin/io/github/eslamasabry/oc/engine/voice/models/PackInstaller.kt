package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A pack whose marker and stamp check passed (plan 10 §4.6). [path] = absolute path of a file role. */
public data class InstalledPack(val spec: ModelPackSpec, val dir: String) {
    public fun path(role: String): String = "$dir/${spec.file(role).name}"
}

public sealed interface InstallProgress {
    public data class Downloading(val received: Long, val total: Long) : InstallProgress

    public data object Verifying : InstallProgress
}

/** Thrown by a [ModelFiles] write when the disk is full (Android maps `ENOSPC`); becomes `DownloadNoSpace`. */
public class StorageFullException : RuntimeException("storage full")

/**
 * Downloads, verifies and publishes one pack (plan 10 §7.4, B-MEDIA-012, 025, 026, 027), ported from Dart
 * `VoiceModelDownloader` with the native choices M3 (Cancel keeps the `.part` files) and M4 (verified stamp).
 *
 * Layout under [root] (= `files/voice_models`, read in place): `<pack>-<rev>/<file>`, `.installed.json`,
 * `.native-verified.json`; staging `<pack>-<rev>.installing/<file>.part`; `<file>.voice-backup` during a replace.
 * One [Mutex] per pack directory: same-pack installs and removes serialize, and a remove waits for an install.
 */
public class PackInstaller(
    private val root: String,
    private val files: ModelFiles,
    private val fetcher: ModelFetcher,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val digests: DigestFactory = DigestFactory.Pure,
    private val stepTimeout: Duration = 15.seconds,
    private val idleTimeout: Duration = 20.seconds,
) {
    private val log = AppLog.tagged("voice.install")
    private val locksGuard = Mutex()
    private val locks = mutableMapOf<String, Mutex>()

    public fun packDir(spec: ModelPackSpec): String = "$root/${spec.dirName}"

    public fun stagingDir(spec: ModelPackSpec): String = "$root/${spec.dirName}.installing"

    private fun partPath(spec: ModelPackSpec, file: ModelFile) = "${stagingDir(spec)}/${file.name}.part"

    private fun markerPath(spec: ModelPackSpec) = "${packDir(spec)}/${InstalledMarker.NAME}"

    private fun stampPath(spec: ModelPackSpec) = "${packDir(spec)}/${VerifiedStamp.NAME}"

    private suspend fun lockOf(spec: ModelPackSpec): Mutex = locksGuard.withLock { locks.getOrPut(packDir(spec)) { Mutex() } }

    /** True while an install or remove of [spec] holds its lock. */
    public suspend fun isBusy(spec: ModelPackSpec): Boolean = lockOf(spec).isLocked

    /** Bytes already in the pack's `.part` files (each capped at its pinned length), for resume and the storage check. */
    public suspend fun partialBytes(spec: ModelPackSpec): Long =
        withContext(dispatchers.io) {
            spec.files.sumOf { file -> (files.size(partPath(spec, file)) ?: 0L).coerceAtMost(file.bytes) }
        }

    /**
     * The marker matches and the stamp matches every file's length and head/tail hashes. A missing or stale stamp runs
     * one full SHA-256 pass and rewrites it; a mismatching file deletes the marker and the stamp (B-MEDIA-025).
     */
    public suspend fun installed(spec: ModelPackSpec): InstalledPack? =
        withContext(dispatchers.io) {
            val marker = readMarker(spec) ?: return@withContext null
            if (marker != InstalledMarker.of(spec)) {
                invalidate(spec)
                return@withContext null
            }
            if (spec.files.any { files.size(fileIn(spec, it)) != it.bytes }) {
                invalidate(spec)
                return@withContext null
            }
            if (stampMatches(spec)) return@withContext InstalledPack(spec, packDir(spec))
            if (!verifyFilesFully(spec)) {
                invalidate(spec)
                return@withContext null
            }
            writeStamp(spec)
            InstalledPack(spec, packDir(spec))
        }

    /** The full check after a recognizer load failure (plan 10 §6.3): false deletes the marker (pack becomes Missing). */
    public suspend fun verifyFully(spec: ModelPackSpec): Boolean =
        withContext(dispatchers.io) {
            val ok = readMarker(spec) == InstalledMarker.of(spec) && verifyFilesFully(spec)
            if (ok) writeStamp(spec) else invalidate(spec)
            ok
        }

    /** Downloads (resuming kept `.part` files), verifies twice, publishes with rollback, then writes the marker. */
    public suspend fun install(
        spec: ModelPackSpec,
        replaceExisting: Boolean = false,
        onProgress: (InstallProgress) -> Unit = {},
    ): Outcome<InstalledPack> =
        lockOf(spec).withLock {
            if (!replaceExisting) installed(spec)?.let { return@withLock it.ok() }
            var phase = Phase.Downloading
            try {
                withContext(dispatchers.io) { files.createDirectories(stagingDir(spec)) }
                val progress = ProgressThrottle(spec.totalBytes, clock, onProgress)
                var before = 0L
                for (file in spec.files) {
                    val result = downloadFile(spec, file) { received -> progress.report(before + received) }
                    if (result is Outcome.Failed) return@withLock result
                    before += file.bytes
                }
                phase = Phase.Verifying
                progress.verifying()
                for (file in spec.files) {
                    if (!verifyStaged(spec, file)) {
                        withContext(dispatchers.io) { files.delete(partPath(spec, file)) }
                        return@withLock media(MediaProblem.DownloadChecksum)
                    }
                }
                // Second pass right before publishing: nothing changed the staged bytes in between.
                if (spec.files.any { !verifyStaged(spec, it) }) {
                    withContext(NonCancellable + dispatchers.io) { cleanStaging(spec) }
                    return@withLock media(MediaProblem.DownloadVerification)
                }
                phase = Phase.Publishing
                val published = publish(spec)
                if (published is Outcome.Failed) return@withLock published
                withContext(NonCancellable + dispatchers.io) { cleanAfterPublish(spec) }
                InstalledPack(spec, packDir(spec)).ok()
            } catch (e: CancellationException) {
                // M3: a cancel while downloading keeps the .part files; later phases clean the staged files.
                if (phase != Phase.Downloading) withContext(NonCancellable + dispatchers.io) { cleanStaging(spec) }
                throw e
            } catch (e: StorageFullException) {
                media(MediaProblem.DownloadNoSpace, e)
            }
        }

    /** Deletes every file of the pack: the model files, markers, staged parts, backups and temporaries. */
    public suspend fun remove(spec: ModelPackSpec): Outcome<Unit> =
        lockOf(spec).withLock {
            attemptIo {
                files.deleteTree(packDir(spec))
                files.deleteTree(stagingDir(spec))
            }
        }

    /** Deletes only `<pack>.installing/` (Settings "Discard 120 MB"). */
    public suspend fun discardPartial(spec: ModelPackSpec): Outcome<Unit> =
        lockOf(spec).withLock { attemptIo { files.deleteTree(stagingDir(spec)) } }

    // ---- download one file

    private suspend fun downloadFile(spec: ModelPackSpec, file: ModelFile, progress: (Long) -> Unit): Outcome<Unit> {
        val part = partPath(spec, file)
        var offset = withContext(dispatchers.io) { files.size(part) ?: 0L }
        if (offset > file.bytes) {
            withContext(dispatchers.io) { files.delete(part) }
            offset = 0
        }
        if (offset == file.bytes) {
            progress(offset)
            return Unit.ok() // already complete: no request, straight to verify
        }
        val response =
            when (val opened = openFollowingRedirects(spec.urlFor(file), offset.takeIf { it > 0 })) {
                is Outcome.Failed -> return opened
                is Outcome.Ok -> opened.value
            }
        try {
            val expectedRange = "bytes $offset-${file.bytes - 1}/${file.bytes}"
            if (offset > 0 && response.status == 206 && response.contentRange == expectedRange) {
                // resume: append
            } else if (response.status == 200) {
                withContext(dispatchers.io) { files.delete(part) }
                offset = 0
            } else {
                return media(MediaProblem.DownloadHttp, status = response.status)
            }
            val remaining = file.bytes - offset
            val declared = response.contentLength
            if (declared != null && declared != remaining) return media(MediaProblem.DownloadLength)
            progress(offset)
            val sink = withContext(dispatchers.io) { files.openAppend(part) }
            try {
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = readWithIdleTimeout(response, buffer) ?: return media(MediaProblem.DownloadTimedOut)
                    if (n < 0) break
                    if (offset + n > file.bytes) return media(MediaProblem.DownloadLength) // past the pin: abort now
                    withContext(dispatchers.io) { sink.write(buffer, 0, n) }
                    offset += n
                    progress(offset)
                }
                withContext(dispatchers.io) { sink.flush() }
            } catch (e: FetchFailure) {
                return media(e.problem, e)
            } finally {
                withContext(NonCancellable + dispatchers.io) { sink.close() }
            }
            if (offset != file.bytes) return media(MediaProblem.DownloadIncomplete)
            return Unit.ok()
        } finally {
            response.close()
        }
    }

    private suspend fun readWithIdleTimeout(response: FetchResponse, buffer: ByteArray): Int? =
        try {
            withTimeout(idleTimeout) { response.read(buffer) }
        } catch (_: TimeoutCancellationException) {
            null
        }

    /** HTTPS only; at most 5 redirects, each hop `https` (plan 10 §7.4 "Scheme"). */
    private suspend fun openFollowingRedirects(firstUrl: String, rangeStart: Long?): Outcome<FetchResponse> {
        var url = firstUrl
        var hops = 0
        while (true) {
            if (!url.startsWith("https://")) {
                return media(if (hops == 0) MediaProblem.DownloadHttpsRequired else MediaProblem.DownloadUnsafeRedirect)
            }
            val opened =
                try {
                    withTimeout(stepTimeout) { fetcher.open(FetchRequest(url, rangeStart)) }
                } catch (_: TimeoutCancellationException) {
                    return media(MediaProblem.DownloadTimedOut)
                }
            val response =
                when (opened) {
                    is Outcome.Failed -> return opened
                    is Outcome.Ok -> opened.value
                }
            if (response.status !in 300..399) return response.ok()
            response.close()
            hops++
            val next = response.location?.let { resolveLocation(url, it) }
            if (next == null || hops > MAX_REDIRECTS) return media(MediaProblem.DownloadRedirect)
            url = next
        }
    }

    // ---- verify

    private suspend fun verifyStaged(spec: ModelPackSpec, file: ModelFile): Boolean =
        withContext(dispatchers.io) { hashMatches(partPath(spec, file), file) }

    private suspend fun verifyFilesFully(spec: ModelPackSpec): Boolean = spec.files.all { hashMatches(fileIn(spec, it), it) }

    private suspend fun hashMatches(path: String, file: ModelFile): Boolean {
        if (files.size(path) != file.bytes) return false
        val digest = digests.sha256()
        val source = files.openRead(path)
        try {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = source.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        } finally {
            source.close()
        }
        return digest.digestHex() == file.sha256
    }

    // ---- publish

    private suspend fun publish(spec: ModelPackSpec): Outcome<Unit> =
        withContext(NonCancellable + dispatchers.io) {
            val dir = packDir(spec)
            val published = mutableListOf<String>()
            val backedUp = mutableListOf<String>()
            try {
                files.createDirectories(dir)
                files.delete("$dir/${VerifiedStamp.NAME}")
                for (file in spec.files) {
                    val target = "$dir/${file.name}"
                    if (files.size(target) != null) {
                        files.move(target, "$target$BACKUP")
                        backedUp += target
                    }
                    files.move(partPath(spec, file), target)
                    published += target
                }
                val tmp = "${markerPath(spec)}.tmp"
                files.writeText(tmp, recordJson.encodeToString(InstalledMarker.serializer(), InstalledMarker.of(spec)))
                files.move(tmp, markerPath(spec))
                writeStamp(spec)
                Unit.ok()
            } catch (e: Throwable) {
                // "Re-download keeps the verified old model on failure": take the new files out, put the old back.
                log.w(e) { "publish failed; rolling back" }
                for (path in published) orNullOnIoError { files.delete(path) }
                for (path in backedUp) orNullOnIoError { files.move("$path$BACKUP", path) }
                orNullOnIoError { files.delete("${markerPath(spec)}.tmp") }
                if (e is StorageFullException) media(MediaProblem.DownloadNoSpace, e) else media(MediaProblem.DownloadIncomplete, e)
            }
        }

    private fun cleanAfterPublish(spec: ModelPackSpec) {
        orNullOnIoError {
            for (file in spec.files) files.delete("${packDir(spec)}/${file.name}$BACKUP")
            files.deleteTree(stagingDir(spec))
        }
    }

    private fun cleanStaging(spec: ModelPackSpec) {
        orNullOnIoError { files.deleteTree(stagingDir(spec)) }
    }

    // ---- records

    private fun fileIn(spec: ModelPackSpec, file: ModelFile) = "${packDir(spec)}/${file.name}"

    private fun readMarker(spec: ModelPackSpec): InstalledMarker? {
        val text = files.readText(markerPath(spec)) ?: return null
        return orNullOnIoError { recordJson.decodeFromString(InstalledMarker.serializer(), text) }
    }

    private suspend fun invalidate(spec: ModelPackSpec) {
        if (lockOf(spec).isLocked) return // an install owns the folder right now
        orNullOnIoError {
            files.delete(markerPath(spec))
            files.delete(stampPath(spec))
        }
    }

    /** SHA-256 of the first and of the last 1 MiB (the whole file when shorter): about 2 MiB read per file. */
    private fun edgeHashes(path: String, length: Long): Pair<String, String> {
        val edge = VerifiedStamp.EDGE
        return hashRange(path, 0, minOf(edge, length)) to hashRange(path, (length - edge).coerceAtLeast(0), length)
    }

    private fun hashRange(path: String, from: Long, to: Long): String {
        val digest = digests.sha256()
        val source = files.openRead(path, from)
        try {
            val buffer = ByteArray(64 * 1024)
            var position = from
            while (position < to) {
                val n = source.read(buffer)
                if (n < 0) break
                val take = minOf(n.toLong(), to - position).toInt()
                digest.update(buffer, 0, take)
                position += take
            }
        } finally {
            source.close()
        }
        return digest.digestHex()
    }

    private fun stampMatches(spec: ModelPackSpec): Boolean {
        val text = files.readText(stampPath(spec)) ?: return false
        val stamp = orNullOnIoError { recordJson.decodeFromString(VerifiedStamp.serializer(), text) } ?: return false
        if (stamp.v != 1 || stamp.files.map { it.name } != spec.files.map { it.name }) return false
        return spec.files.zip(stamp.files).all { (file, recorded) ->
            recorded.length == file.bytes &&
                edgeHashes(fileIn(spec, file), file.bytes) == (recorded.headSha256 to recorded.tailSha256)
        }
    }

    private fun writeStamp(spec: ModelPackSpec) {
        val stamp =
            VerifiedStamp(
                files =
                    spec.files.map { file ->
                        val (head, tail) = edgeHashes(fileIn(spec, file), file.bytes)
                        VerifiedStamp.StampFile(file.name, file.bytes, head, tail)
                    },
                verifiedAtMs = clock.now().toEpochMilliseconds(),
            )
        val tmp = "${stampPath(spec)}.tmp"
        files.writeText(tmp, recordJson.encodeToString(VerifiedStamp.serializer(), stamp))
        files.move(tmp, stampPath(spec))
    }

    private suspend fun attemptIo(block: () -> Unit): Outcome<Unit> =
        withContext(NonCancellable + dispatchers.io) {
            try {
                block()
                Unit.ok()
            } catch (e: Exception) {
                log.w(e) { "voice model files could not be removed" }
                Failure.StorageRefused(Redacted.shape(e)).failed()
            }
        }

    private enum class Phase { Downloading, Verifying, Publishing }

    /** ≥ 100 ms and ≥ 1 % between updates (plan 10 §7.4 "Progress"); a failing listener never breaks the download. */
    private class ProgressThrottle(
        private val total: Long,
        private val clock: Clock,
        private val listener: (InstallProgress) -> Unit,
    ) {
        private var lastAt: Instant? = null
        private var lastBytes = -1L

        fun report(received: Long) {
            val now = clock.now()
            val step = total / 100
            val due =
                lastAt == null || received == total ||
                    (now - lastAt!! >= 100.milliseconds && received - lastBytes >= step)
            if (!due || received == lastBytes) return
            lastAt = now
            lastBytes = received
            emit(InstallProgress.Downloading(received, total))
        }

        fun verifying() = emit(InstallProgress.Verifying)

        private fun emit(progress: InstallProgress) {
            try {
                listener(progress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // B-MEDIA-026: a progress failure never fails the download.
            }
        }
    }

    internal companion object {
        const val MAX_REDIRECTS = 5
        const val BACKUP = ".voice-backup"

        /** Absolute Location, or a path on the current origin. */
        fun resolveLocation(current: String, location: String): String? {
            if (location.startsWith("https://") || location.startsWith("http://")) return location
            if (!location.startsWith("/")) return null
            val schemeEnd = current.indexOf("://")
            if (schemeEnd < 0) return null
            val hostEnd = current.indexOf('/', schemeEnd + 3).let { if (it < 0) current.length else it }
            return current.substring(0, hostEnd) + location
        }

        fun media(problem: MediaProblem, error: Throwable? = null, status: Int? = null): Outcome<Nothing> =
            Failure.Media(
                problem,
                when {
                    error != null || status != null -> Redacted.shape(error, status)
                    else -> null
                },
            ).failed()

        inline fun <T> orNullOnIoError(block: () -> T): T? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
    }
}
