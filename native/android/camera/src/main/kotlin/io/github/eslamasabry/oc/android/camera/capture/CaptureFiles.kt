package io.github.eslamasabry.oc.android.camera.capture

import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Camera capture targets (plan 10 §10.4, §10.5): `files/camera-capture-v1/<captureId>.jpg` (BlobArea.CameraCapture,
 * C10-22), where the id is microseconds since the epoch as text, as the pending-photo record stores it. Ids that are
 * not plain ASCII digits never name a file. The FileProvider root `camera` (C10-14) points at the same folder.
 */
public class CaptureFiles(filesDir: File) {
    private val dir = File(filesDir, DIR)

    @OptIn(ExperimentalTime::class)
    public fun newCaptureId(clock: Clock): String {
        val now = clock.now()
        return (now.epochSeconds * 1_000_000L + now.nanosecondsOfSecond / 1_000).toString()
    }

    /** The target for [captureId], creating the folder; null for an id that is not 1–20 ASCII digits. */
    public fun fileFor(captureId: String): File? {
        if (!isValid(captureId)) return null
        dir.mkdirs()
        return File(dir, "$captureId.jpg")
    }

    /** The camera wrote something: the file exists and is not empty. */
    public fun hasPhoto(captureId: String): Boolean {
        if (!isValid(captureId)) return false
        val f = File(dir, "$captureId.jpg")
        return f.isFile && f.length() > 0
    }

    /** Cancel, denial or failure: no file is left behind. Idempotent. */
    public fun discard(captureId: String) {
        if (isValid(captureId)) File(dir, "$captureId.jpg").delete()
    }

    /** At start: deletes every capture file whose id has no pending record; returns how many went. */
    public fun sweepStale(keep: Set<String>): Int {
        val keepNames = keep.filter(::isValid).mapTo(HashSet()) { "$it.jpg" }
        return dir.listFiles().orEmpty().count { it.isFile && it.name !in keepNames && it.delete() }
    }

    private fun isValid(id: String): Boolean = id.length in 1..20 && id.all { it in '0'..'9' }

    public companion object {
        public const val DIR: String = "camera-capture-v1"
    }
}

/** Takes one photo into [target] (an app-owned file from [CaptureFiles]). Ok(true) saved, Ok(false) cancelled. */
public interface PhotoCapturer {
    public suspend fun capture(target: File): io.github.eslamasabry.oc.core.common.Outcome<Boolean>
}
