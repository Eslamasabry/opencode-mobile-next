package io.github.eslamasabry.oc.android.camera.access

import io.github.eslamasabry.oc.core.common.MediaProblem

/** What the person can do on a scanner stage. Paste is always there: a hidden or broken camera never blocks pairing. */
public enum class ScannerAction { AllowCamera, OpenSettings, TryAgain, PasteInstead }

/**
 * The pairing scanner's stages (plan 10 §14.2). No copy here: the screen picks the words in `:ui:words` (the
 * `e7SetupCamera*`, `pairingScanner*` strings and the [problem]'s sentence), in plain words:
 * Denied "The camera is needed to scan the code.", PermanentlyDenied "Android blocks the camera for this app.",
 * NoCamera "This device has no camera.", Failed "Camera failed" with redacted details.
 */
public enum class ScannerStage(public val problem: MediaProblem?, primary: ScannerAction?) {
    /** The camera is opening; after [StartingNotice.SLOW_AFTER] "This is taking longer than usual". */
    Starting(null, null),
    Scanning(null, null),

    /** Android will ask again, so retrying is honest. */
    Denied(MediaProblem.CameraDenied, ScannerAction.AllowCamera),

    /** Android will not ask again: only app settings can change it, so there is no retry button. */
    PermanentlyDenied(MediaProblem.CameraDeniedForever, ScannerAction.OpenSettings),

    /** No camera (or the platform has none, like desktop): never ask for a permission it can't use. */
    NoCamera(MediaProblem.NoCamera, null),
    Failed(MediaProblem.CameraFailed, ScannerAction.TryAgain),
    ;

    public val actions: List<ScannerAction> = listOfNotNull(primary, ScannerAction.PasteInstead)
}
