package io.github.eslamasabry.oc.android.camera.access

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The camera permission flow of the pairing scanner (plan 10 §14.2, §6.8 table; port of the Flutter
 * `PairingScannerScreen` permission stages). The screen's ViewModel calls it; it never runs from composition.
 */
public class CameraAccessFlow(private val gate: PermissionGate, private val hardware: CameraHardware) {
    /** Opening the scanner: no camera → NoCamera with no prompt; granted → Starting; else ask once. */
    public suspend fun open(): ScannerStage {
        if (!cameraPresent()) return ScannerStage.NoCamera
        return when (val status = statusOrDenied()) {
            PermissionStatus.Granted, PermissionStatus.NotApplicable -> stageFor(status)
            PermissionStatus.Denied, PermissionStatus.PermanentlyDenied -> request()
        }
    }

    /** "Allow camera" on the Denied stage: Android asks again. */
    public suspend fun allowCamera(): ScannerStage =
        if (cameraPresent()) request() else ScannerStage.NoCamera

    /** "Open app settings" on the PermanentlyDenied stage. */
    public fun openSettings() {
        gate.openSettings(AppPermission.Camera)
    }

    /** Back on screen (ON_RESUME, e.g. from app settings): re-read the status; never prompts. */
    public suspend fun recheck(): ScannerStage =
        if (cameraPresent()) stageFor(statusOrDenied()) else ScannerStage.NoCamera

    /** A probe that fails is "no camera", never a false yes (B-ANDR-049). */
    private fun cameraPresent(): Boolean =
        try {
            hardware.hasCamera()
        } catch (_: RuntimeException) {
            false
        }

    private suspend fun request(): ScannerStage {
        val answer = attempt({ Failure.Device(Redacted.shape(it)) }) { gate.request(AppPermission.Camera) }
        return stageFor((answer as? Outcome.Ok)?.value ?: PermissionStatus.Denied)
    }

    private suspend fun statusOrDenied(): PermissionStatus {
        val status = attempt({ Failure.Device(Redacted.shape(it)) }) { gate.status(AppPermission.Camera) }
        return (status as? Outcome.Ok)?.value ?: PermissionStatus.Denied
    }

    private fun stageFor(status: PermissionStatus): ScannerStage =
        when (status) {
            PermissionStatus.Granted -> ScannerStage.Starting
            PermissionStatus.Denied -> ScannerStage.Denied
            PermissionStatus.PermanentlyDenied -> ScannerStage.PermanentlyDenied
            PermissionStatus.NotApplicable -> ScannerStage.NoCamera
        }
}

/** Camera errors as plain `Failure.Media` values; details are the exception's class name only (never its message). */
public object CameraFailures {
    public fun classify(error: Throwable): Failure =
        Failure.Media(
            if (error is SecurityException) MediaProblem.CameraDenied else MediaProblem.CameraFailed,
            Redacted.shape(error),
        )

    public fun stageFor(failure: Failure): ScannerStage =
        when ((failure as? Failure.Media)?.problem) {
            MediaProblem.CameraDenied -> ScannerStage.Denied
            MediaProblem.CameraDeniedForever -> ScannerStage.PermanentlyDenied
            MediaProblem.NoCamera -> ScannerStage.NoCamera
            else -> ScannerStage.Failed
        }
}

/** The Starting stage's "This is taking longer than usual" after [SLOW_AFTER]. Create it when the camera starts. */
@OptIn(ExperimentalTime::class)
public class StartingNotice(private val clock: Clock) {
    private val startedAt = clock.now()

    public fun isSlow(): Boolean = clock.now() - startedAt >= SLOW_AFTER

    public companion object {
        public val SLOW_AFTER: Duration = 8.seconds
    }
}
